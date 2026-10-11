package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable

/** iOS gives apps no API to set the system volume (only MPVolumeView), so the row is hidden. */
@Composable
actual fun rememberDeviceVolumeController(): DeviceVolumeController? = null
