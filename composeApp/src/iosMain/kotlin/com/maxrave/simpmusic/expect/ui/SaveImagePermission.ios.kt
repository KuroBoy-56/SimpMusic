package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** iOS asks for Photos add-access itself the first time an image is saved. */
@Composable
actual fun rememberSaveImagePermission(onResult: (granted: Boolean) -> Unit): SaveImagePermissionRequester {
    val callback = rememberUpdatedState(onResult)
    return remember {
        object : SaveImagePermissionRequester {
            override fun requestIfNeeded() {
                callback.value(true)
            }
        }
    }
}
