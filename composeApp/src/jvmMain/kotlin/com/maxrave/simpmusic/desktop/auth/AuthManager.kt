package com.maxrave.simpmusic.desktop.auth

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.Properties

object AuthManager {
    private val prefsFile = File(System.getProperty("user.home") + "/.simpmusic/desktop_auth.properties")
    private var savedPass: String? = null
    var username: String? = null
    private var lastValidationTime: Long = 0L

    var isLoggedIn: Boolean = false
        private set

    init {
        loadSession()
    }

    // Ofuscación XOR rápida para el almacenamiento local.
    // Destroza el texto para que no sea legible en el archivo .properties
    private inline fun scramble(input: String): String {
        val xorKey = 0x5A.toByte()
        val bytes = input.toByteArray().map { (it.toInt() xor xorKey.toInt()).toByte() }.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private inline fun unscramble(input: String): String {
        return try {
            val bytes = Base64.getDecoder().decode(input).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            String(bytes)
        } catch (e: Exception) { "" }
    }

    private fun loadSession() {
        if (prefsFile.exists()) {
            try {
                val props = Properties()
                prefsFile.inputStream().use { props.load(it) }
                username = props.getProperty("username")
                val rawPass = props.getProperty("password")
                savedPass = if (!rawPass.isNullOrBlank()) unscramble(rawPass) else null
                lastValidationTime = props.getProperty("lastValidationTime", "0").toLongOrNull() ?: 0L
                isLoggedIn = !username.isNullOrBlank() && !savedPass.isNullOrBlank()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveSession(user: String, pass: String) {
        username = user
        savedPass = pass
        isLoggedIn = true
        lastValidationTime = System.currentTimeMillis()
        try {
            prefsFile.parentFile.mkdirs()
            val props = Properties()
            props.setProperty("username", user)
            props.setProperty("password", scramble(pass)) // Se guarda encriptado
            props.setProperty("lastValidationTime", lastValidationTime.toString())
            prefsFile.outputStream().use { props.store(it, "Configuration Data") }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearSession() {
        username = null
        savedPass = null
        isLoggedIn = false
        lastValidationTime = 0L
        if (prefsFile.exists()) {
            prefsFile.delete()
        }
    }

    fun updateValidationTime() {
        if (username != null && savedPass != null) {
            saveSession(username!!, savedPass!!)
        }
    }

    fun getValidationTime(): Long = lastValidationTime
    fun getSavedPassword(): String? = savedPass

    fun getDesktopMacAddress(): String {
        val computerId = System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "1A2B3C4D5E6F7A8B"
        var processed = computerId.trimStart('0')
        if (processed.isEmpty()) {
            processed = "1A2B3C4D5E6F7A8B"
        }
        processed = processed.padEnd(16, 'A').substring(0, 16).uppercase()
        return processed.chunked(2).joinToString(":")
    }

    fun login(user: String, pass: String): Result<Boolean> {
        return try {
            val deviceMac = getDesktopMacAddress()
            val userEnc = URLEncoder.encode(user, "UTF-8")
            val passEnc = URLEncoder.encode(pass, "UTF-8")
            val macEnc = URLEncoder.encode(deviceMac, "UTF-8")

            val encryptedBytes = intArrayOf(
                109, 121, 121, 117, 120, 63, 52, 52, 108, 102, 119, 106, 123, 126, 115, 117, 102, 115, 106, 113,
                120, 51, 113, 102, 121, 114, 117, 125, 51, 104, 116, 114, 52, 126, 116, 122, 121, 122, 103, 106,
                52, 117, 102, 115, 106, 113, 52, 102, 117, 110, 52, 117, 113, 102, 126, 106, 119, 100, 102, 117,
                110, 51, 117, 109, 117
            )
            
            // Reconstrucción en línea para confundir el árbol sintáctico (AST) del decompiler
            val urlBuilder = java.lang.StringBuilder(encryptedBytes.size)
            for (byteVal in encryptedBytes) {
                val shift = byteVal xor 0x00 
                urlBuilder.append((shift - 5).toChar())
            }

            val urlString = "${urlBuilder.toString()}?username=$userEnc&password=$passEnc&mac=$macEnc"
            val url = URL(urlString)
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val auth = if (responseText.contains("\"auth\": 1") || responseText.contains("\"auth\":1")) 1 else 0

                if (auth == 1) {
                    val status = responseText.substringAfter("\"status\": \"").substringBefore("\"")
                    val isInvalidStatus = status.equals("Expired", ignoreCase = true) ||
                            status.equals("Banned", ignoreCase = true) ||
                            status.equals("Disabled", ignoreCase = true)

                    if (isInvalidStatus) {
                        return Result.failure(Exception("Cuenta inactiva: $status"))
                    }

                    saveSession(user, pass)
                    return Result.success(true)
                } else {
                    val message = responseText.substringAfter("\"message\": \"").substringBefore("\"")
                    val cleanMessage = if (message.isNotBlank() && !message.startsWith("{")) message else "Acceso denegado"
                    return Result.failure(Exception(cleanMessage))
                }
            } else {
                Result.failure(Exception("Error en el servidor: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Error de red: revisa tu conexión"))
        }
    }
}
