package com.maxrave.simpmusic


actual fun getPlatform(): Platform = Platform.Desktop

actual fun desktopOsName(): String = System.getProperty("os.name") ?: "jvm"
