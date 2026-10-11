package com.maxrave.common

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

// Multiplatform stand-ins for the few `String.format` patterns the shared code uses.
// `String.format` is a JVM-only extension, so it does not exist on iOS.

/** `"%02d:%02d".format(seconds / 60, seconds % 60)` */
fun formatMinutesSeconds(totalSeconds: Int): String = "${(totalSeconds / 60).pad2()}:${(totalSeconds % 60).pad2()}"

/** `"%02d".format(n)` */
fun Int.pad2(): String = toString().padStart(2, '0')

fun Long.pad2(): String = toString().padStart(2, '0')

/** `"%,d".format(n)` — groups digits by thousands with a comma. */
fun Number.withThousandsSeparator(): String {
    val n = toLong()
    val digits = abs(n).toString().reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$digits" else digits
}

/** `"%.Nf".format(value)` with a '.' decimal separator. */
fun Double.toFixed(decimals: Int): String {
    if (decimals <= 0) return roundToLong().toString()
    val factor = 10.0.pow(decimals)
    val scaled = (abs(this) * factor).roundToLong()
    val whole = scaled / factor.toLong()
    val frac = (scaled % factor.toLong()).toString().padStart(decimals, '0')
    return "${if (this < 0 && scaled != 0L) "-" else ""}$whole.$frac"
}

fun Float.toFixed(decimals: Int): String = toDouble().toFixed(decimals)
