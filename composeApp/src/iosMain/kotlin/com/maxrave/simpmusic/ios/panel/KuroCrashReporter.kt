@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class, BetaInteropApi::class)

package com.maxrave.simpmusic.ios.panel

import com.maxrave.logger.Logger
import com.maxrave.simpmusic.BuildKonfig
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import platform.Foundation.NSException
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSSetUncaughtExceptionHandler
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.UIKit.UIDevice
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_SEC
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time
import platform.posix.uname
import platform.posix.utsname
import kotlin.concurrent.AtomicInt
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook

/**
 * iOS port of Android's `CrashManager`: same endpoint (`log_error.php`), same JSON fields
 * (`app_name`, `tipo`, `mensaje`, `stacktrace`, `dispositivo`, `version_app`).
 *
 * Two things Android gets for free need extra care here:
 * - A crash ends the process right after the hook returns, so the report is first saved to
 *   NSUserDefaults, then sent synchronously with a short timeout, and any report that did not
 *   make it is re-sent on the next launch.
 * - A native crash or a watchdog kill runs no Kotlin code at all. A launch marker covers that:
 *   it is set when start-up begins and cleared once the UI has been up for a few seconds, so a
 *   marker still set on the next launch is reported as `ARRANQUE_FALLIDO` together with the last
 *   start-up step that was reached.
 */
object KuroCrashReporter {
    private const val TAG = "KuroCrashReporter"
    private const val KEY_PENDING = "kuro.pending_crash_reports"
    private const val KEY_LAUNCH_STEP = "kuro.launch_step"
    private const val MAX_PENDING = 10

    private val json = Json { ignoreUnknownKeys = true }
    private var installed = false

    fun install() {
        if (installed) return
        installed = true

        // A launch that never reached "ui-ready" died without running any Kotlin handler.
        NSUserDefaults.standardUserDefaults.stringForKey(KEY_LAUNCH_STEP)?.let { lastStep ->
            savePending(
                report(
                    "ARRANQUE_FALLIDO",
                    "La app se cerró durante el arranque anterior sin excepción de Kotlin " +
                        "(crash nativo o cierre por el sistema). Último paso alcanzado: $lastStep",
                    "",
                ),
            )
        }
        markLaunchStep("crash-reporter")

        val previous =
            setUnhandledExceptionHook { throwable ->
                onFatal("FATAL_CRASH", throwable.message ?: throwable::class.simpleName ?: "Sin mensaje", throwable.stackTraceToString())
            }
        Logger.d(TAG, "Installed (previous hook: ${previous != null})")
        NSSetUncaughtExceptionHandler(staticCFunction(::onNSException))

        flushPendingAsync()
    }

    /** Records how far start-up got; cleared by [launchCompleted]. */
    fun markLaunchStep(step: String) {
        NSUserDefaults.standardUserDefaults.setObject(step, KEY_LAUNCH_STEP)
        NSUserDefaults.standardUserDefaults.synchronize()
    }

