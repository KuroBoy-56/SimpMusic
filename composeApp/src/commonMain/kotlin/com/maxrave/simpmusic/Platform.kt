package com.maxrave.simpmusic

sealed class Platform {
    object Android : Platform()
    object iOS : Platform()
    object Desktop : Platform()

    fun osName(): String = when (this) {
        Android -> "android"
        iOS -> "iOS"
        Desktop -> desktopOsName()
    }
}

expect fun getPlatform(): Platform

/**
 * Short tag sent to the account panel so it can store errors per platform and target an
 * announcement at one OS (e.g. a message only for the iOS app). "android" | "ios" | "desktop".
 */
fun platformTag(): String = when (getPlatform()) {
    Platform.Android -> "android"
    Platform.iOS -> "ios"
    Platform.Desktop -> "desktop"
}

/** Phone/tablet platforms (Android and iOS) share the touch layout: mini player, transport controls, keyboard focus. */
fun isMobilePlatform(): Boolean = getPlatform().let { it == Platform.Android || it == Platform.iOS }

/** `os.name` on the JVM; never consulted on Android or iOS. */
expect fun desktopOsName(): String