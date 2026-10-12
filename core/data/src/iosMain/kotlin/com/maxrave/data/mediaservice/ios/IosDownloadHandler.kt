@file:OptIn(ExperimentalForeignApi::class)

package com.maxrave.data.mediaservice.ios

import com.maxrave.common.MERGING_DATA_TYPE
import com.maxrave.data.io.fileDir
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.repository.StreamRepository
import com.maxrave.logger.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.downloadTaskWithRequest
import platform.Foundation.setValue
import kotlin.coroutines.resume

private const val TAG = "IosDownloadHandler"

private const val STREAM_USER_AGENT =
    "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"

/**
 * iOS offline downloads.
 *
 * The shared [DownloadHandler] on Android/Desktop goes through `YouTube.download`, which picks the
 * highest-bitrate audio (Opus in a WebM container) and then remuxes it to mp3 with FFmpeg. Neither
 * step works on iOS: AVPlayer has no Opus/WebM decoder, and there is no FFmpeg build. So this
 * downloads exactly the stream iOS *plays* — the progressive AAC format (itag 140/141) that
 * [StreamRepository.getStream] already resolves here (because `onlyAacAudioPlayable` is true) — and
 * saves it as a plain `.m4a`. No muxing, no conversion.
 *
 * The file lands in `<fileDir>/download/<videoId>.m4a`, which is exactly where the player's
 * `localFileFor()` looks, so a downloaded song plays offline with no other wiring. The per-song
 * download button in the UI follows `SongEntity.downloadState`, which this drives through
 * [SongRepository.updateDownloadState]; the two flows mirror that for the playlist-level UI.
 */
internal class IosDownloadHandler(
    private val streamRepository: StreamRepository,
    private val songRepository: SongRepository,
    private val dataStoreManager: DataStoreManager,
) : DownloadHandler {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloads =
        MutableStateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>>(emptyMap())
    override val downloads: StateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>> =
        _downloads.asStateFlow()

    private val _downloadTask = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val downloadTask: StateFlow<Map<String, Int>> = _downloadTask.asStateFlow()

    private fun downloadDir(): String = fileDir() + "/download"

    /** Mirrors a per-track state into both UI flows; a null [state] drops the entry. */
    private fun publish(
        videoId: String,
        state: Int?,
    ) {
        _downloadTask.update { if (state == null) it - videoId else it + (videoId to state) }
        _downloads.update {
            if (state == null) it - videoId else it + (videoId to Pair(DownloadHandler.Download(state), null))
        }
    }

    override suspend fun downloadTrack(
        videoId: String,
        title: String,
        thumbnail: String,
    ) {
        songRepository.updateDownloadState(videoId, DownloadState.STATE_DOWNLOADING)
        publish(videoId, DownloadState.STATE_DOWNLOADING)

        val url =
            runCatching {
                streamRepository
                    .getStream(
                        dataStoreManager = dataStoreManager,
                        videoId = videoId,
                        isDownloading = true,
                        isVideo = false,
                        muxed = false,
                    ).lastOrNull()
            }.getOrNull()

        if (url.isNullOrEmpty()) {
            Logger.e(TAG, "No AAC stream resolved for $videoId — cannot download")
            fail(videoId)
            return
        }

        createDownloadDir()
        val dest = downloadDir() + "/$videoId.m4a"
        val ok =
            runCatching { fetchToFile(url, dest) }
                .onFailure { Logger.e(TAG, "Download of $videoId failed: ${it.message}") }
                .getOrDefault(false)

        if (ok) {
            Logger.d(TAG, "Downloaded $videoId to $dest")
            songRepository.updateDownloadState(videoId, DownloadState.STATE_DOWNLOADED)
            publish(videoId, DownloadState.STATE_DOWNLOADED)
        } else {
            NSFileManager.defaultManager.removeItemAtPath(dest, null)
            fail(videoId)
        }
    }

    private suspend fun fail(videoId: String) {
        songRepository.updateDownloadState(videoId, DownloadState.STATE_NOT_DOWNLOADED)
        publish(videoId, null)
    }

    private fun createDownloadDir() {
        NSFileManager.defaultManager.createDirectoryAtPath(
            downloadDir(),
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    /**
     * Downloads [url] to [destPath] with NSURLSession. The download task streams to a temp file on
     * disk (so a large song never sits in memory), and the completion block moves it into place
     * before returning — the temp file is deleted the moment the block returns, so the move cannot
     * be deferred to a coroutine.
     */
    private suspend fun fetchToFile(
        url: String,
        destPath: String,
    ): Boolean =
        suspendCancellableCoroutine { cont ->
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            val request = NSMutableURLRequest.requestWithURL(nsUrl)
            request.setValue(STREAM_USER_AGENT, forHTTPHeaderField = "User-Agent")

            val task: NSURLSessionDownloadTask =
                NSURLSession.sharedSession.downloadTaskWithRequest(request) { location: NSURL?, response: NSURLResponse?, error: NSError? ->
                    if (error != null || location == null) {
                        Logger.e(TAG, "NSURLSession error: ${error?.localizedDescription}")
                        if (cont.isActive) cont.resume(false)
                        return@downloadTaskWithRequest
                    }
                    val status = ((response as? NSHTTPURLResponse)?.statusCode ?: 0L).toInt()
                    if (status !in 200..299) {
                        Logger.e(TAG, "HTTP $status for download")
                        if (cont.isActive) cont.resume(false)
                        return@downloadTaskWithRequest
                    }
                    val srcPath = location.path
                    if (srcPath == null) {
                        if (cont.isActive) cont.resume(false)
                        return@downloadTaskWithRequest
                    }
                    val fm = NSFileManager.defaultManager
                    fm.removeItemAtPath(destPath, null)
                    val moved = fm.moveItemAtPath(srcPath, destPath, null)
                    if (cont.isActive) cont.resume(moved)
                }
            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }

    override fun removeDownload(videoId: String) {
        val fm = NSFileManager.defaultManager
        val dir = downloadDir()
        val files = fm.contentsOfDirectoryAtPath(dir, null)?.filterIsInstance<String>().orEmpty()
        files
            .filter { it.contains(videoId) }
            .forEach { fm.removeItemAtPath("$dir/$it", null) }
        publish(videoId, null)
        scope.launch {
            songRepository.updateDownloadState(videoId, DownloadState.STATE_NOT_DOWNLOADED)
        }
    }

    override fun removeAllDownloads() {
        val fm = NSFileManager.defaultManager
        val dir = downloadDir()
        val files = fm.contentsOfDirectoryAtPath(dir, null)?.filterIsInstance<String>().orEmpty()
        _downloadTask.value = emptyMap()
        _downloads.value = emptyMap()
        files.forEach { name ->
            fm.removeItemAtPath("$dir/$name", null)
            val videoId = name.substringBeforeLast(".").removePrefix(MERGING_DATA_TYPE.VIDEO)
            scope.launch {
                songRepository.updateDownloadState(videoId, DownloadState.STATE_NOT_DOWNLOADED)
            }
        }
    }
}
