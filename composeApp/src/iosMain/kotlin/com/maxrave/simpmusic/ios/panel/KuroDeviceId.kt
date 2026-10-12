@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.maxrave.simpmusic.ios.panel

import com.maxrave.logger.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIDevice

/**
 * The identifier the panel binds an account to — the iOS counterpart of Android's
 * `getCustomMacAddress()`, formatted the same way: 16 upper-case hex digits in colon pairs
 * (`AB:CD:EF:01:23:45:67:89`).
 *
 * Android derives it from `ANDROID_ID`. iOS exposes no hardware identifier to apps; the
 * supported per-device value is `identifierForVendor`, which iOS resets when every app from the
 * same vendor is deleted. The derived value is therefore stored in the Keychain on first use,
 * which survives a reinstall, so deleting and sideloading the app again does not make the panel
 * see a new device.
 */
object KuroDeviceId {
    private const val TAG = "KuroDeviceId"
    private const val SERVICE = "com.kuroboy56.kuromusic.device"
    private const val ACCOUNT = "device_id"
    private const val DEFAULTS_KEY = "kuro.device_id"
    private const val FALLBACK = "1A2B3C4D5E6F7A8B"

    private var cached: String? = null

    fun mac(): String = raw().chunked(2).joinToString(":")

    private fun raw(): String {
        cached?.let { return it }
        val stored =
            keychainRead()?.takeIf { it.isValid() }
                ?: NSUserDefaults.standardUserDefaults.stringForKey(DEFAULTS_KEY)?.takeIf { it.isValid() }
        val id = stored ?: derive()
        if (stored == null || keychainRead() != id) keychainWrite(id)
        NSUserDefaults.standardUserDefaults.setObject(id, DEFAULTS_KEY)
        cached = id
        return id
    }

    /** Same processing as Android: strip leading zeros, pad with 'A', 16 upper-case chars. */
    private fun derive(): String {
        val vendorId =
            UIDevice.currentDevice.identifierForVendor
                ?.UUIDString
                ?.replace("-", "")
                ?: FALLBACK
        var processed = vendorId.trimStart('0')
        if (processed.isEmpty()) processed = FALLBACK
        return processed.padEnd(16, 'A').substring(0, 16).uppercase()
    }

    private fun String.isValid() = length == 16 && all { it in '0'..'9' || it in 'A'..'F' }

    private fun keychainRead(): String? =
        memScoped {
            val query = baseQuery()
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            CFRelease(query)
            if (status != errSecSuccess) return null
            val data = CFBridgingRelease(result.value) as? NSData ?: return null
            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        }

    private fun keychainWrite(value: String) {
        val delete = baseQuery()
        SecItemDelete(delete)
        CFRelease(delete)

        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val add = baseQuery()
        val dataRef = CFBridgingRetain(data)
        CFDictionaryAddValue(add, kSecValueData, dataRef)
        CFDictionaryAddValue(add, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        val status = SecItemAdd(add, null)
        CFRelease(add)
        dataRef?.let { CFRelease(it) }
        if (status != errSecSuccess) Logger.w(TAG, "Keychain write failed: $status (UserDefaults copy kept)")
    }

    private fun baseQuery(): CFMutableDictionaryRef? {
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, retained(SERVICE))
        CFDictionaryAddValue(query, kSecAttrAccount, retained(ACCOUNT))
        return query
    }

    // The two strings live for the whole process, so they are retained once and never released.
    private val retainedStrings = mutableMapOf<String, CFTypeRef?>()

    private fun retained(s: String): CFTypeRef? = retainedStrings.getOrPut(s) { CFBridgingRetain(NSString.create(string = s)) }
}
