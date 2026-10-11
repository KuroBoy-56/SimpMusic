@file:OptIn(ExperimentalForeignApi::class)

package com.maxrave.simpmusic.expect.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

/**
 * Snapshot of WKWebView's cookie jar. WebKit only hands cookies out asynchronously, while the
 * shared login screens read them synchronously in `onPageFinished`, so every finished navigation
 * refreshes this snapshot first and only then reports the page as finished.
 */
private object IosCookieJar {
    var cookies: List<NSHTTPCookie> = emptyList()

    fun refresh(then: () -> Unit) {
        WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { all ->
            cookies = all.orEmpty().filterIsInstance<NSHTTPCookie>()
            then()
        }
    }

    fun headerFor(url: String): String {
        val host = NSURL.URLWithString(url)?.host ?: return ""
        return cookies
            .filter { cookie ->
                val domain = cookie.domain.removePrefix(".")
                host == domain || host.endsWith(".$domain")
            }.joinToString("; ") { "${it.name}=${it.value}" }
    }
}

actual fun createWebViewCookieManager(): WebViewCookieManager =
    object : WebViewCookieManager {
        override fun getCookie(url: String): String = IosCookieJar.headerFor(url)

        override fun removeAllCookies() {
            IosCookieJar.cookies = emptyList()
            WKWebsiteDataStore.defaultDataStore().removeDataOfTypes(
                WKWebsiteDataStore.allWebsiteDataTypes(),
                modifiedSince = NSDate.dateWithTimeIntervalSince1970(0.0),
                completionHandler = {},
            )
        }
    }

private class FinishDelegate(
    private val onFinished: (WKWebView, String) -> Unit,
) : NSObject(),
    WKNavigationDelegateProtocol {
    override fun webView(
        webView: WKWebView,
        didFinishNavigation: WKNavigation?,
    ) {
        val url = webView.URL?.absoluteString ?: return
        IosCookieJar.refresh { onFinished(webView, url) }
    }
}

@Composable
private fun WebViewHost(
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onFinished: (WKWebView, String) -> Unit,
) {
    // Held for the composable's lifetime: WKWebView keeps only a weak reference to its delegate.
    val delegate = remember { FinishDelegate(onFinished) }
    Box {
        UIKitView(
            factory = {
                WKWebView(frame = CGRectZero.readValue(), configuration = WKWebViewConfiguration()).apply {
                    navigationDelegate = delegate
                    NSURL.URLWithString(initUrl)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        aboveContent()
    }
}

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {
    WebViewHost(initUrl, aboveContent) { _, url ->
        state.value = WebViewState.Finished
        onPageFinished(url)
    }
}

/** Reads Discord's token out of localStorage once the web client has signed in and reached /app. */
private const val DISCORD_TOKEN_JS =
    "(function(){var i=document.createElement('iframe');document.body.appendChild(i);" +
        "return i.contentWindow.localStorage.token.slice(1,-1)})()"

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (token: String) -> Unit,
) {
    WebViewHost("https://discord.com/login", aboveContent) { webView, url ->
        state.value = WebViewState.Finished
        if (url.endsWith("/app")) {
            webView.evaluateJavaScript(DISCORD_TOKEN_JS) { result, _ ->
                (result as? String)?.takeIf { it.isNotBlank() }?.let(onLoginDone)
            }
        }
    }
}