    fun launchCompleted() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(KEY_LAUNCH_STEP)
    }

    /** Android's `CrashManager.logSilentError`. */
    fun logSilentError(
        tipo: String,
        mensaje: String,
        stacktrace: String = "",
    ) {
        val payload = report(tipo, mensaje, stacktrace)
        send(payload) { ok -> if (!ok) savePending(payload) }
    }

    internal fun onNSExceptionReport(exception: NSException?) {
        val stack = exception?.callStackSymbols?.joinToString("\n").orEmpty()
        onFatal("FATAL_CRASH_OBJC", "${exception?.name}: ${exception?.reason}", stack)
    }

    private fun onFatal(
        tipo: String,
        mensaje: String,
        stacktrace: String,
    ) {
        val payload = report(tipo, mensaje, stacktrace)
        savePending(payload)
        launchCompleted()
        // Synchronous: the process is about to die. Whatever does not make it in 4 s is sent on
        // the next launch from the saved copy.
        if (sendBlocking(payload, timeoutSeconds = 4)) removePending(payload)
    }

    private fun report(
        tipo: String,
        mensaje: String,
        stacktrace: String,
    ): String =
        buildJsonObject {
            put("app_name", KuroPanel.APP_NAME)
            put("tipo", tipo)
            put("mensaje", mensaje)
            put("stacktrace", stacktrace.take(60_000))
            put("dispositivo", deviceDescription())
            put("version_app", "${BuildKonfig.versionName} (iOS)")
            // So log_error.php can filter iOS reports (and send iOS-only replies).
            put("platform", "ios")
            put("mac", runCatching { KuroDeviceId.mac() }.getOrDefault(""))
        }.toString()

    private fun deviceDescription(): String {
        val machine =
            runCatching {
                memScoped {
                    val info = alloc<utsname>()
                    uname(info.ptr)
                    info.machine.toKString()
                }
            }.getOrDefault(UIDevice.currentDevice.model)
        val device = UIDevice.currentDevice
        val id = runCatching { KuroDeviceId.mac() }.getOrDefault("?")
        return "Apple $machine (${device.systemName} ${device.systemVersion}) ID $id"
    }

    // ---------------------------------------------------------------------------------------
    // Pending queue
    // ---------------------------------------------------------------------------------------

    private fun pending(): List<String> =
        NSUserDefaults.standardUserDefaults.stringForKey(KEY_PENDING)?.let { stored ->
            runCatching { json.parseToJsonElement(stored).jsonArray.mapNotNull { (it as? JsonPrimitive)?.content } }.getOrNull()
        } ?: emptyList()

    private fun storePending(list: List<String>) {
        NSUserDefaults.standardUserDefaults.setObject(JsonArray(list.map { JsonPrimitive(it) }).toString(), KEY_PENDING)
        NSUserDefaults.standardUserDefaults.synchronize()
    }

    private fun savePending(payload: String) = storePending((pending() + payload).takeLast(MAX_PENDING))

    private fun removePending(payload: String) = storePending(pending() - payload)

    private fun flushPendingAsync() {
        pending().forEach { payload ->
            send(payload) { ok -> if (ok) removePending(payload) }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Transport (plain NSURLSession so it works even while Kotlin is tearing down)
    // ---------------------------------------------------------------------------------------

    private fun request(payload: String): NSMutableURLRequest? {
        val url = KuroPanel.errorReportUrl.takeIf { it.isNotEmpty() }?.let { NSURL.URLWithString(it) } ?: return null
        return NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod("POST")
            setValue("application/json", forHTTPHeaderField = "Content-Type")
            setHTTPBody(NSString.create(string = payload).dataUsingEncoding(NSUTF8StringEncoding))
            setTimeoutInterval(4.0)
        }
    }

    private fun send(
        payload: String,
        onDone: (Boolean) -> Unit,
    ) {
        val request = request(payload) ?: return onDone(false)
        NSURLSession.sharedSession
            .dataTaskWithRequest(request) { _, response, error ->
                val ok = error == null && ((response as? NSHTTPURLResponse)?.statusCode ?: 0L) in 200L..299L
                if (!ok) Logger.w(TAG, "Report not delivered: ${error?.localizedDescription}")
                onDone(ok)
            }.resume()
    }

    private fun sendBlocking(
        payload: String,
        timeoutSeconds: Long,
    ): Boolean {
        val request = request(payload) ?: return false
        val delivered = AtomicInt(0)
        val semaphore = dispatch_semaphore_create(0)
        NSURLSession.sharedSession
            .dataTaskWithRequest(request) { _, response, error ->
                if (error == null && ((response as? NSHTTPURLResponse)?.statusCode ?: 0L) in 200L..299L) delivered.value = 1
                dispatch_semaphore_signal(semaphore)
            }.resume()
        dispatch_semaphore_wait(semaphore, dispatch_time(DISPATCH_TIME_NOW, timeoutSeconds * NSEC_PER_SEC.toLong()))
        return delivered.value == 1
    }
}

private fun onNSException(exception: NSException?) {
    KuroCrashReporter.onNSExceptionReport(exception)
}
