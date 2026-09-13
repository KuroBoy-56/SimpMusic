package com.maxrave.simpmusic.ui.screen

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kmpalette.rememberPaletteState
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.utils.connectArtists
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.expect.toggleMiniPlayer
import com.maxrave.simpmusic.expect.ui.PlatformBackdrop
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.extension.formatDuration
import com.maxrave.simpmusic.extension.getColorFromPalette
import com.maxrave.simpmusic.extension.toResizedBitmap
import com.maxrave.simpmusic.getPlatform
import com.maxrave.simpmusic.ui.component.ExplicitBadge
import com.maxrave.simpmusic.ui.component.HeartCheckBox
import com.maxrave.simpmusic.ui.component.PlayPauseButton
import com.maxrave.simpmusic.ui.component.PlayerControlLayout
import com.maxrave.simpmusic.ui.component.QueueBottomSheet
import com.maxrave.simpmusic.ui.component.liquidGlass
import com.maxrave.simpmusic.ui.component.rememberHolderPainter
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.PictureInPictureAlt
import com.maxrave.simpmusic.ui.icon.QueueMusic
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.VolumeOff
import com.maxrave.simpmusic.ui.icon.VolumeUp
import com.maxrave.simpmusic.ui.theme.LocalIsDarkTheme
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.crossfading
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

import com.maxrave.simpmusic.ui.screen.player.cleanMusicTitle
import com.maxrave.simpmusic.ui.screen.player.rememberHighResArtwork

