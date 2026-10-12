@file:OptIn(ExperimentalForeignApi::class)

package com.maxrave.simpmusic.expect

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIScreen

actual fun currentOrientation(): Orientation =
    UIScreen.mainScreen.bounds.useContents {
        if (size.width > size.height) Orientation.LANDSCAPE else Orientation.PORTRAIT
    }
