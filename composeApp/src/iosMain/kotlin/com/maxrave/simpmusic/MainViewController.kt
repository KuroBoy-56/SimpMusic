package com.maxrave.simpmusic

import androidx.compose.ui.window.ComposeUIViewController
import com.maxrave.common.AppIdentity
import com.maxrave.data.di.loader.loadAllModules
import com.maxrave.data.mediaservice.ios.IosPlaybackDiagnostics
import com.maxrave.simpmusic.di.viewModelModule
import com.maxrave.simpmusic.ios.panel.KuroCrashReporter
import com.maxrave.simpmusic.ios.panel.KuroGate
import com.maxrave.simpmusic.ios.panel.KuroPanel
import com.maxrave.simpmusic.ios.selftest.playbackSelfTest
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSProcessInfo
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
    // First, so a failure anywhere below still reaches the panel.
    KuroCrashReporter.install()
    // Playback/download failures on a device reach the panel with the stage and HTTP status.
    IosPlaybackDiagnostics.reporter = { tipo, mensaje, detalle -> KuroCrashReporter.logSilentError(tipo, mensaje, detalle) }
    KuroCrashReporter.markLaunchStep("koin")
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
    KuroCrashReporter.markLaunchStep("version-manager")
    VersionManager.initialize()
    configLastfm(BuildKonfig.lastfmApiKey, BuildKonfig.lastfmSecret)
    KuroCrashReporter.markLaunchStep("ui")
    if (NSProcessInfo.processInfo.arguments.any { it == "-kuroPanelSelfTest" }) {
        MainScope().launch { println("KURO_SELFTEST " + KuroPanel.selfTest()) }
    }
    if (NSProcessInfo.processInfo.arguments.any { it == "-kuroPlaybackSelfTest" }) {
        MainScope().launch { println("KURO_PLAYTEST " + playbackSelfTest()) }
    }
}

/** Entry point called from Swift (`MainViewControllerKt.MainViewController()`). */
fun MainViewController(): UIViewController {
    startOnce()
    return ComposeUIViewController { KuroGate { App() } }
}
