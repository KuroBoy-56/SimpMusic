package com.maxrave.simpmusic.ios.selftest

import com.maxrave.data.io.fileDir
import com.maxrave.data.mediaservice.ios.AvPlayerAdapter
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.data.player.GenericMediaMetadata
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.player.MediaPlayerInterface
import com.maxrave.domain.repository.StreamRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.lastOrNull
import org.koin.mp.KoinPlatform
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber

/**
 * Launched with `-kuroPlaybackSelfTest` (the simulator CI does this). It exercises the REAL path
 * the UI uses — the [MediaPlayerHandler], not the player adapter directly — so a break in the
 * handler layer reproduces here. It also resolves the stream once on its own to report the itag.
 * "Loads forever" shows up as `handlerPos=0`.
 */
suspend fun playbackSelfTest(videoId: String = "dQw4w9WgXcQ"): String {
    val koin = KoinPlatform.getKoin()

    // 1) Stream resolution on its own, same as the adapter does, to report the itag/url.
    val url =
        runCatching {
            koin.get<StreamRepository>()
                .getStream(koin.get<DataStoreManager>(), videoId, isDownloading = false, isVideo = false, muxed = false)
                .lastOrNull()
        }.getOrElse { "ERROR ${it::class.simpleName}: ${it.message}" }
    val itag = url?.let { Regex("[?&]itag=(\\d+)").find(it)?.groupValues?.get(1) }

    // iOS now plays HLS first (resolveSource); report whether the manifest resolves.
    val hls =
        runCatching {
            koin.get<StreamRepository>()
                .getStream(koin.get<DataStoreManager>(), videoId, isDownloading = false, isVideo = false, muxed = true)
                .lastOrNull()
        }.getOrElse { "ERROR ${it::class.simpleName}: ${it.message}" }
    val hlsDesc = if (hls == null) "null" else if (hls.startsWith("ERROR")) hls else if (hls.contains(".m3u8")) "ok m3u8" else "ok"

    // 2) The UI path: handler.clearMediaItems() + handler.addMediaItem(item, playWhenReady = true).
    val handler = koin.get<MediaPlayerHandler>()
    val item = GenericMediaItem(mediaId = videoId, uri = videoId, metadata = GenericMediaMetadata(title = "self-test"))
    val handlerResult =
        runCatching {
            handler.clearMediaItems()
            handler.addMediaItem(item, playWhenReady = true)
            var pos = 0L
            var waited = 0
            while (waited < 25 && pos <= 3000) {
                delay(1000)
                waited++
                pos = handler.getProgress()
            }
            val source = (koin.get<MediaPlayerInterface>() as? AvPlayerAdapter)?.currentSourceName
            "handlerPos=${pos}ms handlerPlaying=${handler.controlState.value.isPlaying} dur=${handler.getPlayerDuration()}ms source=$source"
        }.getOrElse { "HANDLER_ERROR ${it::class.simpleName}: ${it.message}" }

    // 3) The adapter directly, for comparison — isolates handler vs adapter.
    val adapterResult =
        runCatching {
            val player = koin.get<MediaPlayerInterface>()
            player.setMediaItem(GenericMediaItem(videoId, null, GenericMediaMetadata(title = "adapter-test")))
            player.prepare()
            player.playWhenReady = true
            player.play()
            var pos = 0L
            var waited = 0
            while (waited < 20 && pos <= 3000) {
                delay(1000)
                waited++
                pos = player.currentPosition
            }
            player.pause()
            "adapterPos=${pos}ms adapterPlaying=${player.isPlaying}"
        }.getOrElse { "ADAPTER_ERROR ${it::class.simpleName}: ${it.message}" }

    // 4) A real download through the iOS download handler, then the size of the file it wrote.
    val downloadResult =
        runCatching {
            val downloadId = "jNQXAC9IVRw"
            koin.get<DownloadHandler>().downloadTrack(downloadId, "self-test", "")
            val path = fileDir() + "/download/$downloadId.m4a"
            val size =
                (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)
                    ?.longLongValue ?: -1L
            NSFileManager.defaultManager.removeItemAtPath(path, null)
            "download=${if (size > 0) "ok ${size}B" else "FAILED"}"
        }.getOrElse { "DOWNLOAD_ERROR ${it::class.simpleName}: ${it.message}" }

    val urlDesc = if (url == null) "null" else if (url.startsWith("ERROR")) url else "ok itag=$itag"
    return "url=$urlDesc | hls=$hlsDesc | $handlerResult | $adapterResult | $downloadResult"
}
