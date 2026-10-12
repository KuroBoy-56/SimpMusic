package com.maxrave.simpmusic.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.maxrave.domain.data.model.ui.ScreenSizeInfo
import platform.UIKit.UIApplication
import kotlin.math.roundToInt

@Composable
actual fun getScreenSizeInfo(): ScreenSizeInfo {
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    return ScreenSizeInfo(
        hDP = with(density) { size.height.toDp().value.roundToInt() },
        wDP = with(density) { size.width.toDp().value.roundToInt() },
        hPX = size.height,
        wPX = size.width,
    )
}

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

@Composable
actual fun rememberIsInPipMode(): Boolean = false
