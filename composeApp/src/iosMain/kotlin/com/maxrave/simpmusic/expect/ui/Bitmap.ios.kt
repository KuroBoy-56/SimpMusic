package com.maxrave.simpmusic.expect.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import coil3.toBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

actual fun ImageBitmap.toByteArray(): ByteArray? =
    Image.makeFromBitmap(this.asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, 100)?.bytes

actual fun ImageBitmap.toPngByteArray(): ByteArray? =
    Image.makeFromBitmap(this.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes

actual fun coil3.Image.toImageBitmap(): ImageBitmap = this.toBitmap().asComposeImageBitmap()
