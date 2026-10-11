package com.maxrave.simpmusic.viewModel

import com.eygraber.uri.Uri
import com.maxrave.domain.repository.CacheRepository
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.simpmusic.ios.documentsDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

actual suspend fun calculateDataFraction(cacheRepository: CacheRepository): SettingsStorageSectionFraction? = null

/** Backup archives are zip files, and there is no zip codec on iOS in this build. */
actual suspend fun restoreNative(
    commonRepository: CommonRepository,
    uri: Uri,
    getData: () -> Unit,
) {
    withContext(Dispatchers.Main) { showToast("Restore is not available on iOS yet", ToastGravity.Bottom) }
}

actual suspend fun backupNative(
    commonRepository: CommonRepository,
    uri: Uri,
    backupDownloaded: Boolean,
) {
    withContext(Dispatchers.Main) { showToast("Backup is not available on iOS yet", ToastGravity.Bottom) }
}

actual fun getPackageName(): String = NSBundle.mainBundle.bundleIdentifier.orEmpty()

actual fun getFileDir(): String = documentsDirectory()

/** Takes effect on the next launch, which is how iOS applies a per-app language. */
actual fun changeLanguageNative(code: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(code), forKey = "AppleLanguages")
}
