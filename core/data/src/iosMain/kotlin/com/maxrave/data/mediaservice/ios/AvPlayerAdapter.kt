@file:OptIn(ExperimentalForeignApi::class)

package com.maxrave.data.mediaservice.ios

import com.maxrave.common.MERGING_DATA_TYPE
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.data.player.GenericPlaybackParameters
import com.maxrave.domain.data.player.PlayerConstants
import com.maxrave.domain.data.player.PlayerError
import com.maxrave.domain.extension.isVideo
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.player.MediaPlayerInterface
import com.maxrave.domain.mediaservice.player.MediaPlayerListener
import com.maxrave.domain.repository.StreamRepository
import com.maxrave.logger.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.setActive
import kotlin.time.TimeSource
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.playImmediatelyAtRate
import platform.AVFoundation.setAutomaticallyWaitsToMinimizeStalling
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.loadedTimeRanges
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.AVFoundation.CMTimeRangeValue
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSValue
import platform.darwin.NSObjectProtocol
import kotlin.concurrent.Volatile
import kotlin.math.max

private const val TAG = "AvPlayerAdapter"
private const val PREPARE_TIMEOUT_NANOS = 25_000_000_000L // 25 s

/** User agent the direct-client stream URLs were issued to; googlevideo checks it against the URL. */
private const val STREAM_USER_AGENT =
    "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"

/**
 * The player whose picture the UI should draw, for [com.maxrave.simpmusic.expect.ui.MediaPlayerViewWithSubtitle]
 * on iOS. Null while nothing is loaded; [isVideo] tells the UI whether a picture exists at all.
 */
object IosPlayerVideoOutput {
    internal val playerFlow = MutableStateFlow<AVPlayer?>(null)
    internal val isVideoFlow = MutableStateFlow(false)

    val player: StateFlow<AVPlayer?> = playerFlow.asStateFlow()
    val isVideo: StateFlow<Boolean> = isVideoFlow.asStateFlow()
}

/**
 * AVPlayer implementation of [MediaPlayerInterface] for iOS.
 *
 * A structural port of the desktop `MpvPlayerAdapter`: the playlist, shuffle order, repeat modes,
 * listener contract and URL resolution are the same, so the shared player handler drives both the
 * same way. Not ported: crossfade/DJ mode, the equalizer and the delay/reverb effects (all no-ops
 * here — AVPlayer has no filter chain to hang them on) and pre-caching of the next track.
 *
 * Everything runs on [coroutineScope], a single-threaded scope, so the state machine has one writer.
 * AVPlayer itself is safe to drive from one consistent non-main thread.
 */
