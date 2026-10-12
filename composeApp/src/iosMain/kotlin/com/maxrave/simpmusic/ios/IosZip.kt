@file:OptIn(ExperimentalForeignApi::class)

package com.maxrave.simpmusic.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.crc32
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.uInt
import platform.zlib.z_stream
import platform.zlib.zlibVersion

/**
 * A tiny ZIP reader/writer for the backup file. Backups are a ZIP of two members (the settings
 * `.preferences_pb` and the Room database). There is no `java.util.zip` on Kotlin/Native, so this
 * writes STORED entries (no compression — simplest and always valid) and reads both STORED and
 * DEFLATE, the latter through the system zlib so a backup made on Android or Desktop also restores.
 */
object IosZip {
    private fun ByteArray.u16(off: Int) = (this[off].toInt() and 0xFF) or ((this[off + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.u32(off: Int): Long =
        (this[off].toLong() and 0xFF) or
            ((this[off + 1].toLong() and 0xFF) shl 8) or
            ((this[off + 2].toLong() and 0xFF) shl 16) or
            ((this[off + 3].toLong() and 0xFF) shl 24)

    private fun MutableList<Byte>.putU16(v: Int) {
        add((v and 0xFF).toByte())
        add(((v ushr 8) and 0xFF).toByte())
    }

    private fun MutableList<Byte>.putU32(v: Long) {
        add((v and 0xFF).toByte())
        add(((v ushr 8) and 0xFF).toByte())
        add(((v ushr 16) and 0xFF).toByte())
        add(((v ushr 24) and 0xFF).toByte())
    }

    private fun crc32Of(data: ByteArray): Long {
        if (data.isEmpty()) return 0L
        return data.usePinned { pinned ->
            crc32(0u, pinned.addressOf(0).reinterpret(), data.size.convert()).toLong() and 0xFFFFFFFFL
        }
    }

    /** Builds a STORED ZIP containing [entries] (name → bytes). */
    fun write(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = mutableListOf<Byte>()
        data class Central(val name: ByteArray, val crc: Long, val size: Int, val offset: Int)
        val centrals = mutableListOf<Central>()
        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32Of(data)
            val offset = out.size
            out.putU32(0x04034b50)
            out.putU16(20) // version needed
            out.putU16(0) // flags
            out.putU16(0) // method 0 = stored
            out.putU16(0) // time
            out.putU16(0) // date
            out.putU32(crc)
            out.putU32(data.size.toLong()) // compressed size
            out.putU32(data.size.toLong()) // uncompressed size
            out.putU16(nameBytes.size)
            out.putU16(0) // extra len
            nameBytes.forEach { out.add(it) }
            data.forEach { out.add(it) }
            centrals.add(Central(nameBytes, crc, data.size, offset))
        }
        val centralStart = out.size
        for (c in centrals) {
            out.putU32(0x02014b50)
            out.putU16(20) // version made by
            out.putU16(20) // version needed
            out.putU16(0) // flags
            out.putU16(0) // method
            out.putU16(0) // time
            out.putU16(0) // date
            out.putU32(c.crc)
            out.putU32(c.size.toLong())
            out.putU32(c.size.toLong())
            out.putU16(c.name.size)
            out.putU16(0) // extra
            out.putU16(0) // comment
            out.putU16(0) // disk
            out.putU16(0) // internal attrs
            out.putU32(0) // external attrs
            out.putU32(c.offset.toLong())
            c.name.forEach { out.add(it) }
        }
        val centralSize = out.size - centralStart
        out.putU32(0x06054b50)
        out.putU16(0) // disk
        out.putU16(0) // disk with central
        out.putU16(centrals.size)
        out.putU16(centrals.size)
        out.putU32(centralSize.toLong())
        out.putU32(centralStart.toLong())
        out.putU16(0) // comment len
        return out.toByteArray()
    }

    /** Reads every member of [zip] (STORED or DEFLATE) as name → bytes. */
    fun read(zip: ByteArray): Map<String, ByteArray> {
        // Find the End Of Central Directory record (search backwards; no trailing comment expected).
        var eocd = -1
        var i = zip.size - 22
        while (i >= 0) {
            if (zip.u32(i) == 0x06054b50L) {
                eocd = i
                break
            }
            i--
        }
        if (eocd < 0) return emptyMap()
        val count = zip.u16(eocd + 10)
        var p = zip.u32(eocd + 16).toInt()
        val result = linkedMapOf<String, ByteArray>()
        repeat(count) {
            if (p < 0 || p + 46 > zip.size || zip.u32(p) != 0x02014b50L) return@repeat
            val method = zip.u16(p + 10)
            val compSize = zip.u32(p + 20).toInt()
            val uncompSize = zip.u32(p + 24).toInt()
            val nameLen = zip.u16(p + 28)
            val extraLen = zip.u16(p + 30)
            val commentLen = zip.u16(p + 32)
            val localOff = zip.u32(p + 42).toInt()
            val name = zip.decodeToString(p + 46, p + 46 + nameLen)
            // Local header: data starts after its own name + extra fields.
            if (localOff + 30 <= zip.size && zip.u32(localOff) == 0x04034b50L) {
                val lNameLen = zip.u16(localOff + 26)
                val lExtraLen = zip.u16(localOff + 28)
                val dataStart = localOff + 30 + lNameLen + lExtraLen
                val comp = zip.copyOfRange(dataStart, (dataStart + compSize).coerceAtMost(zip.size))
                val data = if (method == 0) comp else inflateRaw(comp, uncompSize)
                if (data != null) result[name] = data
            }
            p += 46 + nameLen + extraLen + commentLen
        }
        return result
    }

    /** Raw DEFLATE (ZIP method 8) through the system zlib. */
    private fun inflateRaw(
        input: ByteArray,
        expectedSize: Int,
    ): ByteArray? {
        if (input.isEmpty()) return ByteArray(0)
        val output = ByteArray(if (expectedSize > 0) expectedSize else input.size * 4 + 64)
        return memScoped {
            val strm = alloc<z_stream>()
            // -15 = raw deflate stream, no zlib/gzip header.
            if (inflateInit2_(strm.ptr, -15, zlibVersion(), z_stream.size.convert()) != Z_OK) return@memScoped null
            try {
                input.usePinned { inPin ->
                    output.usePinned { outPin ->
                        strm.next_in = inPin.addressOf(0).reinterpret()
                        strm.avail_in = input.size.convert()
                        strm.next_out = outPin.addressOf(0).reinterpret()
                        strm.avail_out = output.size.convert()
                        val rc = inflate(strm.ptr, Z_FINISH)
                        if (rc != Z_STREAM_END && rc != Z_OK) return@memScoped null
                        val produced = output.size - strm.avail_out.toInt()
                        if (produced == output.size) output else output.copyOf(produced)
                    }
                }
            } finally {
                inflateEnd(strm.ptr)
            }
        }
    }

    // Keep the deflate imports meaningful if later switched to compressed writing.
    @Suppress("unused")
    private val unusedFlushMarkers = intArrayOf(Z_NO_FLUSH, Z_FINISH)
    private val unusedUInt: uInt = 0u
}
