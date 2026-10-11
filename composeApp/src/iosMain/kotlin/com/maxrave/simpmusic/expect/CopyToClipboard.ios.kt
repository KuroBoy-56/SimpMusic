package com.maxrave.simpmusic.expect

import platform.UIKit.UIPasteboard

actual fun copyToClipboard(
    label: String,
    text: String,
) {
    UIPasteboard.generalPasteboard.string = text
}
