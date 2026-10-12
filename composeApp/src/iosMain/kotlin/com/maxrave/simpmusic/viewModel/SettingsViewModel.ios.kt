@file:OptIn(ExperimentalForeignApi::class)

package com.maxrave.simpmusic.viewModel

import com.eygraber.uri.Uri
import com.maxrave.common.DB_NAME
import com.maxrave.common.SETTINGS_FILENAME
import com.maxrave.domain.repository.CacheRepository
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.ios.IosZip
import com.maxrave.simpmusic.ios.documentsDirectory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSBundle
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

private const val TAG = "BackupRestoreIos"

actual suspend fun calculateDataFraction(cacheRepository: CacheRepository): SettingsStorageSectionFraction? = null

/**
 * Writes the backup ZIP into the app's Documents folder, which the Files app shows under
 * "On My iPhone → KuroMusic" (Info.plist enables file sharing). From there the user can move or
 * copy it anywhere, and restore picks it back up. [uri]'s last path segment is the file name the
 * save dialog proposed.
 */
actual suspend fun backupNative(
    commonRepository: CommonRepository,
    uri: Uri,
    backupDownloaded: Boolean,
) {
    val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() && it.endsWith(".backup") } ?: "KuroMusic.backup"
    try {
        commonRepository.databaseDaoCheckpoint()
        val entries = mutableListOf<Pair<String, ByteArray>>()
        readFile("${documentsDirectory()}/$SETTINGS_FILENAME.preferences_pb")?.let {
            entries.add("$SETTINGS_FILENAME.preferences_pb" to it)
        }
        val dbPath = commonRepository.getDatabasePath() ?: "${documentsDirectory()}/$DB_NAME"
        readFile(dbPath)?.let { entries.add(DB_NAME to it) }
        if (entries.isEmpty()) {
            withContext(Dispatchers.Main) { showToast("No hay datos para respaldar", ToastGravity.Bottom) }
            return
        }
        val zip = IosZip.write(entries)
        val outPath = "${documentsDirectory()}/$fileName"
        if (!writeFile(outPath, zip)) {
            withContext(Dispatchers.Main) { showToast("No se pudo guardar el respaldo", ToastGravity.Bottom) }
            return
        }
        Logger.d(TAG, "Backup written to $outPath (${zip.size} bytes)")
        withContext(Dispatchers.Main) {
            showToast("Respaldo guardado en Archivos › En mi iPhone › KuroMusic › $fileName", ToastGravity.Bottom)
        }
    } catch (e: Exception) {
        Logger.e(TAG, "Backup failed: ${e.message}")
        withContext(Dispatchers.Main) { showToast("Error al crear el respaldo", ToastGravity.Bottom) }
    }
}

actual suspend fun restoreNative(
    commonRepository: CommonRepository,
    uri: Uri,
    getData: () -> Unit,
) {
    val path = uri.path ?: uri.toString()
    try {
        val zip =
            readFile(path)
                ?: readFile(path.removePrefix("file://"))
                ?: run {
                    withContext(Dispatchers.Main) { showToast("No se pudo leer el archivo", ToastGravity.Bottom) }
                    return
                }
        val members = IosZip.read(zip)
        if (members.isEmpty()) {
            withContext(Dispatchers.Main) { showToast("El archivo no es un respaldo válido", ToastGravity.Bottom) }
            return
        }
        commonRepository.databaseDaoCheckpoint()
        commonRepository.closeDatabase()
        members["$SETTINGS_FILENAME.preferences_pb"]?.let {
            writeFile("${documentsDirectory()}/$SETTINGS_FILENAME.preferences_pb", it)
        }
        members[DB_NAME]?.let {
            val dbPath = commonRepository.getDatabasePath() ?: "${documentsDirectory()}/$DB_NAME"
            writeFile(dbPath, it)
        }
        getData()
        withContext(Dispatchers.Main) {
            showToast("Respaldo restaurado. Cierra y vuelve a abrir la app.", ToastGravity.Bottom)
        }
    } catch (e: Exception) {
        Logger.e(TAG, "Restore failed: ${e.message}")
        withContext(Dispatchers.Main) { showToast("Error al restaurar el respaldo", ToastGravity.Bottom) }
    }
}

private fun readFile(path: String): ByteArray? {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val size = data.length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, size.convert()) }
    return bytes
}

private fun writeFile(
    path: String,
    bytes: ByteArray,
): Boolean =
    bytes.usePinned { pinned ->
        val data =
            if (bytes.isEmpty()) {
                NSData.create(bytes = null, length = 0u)
            } else {
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.convert())
            }
        data.writeToFile(path, atomically = true)
    }

actual fun getPackageName(): String = NSBundle.mainBundle.bundleIdentifier.orEmpty()

actual fun getFileDir(): String = documentsDirectory()

/** Takes effect on the next launch, which is how iOS applies a per-app language. */
actual fun changeLanguageNative(code: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(code), forKey = "AppleLanguages")
}
