@file:OptIn(ExperimentalForeignApi::class)

package com.maxrave.data.mediaservice.ios

import com.maxrave.logger.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackQueueCount
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackQueueIndex
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingPlaybackStatePaused
import platform.MediaPlayer.MPNowPlayingPlaybackStatePlaying
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val TAG = "IosNowPlaying"

/** Remote commands from the lock screen, Control Center, headphones and CarPlay. */
interface IosRemoteCommandListener {
    fun onPlay()

    fun onPause()

    fun onTogglePlayPause()

    fun onStop()

    fun onNextTrack()

    fun onPreviousTrack()

    fun onSeekForward()

    fun onSeekBackward()

    fun onChangePlaybackPosition(positionSeconds: Double)
}

data class NowPlayingInfo(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Double,
    val elapsedTimeSeconds: Double = 0.0,
    val playbackRate: Double = 1.0,
    val artworkUrl: String? = null,
    val queueIndex: Int = 0,
    val queueCount: Int = 1,
)

/**
 * iOS counterpart of the desktop `MacOSMediaIntegration`, with the same surface so the player
 * handler drives both identically. Every MediaPlayer call is marshalled to the main queue.
 */
class IosNowPlayingIntegration private constructor() {
    private var listener: IosRemoteCommandListener? = null
    private var info: MutableMap<Any?, Any?> = mutableMapOf()
    private var initialized = false

    fun initialize(): Boolean {
        if (initialized) return true
        initialized = true
        onMain {
            val center = MPRemoteCommandCenter.sharedCommandCenter()
            center.playCommand.addTargetWithHandler { _ ->
                listener?.onPlay()
                MPRemoteCommandHandlerStatusSuccess
            }
            center.pauseCommand.addTargetWithHandler { _ ->
                listener?.onPause()
                MPRemoteCommandHandlerStatusSuccess
            }
            center.togglePlayPauseCommand.addTargetWithHandler { _ ->
                listener?.onTogglePlayPause()
                MPRemoteCommandHandlerStatusSuccess
            }
            center.stopCommand.addTargetWithHandler { _ ->
                listener?.onStop()
                MPRemoteCommandHandlerStatusSuccess
            }
            center.nextTrackCommand.addTargetWithHandler { _ ->
                listener?.onNextTrack()
                MPRemoteCommandHandlerStatusSuccess
            }
            center.previousTrackCommand.addTargetWithHandler { _ ->
                listener?.onPreviousTrack()
                MPRemoteCommandHandlerStatusSuccess
            }
            center.changePlaybackPositionCommand.addTargetWithHandler { event ->
                (event as? MPChangePlaybackPositionCommandEvent)?.let {
                    listener?.onChangePlaybackPosition(it.positionTime)
                }
                MPRemoteCommandHandlerStatusSuccess
            }
        }
        return true
    }

    fun setRemoteCommandListener(listener: IosRemoteCommandListener?) {
        this.listener = listener
    }

    fun updateNowPlayingInfo(nowPlaying: NowPlayingInfo) {
        val keepArtwork = info[MPMediaItemPropertyArtwork]?.takeIf { info[MPMediaItemPropertyTitle] == nowPlaying.title }
        info =
            mutableMapOf(
                MPMediaItemPropertyTitle to nowPlaying.title,
                MPMediaItemPropertyArtist to nowPlaying.artist,
                MPMediaItemPropertyAlbumTitle to nowPlaying.album,
                MPMediaItemPropertyPlaybackDuration to nowPlaying.durationSeconds,
                MPNowPlayingInfoPropertyElapsedPlaybackTime to nowPlaying.elapsedTimeSeconds,
                MPNowPlayingInfoPropertyPlaybackRate to nowPlaying.playbackRate,
                MPNowPlayingInfoPropertyPlaybackQueueIndex to nowPlaying.queueIndex.toLong(),
                MPNowPlayingInfoPropertyPlaybackQueueCount to nowPlaying.queueCount.toLong(),
            )
        if (keepArtwork != null) info[MPMediaItemPropertyArtwork] = keepArtwork
        publish()
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        info[MPNowPlayingInfoPropertyPlaybackRate] = if (isPlaying) 1.0 else 0.0
        publish()
        onMain {
            MPNowPlayingInfoCenter.defaultCenter().playbackState =
                if (isPlaying) MPNowPlayingPlaybackStatePlaying else MPNowPlayingPlaybackStatePaused
        }
    }

    fun updateCommandsEnabled(
        hasNext: Boolean,
        hasPrevious: Boolean,
        canSeek: Boolean,
    ) {
        onMain {
            val center = MPRemoteCommandCenter.sharedCommandCenter()
            center.nextTrackCommand.enabled = hasNext
            center.previousTrackCommand.enabled = hasPrevious
            center.changePlaybackPositionCommand.enabled = canSeek
        }
    }

    fun updateElapsedTime(
        elapsedSeconds: Double,
        rate: Double,
    ) {
        if (info.isEmpty()) return
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsedSeconds
        publish()
    }

    fun clearNowPlayingInfo() {
        info = mutableMapOf()
        onMain { MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null }
    }

    suspend fun loadAndSetArtwork(artworkUrl: String?) {
        if (artworkUrl.isNullOrEmpty()) return
        val title = info[MPMediaItemPropertyTitle]
        val image =
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = NSURL.URLWithString(artworkUrl) ?: return@runCatching null
                    val data = NSData.dataWithContentsOfURL(url) ?: return@runCatching null
                    UIImage.imageWithData(data)
                }.onFailure { Logger.w(TAG, "Artwork load failed: ${it.message}") }.getOrNull()
            } ?: return
        if (info[MPMediaItemPropertyTitle] != title) return
        val artwork = MPMediaItemArtwork(boundsSize = CGSizeMake(512.0, 512.0)) { _ -> image }
        info[MPMediaItemPropertyArtwork] = artwork
        publish()
    }

    fun release() {
        listener = null
        clearNowPlayingInfo()
    }

    private fun publish() {
        val snapshot = info.toMap()
        onMain { MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = snapshot }
    }

    private fun onMain(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) {
            runCatching(block).onFailure { Logger.e(TAG, "MediaPlayer call failed: ${it.message}") }
        }
    }

    companion object {
        private val instance by lazy { IosNowPlayingIntegration() }

        fun getInstance(): IosNowPlayingIntegration = instance

        fun isSupported(): Boolean = true
    }
}
