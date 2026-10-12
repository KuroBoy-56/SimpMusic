package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Login sync pairs Android with Desktop only; iOS takes no part, so the dialog closes at once. */
@Composable
actual fun LoginSyncDialog(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { onDismiss() }
}
