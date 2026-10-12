package com.maxrave.ktorext.encoding

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.ContentEncodingConfig

/**
 * True where the HTTP engine itself negotiates and decodes `Content-Encoding`.
 *
 * On iOS the Darwin engine runs on NSURLSession, which sends its own `Accept-Encoding` and hands
 * Ktor the body already decoded while leaving the `Content-Encoding` header in place. Installing
 * Ktor's [ContentEncoding] there would decode the same bytes a second time.
 */
expect val engineDecodesContentEncoding: Boolean

/** Installs [ContentEncoding] only where the engine does not already decode responses. */
fun HttpClientConfig<*>.installContentEncoding(block: ContentEncodingConfig.() -> Unit) {
    if (engineDecodesContentEncoding) return
    install(ContentEncoding, block)
}
