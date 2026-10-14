@file:OptIn(ExperimentalForeignApi::class)

package com.maxrave.simpmusic.expect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.ui.icon.Airplay
import com.maxrave.simpmusic.ui.icon.SimpIcons
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVKit.AVRoutePickerView
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIControl
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIView
import platform.UIKit.UIWindow

/**
 * On iOS the "cast" slot is the system audio-output picker (AVRoutePickerView): tapping it lists
 * paired Bluetooth headphones and speakers, AirPlay receivers and the iPhone itself, and moves
 * playback there. Google Cast needs Google's SDK, which this build does not ship; the system
 * picker is also what every iOS music app puts in this place.
 *
 * The icon is drawn by Compose, not by an embedded UIKit view: an interop view sits behind the
 * Compose layer, which has to punch a hole through whatever is drawn over it — so the button showed
 * as a see-through square instead of on its pill or on the player background. The native picker is
 * only used to present the menu: a practically invisible one is moved over the icon and tapped
 * programmatically, which anchors the system menu to the icon.
 */
@Composable
actual fun PlatformCastButton(
    modifier: Modifier,
    tint: Color,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current.density
    Box(
        modifier =
            modifier
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                ) {
                    IosRoutePicker.show(
                        x = bounds.left / density,
                        y = bounds.top / density,
                        width = bounds.width / density,
                        height = bounds.height / density,
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SimpIcons.Airplay,
            contentDescription = "AirPlay",
            tint = tint,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

actual fun isPlatformCastAvailable(): Boolean = true

/** One hidden AVRoutePickerView in the key window, positioned over the tapped icon and triggered. */
private object IosRoutePicker {
    private var picker: AVRoutePickerView? = null

    fun show(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        val app = UIApplication.sharedApplication
        val window =
            app.keyWindow
                ?: app.windows.firstOrNull() as? UIWindow
                ?: return
        val view =
            picker ?: AVRoutePickerView().apply {
                prioritizesVideoDevices = false
                backgroundColor = UIColor.clearColor
                // Not hidden (a hidden control may refuse to present) but invisible, and never in
                // the way of a real touch: only the programmatic tap below reaches it.
                alpha = 0.02
                userInteractionEnabled = false
            }.also { picker = it }
        if (view.superview != window) window.addSubview(view)
        view.setFrame(
            CGRectMake(
                x.toDouble(),
                y.toDouble(),
                width.toDouble().coerceAtLeast(1.0),
                height.toDouble().coerceAtLeast(1.0),
            ),
        )
        window.bringSubviewToFront(view)
        view.layoutIfNeeded()
        val control = findControl(view)
        if (control == null) {
            Logger.w("IosRoutePicker", "AVRoutePickerView has no control to trigger")
            return
        }
        control.sendActionsForControlEvents(UIControlEventTouchUpInside)
    }

    private fun findControl(view: UIView): UIControl? {
        (view as? UIControl)?.let { return it }
        for (child in view.subviews) {
            (child as? UIView)?.let { findControl(it) }?.let { return it }
        }
        return null
    }
}
