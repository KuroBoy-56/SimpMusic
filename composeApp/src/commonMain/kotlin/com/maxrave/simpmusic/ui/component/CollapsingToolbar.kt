package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.toBitmap
import com.kmpalette.rememberPaletteState
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.extension.getColorFromPalette
import com.maxrave.simpmusic.extension.getScreenSizeInfo
import com.maxrave.simpmusic.extension.rgbFactor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.holder_video

private val paddingMedium = 0.dp

private val titlePaddingStart = 20.dp
private val titlePaddingEnd = 72.dp

private const val TITLE_FONT_SCALE_START = 1f
private const val TITLE_FONT_SCALE_END = 0.46f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@ExperimentalMaterial3Api
fun CollapsingToolbarParallaxEffect(
    modifier: Modifier = Modifier,
    title: String,
    imageUrl: String? = null,
    onBack: () -> Unit,
    content:
    @Composable()
    ((color: Color) -> Unit) = {},
) {
    val density = LocalDensity.current
    val toolbarHeight =
        TopAppBarDefaults.TopAppBarExpandedHeight + with(density) { WindowInsets.statusBars.getTop(this).toDp() * 2 }

    val scroll: ScrollState = rememberScrollState(0)
    val headerHeight = (getScreenSizeInfo().hDP.dp * 2 / 4).coerceAtLeast(250.dp)

    val headerHeightPx = with(density) { headerHeight.toPx() }
    val toolbarHeightPx = with(density) { toolbarHeight.toPx() }

    val paletteState = rememberPaletteState()
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val bgColor = MaterialTheme.colorScheme.background
    var color by remember { mutableStateOf(bgColor) }
    var showBackButton by rememberSaveable { mutableStateOf(true) }

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
                val paletteColor = it.getColorFromPalette()
                if (paletteColor != Color.Transparent) {
                    color = paletteColor
                }
            }
    }

    // LÓGICA DE CONTRASTE DINÁMICO PARA EL TÍTULO
    val isLightPalette = color.luminance() > 0.5f
    val dynamicTitleColor = if (isLightPalette) Color.Black else Color.White

    Box(modifier = modifier) {
        Header(
            scroll = scroll,
            headerHeightPx = headerHeightPx,
            imageUrl = imageUrl,
            backgroundColor = color,
            modifier = Modifier.fillMaxWidth().height(headerHeight),
        ) { bm ->
            bitmap = bm
        }
        Body(
            scroll = scroll,
            modifier = Modifier.fillMaxSize(),
            headerHeight = headerHeight,
        ) {
            content(color)
        }
        Toolbar(
            scroll = scroll,
            headerHeightPx = headerHeightPx,
            toolbarHeightPx = toolbarHeightPx,
            backgroundColor = color,
            onShow = { show ->
                showBackButton = !show
            },
            onBack = onBack,
        )
        Title(
            scroll = scroll,
            title = title,
            headerHeight = headerHeight,
            toolbarHeight = toolbarHeight,
            textColor = dynamicTitleColor // Aplicamos el color inteligente
        )
        AnimatedVisibility(
            showBackButton,
            enter = fadeIn() + slideInHorizontally(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .wrapContentSize()
                    .align(Alignment.TopStart)
                    .padding(top = with(density) { WindowInsets.statusBars.getTop(this).toDp() })
                    .padding(12.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors().copy(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    ),
                ) {
                    Icon(Icons.Default.ArrowBackIosNew, "Back")
                }
            }
        }
    }
}

