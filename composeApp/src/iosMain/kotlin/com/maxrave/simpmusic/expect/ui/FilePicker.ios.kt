package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable

@Composable
actual fun filePickerResult(
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher =
    object : FilePickerLauncher {
        override fun launch() {
            onResultUri(null)
        }
    }

/**
 * iOS has no "pick where to save" dialog that hands back a writable URL before the file exists, so
 * [backupNative] on iOS writes into the app's Documents folder itself (visible in Files) and
 * ignores the URL's directory. The launcher simply reports the proposed file name so that write can
 * name the file, which is why it returns the name rather than null.
 */
@Composable
actual fun fileSaverResult(
    fileName: String,
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher =
    object : FilePickerLauncher {
        override fun launch() {
            onResultUri(fileName)
        }
    }
