@file:OptIn(ExperimentalMaterial3Api::class)

package com.maxrave.simpmusic.ui.screen.player

import androidx.compose.animation.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kmpalette.rememberPaletteState
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.extension.GradientAngle
import com.maxrave.simpmusic.extension.GradientOffset
import com.maxrave.simpmusic.extension.KeepScreenOn
import com.maxrave.simpmusic.extension.getColorFromPalette
import com.maxrave.simpmusic.extension.hsvToColor
import com.maxrave.simpmusic.extension.rememberIsInPipMode
import com.maxrave.simpmusic.ui.component.AddToPlaylistModalBottomSheet
import com.maxrave.simpmusic.ui.component.FullscreenLyricsSheet
import com.maxrave.simpmusic.ui.component.InfoPlayerBottomSheet
import com.maxrave.simpmusic.ui.component.NowPlayingBottomSheet
import com.maxrave.simpmusic.ui.component.QueueBottomSheet
import com.maxrave.simpmusic.ui.component.VoteLyricsDialog
import com.maxrave.simpmusic.ui.icon.KeyboardArrowDown
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.list.ArtistDestination
import com.maxrave.simpmusic.ui.navigation.destination.player.FullscreenDestination
import com.maxrave.simpmusic.ui.screen.player.content.NowPlayingContentActions
import com.maxrave.simpmusic.ui.screen.player.content.NowPlayingContentAppleMusic
import com.maxrave.simpmusic.ui.screen.player.content.NowPlayingContentM3Expressive
import com.maxrave.simpmusic.ui.screen.player.content.NowPlayingContentSpotify
import com.maxrave.simpmusic.ui.screen.player.content.NowPlayingContentState
import com.maxrave.simpmusic.ui.screen.player.content.PlayerBackdropColor
import com.maxrave.simpmusic.ui.screen.player.content.toAudioCodecLabel
import com.maxrave.simpmusic.viewModel.LyricsProvider
import com.maxrave.simpmusic.viewModel.NowPlayingBottomSheetUIEvent
import com.maxrave.simpmusic.viewModel.NowPlayingBottomSheetViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

