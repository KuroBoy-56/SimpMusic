package com.maxrave.simpmusic

import androidx.compose.ui.window.ComposeUIViewController
import com.maxrave.common.AppIdentity
import com.maxrave.data.di.loader.loadAllModules
import com.maxrave.simpmusic.di.viewModelModule
import com.maxrave.simpmusic.utils.VersionManager
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.simpmusic.lastfm.configLastfm
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice
import platform.UIKit.UIViewController

private var started = false

/** Same start-up as Desktop's `runDesktopApp`: Koin once, then the shared app. */
private fun startOnce() {
    if (started) return
    started = true
    startKoin {
        loadAllModules(
            AppIdentity(
                applicationId = NSBundle.mainBundle.bundleIdentifier ?: "com.maxrave.simpmusic",
                versionName = BuildKonfig.versionName,
                platform = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}",
            ),
        )
        loadKoinModules(viewModelModule)
    }
    VersionManager.initialize()
    configLastfm(BuildKonfig.lastfmApiKey, BuildKonfig.lastfmSecret)
}

/** Entry point called from Swift (`MainViewControllerKt.MainViewController()`). */
fun MainViewController(): UIViewController {
    startOnce()
    return ComposeUIViewController { App() }
}