class AvPlayerAdapter(
    private val coroutineScope: CoroutineScope,
    private val dataStoreManager: DataStoreManager,
    private val streamRepository: StreamRepository,
) : MediaPlayerInterface {
    private enum class InternalState { IDLE, PREPARING, READY, PLAYING, PAUSED, ENDED, ERROR }

    private val avPlayer = AVPlayer()
    private val listeners = mutableListOf<MediaPlayerListener>()

    private val playlist = mutableListOf<GenericMediaItem>()
    private val shuffleOrder = mutableListOf<Int>()
    private val shuffleIndices = mutableListOf<Int>()

    @Volatile private var localCurrentMediaItemIndex = -1

    @Volatile private var internalState = InternalState.IDLE

    @Volatile private var internalPlayWhenReady = true

    @Volatile private var internalVolume = 1f

    @Volatile private var internalSleepFadeFactor = 1f

    @Volatile private var internalRepeatMode = PlayerConstants.REPEAT_MODE_OFF

    @Volatile private var internalShuffleModeEnabled = false

    @Volatile private var internalSpeed = 1f

    @Volatile private var internalPitch = 1f

    @Volatile private var cachedPosition = 0L

    @Volatile private var cachedDuration = 0L

    @Volatile private var cachedBufferedPosition = 0L

    @Volatile private var cachedIsLoading = false

    @Volatile private var watchVideoEnabled = false

    private var currentLoadJob: Job? = null
    private var pollJob: Job? = null
    private var endObserver: NSObjectProtocol? = null
    private var failObserver: NSObjectProtocol? = null
    private var interruptionObserver: NSObjectProtocol? = null
    private var retryVideoId: String? = null
    private var retryCount = 0
    private val maxRetryCount = 2

    // When the current item entered PREPARING, to give up if it never becomes ready (see polling).
    private val monotonic = TimeSource.Monotonic.markNow()

    private fun elapsedNanos(): Long = monotonic.elapsedNow().inWholeNanoseconds

    @Volatile private var preparingSinceNanos = 0L

    override var crossfadeSuppressed: Boolean = false
    override var albumTrackIds: Set<String> = emptySet()
    override var skipSilenceEnabled: Boolean = false

    init {
        activateAudioSession()
        // Default is true: AVPlayer then decides on its own when the buffer is "enough" and can sit
        // in WaitingToPlayAtSpecifiedRate forever without ever starting — the "loads forever, no
        // audio" seen on real devices (the simulator almost always starts, which is why it hid
        // here). false makes it start as soon as it has any data, like Android/ExoPlayer does.
        avPlayer.setAutomaticallyWaitsToMinimizeStalling(false)
        coroutineScope.launch {
            dataStoreManager.watchVideoInsteadOfPlayingAudio.collect {
                watchVideoEnabled = it == DataStoreManager.TRUE
            }
        }
        interruptionObserver =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVAudioSessionInterruptionNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { notification: NSNotification? ->
                val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
                if (type == AVAudioSessionInterruptionTypeBegan) {
                    Logger.d(TAG, "Audio session interrupted — pausing")
                    pause()
                }
            }
    }

    /** Background playback needs the Playback category plus `UIBackgroundModes=audio` in Info.plist. */
    private fun activateAudioSession() {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeDefault, options = 0u, error = null)
            session.setActive(true, error = null)
        }.onFailure { Logger.e(TAG, "Audio session activation failed: ${it.message}") }
    }

    // ========== Transport ==========

    override fun play() {
        coroutineScope.launch {
            internalPlayWhenReady = true
            when (internalState) {
                InternalState.READY, InternalState.PAUSED, InternalState.ENDED -> {
                    if (internalState == InternalState.ENDED) {
                        avPlayer.seekToTime(CMTimeMakeWithSeconds(0.0, 1000))
                        cachedPosition = 0L
                    }
                    activateAudioSession()
                    startAvPlayback()
                    transitionToState(InternalState.PLAYING)
                }

                InternalState.PREPARING -> {
                    if (!cachedIsLoading) {
                        cachedIsLoading = true
                        notifyListeners { onIsLoadingChanged(true) }
                    }
                }

                InternalState.IDLE, InternalState.ERROR -> {
                    if (localCurrentMediaItemIndex in playlist.indices) {
                        loadAndPlayTrackInternal(localCurrentMediaItemIndex, cachedPosition, true)
                    }
                }

                InternalState.PLAYING -> Unit
            }
        }
    }

    override fun pause() {
        coroutineScope.launch {
            internalPlayWhenReady = false
            if (internalState == InternalState.PLAYING || internalState == InternalState.READY) {
                avPlayer.pause()
                transitionToState(InternalState.PAUSED)
            }
            // Same contract as the other adapters: a finished sleep fade is cleared by the pause itself.
            if (internalSleepFadeFactor < 1f) {
                internalSleepFadeFactor = 1f
                applyVolume()
            }
        }
    }

    override fun stop() {
        coroutineScope.launch {
            avPlayer.pause()
            transitionToState(InternalState.IDLE)
            stopPolling()
        }
    }

    override fun seekTo(positionMs: Long) {
        cachedPosition = positionMs
        coroutineScope.launch {
            avPlayer.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000))
            notifyListeners { onSeeked(positionMs) }
        }
    }

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
    ) {
        coroutineScope.launch {
            if (mediaItemIndex !in playlist.indices) return@launch
            localCurrentMediaItemIndex = mediaItemIndex
            loadAndPlayTrackInternal(mediaItemIndex, positionMs, internalPlayWhenReady)
        }
    }

    override fun seekBack() = seekTo((cachedPosition - 5_000).coerceAtLeast(0))

    override fun seekForward() = seekTo((cachedPosition + 5_000).coerceAtMost(max(cachedDuration, 0)))

    override fun seekToNext() {
        coroutineScope.launch {
            if (hasNextMediaItem()) seekTo(getNextMediaItemIndex(), 0)
        }
    }

    override fun seekToPrevious() {
        coroutineScope.launch {
            if (cachedPosition > 3_000L || !hasPreviousMediaItem()) {
                seekTo(0L)
            } else {
                seekTo(getPreviousMediaItemIndex(), 0)
            }
        }
    }

    override fun seekToPreviousMediaItem() {
        coroutineScope.launch {
            if (hasPreviousMediaItem()) seekTo(getPreviousMediaItemIndex(), 0)
        }
    }

    override fun prepare() {
        coroutineScope.launch {
            if (localCurrentMediaItemIndex in playlist.indices && internalState == InternalState.IDLE) {
                loadAndPlayTrackInternal(localCurrentMediaItemIndex, 0, false)
            }
        }
    }

    // ========== Queue ==========

    override fun setMediaItem(mediaItem: GenericMediaItem) {
        coroutineScope.launch {
            currentLoadJob?.cancel()
            playlist.clear()
            playlist.add(mediaItem)
            localCurrentMediaItemIndex = 0
            if (internalShuffleModeEnabled) createShuffleOrder()
            notifyTimelineChanged()
            loadAndPlayTrackInternal(0, 0, internalPlayWhenReady)
        }
    }

    override fun addMediaItem(mediaItem: GenericMediaItem) {
        playlist.add(mediaItem)
        if (internalShuffleModeEnabled) createShuffleOrder()
        notifyTimelineChanged()
    }

    override fun addMediaItem(
        index: Int,
        mediaItem: GenericMediaItem,
    ) {
        if (index !in 0..playlist.size) return
        val currentBeforeInsert = localCurrentMediaItemIndex
        playlist.add(index, mediaItem)
        if (index <= localCurrentMediaItemIndex) localCurrentMediaItemIndex++
        if (internalShuffleModeEnabled) {
            if (currentBeforeInsert >= 0 && index == currentBeforeInsert + 1) {
                insertIntoShuffleOrder(index, shuffleIndices.getOrNull(currentBeforeInsert) ?: 0)
            } else {
                createShuffleOrder()
            }
        }
        notifyTimelineChanged()
    }

    override fun removeMediaItem(index: Int) {
        coroutineScope.launch {
            if (index !in playlist.indices) return@launch
            playlist.removeAt(index)
            when {
                index < localCurrentMediaItemIndex -> localCurrentMediaItemIndex--
                index == localCurrentMediaItemIndex -> {
                    if (localCurrentMediaItemIndex >= playlist.size) localCurrentMediaItemIndex = playlist.size - 1
                    if (localCurrentMediaItemIndex >= 0) {
                        loadAndPlayTrackInternal(localCurrentMediaItemIndex, 0, internalPlayWhenReady)
                    } else {
                        clearCurrentItem()
                    }
                }
            }
            if (internalShuffleModeEnabled) createShuffleOrder()
            notifyTimelineChanged()
        }
    }

    override fun removeMediaItems(
        fromIndex: Int,
        toIndex: Int,
    ) {
        coroutineScope.launch {
            if (fromIndex < 0 || toIndex <= fromIndex) return@launch
            if (toIndex > playlist.size || toIndex > localCurrentMediaItemIndex) return@launch
            val count = toIndex - fromIndex
            playlist.subList(fromIndex, toIndex).clear()
            localCurrentMediaItemIndex -= count
            if (internalShuffleModeEnabled) createShuffleOrder()
            notifyTimelineChanged()
        }
    }

    override fun moveMediaItem(
        fromIndex: Int,
        toIndex: Int,
    ) {
        coroutineScope.launch {
            if (fromIndex !in playlist.indices || toIndex !in playlist.indices) return@launch
            val item = playlist.removeAt(fromIndex)
            playlist.add(toIndex, item)
            localCurrentMediaItemIndex =
                when {
                    localCurrentMediaItemIndex == fromIndex -> toIndex
                    fromIndex < localCurrentMediaItemIndex && toIndex >= localCurrentMediaItemIndex -> localCurrentMediaItemIndex - 1
                    fromIndex > localCurrentMediaItemIndex && toIndex <= localCurrentMediaItemIndex -> localCurrentMediaItemIndex + 1
                    else -> localCurrentMediaItemIndex
                }
            if (internalShuffleModeEnabled) createShuffleOrder()
            notifyTimelineChanged()
        }
    }

    override fun clearMediaItems() {
        coroutineScope.launch {
            playlist.clear()
            localCurrentMediaItemIndex = -1
            shuffleOrder.clear()
            shuffleIndices.clear()
            notifyTimelineChanged()
            clearCurrentItem()
        }
    }

    override fun replaceMediaItem(
        index: Int,
        mediaItem: GenericMediaItem,
    ) {
        coroutineScope.launch {
            if (index !in playlist.indices) return@launch
            playlist[index] = mediaItem
            if (internalShuffleModeEnabled) createShuffleOrder()
            notifyTimelineChanged()
            if (index == localCurrentMediaItemIndex) {
                loadAndPlayTrackInternal(index, 0, internalPlayWhenReady)
            }
        }
    }

    override fun getMediaItemAt(index: Int): GenericMediaItem? = playlist.getOrNull(index)

    override fun getCurrentMediaTimeLine(): List<GenericMediaItem> = shuffledList()

    override fun getUnshuffledIndex(shuffledIndex: Int): Int =
        if (internalShuffleModeEnabled) shuffleOrder.getOrNull(shuffledIndex) ?: -1 else shuffledIndex

    // ========== State ==========

    override val isPlaying: Boolean get() = internalState == InternalState.PLAYING
    override val currentPosition: Long get() = cachedPosition
    override val duration: Long get() = cachedDuration
    override val bufferedPosition: Long get() = cachedBufferedPosition
    override val bufferedPercentage: Int
        get() = if (cachedDuration <= 0) 0 else ((cachedBufferedPosition * 100) / cachedDuration).toInt().coerceIn(0, 100)
    override val currentMediaItem: GenericMediaItem? get() = playlist.getOrNull(localCurrentMediaItemIndex)
    override val currentMediaItemIndex: Int get() = localCurrentMediaItemIndex
    override val mediaItemCount: Int get() = playlist.size
    override val contentPosition: Long get() = cachedPosition
    override val playbackState: Int
        get() =
            when (internalState) {
                InternalState.IDLE, InternalState.ERROR -> PlayerConstants.STATE_IDLE
                InternalState.PREPARING -> PlayerConstants.STATE_BUFFERING
                InternalState.READY, InternalState.PLAYING, InternalState.PAUSED -> PlayerConstants.STATE_READY
                InternalState.ENDED -> PlayerConstants.STATE_ENDED
            }

    override fun hasNextMediaItem(): Boolean =
        when (internalRepeatMode) {
            PlayerConstants.REPEAT_MODE_ONE, PlayerConstants.REPEAT_MODE_ALL -> playlist.isNotEmpty()
            else ->
                if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
                    val pos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: -1
                    pos >= 0 && pos < shuffleOrder.lastIndex
                } else {
                    localCurrentMediaItemIndex < playlist.size - 1
                }
        }

    override fun hasPreviousMediaItem(): Boolean =
        when (internalRepeatMode) {
            PlayerConstants.REPEAT_MODE_ONE, PlayerConstants.REPEAT_MODE_ALL -> playlist.isNotEmpty()
            else ->
                if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
                    (shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: -1) > 0
                } else {
                    localCurrentMediaItemIndex > 0
                }
        }

    private fun getNextMediaItemIndex(): Int {
        if (internalRepeatMode == PlayerConstants.REPEAT_MODE_ONE) return localCurrentMediaItemIndex
        val wrap = internalRepeatMode == PlayerConstants.REPEAT_MODE_ALL
        return if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
            val pos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: 0
            val next = pos + 1
            when {
                next < shuffleOrder.size -> shuffleOrder[next]
                wrap -> shuffleOrder[0]
                else -> localCurrentMediaItemIndex
            }
        } else {
            when {
                localCurrentMediaItemIndex < playlist.size - 1 -> localCurrentMediaItemIndex + 1
                wrap -> 0
                else -> localCurrentMediaItemIndex
            }
        }
    }

    private fun getPreviousMediaItemIndex(): Int {
        if (internalRepeatMode == PlayerConstants.REPEAT_MODE_ONE) return localCurrentMediaItemIndex
        val wrap = internalRepeatMode == PlayerConstants.REPEAT_MODE_ALL
        return if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
            val pos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: 0
            when {
                pos > 0 -> shuffleOrder[pos - 1]
                wrap -> shuffleOrder.last()
                else -> localCurrentMediaItemIndex
            }
        } else {
            when {
                localCurrentMediaItemIndex > 0 -> localCurrentMediaItemIndex - 1
                wrap -> playlist.size - 1
                else -> localCurrentMediaItemIndex
            }
        }
    }

    override var shuffleModeEnabled: Boolean
        get() = internalShuffleModeEnabled
        set(value) {
            if (internalShuffleModeEnabled == value) return
            internalShuffleModeEnabled = value
            if (value) {
                createShuffleOrder()
            } else {
                shuffleOrder.clear()
                shuffleIndices.clear()
            }
            val list = shuffledList()
            notifyListeners { onShuffleModeEnabledChanged(value, list) }
            notifyTimelineChanged()
        }

    override var repeatMode: Int
        get() = internalRepeatMode
        set(value) {
            if (internalRepeatMode == value) return
            internalRepeatMode = value
            notifyListeners { onRepeatModeChanged(value) }
        }

    override var playWhenReady: Boolean
        get() = internalPlayWhenReady
        set(value) {
            internalPlayWhenReady = value
            if (value) play() else pause()
        }

    override var playbackParameters: GenericPlaybackParameters
        get() = GenericPlaybackParameters(internalSpeed, internalPitch)
        set(value) {
            internalSpeed = value.speed.takeIf { it > 0f } ?: 1f
            internalPitch = value.pitch
            coroutineScope.launch {
                if (internalState == InternalState.PLAYING) avPlayer.rate = internalSpeed
            }
        }

    override val audioSessionId: Int get() = PlayerConstants.AUDIO_SESSION_ID_UNSET

    override var volume: Float
        get() = internalVolume
        set(value) {
            internalVolume = value.coerceIn(0f, 1f)
            coroutineScope.launch { applyVolume() }
            notifyListeners { onVolumeChanged(internalVolume) }
        }

    override var sleepFadeFactor: Float
        get() = internalSleepFadeFactor
        set(value) {
            internalSleepFadeFactor = value.coerceIn(0f, 1f)
            coroutineScope.launch { applyVolume() }
        }

    private fun applyVolume() {
        avPlayer.volume = internalVolume * internalSleepFadeFactor
    }

    override fun addListener(listener: MediaPlayerListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: MediaPlayerListener) {
        listeners.remove(listener)
    }

    override fun release() {
        currentLoadJob?.cancel()
        stopPolling()
        avPlayer.pause()
        clearItemObservers()
        interruptionObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        IosPlayerVideoOutput.playerFlow.value = null
        listeners.clear()
    }

    // ========== Loading ==========

    private fun startAvPlayback() {
        applyVolume()
        // playImmediatelyAtRate starts without waiting for a "full enough" buffer, paired with
        // automaticallyWaitsToMinimizeStalling = false above.
        avPlayer.playImmediatelyAtRate(if (internalSpeed > 0f) internalSpeed else 1f)
    }

    private fun loadAndPlayTrackInternal(
        index: Int,
        startPositionMs: Long,
        shouldPlay: Boolean,
    ) {
        if (index !in playlist.indices) return
        val mediaItem = playlist[index]
        val videoId = mediaItem.mediaId
        currentLoadJob?.cancel()
        currentLoadJob =
            coroutineScope.launch {
                try {
                    internalPlayWhenReady = shouldPlay
                    stopPolling()
                    avPlayer.pause()
                    cachedPosition = startPositionMs
                    cachedDuration = 0L
                    preparingSinceNanos = elapsedNanos()
                    transitionToState(InternalState.PREPARING)
                    notifyListeners {
                        onMediaItemTransition(mediaItem, PlayerConstants.MEDIA_ITEM_TRANSITION_REASON_AUTO)
                    }

                    var source = withContext(Dispatchers.IO) { resolveSource(mediaItem) }
                    if (source == null) {
                        Logger.w(TAG, "First extract failed for $videoId, invalidating and retrying")
                        withContext(Dispatchers.IO) {
                            streamRepository.invalidateFormat(videoId)
                            streamRepository.invalidateFormat("${MERGING_DATA_TYPE.VIDEO}$videoId")
                        }
                        source = withContext(Dispatchers.IO) { resolveSource(mediaItem) }
                    }
                    if (source == null) {
                        Logger.e(TAG, "No playable URL for $videoId")
                        transitionToState(InternalState.ERROR)
                        return@launch
                    }

                    val item = createPlayerItem(source.url) ?: run {
                        transitionToState(InternalState.ERROR)
                        return@launch
                    }
                    clearItemObservers()
                    avPlayer.replaceCurrentItemWithPlayerItem(item)
                    observeItem(item)
                    IosPlayerVideoOutput.isVideoFlow.value = source.isVideo
                    IosPlayerVideoOutput.playerFlow.value = avPlayer
                    if (startPositionMs > 0) {
                        avPlayer.seekToTime(CMTimeMakeWithSeconds(startPositionMs / 1000.0, 1000))
                    }
                    applyVolume()
                    if (shouldPlay) {
                        activateAudioSession()
                        startAvPlayback()
                    }
                    startPolling(item)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "Load track error: ${e.message}")
                    transitionToState(InternalState.ERROR)
                }
            }
    }

    private class Source(
        val url: String,
        val isVideo: Boolean,
    )

    /**
     * Same order as the desktop adapter: a cached format first, then a fresh extraction. Video is
     * played from the HLS manifest (`muxed`), which carries its own audio — AVPlayer cannot mux two
     * separate DASH streams the way mpv's `edl://` does.
     */
    private suspend fun resolveSource(mediaItem: GenericMediaItem): Source? {
        val videoId = mediaItem.mediaId
        val wantVideo = mediaItem.isVideo() && watchVideoEnabled
        localFileFor(videoId)?.let { return Source(it, isVideo = false) }
        if (!wantVideo) {
            streamRepository.getNewFormat(videoId).firstOrNull()?.audioUrl?.let { url ->
                if (url.isNotEmpty() && isAacUrl(url) && streamRepository.is403Url(url).firstOrNull() == false) {
                    return Source(url, isVideo = false)
                }
            }
        }
        val url =
            streamRepository
                .getStream(
                    dataStoreManager = dataStoreManager,
                    videoId = videoId,
                    isDownloading = false,
                    isVideo = wantVideo,
                    muxed = wantVideo,
                ).lastOrNull()
        return url?.takeIf { it.isNotEmpty() }?.let { Source(it, isVideo = wantVideo) }
    }

    /** WebM/Opus URLs (cached by an older format row) cannot be decoded by AVPlayer. */
    private fun isAacUrl(url: String): Boolean = !url.contains("mime=audio%2Fwebm") && !url.contains("mime=audio/webm")

    private fun localFileFor(videoId: String): String? {
        val dir = com.maxrave.data.io.fileDir() + "/download"
        val files = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, null) ?: return null
        val name =
            files.filterIsInstance<String>().firstOrNull { it.contains(videoId) && !it.contains(MERGING_DATA_TYPE.VIDEO) }
                ?: return null
        return "file://$dir/$name"
    }

    private fun createPlayerItem(url: String): AVPlayerItem? {
        val nsUrl = (if (url.startsWith("file://")) NSURL.fileURLWithPath(url.removePrefix("file://")) else NSURL.URLWithString(url)) ?: return null
        val options: Map<Any?, *> = mapOf("AVURLAssetHTTPHeaderFieldsKey" to mapOf("User-Agent" to STREAM_USER_AGENT))
        val asset = AVURLAsset(uRL = nsUrl, options = options)
        return AVPlayerItem(asset = asset)
    }

    private fun observeItem(item: AVPlayerItem) {
        val center = NSNotificationCenter.defaultCenter
        endObserver =
            center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, item, NSOperationQueue.mainQueue) { _ ->
                coroutineScope.launch {
                    if (avPlayer.currentItem != item) return@launch
                    transitionToState(InternalState.ENDED)
                    handleTrackEnd()
                }
            }
        failObserver =
            center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, item, NSOperationQueue.mainQueue) { _ ->
                coroutineScope.launch {
                    if (avPlayer.currentItem == item) onPlaybackFailed()
                }
            }
    }

    private fun clearItemObservers() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        failObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        failObserver = null
    }

    private fun clearCurrentItem() {
        stopPolling()
        clearItemObservers()
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        IosPlayerVideoOutput.playerFlow.value = null
        IosPlayerVideoOutput.isVideoFlow.value = false
        transitionToState(InternalState.IDLE)
    }

    private fun onPlaybackFailed() {
        val videoId = playlist.getOrNull(localCurrentMediaItemIndex)?.mediaId
        if (videoId != null) {
            if (retryVideoId != videoId) {
                retryVideoId = videoId
                retryCount = 0
            }
            if (retryCount < maxRetryCount) {
                retryCount++
                Logger.w(TAG, "Retrying $videoId ($retryCount/$maxRetryCount)")
                coroutineScope.launch {
                    streamRepository.invalidateFormat(videoId)
                    streamRepository.invalidateFormat("${MERGING_DATA_TYPE.VIDEO}$videoId")
                    loadAndPlayTrackInternal(localCurrentMediaItemIndex, cachedPosition, true)
                }
                return
            }
        }
        transitionToState(InternalState.ERROR)
    }

    private fun handleTrackEnd() {
        when {
            internalRepeatMode == PlayerConstants.REPEAT_MODE_ONE -> seekTo(localCurrentMediaItemIndex, 0)
            hasNextMediaItem() -> seekToNext()
        }
    }

    /**
     * AVPlayer reports through KVO; polling the few values the handler needs on the service thread
     * avoids KVO's NSObject-subclass plumbing from Kotlin and keeps every state change on one thread.
     */
    private fun startPolling(item: AVPlayerItem) {
        stopPolling()
        pollJob =
            coroutineScope.launch {
                while (isActive) {
                    if (avPlayer.currentItem != item) break
                    when (item.status) {
                        AVPlayerItemStatusFailed -> {
                            Logger.e(TAG, "Item failed: ${item.error?.localizedDescription}")
                            onPlaybackFailed()
                            break
                        }

                        AVPlayerItemStatusReadyToPlay -> {
                            if (internalState == InternalState.PREPARING) {
                                retryCount = 0
                                transitionToState(InternalState.READY)
                            }
                        }

                        else -> Unit
                    }
                    readTimes(item)
                    val status = avPlayer.timeControlStatus
                    val loading = status == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
                    if (loading != cachedIsLoading && internalState != InternalState.PREPARING) {
                        cachedIsLoading = loading
                        notifyListeners { onIsLoadingChanged(loading) }
                    }
                    if (status == AVPlayerTimeControlStatusPlaying && internalState == InternalState.READY) {
                        transitionToState(InternalState.PLAYING)
                    }
                    // Never spin forever: if the item has not become ready in time (it can sit in
                    // AVPlayerItemStatusUnknown without ever flipping to .failed on a real device),
                    // treat it as a failure so it retries and then surfaces an error instead of an
                    // endless loading spinner with no audio.
                    if (internalState == InternalState.PREPARING &&
                        preparingSinceNanos > 0L &&
                        elapsedNanos() - preparingSinceNanos > PREPARE_TIMEOUT_NANOS
                    ) {
                        Logger.e(TAG, "Item never became ready in time, treating as failure")
                        preparingSinceNanos = 0L
                        onPlaybackFailed()
                        break
                    }
                    delay(100)
                }
            }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun readTimes(item: AVPlayerItem) {
        val position = CMTimeGetSeconds(avPlayer.currentTime())
        if (!position.isNaN() && position >= 0) cachedPosition = (position * 1000).toLong()
        val dur = CMTimeGetSeconds(item.duration)
        if (!dur.isNaN() && !dur.isInfinite() && dur > 0) cachedDuration = (dur * 1000).toLong()
        val range = item.loadedTimeRanges.firstOrNull() as? NSValue
        if (range != null) {
            range.CMTimeRangeValue.useContents {
                val end = CMTimeGetSeconds(start.readValue()) + CMTimeGetSeconds(duration.readValue())
                if (!end.isNaN()) cachedBufferedPosition = (end * 1000).toLong()
            }
        }
    }

    private fun transitionToState(newState: InternalState) {
        if (internalState == newState) return
        internalState = newState
        when (newState) {
            InternalState.PAUSED, InternalState.IDLE -> {
                emit(PlayerConstants.STATE_IDLE.takeIf { newState == InternalState.IDLE } ?: PlayerConstants.STATE_READY)
                listeners.forEach { it.onIsPlayingChanged(false) }
            }

            InternalState.PREPARING -> {
                cachedIsLoading = true
                emit(PlayerConstants.STATE_BUFFERING)
                listeners.forEach { it.onIsLoadingChanged(true) }
            }

            InternalState.READY -> {
                cachedIsLoading = false
                emit(PlayerConstants.STATE_READY)
                listeners.forEach { it.onIsLoadingChanged(false) }
                if (internalPlayWhenReady) {
                    startAvPlayback()
                    internalState = InternalState.PLAYING
                    listeners.forEach { it.onIsPlayingChanged(true) }
                } else {
                    listeners.forEach { it.onIsPlayingChanged(false) }
                }
            }

            InternalState.PLAYING -> {
                cachedIsLoading = false
                emit(PlayerConstants.STATE_READY)
                listeners.forEach { it.onIsLoadingChanged(false) }
                listeners.forEach { it.onIsPlayingChanged(true) }
            }

            InternalState.ENDED -> {
                emit(PlayerConstants.STATE_ENDED)
                listeners.forEach { it.onIsPlayingChanged(false) }
            }

            InternalState.ERROR -> {
                emit(PlayerConstants.STATE_IDLE)
                listeners.forEach { it.onIsPlayingChanged(false) }
                listeners.forEach {
                    it.onPlayerError(
                        PlayerError(
                            errorCode = PlayerConstants.ERROR_CODE_TIMEOUT,
                            errorCodeName = "AVPLAYER_ERROR",
                            message = "Can not extract playable URL or playback error",
                        ),
                    )
                }
            }
        }
    }

    private fun emit(state: Int) {
        listeners.forEach { it.onPlaybackStateChanged(state) }
    }

    private fun notifyListeners(block: MediaPlayerListener.() -> Unit) {
        coroutineScope.launch { listeners.toList().forEach(block) }
    }

    // ========== Shuffle ==========

    private fun shuffledList(): List<GenericMediaItem> =
        if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
            shuffleOrder.mapNotNull { playlist.getOrNull(it) }
        } else {
            playlist.toList()
        }

    private fun notifyTimelineChanged() {
        val list = shuffledList()
        notifyListeners { onTimelineChanged(list, "TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED") }
    }

    private fun createShuffleOrder() {
        shuffleOrder.clear()
        shuffleIndices.clear()
        if (playlist.isEmpty()) return
        val indices = playlist.indices.toMutableList()
        val current = localCurrentMediaItemIndex
        if (current in indices) indices.remove(current)
        indices.shuffle()
        if (current in playlist.indices) indices.add(0, current)
        shuffleOrder.addAll(indices)
        rebuildShuffleIndices()
    }

    private fun insertIntoShuffleOrder(
        insertedIndex: Int,
        afterShufflePos: Int,
    ) {
        for (i in shuffleOrder.indices) {
            if (shuffleOrder[i] >= insertedIndex) shuffleOrder[i]++
        }
        shuffleOrder.add((afterShufflePos + 1).coerceIn(0, shuffleOrder.size), insertedIndex)
        rebuildShuffleIndices()
    }

    private fun rebuildShuffleIndices() {
        shuffleIndices.clear()
        shuffleIndices.addAll(List(playlist.size) { 0 })
        shuffleOrder.forEachIndexed { pos, original ->
            if (original < shuffleIndices.size) shuffleIndices[original] = pos
        }
    }
}
