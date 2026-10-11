package com.maxrave.simpmusic.expect.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.expect.toNSData
import com.maxrave.simpmusic.ios.documentsDirectory
import com.maxrave.simpmusic.ios.ensureDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.writeToFile

private const val TAG = "ImageIo"

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }
        .onFailure { Logger.w(TAG, "Could not decode image: ${it.message}") }
        .getOrNull()

actual suspend fun persistPickedImage(
    bytes: ByteArray,
    fileName: String,
): String? =
    withContext(Dispatchers.IO) {
        val dir = ensureDirectory(documentsDirectory() + "/playlist_covers")
        val path = "$dir/$fileName"
        if (bytes.toNSData().writeToFile(path, atomically = true)) "file://$path" else null
    }