@Composable
private fun Header(
    modifier: Modifier = Modifier,
    scroll: ScrollState,
    imageUrl: String? = null,
    headerHeightPx: Float,
    backgroundColor: Color,
    onImageLoaded: (ImageBitmap) -> Unit,
) {
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = -scroll.value.toFloat() / 2f
                alpha = (-1f / headerHeightPx) * scroll.value + 1
            }
            .background(backgroundColor.rgbFactor(0.5f)),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(imageUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCacheKey(imageUrl)
                .crossfade(true)
                .build(),
            onSuccess = { onImageLoaded(it.result.image.toBitmap().asImageBitmap()) },
            placeholder = painterResource(Res.drawable.holder_video),
            error = painterResource(Res.drawable.holder_video),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            bgColor.copy(alpha = 0.3f),
                            bgColor.copy(alpha = 0.6f),
                            bgColor.copy(alpha = 0.85f),
                            bgColor,
                        ),
                        startY = headerHeightPx / 2,
                        endY = headerHeightPx,
                    ),
                ),
        )
    }
}

@Composable
private fun Body(
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    headerHeight: Dp,
    content: @Composable (() -> Unit) = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(scroll),
    ) {
        Spacer(Modifier.height(headerHeight))
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}

@Composable
@ExperimentalMaterial3Api
private fun Toolbar(
    modifier: Modifier = Modifier,
    scroll: ScrollState,
    headerHeightPx: Float,
    toolbarHeightPx: Float,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    onShow: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val toolbarBottom by remember { mutableFloatStateOf(headerHeightPx - toolbarHeightPx) }
    val showToolbar by remember { derivedStateOf { scroll.value >= toolbarBottom } }

    LaunchedEffect(showToolbar) { onShow(showToolbar) }

    AnimatedVisibility(
        modifier = modifier,
        visible = showToolbar,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
    ) {
        TopAppBar(
            windowInsets = TopAppBarDefaults.windowInsets.exclude(TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Start)),
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(backgroundColor.rgbFactor(0.8f), backgroundColor.rgbFactor(0.6f))
                )
            ),
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(16.dp).size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBackIosNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
            title = {},
            colors = TopAppBarDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun Title(
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    headerHeight: Dp,
    toolbarHeight: Dp,
    title: String,
    textColor: Color
) {
    var titleHeightPx by remember { mutableFloatStateOf(0f) }
    var titleWidthPx by remember { mutableFloatStateOf(0f) }

    Text(
        text = title,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.5f), // Pequeña sombra para asegurar legibilidad
                offset = Offset(0f, 4f),
                blurRadius = 8f
            )
        ),
        modifier = modifier
            .graphicsLayer {
                val collapseRange: Float = (headerHeight.toPx() - toolbarHeight.toPx())
                val collapseFraction: Float = (scroll.value / collapseRange).coerceIn(0f, 1f)

                val scaleXY = lerp(TITLE_FONT_SCALE_START.dp, TITLE_FONT_SCALE_END.dp, collapseFraction)
                val titleExtraStartPadding = titleWidthPx.toDp() * (1 - scaleXY.value) / 2f
                val titleYFirstInterpolatedPoint = lerp(headerHeight - titleHeightPx.toDp() - paddingMedium, headerHeight / 2, collapseFraction)
                val titleXFirstInterpolatedPoint = lerp(titlePaddingStart, (titlePaddingEnd - titleExtraStartPadding) * 5 / 4, collapseFraction)
                val titleYSecondInterpolatedPoint = lerp(headerHeight / 2, toolbarHeight / 2 - titleHeightPx.toDp() / 2, collapseFraction)
                val titleXSecondInterpolatedPoint = lerp((titlePaddingEnd - titleExtraStartPadding) * 5 / 4, titlePaddingEnd - titleExtraStartPadding, collapseFraction)

                translationY = lerp(titleYFirstInterpolatedPoint, titleYSecondInterpolatedPoint, collapseFraction).toPx()
                translationX = lerp(titleXFirstInterpolatedPoint, titleXSecondInterpolatedPoint, collapseFraction).toPx()
                scaleX = scaleXY.value
                scaleY = scaleXY.value
            }.onGloballyPositioned {
                titleHeightPx = it.size.height.toFloat()
                titleWidthPx = it.size.width.toFloat()
            },
    )
}