package com.maxrave.simpmusic.desktop.auth

import com.maxrave.simpmusic.BuildKonfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SecurityStatus(
    val isValid: Boolean,
    val alertTitle: String?,
    val alertMessage: String?
)

data class UpdateInfo(
    val hasUpdate: Boolean,
    val changelog: String?,
    val downloadUrl: String?,
    val isMandatory: Boolean
)

object SecurityGuard {

    private inline fun decryptUrl(encryptedBytes: IntArray): String {
        val sb = java.lang.StringBuilder(encryptedBytes.size)
        for (byteVal in encryptedBytes) {
            val mask = byteVal and 0xFF
            sb.append((mask - 5).toChar())
        }
        return sb.toString()
    }

    private inline fun decodeUnicode(encoded: String): String {
        val regex = Regex("\\\\u([0-9a-fA-F]{4})")
        return regex.replace(encoded) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
    }

    fun checkRemainingDays(username: String): SecurityStatus {
        return try {
            val encryptedBytes = intArrayOf(
                109, 121, 121, 117, 120, 63, 52, 52, 108, 102, 119, 106, 123, 126, 115, 117, 102, 115, 106, 113,
                120, 51, 113, 102, 121, 114, 117, 125, 51, 104, 116, 114, 52, 126, 116, 122, 121, 122, 103, 106,
                52, 117, 102, 115, 106, 113, 52, 102, 117, 110, 52, 104, 109, 106, 104, 112, 100, 105, 102, 126,
                120, 51, 117, 109, 117
            )
            val baseUrl = decryptUrl(encryptedBytes)
            val userEnc = URLEncoder.encode(username, "UTF-8")
            val url = URL("$baseUrl?username=$userEnc")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                val titleMatch = Regex("\"(?:alert_title|title)\"\\s*:\\s*\"([^\"]*)\"").find(response)
                val msgMatch = Regex("\"(?:alert_msg|message|msg|error)\"\\s*:\\s*\"([^\"]*)\"").find(response)
                
                val title = titleMatch?.groupValues?.get(1)?.let { decodeUnicode(it).replace("\\n", "\n") }
                val msg = msgMatch?.groupValues?.get(1)?.let { decodeUnicode(it).replace("\\n", "\n") }

                if (response.contains("\"status\":\"error\"") || response.contains("\"status\": \"error\"")) {
                    return SecurityStatus(false, title, msg)
                }
                
                val daysMatch = Regex("\"days_left\"\\s*:\\s*(-?\\d+)").find(response)
                val days = daysMatch?.groupValues?.get(1)?.toIntOrNull()
                
                if (days != null) {
                    if (days <= 0 || days == -1) {
                        return SecurityStatus(false, title, msg)
                    } else if (days <= 3) {
                        return SecurityStatus(true, title, msg)
                    }
                }
            }
            SecurityStatus(true, null, null)
        } catch (e: Exception) {
            SecurityStatus(true, null, null)
        }
    }

    fun checkAppUpdates(): UpdateInfo? {
        return try {
            val encryptedBytes = intArrayOf(
                109, 121, 121, 117, 120, 63, 52, 52, 108, 102, 119, 106, 123, 126, 115, 117, 102, 115, 106, 113, 
                120, 51, 113, 102, 121, 114, 117, 125, 51, 104, 116, 114, 52, 126, 116, 122, 121, 122, 103, 106, 
                52, 117, 102, 115, 106, 113, 52, 102, 117, 110, 52, 105, 106, 120, 112, 121, 116, 117, 100, 122, 
                117, 105, 102, 121, 106, 51, 117, 109, 117
            )
            val baseUrl = decryptUrl(encryptedBytes)
            val currentVersionCode = BuildKonfig.versionCode
            val url = URL("$baseUrl?version_pc=$currentVersionCode")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                val isUpdateAvailable = response.contains("\"update_available\":true") || response.contains("\"update_available\": true")
                
                val obsoleteMatch = Regex("\"obsolete_versions\"\\s*:\\s*\\[(.*?)\\]").find(response)
                val obsoleteListStr = obsoleteMatch?.groupValues?.get(1) ?: ""
                val isThisVersionObsolete = obsoleteListStr.split(",").map { it.trim() }.contains(currentVersionCode.toString())
                
                if (isUpdateAvailable) {
                    val noteMatch = Regex("\"changelog\"\\s*:\\s*\"([^\"]*)\"").find(response)
                    val urlMatch = Regex("\"download_url\"\\s*:\\s*\"([^\"]*)\"").find(response)
                    val isMandatoryMatch = Regex("\"is_mandatory\"\\s*:\\s*(true|false)").find(response)
                    
                    val note = noteMatch?.groupValues?.get(1) ?: ""
                    val downloadUrl = urlMatch?.groupValues?.get(1)?.replace("\\/", "/") ?: ""
                    val isMandatoryServer = isMandatoryMatch?.groupValues?.get(1) == "true"
                    
                    val cleanNote = decodeUnicode(note).replace("\\n", "\n")
                    
                    return UpdateInfo(
                        hasUpdate = true,
                        changelog = cleanNote.ifBlank { "Mejoras de rendimiento." },
                        downloadUrl = downloadUrl,
                        isMandatory = isMandatoryServer || isThisVersionObsolete 
                    )
                } else if (isThisVersionObsolete) {
                    return UpdateInfo(
                        hasUpdate = true,
                        changelog = "Tu versión actual ha sido deshabilitada por el administrador.",
                        downloadUrl = "",
                        isMandatory = true 
                    )
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun downloadAndInstallUpdate(downloadUrl: String, onProgress: (Float, Long, Long) -> Unit): Boolean {
        if (downloadUrl.isBlank()) return false
        return try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            connection.instanceFollowRedirects = true
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) return false

            val fileLength = connection.contentLengthLong
            val tempDir = System.getProperty("java.io.tmpdir")
            val installerFile = File(tempDir, "YT Music Mod-1.5.0.msi")
            
            if (installerFile.exists()) {
                installerFile.delete()
            }
            
            connection.inputStream.use { input ->
                installerFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (fileLength > 0) {
                            val progress = totalBytesRead.toFloat() / fileLength.toFloat()
                            onProgress(progress, totalBytesRead, fileLength)
                        } else {
                            onProgress(0.5f, totalBytesRead, totalBytesRead * 2)
                        }
                    }
                }
            }
            
            if (installerFile.exists() && installerFile.length() > 0) {
                onProgress(1f, if(fileLength > 0) fileLength else installerFile.length(), if(fileLength > 0) fileLength else installerFile.length())
                
                val cmdCommand = "ping 127.0.0.1 -n 3 > nul && start \"\" msiexec /i \"${installerFile.absolutePath}\" /passive"
                ProcessBuilder("cmd.exe", "/c", cmdCommand).start()
                
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
