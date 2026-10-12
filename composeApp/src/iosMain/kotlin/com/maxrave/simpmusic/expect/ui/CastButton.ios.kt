@file:OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)

package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVKit.AVRoutePickerView
import platform.UIKit.UIColor

/**
 * On iOS the "cast" slot is the system audio-output picker (AVRoutePickerView): tapping it lists
 * paired Bluetooth headphones and speakers, AirPlay receivers and the iPhone itself, and moves
 * playback there. Google Cast needs Google's SDK, which this build does not ship; the system
 * picker is also what every iOS music app puts in this place.
 */
@Composable
actual fun PlatformCastButton(
    modifier: Modifier,
    tint: Color,
) {
    UIKitView(
        factory = {
            AVRoutePickerView().apply {
                backgroundColor = UIColor.clearColor
                prioritizesVideoDevices = false
            }
        },
        modifier = modifier,
        update = { picker ->
            picker.tintColor = tint.toUIColor()
            picker.activeTintColor = tint.toUIColor()
        },
        properties =
            UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = true,
            ),
    )
}

actual fun isPlatformCastAvailable(): Boolean = true

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())
