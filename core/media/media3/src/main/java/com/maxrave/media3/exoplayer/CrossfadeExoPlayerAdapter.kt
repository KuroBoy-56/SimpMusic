package com.maxrave.media3.exoplayer

import android.annotation.SuppressLint
import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.data.player.GenericPlaybackParameters
import com.maxrave.domain.data.player.PlayerConstants
import com.maxrave.domain.data.player.PlayerError
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.player.MediaPlayerInterface
import com.maxrave.domain.mediaservice.player.MediaPlayerListener
import com.maxrave.domain.repository.StreamRepository
import com.maxrave.media3.audio.BiquadFilter
import com.maxrave.media3.audio.CrossfadeFilterAudioProcessor
import com.maxrave.media3.service.mediasourcefactory.MergingMediaSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
internal class CrossfadeExoPlayerAdapter(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val dataStoreManager: DataStoreManager,
    private val mediaSourceFactory: MergingMediaSourceFactory,
    private val audioAttributes: AudioAttributes,
    private val streamRepository: StreamRepository,
) : MediaPlayerInterface {

    private enum class InternalState {
        IDLE,
        PREPARING,
        READY,
        PLAYING,
        PAUSED,
        ENDED,
        ERROR,
    }

    init {
        coroutineScope.launch {
            dataStoreManager.crossfadeEnabled.collect { enabled ->
                crossfadeEnabled = (enabled == DataStoreManager.TRUE)
            }
        }
        coroutineScope.launch {
            dataStoreManager.crossfadeDuration.collect { duration ->
                crossfadeDurationMs = duration
            }
        }
        coroutineScope.launch {
            dataStoreManager.crossfadeDjMode.collect { enabled ->
                djCrossfadeEnabled = (enabled == DataStoreManager.TRUE)
            }
        }
    }

    private val listeners = mutableListOf<MediaPlayerListener>()

    @Volatile
    private var currentPlayer: ExoPlayer? = null

    @Volatile
    private var internalState = InternalState.IDLE

    @Volatile
    private var internalPlayWhenReady = true

    @Volatile
    private var internalVolume = 1.0f

    @Volatile
    private var internalRepeatMode = PlayerConstants.REPEAT_MODE_OFF

    @Volatile
    private var internalShuffleModeEnabled = false

    @Volatile
    private var internalPlaybackSpeed = 1.0f

    @Volatile
    private var internalPlaybackPitch = 1.0f

    @Volatile
    private var internalSkipSilence = false

    @Volatile
    private var cachedPosition = 0L

    @Volatile
    private var cachedDuration = 0L

    @Volatile
    private var cachedBufferedPosition = 0L

    @Volatile
    private var cachedIsLoading = false

    private var positionUpdateJob: Job? = null

    private var activePlayerListener: Player.Listener? = null

    private data class PrecachedPlayer(
        val player: ExoPlayer,
        val mediaItem: GenericMediaItem,
        val filter: CrossfadeFilterAudioProcessor? = null,
    )

    private val precachedPlayers = ConcurrentHashMap<String, PrecachedPlayer>()
    private var precacheEnabled = true
    private val maxPrecacheCount = 2
    private var precacheJob: Job? = null

    @Volatile
    private var crossfadeEnabled = false

    @Volatile
    private var crossfadeDurationMs = 5000

    @Volatile
    private var djCrossfadeEnabled = true

    @Volatile
    private var secondaryPlayer: ExoPlayer? = null

    @Volatile
    private var crossfadeJob: Job? = null

    @Volatile
    private var isCrossfading = false

    @Volatile
    private var currentPlayerFilter: CrossfadeFilterAudioProcessor? = null

    @Volatile
    private var secondaryPlayerFilter: CrossfadeFilterAudioProcessor? = null

    @Volatile
    private var crossfadeFromIndex = -1

    private var retryCount = 0
    private var retryVideoId: String? = null
    private val maxRetryCount = 5

    private val audioMetaCache = ConcurrentHashMap<String, SongAudioMeta>()

    private fun setCrossfading(value: Boolean) {
        if (isCrossfading != value) {
            isCrossfading = value
            listeners.forEach { it.onCrossfadeStateChanged(value) }
        }
    }

    private val playlist = mutableListOf<GenericMediaItem>()
    private var localCurrentMediaItemIndex = -1

    private var shuffleIndices = mutableListOf<Int>()
    private var shuffleOrder = mutableListOf<Int>()

    private var currentLoadJob: Job? = null

    private val initialPlayerWithFilter = createExoPlayerInstance(handleAudioFocus = true)

    val forwardingPlayer: DelegatingForwardingPlayer = DelegatingForwardingPlayer(initialPlayerWithFilter.player)

    init {
        currentPlayer = initialPlayerWithFilter.player
        currentPlayerFilter = initialPlayerWithFilter.filter

        forwardingPlayer.playlistNavigationProvider =
            object : DelegatingForwardingPlayer.PlaylistNavigationProvider {
                override fun hasNextMediaItem(): Boolean = this@CrossfadeExoPlayerAdapter.hasNextMediaItem()
                override fun hasPreviousMediaItem(): Boolean = this@CrossfadeExoPlayerAdapter.hasPreviousMediaItem()
                override fun seekToNext(): Unit = this@CrossfadeExoPlayerAdapter.seekToNext()
                override fun seekToPrevious(): Unit = this@CrossfadeExoPlayerAdapter.seekToPrevious()
            }
    }

    private data class PlayerWithFilter(
        val player: ExoPlayer,
        val filter: CrossfadeFilterAudioProcessor,
    )

    private fun createExoPlayerInstance(handleAudioFocus: Boolean = false): PlayerWithFilter {
        val crossfadeFilter = CrossfadeFilterAudioProcessor()

        val perPlayerRenderers =
            object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink =
                    DefaultAudioSink
                        .Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessorChain(
                            DefaultAudioSink.DefaultAudioProcessorChain(
                                arrayOf(crossfadeFilter),
                                SilenceSkippingAudioProcessor(),
                                SonicAudioProcessor(),
                            ),
                        ).build()
            }

        val player =
            ExoPlayer
                .Builder(context)
                .setAudioAttributes(audioAttributes, handleAudioFocus)
                .setLoadControl(
                    DefaultLoadControl
                        .Builder()
                        .setBufferDurationsMs(
                            32000,
                            64000,
                            1500,
                            3000,
                        ).build(),
                ).setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(handleAudioFocus)
                .setSeekForwardIncrementMs(5000)
                .setSeekBackIncrementMs(5000)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(perPlayerRenderers)
                .build()

        return PlayerWithFilter(player, crossfadeFilter)
    }

    private fun cleanupPlayerListenerInternal() {
        activePlayerListener?.let { listener ->
            currentPlayer?.removeListener(listener)
            secondaryPlayer?.removeListener(listener)
        }
        activePlayerListener = null
    }

    private fun cleanupPlayerInternal(player: ExoPlayer) {
        try {
            player.stop()
            player.clearMediaItems()
            player.release()
        } catch (e: Exception) {
        }
    }

    private fun cleanupCurrentPlayerInternal() {
        stopPositionUpdates()
        cleanupPlayerListenerInternal()

        crossfadeJob?.cancel()
        crossfadeJob = null
        setCrossfading(false)

        currentPlayer?.let { cleanupPlayerInternal(it) }
        currentPlayer = null
    }

    override fun play() {
        coroutineScope.launch {
            when (internalState) {
                InternalState.READY, InternalState.ENDED, InternalState.PAUSED -> {
                    currentPlayer?.let { player ->
                        player.play()
                        transitionToState(InternalState.PLAYING)
                        internalPlayWhenReady = true
                    }
                }
                InternalState.PREPARING -> {
                    internalPlayWhenReady = true
                }
                InternalState.PLAYING -> {
                    internalPlayWhenReady = true
                    cachedIsLoading = false
                }
                else -> {
                }
            }
        }
    }

    override fun pause() {
        coroutineScope.launch {
            if (isCrossfading) {
                crossfadeJob?.cancel()
                crossfadeJob = null
                currentPlayerFilter?.enabled = false
                secondaryPlayerFilter?.enabled = false
                setCrossfading(false)
                cleanupPlayerListenerInternal()
                stopPositionUpdates()
                currentPlayer?.let { forwardingPlayer.swapDelegate(it) }
                setupPlayerListenerInternal(currentPlayer!!)
                if (crossfadeFromIndex >= 0) {
                    localCurrentMediaItemIndex = crossfadeFromIndex
                    playlist.getOrNull(crossfadeFromIndex)?.let { mediaItem ->
                        listeners.forEach {
                            it.onMediaItemTransition(
                                mediaItem,
                                PlayerConstants.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                            )
                        }
                    }
                    forwardingPlayer.notifyMediaItemChanged()
                    crossfadeFromIndex = -1
                }
                secondaryPlayer?.let { cleanupPlayerInternal(it) }
                secondaryPlayer = null
                secondaryPlayerFilter = null
            }

            when (internalState) {
                InternalState.PLAYING, InternalState.READY -> {
                    currentPlayer?.let { player ->
                        player.pause()
                        transitionToState(InternalState.PAUSED)
                        internalPlayWhenReady = false
                    }
                }
                InternalState.PREPARING -> {
                    internalPlayWhenReady = false
                }
                else -> {
                }
            }
        }
    }

    override fun stop() {
        coroutineScope.launch {
            currentPlayer?.let { player ->
                player.stop()
                transitionToState(InternalState.IDLE)
                stopPositionUpdates()
                notifyEqualizerIntent(false)
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        currentPlayer?.let { player ->
            try {
                player.seekTo(positionMs)
                cachedPosition = positionMs
            } catch (e: Exception) {
            }
        }
    }

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
    ) {
        if (mediaItemIndex !in playlist.indices) return

        coroutineScope.launch {
            val shouldPlay = internalPlayWhenReady

            if (isCrossfading) {
                crossfadeJob?.cancel()
                crossfadeJob = null
                currentPlayerFilter?.enabled = false
                secondaryPlayerFilter?.enabled = false
                secondaryPlayer?.let { cleanupPlayerInternal(it) }
                secondaryPlayer = null
                secondaryPlayerFilter = null
                setCrossfading(false)
            }

            currentLoadJob?.cancel()
            localCurrentMediaItemIndex = mediaItemIndex
            loadAndPlayTrackInternal(mediaItemIndex, positionMs, shouldPlay)
        }
    }

    override fun seekBack() {
        val newPosition = (cachedPosition - 5000).coerceAtLeast(0)
        seekTo(newPosition)
    }

    override fun seekForward() {
        val newPosition = (cachedPosition + 5000).coerceAtMost(cachedDuration)
        seekTo(newPosition)
    }

    override fun seekToNext() {
        if (hasNextMediaItem()) {
            if (isCrossfading) {
                coroutineScope.launch {
                    crossfadeJob?.cancel()
                    crossfadeJob = null
                    currentPlayerFilter?.enabled = false
                    secondaryPlayerFilter?.enabled = false
                    secondaryPlayer?.let { cleanupPlayerInternal(it) }
                    secondaryPlayer = null
                    secondaryPlayerFilter = null
                    setCrossfading(false)
                }
                seekTo(localCurrentMediaItemIndex, 0)
                return
            }
            val nextIndex = getNextMediaItemIndex()
            seekTo(nextIndex, 0)
        }
    }

    override fun seekToPrevious() {
        if (isCrossfading) {
            coroutineScope.launch {
                crossfadeJob?.cancel()
                crossfadeJob = null
                currentPlayerFilter?.enabled = false
                secondaryPlayerFilter?.enabled = false
                secondaryPlayer?.let { cleanupPlayerInternal(it) }
                secondaryPlayer = null
                secondaryPlayerFilter = null
                setCrossfading(false)
                if (crossfadeFromIndex >= 0) {
                    localCurrentMediaItemIndex = crossfadeFromIndex
                    playlist.getOrNull(crossfadeFromIndex)?.let { mediaItem ->
                        listeners.forEach {
                            it.onMediaItemTransition(
                                mediaItem,
                                PlayerConstants.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                            )
                        }
                    }
                    forwardingPlayer.notifyMediaItemChanged()
                    crossfadeFromIndex = -1
                }
            }
        }

        val positionThresholdMs = 3000L
        if (cachedPosition > positionThresholdMs) {
            currentPlayer?.seekTo(0)
            cachedPosition = 0
        } else if (hasPreviousMediaItem()) {
            val prevIndex = getPreviousMediaItemIndex()
            seekTo(prevIndex, 0)
        } else {
            currentPlayer?.seekTo(0)
            cachedPosition = 0
        }
    }

    override fun seekToPreviousMediaItem() {
        if (isCrossfading) {
            coroutineScope.launch {
                crossfadeJob?.cancel()
                crossfadeJob = null
                currentPlayerFilter?.enabled = false
                secondaryPlayerFilter?.enabled = false
                secondaryPlayer?.let { cleanupPlayerInternal(it) }
                secondaryPlayer = null
                secondaryPlayerFilter = null
                setCrossfading(false)
                if (crossfadeFromIndex >= 0) {
                    localCurrentMediaItemIndex = crossfadeFromIndex
                    playlist.getOrNull(crossfadeFromIndex)?.let { mediaItem ->
                        listeners.forEach {
                            it.onMediaItemTransition(
                                mediaItem,
                                PlayerConstants.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                            )
                        }
                    }
                    forwardingPlayer.notifyMediaItemChanged()
                    crossfadeFromIndex = -1
                }
            }
        }

        if (hasPreviousMediaItem()) {
            val prevIndex = getPreviousMediaItemIndex()
            seekTo(prevIndex, 0)
        } else {
            currentPlayer?.seekTo(0)
            cachedPosition = 0
        }
    }

    override fun prepare() {
        if (playlist.isNotEmpty() && localCurrentMediaItemIndex >= 0) {
            coroutineScope.launch {
                loadAndPlayTrackInternal(localCurrentMediaItemIndex, 0, false)
            }
        }
    }

    override fun setMediaItem(mediaItem: GenericMediaItem) {
        coroutineScope.launch {
            currentLoadJob?.cancel()
            cancelPrecaching()
            playlist.clear()
            clearAllPrecacheInternal()
            playlist.add(mediaItem)
            localCurrentMediaItemIndex = 0

            if (internalShuffleModeEnabled) {
                createShuffleOrder()
            }

            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")
            loadAndPlayTrackInternal(0, 0, internalPlayWhenReady)
        }
    }

    override fun addMediaItem(mediaItem: GenericMediaItem) {
        playlist.add(mediaItem)

        if (internalShuffleModeEnabled) {
            createShuffleOrder()
        }

        notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")

        if (playlist.size - 1 - currentMediaItemIndex <= maxPrecacheCount) {
            coroutineScope.launch {
                clearPrecacheExceptCurrentInternal()
                triggerPrecachingInternal()
            }
        }
    }

    override fun addMediaItem(
        index: Int,
        mediaItem: GenericMediaItem,
    ) {
        if (index in 0..playlist.size) {
            val currentIndexBeforeInsert = localCurrentMediaItemIndex
            playlist.add(index, mediaItem)

            if (index <= localCurrentMediaItemIndex) {
                localCurrentMediaItemIndex++
            }

            if (internalShuffleModeEnabled) {
                if (currentIndexBeforeInsert >= 0 && index == currentIndexBeforeInsert + 1) {
                    val currentShufflePos = shuffleIndices.getOrNull(currentIndexBeforeInsert) ?: 0
                    insertIntoShuffleOrder(index, currentShufflePos)
                } else {
                    createShuffleOrder()
                }
            }

            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")

            if (index - 1 - currentMediaItemIndex <= maxPrecacheCount) {
                coroutineScope.launch {
                    clearPrecacheExceptCurrentInternal()
                    triggerPrecachingInternal()
                }
            }
        }
    }

    override fun removeMediaItem(index: Int) {
        if (index !in playlist.indices) return

        coroutineScope.launch {
            val track = playlist.removeAt(index)
            precachedPlayers.remove(track.mediaId)?.let { cached ->
                cleanupPlayerInternal(cached.player)
            }

            when {
                index < localCurrentMediaItemIndex -> {
                    localCurrentMediaItemIndex--
                    clearPrecacheExceptCurrentInternal()
                    triggerPrecachingInternal()
                }
                index == localCurrentMediaItemIndex -> {
                    if (localCurrentMediaItemIndex >= playlist.size) {
                        localCurrentMediaItemIndex = playlist.size - 1
                    }
                    if (localCurrentMediaItemIndex >= 0) {
                        loadAndPlayTrackInternal(localCurrentMediaItemIndex, 0, internalPlayWhenReady)
                    } else {
                        cleanupCurrentPlayerInternal()
                    }
                }
                else -> {
                    clearPrecacheExceptCurrentInternal()
                    triggerPrecachingInternal()
                }
            }

            if (internalShuffleModeEnabled) {
                createShuffleOrder()
            }
            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")
        }
    }

    override fun moveMediaItem(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex !in playlist.indices || toIndex !in playlist.indices) return

        coroutineScope.launch {
            val item = playlist.removeAt(fromIndex)
            playlist.add(toIndex, item)

            localCurrentMediaItemIndex =
                when {
                    localCurrentMediaItemIndex == fromIndex -> {
                        toIndex
                    }
                    fromIndex < localCurrentMediaItemIndex && toIndex >= localCurrentMediaItemIndex -> {
                        localCurrentMediaItemIndex - 1
                    }
                    fromIndex > localCurrentMediaItemIndex && toIndex <= localCurrentMediaItemIndex -> {
                        localCurrentMediaItemIndex + 1
                    }
                    else -> {
                        localCurrentMediaItemIndex
                    }
                }

            if (internalShuffleModeEnabled) {
                createShuffleOrder()
            }

            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")
            clearPrecacheExceptCurrentInternal()
            triggerPrecachingInternal()
        }
    }

    override fun clearMediaItems() {
        coroutineScope.launch {
            playlist.clear()
            localCurrentMediaItemIndex = -1
            clearShuffleOrder()
            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")
            cleanupCurrentPlayerInternal()
            clearAllPrecacheInternal()
        }
    }

    override fun replaceMediaItem(
        index: Int,
        mediaItem: GenericMediaItem,
    ) {
        if (index !in playlist.indices) return

        coroutineScope.launch {
            playlist[index] = mediaItem
            precachedPlayers.remove(mediaItem.mediaId)?.let { cached ->
                cleanupPlayerInternal(cached.player)
            }

            if (internalShuffleModeEnabled) {
                createShuffleOrder()
            }

            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")

            if (index == localCurrentMediaItemIndex) {
                loadAndPlayTrackInternal(index, 0, internalPlayWhenReady)
            } else {
                triggerPrecachingInternal()
            }
        }
    }

    override fun getMediaItemAt(index: Int): GenericMediaItem? = playlist.getOrNull(index)

    override fun getCurrentMediaTimeLine(): List<GenericMediaItem> =
        if (internalShuffleModeEnabled) {
            shuffleOrder.mapNotNull { shuffledIndex -> playlist.getOrNull(shuffledIndex) }
        } else {
            playlist.toList()
        }

    override fun getUnshuffledIndex(shuffledIndex: Int): Int =
        if (internalShuffleModeEnabled) {
            shuffleOrder.getOrNull(shuffledIndex) ?: -1
        } else {
            shuffledIndex
        }

    override val isPlaying: Boolean
        get() = internalState == InternalState.PLAYING

    override val currentPosition: Long
        get() = cachedPosition

    override val duration: Long
        get() = currentPlayer?.duration ?: cachedDuration

    override val bufferedPosition: Long
        get() = cachedBufferedPosition

    override val bufferedPercentage: Int
        get() {
            val dur = duration
            if (dur <= 0) return 0
            return ((bufferedPosition * 100) / dur).toInt().coerceIn(0, 100)
        }

    override val currentMediaItem: GenericMediaItem?
        get() = playlist.getOrNull(localCurrentMediaItemIndex)

    override val currentMediaItemIndex: Int
        get() = localCurrentMediaItemIndex

    override val mediaItemCount: Int
        get() = playlist.size

    override val contentPosition: Long
        get() = cachedPosition

    override val playbackState: Int
        get() =
            when (internalState) {
                InternalState.IDLE -> PlayerConstants.STATE_IDLE
                InternalState.PREPARING -> PlayerConstants.STATE_BUFFERING
                InternalState.READY -> PlayerConstants.STATE_READY
                InternalState.PLAYING -> PlayerConstants.STATE_READY
                InternalState.ENDED -> PlayerConstants.STATE_ENDED
                InternalState.ERROR -> PlayerConstants.STATE_IDLE
                InternalState.PAUSED -> PlayerConstants.STATE_READY
            }

    override fun hasNextMediaItem(): Boolean =
        when (internalRepeatMode) {
            PlayerConstants.REPEAT_MODE_ONE -> true
            PlayerConstants.REPEAT_MODE_ALL -> true
            else -> localCurrentMediaItemIndex < playlist.size - 1
        }

    override fun hasPreviousMediaItem(): Boolean =
        when (internalRepeatMode) {
            PlayerConstants.REPEAT_MODE_ONE -> true
            PlayerConstants.REPEAT_MODE_ALL -> true
            else -> localCurrentMediaItemIndex > 0
        }

    private fun getNextMediaItemIndex(): Int =
        when (internalRepeatMode) {
            PlayerConstants.REPEAT_MODE_ONE -> {
                localCurrentMediaItemIndex
            }
            PlayerConstants.REPEAT_MODE_ALL -> {
                if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
                    val currentShufflePos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: 0
                    val nextShufflePos = (currentShufflePos + 1) % shuffleOrder.size
                    shuffleOrder.getOrNull(nextShufflePos) ?: localCurrentMediaItemIndex
                } else {
                    if (localCurrentMediaItemIndex < playlist.size - 1) {
                        localCurrentMediaItemIndex + 1
                    } else {
                        0
                    }
                }
            }
            else -> {
                if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
                    val currentShufflePos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: 0
                    val nextShufflePos = currentShufflePos + 1
                    if (nextShufflePos < shuffleOrder.size) {
                        shuffleOrder.getOrNull(nextShufflePos) ?: localCurrentMediaItemIndex
                    } else {
                        localCurrentMediaItemIndex
                    }
                } else {
                    (localCurrentMediaItemIndex + 1).coerceAtMost(playlist.size - 1)
                }
            }
        }

    private fun getPreviousMediaItemIndex(): Int =
        when (internalRepeatMode) {
            PlayerConstants.REPEAT_MODE_ONE -> {
                localCurrentMediaItemIndex
            }
            PlayerConstants.REPEAT_MODE_ALL -> {
                if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
                    val currentShufflePos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: 0
                    val prevShufflePos =
                        if (currentShufflePos > 0) {
                            currentShufflePos - 1
                        } else {
                            shuffleOrder.size - 1
                        }
                    shuffleOrder.getOrNull(prevShufflePos) ?: localCurrentMediaItemIndex
                } else {
                    if (localCurrentMediaItemIndex > 0) {
                        localCurrentMediaItemIndex - 1
                    } else {
                        playlist.size - 1
                    }
                }
            }
            else -> {
                if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
                    val currentShufflePos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: 0
                    val prevShufflePos = currentShufflePos - 1
                    if (prevShufflePos >= 0) {
                        shuffleOrder.getOrNull(prevShufflePos) ?: localCurrentMediaItemIndex
                    } else {
                        localCurrentMediaItemIndex
                    }
                } else {
                    (localCurrentMediaItemIndex - 1).coerceAtLeast(0)
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
                clearShuffleOrder()
            }

            val mediaItemList = getShuffledMediaItemList()
            listeners.forEach { it.onShuffleModeEnabledChanged(value, mediaItemList) }
            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")
        }

    override var repeatMode: Int
        get() = internalRepeatMode
        set(value) {
            if (internalRepeatMode == value) return
            internalRepeatMode = value
            listeners.forEach { it.onRepeatModeChanged(value) }
        }

    override var playWhenReady: Boolean
        get() = internalPlayWhenReady
        set(value) {
            internalPlayWhenReady = value
            if (value) play() else pause()
        }

    override var playbackParameters: GenericPlaybackParameters
        get() = GenericPlaybackParameters(internalPlaybackSpeed, internalPlaybackPitch)
        set(value) {
            internalPlaybackSpeed = value.speed
            internalPlaybackPitch = value.pitch
            val params = PlaybackParameters(value.speed, value.pitch)
            currentPlayer?.playbackParameters = params
            secondaryPlayer?.playbackParameters = params
        }

    override val audioSessionId: Int
        get() = currentPlayer?.audioSessionId ?: 0

    override var volume: Float
        get() = internalVolume
        set(value) {
            internalVolume = value.coerceIn(0f, 1f)
            currentPlayer?.volume = internalVolume
            listeners.forEach { it.onVolumeChanged(internalVolume) }
        }

    override var skipSilenceEnabled: Boolean
        get() = internalSkipSilence
        set(value) {
            internalSkipSilence = value
            currentPlayer?.skipSilenceEnabled = value
            secondaryPlayer?.skipSilenceEnabled = value
        }

    override fun addListener(listener: MediaPlayerListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: MediaPlayerListener) {
        listeners.remove(listener)
    }

    override fun release() {
        currentLoadJob?.cancel()
        precacheJob?.cancel()
        positionUpdateJob?.cancel()

        crossfadeJob?.cancel()
        secondaryPlayer?.let { cleanupPlayerInternal(it) }
        secondaryPlayer = null
        secondaryPlayerFilter = null
        currentPlayerFilter = null
        isCrossfading = false

        coroutineScope.cancel()
        cleanupCurrentPlayerInternal()
        clearAllPrecacheInternal()
        listeners.clear()
    }

    private fun propagatePlayerError(error: PlaybackException) {
        val genericError =
            PlayerError(
                errorCode =
                    when (error.errorCode) {
                        PlaybackException.ERROR_CODE_TIMEOUT -> PlayerConstants.ERROR_CODE_TIMEOUT
                        else -> error.errorCode
                    },
                errorCodeName = error.errorCodeName,
                message = error.message,
            )
        listeners.forEach { it.onPlayerError(genericError) }
        transitionToState(InternalState.ERROR)
    }

    private fun transitionToState(newState: InternalState) {
        if (internalState == newState) {
            return
        }

        val oldState = internalState
        internalState = newState

        currentPlayer?.let {
            val dur = it.duration
            if (dur > 0L) {
                cachedDuration = dur
            }
        }

        when (newState) {
            InternalState.PAUSED -> {
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_READY) }
                listeners.forEach { it.onIsPlayingChanged(false) }
            }
            InternalState.IDLE -> {
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_IDLE) }
                listeners.forEach { it.onIsPlayingChanged(false) }
            }
            InternalState.PREPARING -> {
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_BUFFERING) }
            }
            InternalState.READY -> {
                if (internalPlayWhenReady) {
                    play()
                } else {
                    listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_READY) }
                    listeners.forEach { it.onIsPlayingChanged(false) }
                }
            }
            InternalState.PLAYING -> {
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_READY) }
                listeners.forEach { it.onIsLoadingChanged(false) }
                listeners.forEach { it.onIsPlayingChanged(true) }
            }
            InternalState.ENDED -> {
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_ENDED) }
                listeners.forEach { it.onIsPlayingChanged(false) }
            }
            InternalState.ERROR -> {
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_IDLE) }
                listeners.forEach { it.onIsPlayingChanged(false) }
                listeners.forEach {
                    it.onPlayerError(
                        PlayerError(
                            errorCode = 403,
                            errorCodeName = "ERROR_UNKNOWN",
                            message = "Can not extract playable URL or playback error",
                        ),
                    )
                }
            }
        }
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
                var playerToProtect: ExoPlayer? = null
                try {
                    transitionToState(InternalState.PREPARING)

                    listeners.forEach {
                        it.onMediaItemTransition(
                            mediaItem,
                            PlayerConstants.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                        )
                    }

                    val cachedPlayerEntry = precachedPlayers.remove(videoId)
                    val player: ExoPlayer
                    val playerFilter: CrossfadeFilterAudioProcessor?
                    if (cachedPlayerEntry?.player != null) {
                        player = cachedPlayerEntry.player
                        playerFilter = cachedPlayerEntry.filter
                    } else {
                        val pwf = createExoPlayerInstance(handleAudioFocus = false)
                        playerToProtect = pwf.player
                        player = pwf.player
                        playerFilter = pwf.filter
                        player.setMediaItem(mediaItem.toMedia3MediaItem())
                        player.prepare()
                    }

                    cleanupPlayerListenerInternal()
                    stopPositionUpdates()
                    crossfadeJob?.cancel()
                    crossfadeJob = null
                    setCrossfading(false)

                    val oldPlayer = currentPlayer

                    currentPlayer = player
                    currentPlayerFilter = playerFilter
                    playerToProtect = null

                    setupPlayerListenerInternal(player)

                    forwardingPlayer.swapDelegate(player)
                    forwardingPlayer.notifyMediaItemChanged()

                    if (oldPlayer != null && oldPlayer !== player) {
                        cleanupPlayerInternal(oldPlayer)
                    }

                    player.setAudioAttributes(audioAttributes, true)
                    player.setHandleAudioBecomingNoisy(true)

                    player.volume = internalVolume
                    player.playbackParameters = PlaybackParameters(internalPlaybackSpeed, internalPlaybackPitch)
                    player.skipSilenceEnabled = internalSkipSilence

                    if (startPositionMs > 0) {
                        player.seekTo(startPositionMs)
                        cachedPosition = startPositionMs
                    }

                    if (shouldPlay) {
                        player.play()
                        transitionToState(InternalState.PLAYING)
                    } else {
                        player.pause()
                        transitionToState(InternalState.READY)
                    }

                    startPositionUpdates()

                    if (crossfadeEnabled && crossfadeDurationMs == DataStoreManager.CROSSFADE_DURATION_AUTO) {
                        loadAudioMetaIfNeeded(videoId)
                    }

                    triggerPrecachingInternal()
                } catch (e: Exception) {
                    playerToProtect?.let { cleanupPlayerInternal(it) }
                    if (e is CancellationException) throw e
                    transitionToState(InternalState.ERROR)
                }
            }
    }

    private fun setupPlayerListenerInternal(player: ExoPlayer) {
        cleanupPlayerListenerInternal()

        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            transitionToState(InternalState.ENDED)
                            handleTrackEndInternal()
                        }
                        Player.STATE_READY -> {
                            if (cachedIsLoading && player == currentPlayer) {
                                cachedIsLoading = false
                                listeners.forEach { it.onIsLoadingChanged(false) }
                            }
                            val dur = player.duration
                            if (dur > 0) cachedDuration = dur
                            retryCount = 0
                            retryVideoId = null
                        }
                        Player.STATE_BUFFERING -> {
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (player != currentPlayer) {
                        return
                    }
                    if (isPlaying) {
                        if (internalState != InternalState.PLAYING) {
                            transitionToState(InternalState.PLAYING)
                            notifyEqualizerIntent(true)
                        }
                    } else {
                        if (internalState == InternalState.PLAYING) {
                            if (!player.playWhenReady) {
                                transitionToState(InternalState.PAUSED)
                                notifyEqualizerIntent(false)
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (player != currentPlayer) {
                        return
                    }

                    val isRetryableSourceError =
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                            error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

                    val currentVideoId = playlist.getOrNull(localCurrentMediaItemIndex)?.mediaId
                    if (isRetryableSourceError && currentVideoId != null) {
                        if (retryVideoId != currentVideoId) {
                            retryVideoId = currentVideoId
                            retryCount = 0
                        }
                        if (retryCount < maxRetryCount) {
                            retryCount++
                            coroutineScope.launch {
                                try {
                                    streamRepository.invalidateFormat(currentVideoId)
                                    streamRepository.invalidateFormat("${com.maxrave.common.MERGING_DATA_TYPE.VIDEO}$currentVideoId")
                                    precachedPlayers.remove(currentVideoId)?.player?.let { cleanupPlayerInternal(it) }
                                    loadAndPlayTrackInternal(localCurrentMediaItemIndex, 0L, shouldPlay = true)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    propagatePlayerError(error)
                                }
                            }
                            return
                        }
                    }
                    propagatePlayerError(error)
                }

                override fun onIsLoadingChanged(isLoading: Boolean) {
                    val isPlaybackStalled = isLoading && player.playbackState == Player.STATE_BUFFERING
                    val isCurrentPlayer = player == currentPlayer
                    if (cachedIsLoading != isPlaybackStalled && isCurrentPlayer) {
                        cachedIsLoading = isPlaybackStalled
                        listeners.forEach { it.onIsLoadingChanged(isPlaybackStalled) }
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (player != currentPlayer) {
                        return
                    }
                    val genericTracks = tracks.toGenericTracks()
                    listeners.forEach { it.onTracksChanged(genericTracks) }
                }

                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (player != currentPlayer) {
                        return
                    }
                    val shouldBePlaying =
                        !(player.playbackState == Player.STATE_ENDED || !player.playWhenReady)
                    if (events.containsAny(
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_IS_PLAYING_CHANGED,
                            Player.EVENT_POSITION_DISCONTINUITY,
                        )
                    ) {
                        if (shouldBePlaying) {
                            listeners.forEach { it.shouldOpenOrCloseEqualizerIntent(true) }
                        } else {
                            listeners.forEach { it.shouldOpenOrCloseEqualizerIntent(false) }
                        }
                    }
                }
            }

        player.addListener(listener)
        activePlayerListener = listener
    }

    private fun handleTrackEndInternal() {
        val shouldCrossfade =
            crossfadeEnabled &&
                hasNextMediaItem() &&
                !isCrossfading

        if (shouldCrossfade) {
            val nextIndex = getNextMediaItemIndex()
            triggerCrossfadeTransition(nextIndex)
        } else {
            when (internalRepeatMode) {
                PlayerConstants.REPEAT_MODE_ONE -> {
                    seekTo(localCurrentMediaItemIndex, 0)
                }
                PlayerConstants.REPEAT_MODE_ALL -> {
                    if (hasNextMediaItem()) {
                        seekToNext()
                    }
                }
                else -> {
                    if (localCurrentMediaItemIndex < playlist.size - 1) {
                        seekToNext()
                    } else {
                        notifyEqualizerIntent(false)
                    }
                }
            }
        }
    }

    private fun triggerCrossfadeTransition(nextIndex: Int) {
        if (nextIndex !in playlist.indices || isCrossfading) return

        coroutineScope.launch {
            var playerToProtect: ExoPlayer? = null
            try {
                setCrossfading(true)
                val nextMediaItem = playlist[nextIndex]
                val nextVideoId = nextMediaItem.mediaId

                val cachedPlayerEntry = precachedPlayers.remove(nextVideoId)
                val nextPlayer: ExoPlayer
                val nextFilter: CrossfadeFilterAudioProcessor?
                if (cachedPlayerEntry?.player != null) {
                    nextPlayer = cachedPlayerEntry.player
                    nextFilter = cachedPlayerEntry.filter
                } else {
                    val pwf = createExoPlayerInstance(handleAudioFocus = false)
                    playerToProtect = pwf.player
                    nextPlayer = pwf.player
                    nextFilter = pwf.filter
                    nextPlayer.setMediaItem(nextMediaItem.toMedia3MediaItem())
                    nextPlayer.prepare()
                }

                secondaryPlayer = nextPlayer
                secondaryPlayerFilter = nextFilter
                playerToProtect = null

                setupPlayerListenerInternal(nextPlayer)
                nextPlayer.skipSilenceEnabled = internalSkipSilence
                nextPlayer.volume = 0f

                forwardingPlayer.swapDelegate(nextPlayer)
                nextPlayer.play()
                forwardingPlayer.notifyMediaItemChanged()

                val currentVideoId = playlist.getOrNull(localCurrentMediaItemIndex)?.mediaId ?: ""

                val isAutoMode = crossfadeDurationMs == DataStoreManager.CROSSFADE_DURATION_AUTO
                if (isAutoMode) {
                    loadAudioMetaIfNeeded(currentVideoId)
                    loadAudioMetaIfNeeded(nextVideoId)
                }
                val resolvedConfigDurationMs =
                    if (isAutoMode) {
                        resolveAutoCrossfadeDurationMs(currentVideoId, nextVideoId)
                    } else {
                        crossfadeDurationMs
                    }
                val bpmSpeedRatio = if (isAutoMode) calculateBpmSpeedRatio(currentVideoId, nextVideoId) else 1.0f
                val keyPitchRatio = if (isAutoMode) calculateKeyPitchRatio(currentVideoId, nextVideoId) else 1.0f

                nextPlayer.playbackParameters =
                    PlaybackParameters(internalPlaybackSpeed, internalPlaybackPitch)

                crossfadeFromIndex = localCurrentMediaItemIndex
                localCurrentMediaItemIndex = nextIndex

                listeners.forEach {
                    it.onMediaItemTransition(
                        nextMediaItem,
                        PlayerConstants.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                    )
                }

                val actualTimeRemaining =
                    currentPlayer?.let { player ->
                        val dur = player.duration
                        val pos = player.currentPosition
                        val speed = internalPlaybackSpeed.coerceAtLeast(0.1f)
                        if (dur > 0 && pos >= 0) ((dur - pos) / speed).toLong() else resolvedConfigDurationMs.toLong()
                    } ?: resolvedConfigDurationMs.toLong()

                val effectiveCrossfadeDurationMs =
                    minOf(resolvedConfigDurationMs.toLong(), actualTimeRemaining)
                        .coerceAtLeast(1000L)
                        .toInt()

                performCrossfade(nextIndex, nextPlayer, effectiveCrossfadeDurationMs, bpmSpeedRatio, keyPitchRatio)
            } catch (e: Exception) {
                playerToProtect?.let { cleanupPlayerInternal(it) }
                if (e is CancellationException) throw e
                setCrossfading(false)
                seekTo(nextIndex, 0)
            }
        }
    }

    private fun sigmoid(
        t: Float,
        k: Float = DJ_FILTER_SIGMOID_K,
    ): Float = 1.0f / (1.0f + exp(-k * (t - 0.5f)))

    private fun exponentialInterpolate(
        start: Float,
        end: Float,
        t: Float,
    ): Float {
        if (start <= 0f || end <= 0f) return end
        return exp(ln(start) + (ln(end) - ln(start)) * t).toFloat()
    }

    private suspend fun performCrossfade(
        nextIndex: Int,
        nextPlayer: ExoPlayer,
        effectiveDurationMs: Int,
        targetSpeedRatio: Float = 1.0f,
        targetPitchRatio: Float = 1.0f,
    ) {
        val steps = 50
        val delayPerStep = (effectiveDurationMs / steps).coerceAtLeast(20)
        val targetVolume = internalVolume
        val useDjFilter = djCrossfadeEnabled
        val useAutoMixRamp = targetSpeedRatio != 1.0f || targetPitchRatio != 1.0f

        if (useDjFilter) {
            currentPlayerFilter?.let { filter ->
                filter.filterType = BiquadFilter.FilterType.LOW_PASS
                filter.cutoffFrequencyHz = LPF_START_HZ
                filter.enabled = true
            }
            secondaryPlayerFilter?.let { filter ->
                filter.filterType = BiquadFilter.FilterType.HIGH_PASS
                filter.cutoffFrequencyHz = HPF_START_HZ
                filter.enabled = true
            }
        }

        var lastOutgoingSpeed = -1f
        var lastOutgoingPitch = -1f

        crossfadeJob?.cancel()
        crossfadeJob =
            coroutineScope.launch {
                try {
                    for (step in 0..steps) {
                        if (!isActive) break

                        val progress = step.toFloat() / steps

                        val fadeOutVolume = targetVolume * (1f - progress)
                        currentPlayer?.volume = fadeOutVolume

                        val fadeInVolume = targetVolume * progress
                        nextPlayer.volume = fadeInVolume

                        if (useDjFilter) {
                            val filterProgress = sigmoid(progress)

                            currentPlayerFilter?.cutoffFrequencyHz =
                                exponentialInterpolate(LPF_START_HZ, LPF_END_HZ, filterProgress)

                            secondaryPlayerFilter?.cutoffFrequencyHz =
                                exponentialInterpolate(HPF_START_HZ, HPF_END_HZ, filterProgress)
                        }

                        if (useAutoMixRamp) {
                            val rawOutSpeed = lerp(1.0f, targetSpeedRatio, progress)
                            val rawOutPitch = lerp(1.0f, targetPitchRatio, progress)
                            val qOutSpeed = quantize(rawOutSpeed * internalPlaybackSpeed)
                            val qOutPitch = quantize(rawOutPitch * internalPlaybackPitch)

                            if (qOutSpeed != lastOutgoingSpeed || qOutPitch != lastOutgoingPitch) {
                                currentPlayer?.playbackParameters = PlaybackParameters(qOutSpeed, qOutPitch)
                                lastOutgoingSpeed = qOutSpeed
                                lastOutgoingPitch = qOutPitch
                            }
                        }
                        delay(delayPerStep.toLong())
                    }
                    finalizeCrossfade(nextIndex, nextPlayer)
                } catch (e: CancellationException) {
                    currentPlayerFilter?.enabled = false
                    secondaryPlayerFilter?.enabled = false
                    currentPlayer?.playbackParameters =
                        PlaybackParameters(internalPlaybackSpeed, internalPlaybackPitch)
                    cleanupPlayerInternal(nextPlayer)
                    secondaryPlayer = null
                    secondaryPlayerFilter = null
                    setCrossfading(false)
                }
            }
    }

    data class SongAudioMeta(
        val bpm: Int?,
        val key: String?,
        val keyScale: String?,
    )

    fun updateSongAudioMeta(
        videoId: String,
        bpm: Int?,
        key: String?,
        keyScale: String?,
    ) {
        if (bpm != null || key != null) {
            audioMetaCache[videoId] = SongAudioMeta(bpm, key, keyScale)
        }
    }

    private suspend fun loadAudioMetaIfNeeded(videoId: String) {
        if (videoId.isBlank() || audioMetaCache.containsKey(videoId)) return
        try {
            val format = streamRepository.getNewFormat(videoId).firstOrNull()
            if (format == null) {
                return
            }
            if (format.bpm != null || format.musicKey != null) {
                audioMetaCache[videoId] = SongAudioMeta(format.bpm, format.musicKey, format.keyScale)
            } else {
            }
        } catch (e: Exception) {
        }
    }

    private fun lerp(
        start: Float,
        end: Float,
        t: Float,
    ): Float = start + (end - start) * t

    private fun quantize(value: Float): Float = (Math.round(value / SPEED_PITCH_STEP) * SPEED_PITCH_STEP)

    private fun getAutoTargetDurationMs(bpm: Int): Double {
        val clampedBpm = bpm.coerceIn(70, 170)
        return 30000.0 - (clampedBpm - 70) * 230.0
    }

    private fun resolveAutoCrossfadeDurationMs(
        currentVideoId: String,
        nextVideoId: String,
    ): Int {
        val currentBpm = audioMetaCache[currentVideoId]?.bpm
        val nextBpm = audioMetaCache[nextVideoId]?.bpm
        val bpm = currentBpm ?: nextBpm ?: return AUTO_FALLBACK_DURATION_MS

        if (bpm <= 0) return AUTO_FALLBACK_DURATION_MS

        val beatMs = 60_000.0 / bpm
        val targetMs = getAutoTargetDurationMs(bpm)
        val bestBeatCount =
            BEAT_COUNT_OPTIONS.minByOrNull { abs(it * beatMs - targetMs) }
                ?: DEFAULT_BEAT_COUNT
        val duration = (bestBeatCount * beatMs).toInt()

        return duration.coerceIn(AUTO_MIN_DURATION_MS, AUTO_MAX_DURATION_MS)
    }

    private fun calculateBpmSpeedRatio(
        currentVideoId: String,
        nextVideoId: String,
    ): Float {
        val currentMeta = audioMetaCache[currentVideoId]
        val nextMeta = audioMetaCache[nextVideoId]
        val currentBpm = currentMeta?.bpm
        val nextBpm = nextMeta?.bpm

        if (currentBpm == null || nextBpm == null) {
            return 1.0f
        }
        if (currentBpm <= 0 || nextBpm <= 0) return 1.0f

        var ratio = currentBpm.toFloat() / nextBpm.toFloat()

        while (ratio > 1.5f) ratio /= 2f
        while (ratio < 0.67f) ratio *= 2f

        return if (ratio in BPM_RATIO_MIN..BPM_RATIO_MAX) {
            quantize(ratio)
        } else {
            1.0f
        }
    }

    private data class CamelotCode(
        val number: Int,
        val isMinor: Boolean,
    ) {
        override fun toString(): String = "$number${if (isMinor) "A" else "B"}"
    }

    private fun keyToCamelot(
        key: String,
        keyScale: String?,
    ): CamelotCode? {
        val semitone = keyToSemitone(key)
        if (semitone < 0) return null

        val isMinor = keyScale?.uppercase()?.contains("MIN") == true

        val minorCamelotByPitch = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)
        val majorCamelotByPitch = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)

        val number = if (isMinor) minorCamelotByPitch[semitone] else majorCamelotByPitch[semitone]
        return CamelotCode(number, isMinor)
    }

    private fun camelotDistance(
        a: CamelotCode,
        b: CamelotCode,
    ): Int {
        val numberDiff = abs(a.number - b.number)
        val circularDist = minOf(numberDiff, 12 - numberDiff)
        val typeDiff = if (a.isMinor != b.isMinor) 1 else 0
        return circularDist + typeDiff
    }

    private fun calculateKeyPitchRatio(
        currentVideoId: String,
        nextVideoId: String,
    ): Float {
        val currentMeta = audioMetaCache[currentVideoId]
        val nextMeta = audioMetaCache[nextVideoId]
        val currentKey = currentMeta?.key
        val nextKey = nextMeta?.key

        if (currentKey == null || nextKey == null) {
            return 1.0f
        }

        val currentCamelot = keyToCamelot(currentKey, currentMeta.keyScale)
        val nextCamelot = keyToCamelot(nextKey, nextMeta.keyScale)

        if (currentCamelot == null || nextCamelot == null) {
            return 1.0f
        }

        val dist = camelotDistance(currentCamelot, nextCamelot)

        if (dist <= 1) {
            return 1.0f
        }

        if (dist == 2 && currentCamelot.isMinor == nextCamelot.isMinor) {
            val currentSemitone = keyToSemitone(currentKey)
            val nextSemitone = keyToSemitone(nextKey)

            var chromDiff = (nextSemitone - currentSemitone + 12) % 12
            if (chromDiff > 6) chromDiff -= 12

            if (abs(chromDiff) != 2) {
                return 1.0f
            }

            val pitchRatio = exp(ln(2.0) * (-chromDiff.toDouble()) / 12.0).toFloat()
            return pitchRatio
        }

        return 1.0f
    }

    private fun keyToSemitone(key: String): Int =
        when (key.trim()) {
            "C" -> 0
            "C#", "Db" -> 1
            "D" -> 2
            "D#", "Eb" -> 3
            "E" -> 4
            "F" -> 5
            "F#", "Gb" -> 6
            "G" -> 7
            "G#", "Ab" -> 8
            "A" -> 9
            "A#", "Bb" -> 10
            "B" -> 11
            else -> -1
        }

    companion object {
        private const val DJ_FILTER_SIGMOID_K = 12f

        private const val LPF_START_HZ = 20000f
        private const val LPF_END_HZ = 200f
        private const val HPF_START_HZ = 2000f
        private const val HPF_END_HZ = 20f

        private const val AUTO_FALLBACK_DURATION_MS = 25000
        private const val AUTO_MIN_DURATION_MS = 10000
        private const val AUTO_MAX_DURATION_MS = 40000
        private val BEAT_COUNT_OPTIONS = intArrayOf(16, 32, 48, 64, 80, 96)
        private const val DEFAULT_BEAT_COUNT = 32
        private const val BPM_RATIO_MIN = 0.5f
        private const val BPM_RATIO_MAX = 1.5f

        private const val SPEED_PITCH_STEP = 0.02f
    }

    private fun finalizeCrossfade(
        nextIndex: Int,
        nextPlayer: ExoPlayer,
    ) {
        stopPositionUpdates()

        currentPlayer?.let { oldPlayer ->
            cleanupPlayerInternal(oldPlayer)
        }

        secondaryPlayerFilter?.let { filter ->
            filter.enabled = false
        }

        currentPlayer = nextPlayer
        currentPlayerFilter = secondaryPlayerFilter
        secondaryPlayer = null
        secondaryPlayerFilter = null

        nextPlayer.setAudioAttributes(audioAttributes, true)
        nextPlayer.setHandleAudioBecomingNoisy(true)

        currentPlayer?.volume = internalVolume
        currentPlayer?.playbackParameters = PlaybackParameters(internalPlaybackSpeed, internalPlaybackPitch)
        currentPlayer?.skipSilenceEnabled = internalSkipSilence

        setCrossfading(false)
        crossfadeFromIndex = -1
        transitionToState(InternalState.PLAYING)

        forwardingPlayer.notifyMediaItemChanged()

        startPositionUpdates()

        triggerPrecachingInternal()
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()

        positionUpdateJob =
            coroutineScope.launch {
                while (isActive && currentPlayer != null) {
                    try {
                        currentPlayer?.let { player ->
                            if (internalState == InternalState.PLAYING ||
                                internalState == InternalState.READY ||
                                internalState == InternalState.PAUSED
                            ) {
                                val timelinePlayer = if (isCrossfading) secondaryPlayer ?: player else player
                                val pos = timelinePlayer.currentPosition
                                val dur = timelinePlayer.duration
                                val buf = timelinePlayer.bufferedPosition

                                if (pos >= 0) cachedPosition = pos
                                if (dur > 0) cachedDuration = dur
                                if (buf >= 0) cachedBufferedPosition = buf

                                if (crossfadeEnabled &&
                                    !isCrossfading &&
                                    dur > 0 &&
                                    pos > 0
                                ) {
                                    val speed = internalPlaybackSpeed.coerceAtLeast(0.1f)
                                    val timeRemaining = ((dur - pos) / speed).toLong()
                                    val nextVideoId = playlist.getOrNull(getNextMediaItemIndex())?.mediaId
                                    val isPrecached = nextVideoId != null && precachedPlayers.containsKey(nextVideoId)
                                    val preparationBufferMs = if (isPrecached) 0L else 3000L
                                    val resolvedDurationMs =
                                        if (crossfadeDurationMs == DataStoreManager.CROSSFADE_DURATION_AUTO) {
                                            val currentVideoId = playlist.getOrNull(localCurrentMediaItemIndex)?.mediaId ?: ""
                                            resolveAutoCrossfadeDurationMs(currentVideoId, nextVideoId ?: "")
                                        } else {
                                            crossfadeDurationMs
                                        }
                                    val triggerThreshold = resolvedDurationMs.toLong() + preparationBufferMs
                                    if (timeRemaining in 1..triggerThreshold) {
                                        if (hasNextMediaItem()) {
                                            val nextIndex = getNextMediaItemIndex()
                                            triggerCrossfadeTransition(nextIndex)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                    }
                    delay(200)
                }
            }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun triggerPrecachingInternal() {
        if (!precacheEnabled || playlist.isEmpty()) return

        cancelPrecaching()
        precacheJob =
            coroutineScope.launch {
                try {
                    val indicesToPrecache = mutableListOf<Int>()

                    val index = localCurrentMediaItemIndex
                    for (i in 1..maxPrecacheCount) {
                        val nextIndex =
                            when (internalRepeatMode) {
                                PlayerConstants.REPEAT_MODE_ALL -> {
                                    (index + i) % playlist.size
                                }
                                else -> {
                                    val next = index + i
                                    if (next < playlist.size) next else break
                                }
                            }

                        if (nextIndex != localCurrentMediaItemIndex &&
                            !precachedPlayers.containsKey(playlist.getOrNull(nextIndex)?.mediaId)
                        ) {
                            indicesToPrecache.add(nextIndex)
                        }
                    }

                    for (idx in indicesToPrecache) {
                        if (!isActive) break

                        val mediaItem = playlist.getOrNull(idx) ?: continue

                        if (precachedPlayers.containsKey(mediaItem.mediaId)) continue

                        var playerToProtect: ExoPlayer? = null
                        try {
                            val pwf = createExoPlayerInstance(handleAudioFocus = false)
                            playerToProtect = pwf.player
                            pwf.player.setMediaItem(mediaItem.toMedia3MediaItem())
                            pwf.player.prepare()

                            if (!isActive) {
                                cleanupPlayerInternal(pwf.player)
                                break
                            }

                            val oldCached = precachedPlayers.put(mediaItem.mediaId, PrecachedPlayer(pwf.player, mediaItem, pwf.filter))
                            oldCached?.player?.let { cleanupPlayerInternal(it) }
                            playerToProtect = null
                        } catch (e: Exception) {
                            playerToProtect?.let { cleanupPlayerInternal(it) }
                            if (e is CancellationException) throw e
                        }
                        delay(100)
                    }
                } catch (e: CancellationException) {
                }
            }
    }

    private fun cancelPrecaching() {
        precacheJob?.cancel()
        precacheJob = null
    }

    private fun clearPrecacheExceptCurrentInternal() {
        val iterator = precachedPlayers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key != currentMediaItem?.mediaId) {
                cleanupPlayerInternal(entry.value.player)
                iterator.remove()
            }
        }
    }

    private fun clearAllPrecacheInternal() {
        precachedPlayers.values.forEach { cleanupPlayerInternal(it.player) }
        precachedPlayers.clear()
    }

    private fun notifyEqualizerIntent(shouldOpen: Boolean) {
        listeners.forEach { it.shouldOpenOrCloseEqualizerIntent(shouldOpen) }
    }

    private fun createShuffleOrder() {
        if (playlist.isEmpty()) {
            shuffleIndices.clear()
            shuffleOrder.clear()
            return
        }

        val indices = playlist.indices.toMutableList()

        val currentIndex = localCurrentMediaItemIndex
        if (currentIndex in indices) {
            indices.removeAt(currentIndex)
        }

        indices.shuffle()

        if (currentIndex in playlist.indices) {
            indices.add(0, currentIndex)
        }

        shuffleOrder.clear()
        shuffleOrder.addAll(indices)

        shuffleIndices.clear()
        shuffleIndices.addAll(List(playlist.size) { 0 })
        shuffleOrder.forEachIndexed { shuffledPos, originalIndex ->
            shuffleIndices[originalIndex] = shuffledPos
        }
    }

    private fun clearShuffleOrder() {
        shuffleIndices.clear()
        shuffleOrder.clear()
    }

    private fun insertIntoShuffleOrder(
        insertedOriginalIndex: Int,
        afterShufflePos: Int,
    ) {
        if (playlist.isEmpty() || insertedOriginalIndex !in playlist.indices) {
            return
        }

        for (i in shuffleOrder.indices) {
            if (shuffleOrder[i] >= insertedOriginalIndex) {
                shuffleOrder[i]++
            }
        }

        val insertPos = (afterShufflePos + 1).coerceIn(0, shuffleOrder.size)
        shuffleOrder.add(insertPos, insertedOriginalIndex)

        shuffleIndices.clear()
        shuffleIndices.addAll(List(playlist.size) { 0 })
        shuffleOrder.forEachIndexed { shuffledPos, origIndex ->
            if (origIndex < shuffleIndices.size) {
                shuffleIndices[origIndex] = shuffledPos
            }
        }
    }

    private fun getShuffledMediaItemList(): List<GenericMediaItem> {
        if (!internalShuffleModeEnabled || shuffleOrder.isEmpty()) {
            return playlist.toList()
        }
        return shuffleOrder.mapNotNull { playlist.getOrNull(it) }
    }

    private fun notifyTimelineChanged(reason: String) {
        val list = getShuffledMediaItemList()
        listeners.forEach { it.onTimelineChanged(list, reason) }
    }
}