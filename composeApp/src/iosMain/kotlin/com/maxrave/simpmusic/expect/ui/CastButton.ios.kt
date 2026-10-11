package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Google Cast is Android-only; iOS has no Cast SDK in this build. */
@Composable
actual fun PlatformCastButton(
    modifier: Modifier,
    tint: Color,
) {
}

actual fun isPlatformCastAvailable(): Boolean = false
