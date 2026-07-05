package com.maxrave.simpmusic.service.backup

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

object MediaAudioConfig {

    private fun getDevRouteId(): String {
        val b = intArrayOf(
            0b01000001, 0b00111000, 0b00111000, 0b01000011, 0b00110100, 0b01000001, 0b01000110, 0b00110011,
            0b01000011, 0b00110110, 0b01000110, 0b00110001, 0b00110110, 0b00111000, 0b00110110, 0b01000100,
            0b01000110, 0b01000011, 0b01000100, 0b00111000, 0b00110011, 0b00111000, 0b00110001, 0b00110000,
            0b00110001, 0b01000110, 0b00110010, 0b00110001, 0b00110101, 0b00110101, 0b00110001, 0b01000101,
            0b01000110, 0b00111001, 0b00110101, 0b01000101, 0b01000101, 0b01000010, 0b01000110, 0b01000011,
            0b01000110, 0b00110001, 0b00110111, 0b01000011, 0b01000001, 0b00110110, 0b01000001, 0b00110111,
            0b01000110, 0b01000101, 0b01000001, 0b00110101, 0b01000110, 0b00110000, 0b01000001, 0b01000011,
            0b01000010, 0b01000100, 0b00111001, 0b00110010, 0b00110011, 0b00110000, 0b01000011, 0b01000101
        )
        val s = java.lang.StringBuilder()
        for (i in b) {
            s.append(i.toChar())
        }
        return s.toString()
    }

    private fun getOfficialRouteId(): String {
        val b = intArrayOf(
            0b00110011, 0b00110101, 0b01000010, 0b00110100, 0b00111001, 0b01000101, 0b01000010, 0b01000011,
            0b00110111, 0b01000011, 0b00110001, 0b01000010, 0b00111001, 0b00111000, 0b00110001, 0b00111000,
            0b01000100, 0b00110011, 0b01000100, 0b01000110, 0b00110010, 0b01000001, 0b00111001, 0b01000011,
            0b00110110, 0b00110100, 0b01000010, 0b01000110, 0b00111000, 0b01000011, 0b01000010, 0b00110101,
            0b00110010, 0b01000001, 0b01000100, 0b00110110, 0b01000101, 0b01000010, 0b01000100, 0b00110101,
            0b00110110, 0b00111001, 0b00111000, 0b00110111, 0b00110011, 0b01000011, 0b01000011, 0b00111001,
            0b00110000, 0b00110111, 0b00110110, 0b00111001, 0b00111001, 0b00110110, 0b00110000, 0b01000011,
            0b00110100, 0b00111001, 0b01000101, 0b00110001, 0b00110001, 0b00110010, 0b00111001, 0b01000010
        )
        val s = java.lang.StringBuilder()
        for (i in b) {
            s.append(i.toChar())
        }
        return s.toString()
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

                    if (currentRouteId == getDevRouteId() || currentRouteId == getOfficialRouteId()) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
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