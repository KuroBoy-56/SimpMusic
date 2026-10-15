package com.maxrave.kotlinytmusicscraper

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

// Same meaning as the JVM actuals: country = region code (gl), language = language code (hl).
// These were swapped, so requests went out as e.g. gl="es", hl="MX" until settings overrode them.
actual fun getCountry(): String = NSLocale.currentLocale.countryCode ?: "US"

actual fun getLanguage(): String = NSLocale.currentLocale.languageCode
