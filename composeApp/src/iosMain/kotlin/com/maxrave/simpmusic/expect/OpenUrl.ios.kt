package com.maxrave.simpmusic.expect

import com.maxrave.simpmusic.ios.presentShareSheet
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

actual fun shareUrl(
    title: String,
    url: String,
) {
    dispatch_async(dispatch_get_main_queue()) {
        presentShareSheet(listOf(url))
    }
}