// Imports para la carátula de Apple Music
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "NowPlayingScreen"

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalMaterial3Api
@Composable
fun NowPlayingScreen(
    sharedViewModel: SharedViewModel = koinInject(),
    navController: NavController,
    onDismiss: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )

    val hideSheet: () -> Unit = {
        coroutineScope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        modifier =
            Modifier
                .fillMaxHeight(),
        onDismissRequest = {
            onDismiss()
        },
        containerColor = Color.Black,
        dragHandle = {},
        scrimColor = Color.Black.copy(alpha = .5f),
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RectangleShape,
    ) {
        NowPlayingScreenContent(
            sharedViewModel = sharedViewModel,
            navController = navController,
            isExpanded = sheetState.currentValue == SheetValue.Expanded,
            dismissIcon = SimpIcons.KeyboardArrowDown,
            onDismiss = {
                hideSheet()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreenContent(
    sharedViewModel: SharedViewModel = koinInject(),
    mediaPlayerHandler: MediaPlayerHandler = koinInject(),
    navController: NavController,
    isExpanded: Boolean,
    dismissIcon: ImageVector,
    onDismiss: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    // ViewModel State
    val controllerState by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val screenDataState by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val timelineState by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val lyricsOffsetMs by sharedViewModel.getLyricsOffsetMs().collectAsStateWithLifecycle(0)
    val likeStatus by sharedViewModel.likeStatus.collectAsStateWithLifecycle()
    val castState by sharedViewModel.castState.collectAsStateWithLifecycle()
    val formatState by sharedViewModel.format.collectAsStateWithLifecycle(initialValue = null)

    val shouldShowVideo by sharedViewModel.getVideo.collectAsStateWithLifecycle()
    val translatedVoteState by sharedViewModel.translatedVoteState.collectAsStateWithLifecycle()
    val lyricsVoteState by sharedViewModel.lyricsVoteState.collectAsStateWithLifecycle()
    val isUserLoggedIn by sharedViewModel
        .isUserLoggedInFlow()
        .collectAsStateWithLifecycle(initialValue = false)

    val nowPlayingStyle by sharedViewModel
        .getNowPlayingStyle()
        .collectAsStateWithLifecycle(initialValue = DataStoreManager.NOW_PLAYING_STYLE_SPOTIFY)

    val nowPlayingState by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val queueDataState by sharedViewModel.getQueueDataState().collectAsStateWithLifecycle()
    val artworkQueue by remember {
        derivedStateOf { queueDataState?.data?.listTracks ?: emptyList() }
    }
    val nowPlayingVideoId: String? = nowPlayingState?.track?.videoId
    val currentOrderIndex by remember(artworkQueue, nowPlayingVideoId) {
        derivedStateOf {
            deriveOrderIndex(
                queue = artworkQueue,
                nowPlayingVideoId = nowPlayingVideoId,
                playerOrderIndex = mediaPlayerHandler.currentOrderIndex(),
            )
        }
    }
    val artworkPagerState =
        rememberPagerState(
            initialPage = currentOrderIndex.coerceAtLeast(0),
            pageCount = { artworkQueue.size.coerceAtLeast(1) },
        )
    var isAnimatingFromPlayer by remember { mutableStateOf(false) }
    var isUserDraggingActive by remember { mutableStateOf(false) }
    var pendingUserSwipe by remember { mutableStateOf(false) }

    LaunchedEffect(artworkPagerState) {
        snapshotFlow {
            artworkPagerState.isScrollInProgress to isAnimatingFromPlayer
        }.collect { (scrolling, animating) ->
            isUserDraggingActive = scrolling && !animating
            if (isUserDraggingActive) pendingUserSwipe = true
        }
    }

    LaunchedEffect(currentOrderIndex) {
        val target = currentOrderIndex
        if (!isUserDraggingActive &&
            artworkQueue.isNotEmpty() &&
            target in 0 until artworkQueue.size &&
            target != artworkPagerState.currentPage
        ) {
            isAnimatingFromPlayer = true
            try {
                artworkPagerState.animateScrollToPage(target)
            } finally {
                isAnimatingFromPlayer = false
            }
        }
    }

    val latestOrderIndex by rememberUpdatedState(currentOrderIndex)
    val latestQueueSize by rememberUpdatedState(artworkQueue.size)
    LaunchedEffect(artworkPagerState) {
        snapshotFlow { artworkPagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (isAnimatingFromPlayer) return@collect
                if (!pendingUserSwipe) return@collect
                pendingUserSwipe = false
                val queueSize = latestQueueSize
                val orderIndex = latestOrderIndex
                if (queueSize == 0) return@collect
                if (settled !in 0 until queueSize) return@collect
                if (settled == orderIndex) return@collect

                runCatching {
                    when (val action = computeSeekAction(settled, orderIndex)) {
                        ArtworkSeekAction.Next -> {
                            sharedViewModel.onUIEvent(UIEvent.Next)
                        }
                        ArtworkSeekAction.Previous -> {
                            sharedViewModel.onUIEvent(UIEvent.SkipToPrevious)
                        }
                        is ArtworkSeekAction.Skip -> {
                            mediaPlayerHandler.playMediaItemInMediaSource(action.index)
                        }
                        ArtworkSeekAction.NoOp -> {
                            Unit
                        }
                    }
                }.onFailure { error ->
                    Logger.w(TAG, "ArtworkPager seek failed: ${error.message}")
                }
            }
    }

    LaunchedEffect(artworkQueue.size) {
        if (artworkQueue.isNotEmpty() && artworkPagerState.currentPage >= artworkQueue.size) {
            runCatching { artworkPagerState.scrollToPage(artworkQueue.lastIndex) }
        }
    }

    val isInPipMode = rememberIsInPipMode()
    val mainScrollState = rememberScrollState()

    var showHideMiddleLayout by rememberSaveable { mutableStateOf(true) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showFullscreenLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueueBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showInfoBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showVoteDialog by rememberSaveable { mutableStateOf(false) }
    var showAddToPlaylistDirectly by rememberSaveable { mutableStateOf(false) }
    var shouldShowToolbar by remember { mutableStateOf(false) }

    val paletteState = rememberPaletteState()
    val startColor = remember { Animatable(Color.Black) }
    val endColor = remember { Animatable(Color.Black) }
    val gradientOffset by remember { mutableStateOf(GradientOffset(GradientAngle.CW135)) }
    var spotShadowColor by remember { mutableStateOf(Color.White) }

    LaunchedEffect(screenDataState) {
        Logger.d(TAG, "ScreenDataState: $screenDataState")
        showHideMiddleLayout = screenDataState.canvasData == null
    }

    LaunchedEffect(Unit) {
        snapshotFlow { screenDataState.bitmap }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest {
                paletteState.generate(it)
            }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { paletteState.palette }
            .distinctUntilChanged()
            .collectLatest {
                spotShadowColor = it.getColorFromPalette()
                startColor.animateTo(it.getColorFromPalette())
                endColor.animateTo(PlayerBackdropColor)
            }
    }

    LaunchedEffect(spotShadowColor) {
        Logger.d(TAG, "spotShadowColor: $spotShadowColor")
    }

    var isSliding by rememberSaveable { mutableStateOf(false) }
    var sliderValue by rememberSaveable { mutableFloatStateOf(0f) }
    LaunchedEffect(key1 = timelineState, key2 = isSliding) {
        if (!isSliding) {
            sliderValue =
                if (timelineState.total > 0L) {
                    timelineState.current.toFloat() * 100 / timelineState.total.toFloat()
                } else {
                    0f
                }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "crossfadeRainbow")
    val rainbowHue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rainbowHue",
    )
    val rainbowColor = hsvToColor(rainbowHue, 1f, 1f)
    val sliderTrackColor by animateColorAsState(
        targetValue = if (timelineState.isCrossfading) rainbowColor else Color.White,
        animationSpec = tween(300),
        label = "sliderCrossfadeColor",
    )

    var showHideControlLayout by rememberSaveable { mutableStateOf(true) }
    val controlLayoutAlpha: Float by animateFloatAsState(
        targetValue = if (showHideControlLayout) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "ControlLayoutAlpha",
    )

    var showHideJob by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = showHideJob) {
        if (!showHideJob) {
            delay(5000)
            if (mainScrollState.value == 0) showHideControlLayout = false
            showHideJob = true
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            screenDataState
        }.distinctUntilChangedBy {
            it.canvasData?.url
        }.collectLatest {
            if (it.canvasData != null && mainScrollState.value == 0) {
                showHideJob = false
            } else {
                showHideJob = true
                showHideControlLayout = true
            }
        }
    }

    LaunchedEffect(key1 = showHideControlLayout) {
        if (showHideControlLayout && screenDataState.canvasData != null && mainScrollState.value == 0) {
            showHideJob = false
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { mainScrollState.value }
            .distinctUntilChanged()
            .collect {
                if (it > 0 && !showHideControlLayout && screenDataState.canvasData != null) {
                    showHideJob = true
                    showHideControlLayout = true
                } else if (showHideControlLayout && it == 0 && screenDataState.canvasData != null) {
                    showHideJob = false
                }
            }
    }

    var currentLyricLineIndex by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(timelineState, screenDataState.lyricsData?.lyrics, lyricsOffsetMs) {
        val lyrics = screenDataState.lyricsData?.lyrics
        if (lyrics == null || lyrics.syncType == "UNSYNCED" || lyrics.syncType == null) {
            currentLyricLineIndex = -1
            return@LaunchedEffect
        }
        val lines = lyrics.lines ?: return@LaunchedEffect
        val nowMs = timelineState.current - lyricsOffsetMs
        if (nowMs > 0L) {
            lines.indices.forEach { i ->
                val startTimeMs = lines[i].startTimeMs.toLongOrNull() ?: 0L
                val endTimeMs =
                    if (i < lines.size - 1) {
                        lines[i + 1].startTimeMs.toLongOrNull() ?: 0L
                    } else {
                        startTimeMs + 60000
                    }
                if (nowMs in startTimeMs..endTimeMs) {
                    currentLyricLineIndex = i
                }
            }
            if (lines.isNotEmpty() &&
                nowMs in 0..(lines.getOrNull(0)?.startTimeMs?.toLongOrNull() ?: 0L)
            ) {
                currentLyricLineIndex = -1
            }
        } else {
            currentLyricLineIndex = -1
        }
    }

    if (showSheet) {
        NowPlayingBottomSheet(
            onDismiss = { showSheet = false },
            navController = navController,
            onNavigateToOtherScreen = { onDismiss() },
            song = null,
            setSleepTimerEnable = true,
            changeMainLyricsProviderEnable = true,
        )
    }

    if (showFullscreenLyrics) {
        FullscreenLyricsSheet(
            sharedViewModel = sharedViewModel,
            navController = navController,
            color = startColor.value,
        ) {
            showFullscreenLyrics = false
        }
    }

    if (showQueueBottomSheet) {
        QueueBottomSheet(onDismiss = { showQueueBottomSheet = false })
    }

    if (showInfoBottomSheet) {
        InfoPlayerBottomSheet(onDismiss = { showInfoBottomSheet = false })
    }

    if (showAddToPlaylistDirectly) {
        val viewModel: NowPlayingBottomSheetViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.resetPlaylists()
            viewModel.setSongEntity(null)
        }

        AddToPlaylistModalBottomSheet(
            isBottomSheetVisible = true,
            listLocalPlaylist = uiState.listLocalPlaylist,
            listYouTubePlaylist = uiState.listYouTubePlaylist,
            onDismiss = { showAddToPlaylistDirectly = false },
            onClick = { playlist ->
                viewModel.onUIEvent(NowPlayingBottomSheetUIEvent.AddToPlaylist(playlist.id))
                showAddToPlaylistDirectly = false
            },
            onYTPlaylistClick = { playlist ->
                viewModel.onUIEvent(NowPlayingBottomSheetUIEvent.AddToYouTubePlaylist(playlist.browseId))
                showAddToPlaylistDirectly = false
            },
            videoId = uiState.songUIState.videoId,
        )
    }

    if (showVoteDialog) {
        val canVoteLyrics =
            screenDataState.lyricsData?.lyricsProvider == LyricsProvider.SIMPMUSIC &&
                screenDataState.lyricsData?.lyrics?.simpMusicLyrics != null
        val canVoteTranslatedLyrics =
            screenDataState.lyricsData?.translatedLyrics?.second == LyricsProvider.SIMPMUSIC &&
                screenDataState.lyricsData?.translatedLyrics?.first?.simpMusicLyrics != null

        VoteLyricsDialog(
            canVoteLyrics = canVoteLyrics,
            canVoteTranslatedLyrics = canVoteTranslatedLyrics,
            lyricsVoteState = lyricsVoteState,
            translatedLyricsVoteState = translatedVoteState,
            onVoteLyrics = { upvote -> sharedViewModel.voteLyrics(upvote) },
            onVoteTranslatedLyrics = { upvote -> sharedViewModel.voteTranslatedLyrics(upvote) },
            onDismiss = { showVoteDialog = false },
        )
    }

    if (screenDataState.lyricsData != null && controllerState.isPlaying) {
        KeepScreenOn()
    }

    val highResThumbnail = rememberHighResArtwork(
        title = screenDataState.nowPlayingTitle,
        artist = screenDataState.artistName,
        fallbackThumbnail = screenDataState.thumbnailURL
    )

    val state =
        NowPlayingContentState(
            screenData = screenDataState.copy(
                nowPlayingTitle = screenDataState.nowPlayingTitle.cleanMusicTitle(),
                thumbnailURL = highResThumbnail
            ),
            controllerState = controllerState,
            timelineState = timelineState,
            timelineFlow = sharedViewModel.timeline,
            likeStatus = likeStatus,
            castState = castState,
            shouldShowVideo = shouldShowVideo,
            isUserLoggedIn = isUserLoggedIn,
            artworkQueue = artworkQueue,
            currentOrderIndex = currentOrderIndex,
            artworkPagerState = artworkPagerState,
            startColor = startColor,
            endColor = endColor,
            spotShadowColor = spotShadowColor,
            gradientOffset = gradientOffset,
            sliderTrackColor = sliderTrackColor,
            sliderValue = sliderValue,
            currentLyricLineIndex = currentLyricLineIndex,
            showControlLayout = showHideControlLayout,
            controlLayoutAlpha = controlLayoutAlpha,
            showHideMiddleLayout = showHideMiddleLayout,
            shouldShowToolbar = shouldShowToolbar,
            isInPipMode = isInPipMode,
            mainScrollState = mainScrollState,
            isExpanded = isExpanded,
            dismissIcon = dismissIcon,
            audioCodecLabel = formatState?.codecs.toAudioCodecLabel(),
        )
    val actions =
        NowPlayingContentActions(
            onUIEvent = { sharedViewModel.onUIEvent(it) },
            onSeekToQueueIndex = { index ->
                mediaPlayerHandler.playMediaItemInMediaSource(index)
            },
            onArtworkBitmap = { sharedViewModel.setBitmap(it) },
            onSliderChange = { newValue ->
                isSliding = true
                sliderValue = newValue
            },
            onSliderChangeFinished = {
                isSliding = false
                sharedViewModel.onUIEvent(
                    UIEvent.UpdateProgress(sliderValue),
                )
            },
            onToggleControls = {
                showHideJob = true
                showHideControlLayout = !showHideControlLayout
            },
            onNavigateToArtist = {
                val song = sharedViewModel.nowPlayingState.value?.songEntity
                (
                    song?.artistId?.firstOrNull()?.takeIf { it.isNotEmpty() }
                        ?: screenDataState.songInfoData?.authorId
                    )?.let { channelId ->
                        onDismiss()
                        navController.navigate(
                            ArtistDestination(
                                channelId = channelId,
                            ),
                        )
                    }
            },
            onAddToYouTubeLiked = { sharedViewModel.addToYouTubeLiked() },
            onShowMoreSheet = { showSheet = true },
            onShowQueue = { showQueueBottomSheet = true },
            onShowInfo = { showInfoBottomSheet = true },
            onShowAddToPlaylist = { showAddToPlaylistDirectly = true },
            onShowFullscreenLyrics = { showFullscreenLyrics = true },
            onShowVoteDialog = { showVoteDialog = true },
            onEnterFullscreenVideo = {
                onDismiss()
                navController.navigate(FullscreenDestination)
            },
            onDismiss = onDismiss,
            onToolbarVisibilityChange = { shouldShowToolbar = it },
            onMoveQueueItem = { from, to ->
                coroutineScope.launch {
                    mediaPlayerHandler.swap(from, to)
                }
            },
            onRemoveQueueItem = { index ->
                mediaPlayerHandler.removeMediaItem(index)
            },
        )
    when (nowPlayingStyle) {
        DataStoreManager.NOW_PLAYING_STYLE_M3_EXPRESSIVE ->
            NowPlayingContentM3Expressive(
                state = state,
                actions = actions,
            )

        DataStoreManager.NOW_PLAYING_STYLE_APPLE_MUSIC ->
            NowPlayingContentAppleMusic(
                state = state,
                actions = actions,
            )

        else ->
            NowPlayingContentSpotify(
                state = state,
                actions = actions,
            )
    }
}

// -------------------------------------------------------------------------
// UTILIDADES PARA FILTRAR TÍTULO Y OBTENER CARÁTULA EN ALTA RESOLUCIÓN
// -------------------------------------------------------------------------

fun String.cleanMusicTitle(): String {
    var clean = this
    clean = clean.replace(Regex("(?i)[\\[\\(]?(official|audio|music video|lyric|live|remix|visualizer).*?[\\]\\)]?"), "")
    clean = clean.replace(Regex("(?i)\\s+(ft\\.|feat\\.?|featuring).*"), "")
    clean = clean.replace(Regex("(?i)\\s+-\\s+.*"), "")
    return clean.trim().ifEmpty { this }
}

@Composable
fun rememberHighResArtwork(
    title: String?,
    artist: String?,
    fallbackThumbnail: String?
): String? {
    var artworkUrl by remember(title, artist, fallbackThumbnail) {
        mutableStateOf(fallbackThumbnail)
    }

    LaunchedEffect(title, artist) {
        if (title.isNullOrBlank()) return@LaunchedEffect

        val cleanTitle = title.cleanMusicTitle()
        val cleanArtist = artist?.replace(Regex("(?i)- topic"), "")?.trim() ?: ""

        withContext(Dispatchers.IO) {
            try {
                val client = HttpClient(CIO)
                val query = "$cleanTitle $cleanArtist".replace(" ", "+")
                val response = client.get("https://itunes.apple.com/search?term=$query&entity=song&limit=1")
                val body = response.bodyAsText()

                val artworkRegex = Regex(""""artworkUrl100":"([^"]+)"""")
                val match = artworkRegex.find(body)
                if (match != null) {
                    artworkUrl = match.groupValues[1].replace("100x100bb", "600x600bb")
                }
                client.close()
            } catch (e: Exception) {
                // Falla silenciosa, mantiene la carátula de respaldo
            }
        }
    }
    return artworkUrl
}