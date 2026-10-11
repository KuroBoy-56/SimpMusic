package com.maxrave.spotify.auth

import com.maxrave.ktorext.crypto.commonCryptoHmac

/**
 * RFC 6238 TOTP, matching `GoogleAuthenticator` used on Android/Desktop: the secret is Base32,
 * HMAC-SHA1, 30-second steps, 6 digits.
 */
actual fun generateTotp(
    secret: String,
    timestamp: Long,
): String {
    val key = base32Decode(secret)
    val counter = timestamp / 1000L / 30L
    val message = ByteArray(8) { i -> (counter ushr (8 * (7 - i))).toByte() }
    val hash = commonCryptoHmac("HmacSHA1", key, message)
    val offset = hash[hash.size - 1].toInt() and 0x0F
    val binary =
        ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
    return (binary % 1_000_000).toString().padStart(6, '0')
}

private fun base32Decode(input: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val clean = input.uppercase().filter { it != '=' && !it.isWhitespace() && it != '-' }
    val out = ArrayList<Byte>(clean.length * 5 / 8)
    var buffer = 0
    var bits = 0
    for (c in clean) {
        val v = alphabet.indexOf(c)
        if (v < 0) continue
        buffer = (buffer shl 5) or v
        bits += 5
        if (bits >= 8) {
            out.add((buffer ushr (bits - 8)).toByte())
            bits -= 8
        }
    }
    return out.toByteArray()
}