private const val TAG = "MiniPlayer"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    modifier: Modifier,
    backdrop: PlatformBackdrop,
    sharedViewModel: SharedViewModel = koinInject(),
    onClose: () -> Unit,
    onClick: () -> Unit,
) {
    val isLiquidGlassEnabled by sharedViewModel.getEnableLiquidGlass().collectAsStateWithLifecycle(DataStoreManager.FALSE)
    val controllerState by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val timelineState by sharedViewModel.timeline.collectAsStateWithLifecycle()

    val layer = rememberGraphicsLayer()
    val luminanceAnimation = remember { Animatable(0f) }

    val useGlassSurface = isLiquidGlassEnabled == DataStoreManager.TRUE || getPlatform() == Platform.Desktop

    val isDarkTheme = LocalIsDarkTheme.current
    val textColor by animateColorAsState(
        targetValue =
            if (useGlassSurface) {
                if (isDarkTheme) Color.White else Color.Black
            } else if (luminanceAnimation.value > 0.6f) {
                Color.Black
            } else {
                Color.White
            },
        label = "MiniPlayerTextColor",
        animationSpec = tween(500),
    )

    LaunchedEffect(layer, useGlassSurface) {
        val buffer = IntArray(25)
        while (isActive && useGlassSurface) {
            try {
                withContext(Dispatchers.Main) {
                    val imageBitmap = layer.toImageBitmap()
                    val thumbnail = imageBitmap.toResizedBitmap(5, 5)
                    thumbnail.readPixels(buffer)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error getting pixels from layer: ${e.message}")
            }
            val averageLuminance =
                (0 until 25).sumOf { index ->
                    val color = buffer.get(index)
                    val r = (color shr 16 and 0xFF) / 255f
                    val g = (color shr 8 and 0xFF) / 255f
                    val b = (color and 0xFF) / 255f
                    0.2126 * r + 0.7152 * g + 0.0722 * b
                } / 25
            luminanceAnimation.animateTo(
                averageLuminance.coerceIn(0.3, 0.8).toFloat(),
                tween(500),
            )
            delay(1.seconds)
        }
    }

    val (songEntity, setSongEntity) =
        remember {
            mutableStateOf<SongEntity?>(null)
        }
    val (liked, setLiked) =
        remember {
            mutableStateOf(false)
        }
    val (isPlaying, setIsPlaying) =
        remember {
            mutableStateOf(false)
        }
    val (progress, setProgress) =
        remember {
            mutableFloatStateOf(0f)
        }
    val (isCrossfading, setIsCrossfading) =
        remember {
            mutableStateOf(false)
        }

    val coroutineScope = rememberCoroutineScope()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "",
    )

    // Palette state
    val paletteState = rememberPaletteState()
    val background =
        remember {
            Animatable(Color.DarkGray)
        }

    val offsetX = remember { Animatable(initialValue = 0f) }
    val offsetY = remember { Animatable(0f) }

    var loading by rememberSaveable {
        mutableStateOf(true)
    }

    var bitmap by remember {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(bitmap) {
        val bm = bitmap
        if (bm != null) {
            paletteState.generate(bm)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { paletteState.palette }
            .distinctUntilChanged()
            .collectLatest {
                background.animateTo(it.getColorFromPalette())
            }
    }

    LaunchedEffect(key1 = true) {
        val job1 =
            launch {
                sharedViewModel.nowPlayingState.collect { item ->
                    if (item != null) {
                        setSongEntity(item.songEntity)
                    }
                }
            }
        val job2 =
            launch {
                sharedViewModel.controllerState.collectLatest { state ->
                    setLiked(state.isLiked)
                    setIsPlaying(state.isPlaying)
                    setIsCrossfading(state.isCrossfading)
                }
            }
        val job4 =
            launch {
                sharedViewModel.timeline.collect { timeline ->
                    loading = timeline.loading
                    val prog =
                        if (timeline.total > 0L && timeline.current >= 0L) {
                            timeline.current.toFloat() / timeline.total
                        } else {
                            0f
                        }
                    setProgress(prog)
                }
            }
        job1.join()
        job2.join()
        job4.join()
    }

    val highResThumbnail = rememberHighResArtwork(
        title = songEntity?.title,
        artist = songEntity?.artistName?.connectArtists(),
        fallbackThumbnail = songEntity?.thumbnails
    )

    if (getPlatform() == Platform.Android) {
        val miniPlayerShape =
            if (isLiquidGlassEnabled == DataStoreManager.TRUE) CircleShape else RoundedCornerShape(12.dp)
        Card(
            shape = miniPlayerShape,
            colors =
                CardDefaults.cardColors(
                    containerColor = if (isLiquidGlassEnabled == DataStoreManager.TRUE) Color.Transparent else background.value,
                    disabledContainerColor = if (isLiquidGlassEnabled == DataStoreManager.TRUE) Color.Transparent else background.value,
                ),
            modifier =
                modifier
                    .then(
                        if (isLiquidGlassEnabled == DataStoreManager.TRUE) {
                            Modifier.liquidGlass(backdrop, layer, luminanceAnimation.value, RoundedCornerShape(16.dp))
                        } else {
                            Modifier
                        },
                    ).then(
                        Modifier
                            .clip(miniPlayerShape)
                            .offset { IntOffset(0, offsetY.value.roundToInt()) }
                            .clickable(
                                onClick = onClick,
                            ).pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                    },
                                    onVerticalDrag = { change: PointerInputChange, dragAmount: Float ->
                                        if (offsetY.value + dragAmount > 0) {
                                            coroutineScope.launch {
                                                change.consume()
                                                offsetY.animateTo(offsetY.value + 2 * dragAmount)
                                                Logger.w("MiniPlayer", "Dragged ${offsetY.value}")
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            offsetY.animateTo(0f)
                                        }
                                    },
                                    onDragEnd = {
                                        Logger.w("MiniPlayer", "Drag Ended")
                                        coroutineScope.launch {
                                            if (offsetY.value > 70) {
                                                onClose()
                                            }
                                            offsetY.animateTo(0f)
                                        }
                                    },
                                )
                            },
                    ),
        ) {
            Box(modifier = Modifier.fillMaxHeight()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                ) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Box(modifier = Modifier.weight(1F)) {
                        Row(
                            modifier =
                                Modifier
                                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                            },
                                            onHorizontalDrag = {
                                                    change: PointerInputChange,
                                                    dragAmount: Float,
                                                ->
                                                coroutineScope.launch {
                                                    change.consume()
                                                    offsetX.animateTo(offsetX.value + dragAmount * 2)
                                                    Logger.w("MiniPlayer", "Dragged ${offsetX.value}")
                                                }
                                            },
                                            onDragCancel = {
                                                Logger.w("MiniPlayer", "Drag Cancelled")
                                                coroutineScope.launch {
                                                    if (offsetX.value > 200) {
                                                        sharedViewModel.onUIEvent(UIEvent.Previous)
                                                    } else if (offsetX.value < -120) {
                                                        sharedViewModel.onUIEvent(UIEvent.Next)
                                                    }
                                                    offsetX.animateTo(0f)
                                                }
                                            },
                                            onDragEnd = {
                                                Logger.w("MiniPlayer", "Drag Ended")
                                                coroutineScope.launch {
                                                    if (offsetX.value > 200) {
                                                        sharedViewModel.onUIEvent(UIEvent.Previous)
                                                    } else if (offsetX.value < -120) {
                                                        sharedViewModel.onUIEvent(UIEvent.Next)
                                                    }
                                                    offsetX.animateTo(0f)
                                                }
                                            },
                                        )
                                    },
                        ) {
                            AsyncImage(
                                model =
                                    ImageRequest
                                        .Builder(LocalPlatformContext.current)
                                        .data(highResThumbnail)
                                        .crossfade(550)
                                        .build(),
                                placeholder = rememberHolderPainter(),
                                error = rememberHolderPainter(),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                onSuccess = {
                                    bitmap =
                                        it.result.image.toImageBitmap()
                                },
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .align(Alignment.CenterVertically)
                                        .clip(
                                            RoundedCornerShape(4.dp),
                                        ),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            AnimatedContent(
                                targetState = songEntity,
                                modifier = Modifier.weight(1F).fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart,
                                transitionSpec = {
                                    if (targetState != initialState) {
                                        (
                                            slideInHorizontally { width ->
                                                width
                                            } + fadeIn()
                                            ).togetherWith(
                                                slideOutHorizontally { width -> +width } + fadeOut(),
                                            )
                                    } else {
                                        (
                                            slideInHorizontally { width ->
                                                +width
                                            } + fadeIn()
                                            ).togetherWith(
                                                slideOutHorizontally { width -> width } + fadeOut(),
                                            )
                                    }.using(
                                        SizeTransform(clip = false),
                                    )
                                },
                            ) { target ->
                                if (target != null) {
                                    Column(
                                        Modifier
                                            .wrapContentHeight()
                                            .align(Alignment.CenterVertically),
                                    ) {
                                        Text(
                                            text = (songEntity?.title ?: "").toString().cleanMusicTitle(),
                                            style = typo().labelSmall,
                                            color = textColor,
                                            maxLines = 1,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .wrapContentHeight(
                                                        align = Alignment.CenterVertically,
                                                    ).basicMarquee(
                                                        iterations = Int.MAX_VALUE,
                                                        animationMode = MarqueeAnimationMode.Immediately,
                                                    ).focusable(),
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.animation.AnimatedVisibility(visible = songEntity?.isExplicit == true) {
                                                ExplicitBadge(
                                                    modifier =
                                                        Modifier
                                                            .size(20.dp)
                                                            .padding(end = 4.dp)
                                                            .weight(1f),
                                                )
                                            }
                                            Text(
                                                text = (songEntity?.artistName?.connectArtists() ?: ""),
                                                style = typo().bodySmall,
                                                maxLines = 1,
                                                color = textColor,
                                                modifier =
                                                    Modifier
                                                        .weight(1f)
                                                        .wrapContentHeight(
                                                            align = Alignment.CenterVertically,
                                                        ).basicMarquee(
                                                            iterations = Int.MAX_VALUE,
                                                            animationMode = MarqueeAnimationMode.Immediately,
                                                        ).focusable(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(15.dp))
                    HeartCheckBox(checked = liked, size = 30, tint = textColor) {
                        sharedViewModel.onUIEvent(UIEvent.ToggleLike)
                    }
                    Spacer(modifier = Modifier.width(15.dp))
                    Crossfade(targetState = loading, label = "") {
                        if (it) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = textColor,
                                    strokeWidth = 3.dp,
                                )
                            }
                        } else {
                            PlayPauseButton(isPlaying = isPlaying, modifier = Modifier.size(48.dp), tint = textColor) {
                                sharedViewModel.onUIEvent(UIEvent.PlayPause)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(15.dp))
                }
                Box(
                    modifier =
                        Modifier
                            .wrapContentSize(Alignment.Center)
                            .padding(
                                horizontal = 10.dp,
                            ).align(Alignment.BottomCenter),
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(4.dp),
                                ),
                        color = textColor,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    } else {
        val textColor = MaterialTheme.colorScheme.onBackground

        val sweepTransition = rememberInfiniteTransition(label = "miniPlayerCrossfadeSweep")
        val crossfadeSweep by sweepTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(3200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "miniPlayerSweepHead",
        )
        val progressColor = textColor

        var isSliding by rememberSaveable {
            mutableStateOf(false)
        }
        var sliderValue by rememberSaveable {
            mutableFloatStateOf(0f)
        }
        var showQueueBottomSheet by rememberSaveable {
            mutableStateOf(false)
        }
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
        if (showQueueBottomSheet) {
            QueueBottomSheet(
                onDismiss = {
                    showQueueBottomSheet = false
                },
            )
        }
        val capsuleShape = RoundedCornerShape(50)
        val density = LocalDensity.current
        Box(
            modifier
                .liquidGlass(backdrop, layer, luminanceAnimation.value, capsuleShape, blurScale = 1.2f)
                .clip(capsuleShape)
                .clickable {
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(200.dp)) {
                    PlayerControlLayout(
                        controllerState,
                        isSmallSize = true,
                        plainPlayPause = true,
                        horizontalPadding = 0.dp,
                        activeColor = if (isDarkTheme) com.maxrave.simpmusic.ui.theme.seed else MaterialTheme.colorScheme.primary,
                        contentColor = textColor,
                    ) {
                        sharedViewModel.onUIEvent(it)
                    }
                }
                VerticalDivider(
                    modifier = Modifier.height(28.dp).padding(horizontal = 14.dp),
                    color = textColor.copy(alpha = 0.2f),
                )
                val trackInteraction = remember { MutableInteractionSource() }
                val isTrackHovered by trackInteraction.collectIsHoveredAsState()
                val showScrubber = isTrackHovered || isSliding
                Box(
                    modifier =
                        Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                            .hoverable(trackInteraction),
                ) {
                    val scrubberDigits =
                        typo().bodySmall.copy(
                            lineHeight = 11.sp,
                            lineHeightStyle =
                                LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both,
                                ),
                        )
                    val infoAlpha by animateFloatAsState(
                        targetValue = if (showScrubber) 0f else 1f,
                        animationSpec = tween(200),
                        label = "CapsuleInfoAlpha",
                    )
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .graphicsLayer { alpha = infoAlpha },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(LocalPlatformContext.current)
                                    .data(highResThumbnail)
                                    .crossfade(550)
                                    .build(),
                            placeholder = rememberHolderPainter(),
                            error = rememberHolderPainter(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onSuccess = {
                                bitmap =
                                    it.result.image.toImageBitmap()
                            },
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(
                                        RoundedCornerShape(6.dp),
                                    ),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = (songEntity?.title ?: "").toString().cleanMusicTitle(),
                                style = typo().labelSmall.copy(fontSize = 12.sp),
                                color = textColor,
                                maxLines = 1,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(
                                            align = Alignment.CenterVertically,
                                        ).basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            animationMode = MarqueeAnimationMode.Immediately,
                                        ).focusable(),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.animation.AnimatedVisibility(visible = songEntity?.isExplicit == true) {
                                    ExplicitBadge(
                                        modifier =
                                            Modifier
                                                .size(16.dp)
                                                .padding(end = 4.dp),
                                    )
                                }
                                Text(
                                    text = (songEntity?.artistName?.connectArtists() ?: ""),
                                    style = typo().bodySmall,
                                    color = textColor.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                AnimatedVisibility(
                                    visible = timelineState.isCrossfading,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    val shimmerSpan = 140f
                                    val shimmerHead = crossfadeSweep * (shimmerSpan * 3f) - shimmerSpan
                                    Text(
                                        text = " · " + stringResource(Res.string.crossfading),
                                        style =
                                            typo().bodySmall.copy(
                                                brush =
                                                    Brush.horizontalGradient(
                                                        0f to textColor.copy(alpha = 0.45f),
                                                        0.5f to Color.White,
                                                        1f to textColor.copy(alpha = 0.45f),
                                                        startX = shimmerHead,
                                                        endX = shimmerHead + shimmerSpan,
                                                        tileMode = TileMode.Clamp,
                                                    ),
                                            ),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .graphicsLayer { alpha = 1f - infoAlpha },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration((timelineState.total * (sliderValue / 100f)).roundToLong()),
                            style = scrubberDigits,
                            color = textColor.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                        Text(
                            text =
                                "−" +
                                    formatDuration(
                                        (timelineState.total * (1f - sliderValue / 100f)).roundToLong(),
                                    ),
                            style = scrubberDigits,
                            color = textColor.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                    CapsuleProgress(
                        sliderValue = sliderValue,
                        loading = loading,
                        trackHeight = if (showScrubber) 4.dp else 2.dp,
                        thumbSize = 0.dp,
                        textColor = textColor,
                        progressColor = progressColor,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                        onValueChange = {
                            isSliding = true
                            sliderValue = it * 100f
                        },
                        onValueChangeFinished = {
                            isSliding = false
                            sharedViewModel.onUIEvent(
                                UIEvent.UpdateProgress(sliderValue),
                            )
                        },
                    )
                }
                VerticalDivider(
                    modifier = Modifier.height(28.dp).padding(horizontal = 14.dp),
                    color = textColor.copy(alpha = 0.2f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        HeartCheckBox(checked = controllerState.isLiked, size = 32) {
                            sharedViewModel.onUIEvent(UIEvent.ToggleLike)
                        }
                    }
                    IconButton(
                        onClick = {
                            showQueueBottomSheet = true
                        },
                    ) {
                        Icon(
                            imageVector = SimpIcons.QueueMusic,
                            tint = textColor,
                            contentDescription = "",
                        )
                    }
                    if (getPlatform() == Platform.Desktop) {
                        IconButton(onClick = { toggleMiniPlayer() }) {
                            Icon(
                                imageVector = SimpIcons.PictureInPictureAlt,
                                tint = textColor,
                                contentDescription = "Mini Player",
                            )
                        }
                    }
                    var isVolumeSliding by rememberSaveable {
                        mutableStateOf(false)
                    }
                    var volumeValue by rememberSaveable {
                        mutableFloatStateOf(0f)
                    }
                    LaunchedEffect(key1 = controllerState, key2 = isVolumeSliding) {
                        if (!isVolumeSliding) {
                            volumeValue = controllerState.volume
                        }
                    }
                    var previousVolumeValue by rememberSaveable {
                        mutableFloatStateOf(controllerState.volume.takeIf { it > 0f } ?: 1f)
                    }
                    LaunchedEffect(controllerState.volume) {
                        if (controllerState.volume > 0f) {
                            previousVolumeValue = controllerState.volume
                        }
                    }
                    val volumeInteraction = remember { MutableInteractionSource() }
                    val isVolumeHovered by volumeInteraction.collectIsHoveredAsState()
                    val popupInteraction = remember { MutableInteractionSource() }
                    val isPopupHovered by popupInteraction.collectIsHoveredAsState()
                    Box(modifier = Modifier.hoverable(volumeInteraction)) {
                        IconButton(
                            onClick = {
                                if (controllerState.volume > 0f) {
                                    sharedViewModel.onUIEvent(UIEvent.UpdateVolume(0f))
                                } else {
                                    sharedViewModel.onUIEvent(
                                        UIEvent.UpdateVolume(previousVolumeValue.coerceIn(0.1f, 1f)),
                                    )
                                }
                            },
                        ) {
                            Icon(
                                imageVector =
                                    if (controllerState.volume > 0f) {
                                        SimpIcons.VolumeUp
                                    } else {
                                        SimpIcons.VolumeOff
                                    },
                                tint = textColor,
                                contentDescription = if (controllerState.volume > 0f) "Mute" else "Unmute",
                            )
                        }
                        var isVolumePopupVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(isVolumeHovered, isPopupHovered, isVolumeSliding) {
                            if (isVolumeHovered || isPopupHovered || isVolumeSliding) {
                                isVolumePopupVisible = true
                            } else {
                                delay(400)
                                isVolumePopupVisible = false
                            }
                        }
                        if (isVolumePopupVisible) {
                            Popup(
                                alignment = Alignment.TopCenter,
                                offset = IntOffset(0, with(density) { -(VOLUME_POPUP_HEIGHT + 4.dp).roundToPx() }),
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .hoverable(popupInteraction)
                                            .width(44.dp)
                                            .height(VOLUME_POPUP_HEIGHT)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
                                            .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = (volumeValue * 100).roundToInt().toString(),
                                        style = typo().bodySmall,
                                        color = textColor.copy(alpha = 0.7f),
                                        maxLines = 1,
                                    )
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                            Slider(
                                                value = volumeValue,
                                                onValueChangeFinished = {
                                                    isVolumeSliding = false
                                                    sharedViewModel.onUIEvent(
                                                        UIEvent.UpdateVolume(volumeValue.coerceIn(0f, 1f)),
                                                    )
                                                },
                                                onValueChange = {
                                                    isVolumeSliding = true
                                                    volumeValue = it
                                                },
                                                valueRange = 0f..1f,
                                                modifier =
                                                    Modifier
                                                        .graphicsLayer {
                                                            rotationZ = 270f
                                                            transformOrigin = TransformOrigin(0f, 0f)
                                                        }.layout { measurable, constraints ->
                                                            val placeable =
                                                                measurable.measure(
                                                                    Constraints(
                                                                        minWidth = constraints.minHeight,
                                                                        maxWidth = constraints.maxHeight,
                                                                        minHeight = constraints.minWidth,
                                                                        maxHeight = constraints.maxWidth,
                                                                    ),
                                                                )
                                                            layout(placeable.height, placeable.width) {
                                                                placeable.place(-placeable.width, 0)
                                                            }
                                                        }.width(VOLUME_SLIDER_LENGTH),
                                                track = { sliderState ->
                                                    SliderDefaults.Track(
                                                        modifier =
                                                            Modifier
                                                                .height(4.dp),
                                                        enabled = true,
                                                        sliderState = sliderState,
                                                        colors =
                                                            SliderDefaults.colors().copy(
                                                                thumbColor = textColor,
                                                                activeTrackColor = textColor,
                                                                inactiveTrackColor = textColor.copy(alpha = 0.3f),
                                                            ),
                                                        thumbTrackGapSize = 0.dp,
                                                        drawTick = { _, _ -> },
                                                        drawStopIndicator = null,
                                                    )
                                                },
                                                thumb = {
                                                    Spacer(Modifier.size(0.dp))
                                                },
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector =
                                            if (controllerState.volume > 0f) {
                                                SimpIcons.VolumeUp
                                            } else {
                                                SimpIcons.VolumeOff
                                            },
                                        tint = textColor.copy(alpha = 0.7f),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { onClose() }) {
                        Icon(SimpIcons.Close, "", tint = textColor)
                    }
                }
            }
        }
    }
}

private val VOLUME_POPUP_HEIGHT = 180.dp
private val VOLUME_SLIDER_LENGTH = 96.dp

@Composable
private fun CapsuleProgress(
    sliderValue: Float,
    loading: Boolean,
    trackHeight: Dp,
    thumbSize: Dp,
    textColor: Color,
    progressColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Crossfade(targetState = loading, label = "capsuleProgress") { isLoading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(trackHeight)
                                    .clip(RoundedCornerShape(8.dp)),
                            color = progressColor,
                            trackColor = textColor.copy(alpha = 0.25f),
                            strokeCap = StrokeCap.Round,
                        )
                    } else {
                        Slider(
                            value = sliderValue / 100f,
                            onValueChange = onValueChange,
                            onValueChangeFinished = onValueChangeFinished,
                            modifier = Modifier.fillMaxWidth(),
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    modifier = Modifier.height(trackHeight),
                                    enabled = true,
                                    sliderState = sliderState,
                                    colors =
                                        SliderDefaults.colors().copy(
                                            thumbColor = progressColor,
                                            activeTrackColor = progressColor,
                                            inactiveTrackColor = textColor.copy(alpha = 0.25f),
                                        ),
                                    thumbTrackGapSize = 0.dp,
                                    drawTick = { _, _ -> },
                                    drawStopIndicator = null,
                                )
                            },
                            thumb = {
                                if (thumbSize > 0.dp) {
                                    SliderDefaults.Thumb(
                                        modifier = Modifier.size(thumbSize),
                                        thumbSize = DpSize(thumbSize, thumbSize),
                                        interactionSource = remember { MutableInteractionSource() },
                                        colors =
                                            SliderDefaults.colors().copy(
                                                thumbColor = progressColor,
                                                activeTrackColor = progressColor,
                                                inactiveTrackColor = textColor.copy(alpha = 0.25f),
                                            ),
                                        enabled = true,
                                    )
                                } else {
                                    Spacer(Modifier.size(0.dp))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}