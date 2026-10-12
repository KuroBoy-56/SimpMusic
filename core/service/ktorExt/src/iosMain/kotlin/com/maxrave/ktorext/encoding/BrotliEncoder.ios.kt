package com.maxrave.ktorext.encoding

import io.ktor.util.ContentEncoder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.coroutines.CoroutineContext

/**
 * NSURLSession decodes Brotli before Ktor sees the body (see [engineDecodesContentEncoding]),
 * so on iOS this is a pass-through. It must never throw: clients create it while being built,
 * and the old `TODO()` here killed the app on launch, before the first frame.
 */
object BrotliEncoder : ContentEncoder {
    override val name: String
        get() = "br"

    override fun encode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext,
    ): ByteReadChannel = source

    override fun encode(
        source: ByteWriteChannel,
        coroutineContext: CoroutineContext,
    ): ByteWriteChannel = source

    override fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext,
    ): ByteReadChannel = source
}

actual fun createBrotliEncoder(): ContentEncoder = BrotliEncoder
