@file:OptIn(ExperimentalForeignApi::class)

package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.UIKitView
import com.maxrave.data.mediaservice.ios.IosPlayerVideoOutput
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.domain.data.model.streams.TimeLine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVFoundation.volume
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView

/** A UIView whose layer tracks its bounds, since a sublayer does not resize on its own. */
private class PlayerLayerView(
    val playerLayer: AVPlayerLayer,
) : UIView(frame = CGRectZero.readValue()) {
    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.frame = bounds
        CATransaction.commit()
    }
}

/** Spotify canvas / animated artwork: a muted loop of [url]. */
@Composable
actual fun MediaPlayerView(
    url: String,
    modifier: Modifier,
    cropToBounds: Boolean,
) {
    val player =
        remember(url) {
            AVPlayer(uRL = NSURL.URLWithString(url) ?: NSURL.URLWithString("about:blank")!!).apply { volume = 0f }
        }
    DisposableEffect(player) {
        val observer =
            NSNotificationCenter.defaultCenter.addObserverForName(
                AVPlayerItemDidPlayToEndTimeNotification,
                player.currentItem,
                NSOperationQueue.mainQueue,
            ) { _ ->
                player.seekToTime(kCMTimeZero.readValue())
                player.play()
            }
        player.play()
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            player.pause()
        }
    }
    UIKitView(
        factory = {
            PlayerLayerView(
                AVPlayerLayer.playerLayerWithPlayer(player).apply {
                    videoGravity = if (cropToBounds) AVLayerVideoGravityResizeAspectFill else AVLayerVideoGravityResizeAspect
                },
            )
        },
        modifier = modifier,
    )
}

/** The main player's picture when the current track plays as video. Subtitles are not drawn on iOS. */
@Composable
actual fun MediaPlayerViewWithSubtitle(
    modifier: Modifier,
    playerName: String,
    shouldPip: Boolean,
    shouldShowSubtitle: Boolean,
    shouldScaleDownSubtitle: Boolean,
    isInPipMode: Boolean,
    timelineState: TimeLine,
    lyricsData: Lyrics?,
    translatedLyricsData: Lyrics?,
    mainTextStyle: TextStyle,
    translatedTextStyle: TextStyle,
) {
    val player by IosPlayerVideoOutput.player.collectAsState()
    val isVideo by IosPlayerVideoOutput.isVideo.collectAsState()
    val current = player ?: return
    if (!isVideo) return
    UIKitView(
        factory = {
            PlayerLayerView(
                AVPlayerLayer.playerLayerWithPlayer(current).apply { videoGravity = AVLayerVideoGravityResizeAspect },
            )
        },
        modifier = modifier,
        update = { view -> view.playerLayer.player = current },
    )
}

