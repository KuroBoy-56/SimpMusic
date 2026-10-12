@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.maxrave.simpmusic.expect

import com.maxrave.logger.Logger
import com.maxrave.simpmusic.ios.presentShareSheet
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum

private const val TAG = "ShareImage"

internal fun ByteArray.toNSData(): NSData =
    usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }

/** Saves into the Photos library (needs NSPhotoLibraryAddUsageDescription, which Info.plist declares). */
actual suspend fun saveImageToDevice(
    bytes: ByteArray,
    fileName: String,
): Boolean =
    withContext(Dispatchers.Main) {
        runCatching {
            val image = UIImage.imageWithData(bytes.toNSData()) ?: return@runCatching false
            UIImageWriteToSavedPhotosAlbum(image, null, null, null)
            true
        }.onFailure { Logger.w(TAG, "Save failed: ${it.message}") }.getOrDefault(false)
    }

actual suspend fun shareImage(
    bytes: ByteArray,
    fileName: String,
    chooserTitle: String,
): Boolean =
    withContext(Dispatchers.Main) {
        val image = UIImage.imageWithData(bytes.toNSData()) ?: return@withContext false
        presentShareSheet(listOf(image))
        true
    }
