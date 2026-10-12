package com.maxrave.simpmusic.expect

import com.maxrave.simpmusic.ios.documentsDirectory
import com.maxrave.simpmusic.ios.ensureDirectory

actual fun getDownloadFolderPath(): String = ensureDirectory(documentsDirectory() + "/Downloads")
