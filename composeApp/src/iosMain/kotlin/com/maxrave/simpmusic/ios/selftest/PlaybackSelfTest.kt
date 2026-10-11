package com.maxrave.simpmusic.ios.selftest

import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.data.player.GenericMediaMetadata
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.player.MediaPlayerInterface
import com.maxrave.domain.repository.StreamRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.lastOrNull
import org.koin.mp.KoinPlatform

/**
 * Launched with `-kuroPlaybackSelfTest` (the simulator CI does this): resolves a real song through
 * the same repository the player uses, then plays it through the app's own player and reports
 * whether the position moved. "Loads forever" shows up here as `url=null` or `pos=0`.
 */
suspend fun playbackSelfTest(videoId: String = "dQw4w9WgXcQ"): String {
    val koin = KoinPlatform.getKoin()
    val url =
        runCatching {
            koin.get<StreamRepository>()
                .getStream(koin.get<DataStoreManager>(), videoId, isDownloading = false, isVideo = false, muxed = false)
                .lastOrNull()
        }.getOrElse { "ERROR ${it::class.simpleName}: ${it.message}" }
    val itag = url?.let { Regex("[?&]itag=(\\d+)").find(it)?.groupValues?.get(1) }
    val player = koin.get<MediaPlayerInterface>()
    player.setMediaItem(GenericMediaItem(videoId, null, GenericMediaMetadata(title = "self-test")))
    player.prepare()
    player.playWhenReady = true
    player.play()
    var position = 0L
    var waited = 0
    while (waited < 25 && position <= 3000) {
        delay(1000)
        waited++
        position = player.currentPosition
    }
    val playing = player.isPlaying
    player.pause()
    return "url=${if (url == null) "null" else if (url.startsWith("ERROR")) url else "ok itag=$itag"} pos=${position}ms playing=$playing"
}
