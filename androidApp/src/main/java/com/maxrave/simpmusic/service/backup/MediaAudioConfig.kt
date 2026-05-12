package com.maxrave.simpmusic.service.backup

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

object MediaAudioConfig {

    // Generador dinámico del Hash (Totalmente ofuscado con XOR y la llave "KURO")
    private fun getDefaultRouteId(): String {
        val key = "KURO"
        val encryptedBytes = intArrayOf(
            10, 45, 106, 12, 127, 20, 20, 112, 8, 35, 20, 110, 29, 45, 104, 11,
            13, 22, 14, 119, 120, 45, 99, 127, 122, 19, 96, 110, 126, 32, 99, 10,
            13, 36, 103, 10, 14, 17, 20, 12, 13, 36, 101, 12, 10, 35, 17, 120,
            13, 16, 17, 122, 13, 37, 17, 12, 9, 17, 107, 125, 120, 37, 17, 10
        )
        val builder = StringBuilder()
        for (i in encryptedBytes.indices) {
            builder.append((encryptedBytes[i] xor key[i % key.length].code).toChar())
        }
        return builder.toString()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("PackageManagerGetSignatures")
    fun checkAudioRoute(context: Context): Boolean {
        try {
            val pm = context.packageManager
            val packageName = context.packageName
            val signatures: Array<Signature>?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                val info = pm.getPackageInfo(packageName, flags)
                signatures = info.signingInfo?.apkContentsSigners
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                signatures = info.signingInfo?.apkContentsSigners
            } else {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                signatures = info.signatures
            }

            if (signatures != null) {
                for (signature in signatures) {
                    val md = MessageDigest.getInstance("SHA-256")
                    md.update(signature.toByteArray())
                    val currentRouteId = bytesToHex(md.digest())

                    // Comparamos el hash real con el que desencriptamos en memoria
                    if (getDefaultRouteId() == currentRouteId) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Muere en el más absoluto silencio
        }
        return false
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = "0123456789ABCDEF".toCharArray()
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}