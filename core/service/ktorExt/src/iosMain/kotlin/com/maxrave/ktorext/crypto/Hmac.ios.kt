@file:OptIn(ExperimentalForeignApi::class, ExperimentalTime::class)

package com.maxrave.ktorext.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA512_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA1
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCHmacAlgSHA512
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Raw HMAC through CommonCrypto. [algorithm] takes the JCA names: HmacSHA1/256/512. */
fun commonCryptoHmac(
    algorithm: String,
    key: ByteArray,
    data: ByteArray,
): ByteArray {
    val (alg, length) =
        when (algorithm.uppercase()) {
            "HMACSHA1" -> kCCHmacAlgSHA1 to CC_SHA1_DIGEST_LENGTH
            "HMACSHA512" -> kCCHmacAlgSHA512 to CC_SHA512_DIGEST_LENGTH
            else -> kCCHmacAlgSHA256 to CC_SHA256_DIGEST_LENGTH
        }
    val out = ByteArray(length)
    // addressOf(0) needs a non-empty array; an empty key or message is passed as a 1-byte
    // buffer with length 0, which CommonCrypto treats exactly like an empty input.
    val k = if (key.isEmpty()) ByteArray(1) else key
    val d = if (data.isEmpty()) ByteArray(1) else data
    k.usePinned { kp ->
        d.usePinned { dp ->
            out.usePinned { op ->
                CCHmac(alg, kp.addressOf(0), key.size.toULong(), dp.addressOf(0), data.size.toULong(), op.addressOf(0))
            }
        }
    }
    return out
}

actual class Hmac actual constructor(
    private val algorithm: String,
    secretKey: String,
) {
    private val tokenTtl: Long = 300000 // 5 minutes in milliseconds
    private val key = secretKey.encodeToByteArray()

    actual fun getMacTimestampPair(uri: String): Pair<String, String> {
        val timestamp = Clock.System.now().toEpochMilliseconds().toString()
        val data = "$timestamp$uri"
        return generateHmac(data) to timestamp
    }

    @OptIn(ExperimentalEncodingApi::class)
    actual fun generateHmac(data: String): String = Base64.encode(commonCryptoHmac(algorithm, key, data.encodeToByteArray()))

    actual fun validateHmac(
        data: String,
        hmac: String,
    ): Boolean = generateHmac(data) == hmac

    actual fun isValidTimestamp(timestamp: String): Boolean {
        val requestTime = timestamp.toLongOrNull() ?: return false
        return (Clock.System.now().toEpochMilliseconds() - requestTime) < tokenTtl
    }
}
