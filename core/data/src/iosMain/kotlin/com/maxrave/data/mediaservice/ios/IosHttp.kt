@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.maxrave.data.mediaservice.ios

import com.maxrave.logger.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.writeData
import kotlin.coroutines.resume

private const val TAG = "IosHttp"

/** User agents the direct-client stream URLs were issued to (`c=` in the URL names the client). */
private const val USER_AGENT_IOS =
    "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
private const val USER_AGENT_ANDROID_VR =
    "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

/**
 * googlevideo is handed the user agent of the client its URL was issued to. A URL from the
 * ANDROID_VR client carries `c=ANDROID_VR` (query or `/c/ANDROID_VR/` path segment); everything
 * else on iOS came from the IOS client, HLS included.
 */
internal fun streamUserAgentFor(url: String): String =
    if (url.contains("c=ANDROID_VR") || url.contains("/c/ANDROID_VR/")) USER_AGENT_ANDROID_VR else USER_AGENT_IOS

/**
 * Plain NSURLSession helpers for the iOS player and downloader: they talk to googlevideo directly,
 * with the client's user agent and explicit Range requests, so neither depends on how AVPlayer or
 * a single long download is treated by the CDN.
 */
internal object IosHttp {
    class Response(
        val status: Int,
        val data: NSData?,
        val error: String?,
    )

    suspend fun request(
        url: String,
        range: LongRange? = null,
        timeoutSeconds: Double = 20.0,
    ): Response =
        suspendCancellableCoroutine { cont ->
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) {
                cont.resume(Response(0, null, "bad url"))
                return@suspendCancellableCoroutine
            }
            val request = NSMutableURLRequest.requestWithURL(nsUrl)
            request.setHTTPMethod("GET")
            request.setTimeoutInterval(timeoutSeconds)
            request.setValue(streamUserAgentFor(url), forHTTPHeaderField = "User-Agent")
            if (range != null) request.setValue("bytes=${range.first}-${range.last}", forHTTPHeaderField = "Range")
            val task =
                NSURLSession.sharedSession.dataTaskWithRequest(request) { data: NSData?, response: NSURLResponse?, error: NSError? ->
                    val status = ((response as? NSHTTPURLResponse)?.statusCode ?: 0L).toInt()
                    if (cont.isActive) cont.resume(Response(status, data, error?.localizedDescription))
                }
            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }

    /** The body of [url] as text, or null on any failure (used for HLS playlists). */
    suspend fun fetchText(url: String): String? {
        val response = request(url)
        if (response.status !in 200..299 || response.data == null) {
            Logger.w(TAG, "GET text failed: HTTP ${response.status} ${response.error.orEmpty()}")
            return null
        }
        return NSString.create(data = response.data, encoding = NSUTF8StringEncoding)?.toString()
    }

    /**
     * Downloads [url] (a progressive googlevideo stream of [totalLength] bytes) into [destPath] in
     * Range chunks. googlevideo throttles one long request to roughly playback speed, so a single
     * download crawls and can be cut off; chunked requests are served at full speed (yt-dlp does
     * the same). Returns null on success, otherwise a short reason.
     */
    suspend fun downloadInChunks(
        url: String,
        totalLength: Long,
        destPath: String,
        chunkSize: Long = 2L * 1024 * 1024,
        onProgress: (Long) -> Unit = {},
    ): String? {
        val fm = NSFileManager.defaultManager
        fm.removeItemAtPath(destPath, null)
        if (!fm.createFileAtPath(destPath, null, null)) return "cannot create file"
        val handle = NSFileHandle.fileHandleForWritingAtPath(destPath) ?: return "cannot open file"
        try {
            var offset = 0L
            while (offset < totalLength) {
                val end = minOf(offset + chunkSize, totalLength) - 1
                var response: Response? = null
                for (attempt in 0 until 3) {
                    response = request(url, offset..end, timeoutSeconds = 30.0)
                    if (response.status in 200..299 && response.data != null) break
                }
                val data = response?.data
                if (response == null || response.status !in 200..299 || data == null) {
                    return "HTTP ${response?.status} at byte $offset ${response?.error.orEmpty()}".trim()
                }
                val length = data.length.toLong()
                if (length <= 0L) return "empty chunk at byte $offset"
                handle.seekToEndOfFile()
                handle.writeData(data)
                offset += length
                onProgress(offset)
            }
            return null
        } finally {
            handle.closeFile()
        }
    }
}
