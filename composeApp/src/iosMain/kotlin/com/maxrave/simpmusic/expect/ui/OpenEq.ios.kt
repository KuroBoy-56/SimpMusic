package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable

/** iOS has no system equalizer an app can open. */
@Composable
actual fun openEqResult(audioSessionId: Int): OpenEqLauncher =
    object : OpenEqLauncher {
        override fun launch() {}
    }
