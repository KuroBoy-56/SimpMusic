package com.maxrave.data.mediaservice.ios

import com.maxrave.logger.Logger

/**
 * Where iOS playback and download failures are reported. The app module points [reporter] at the
 * panel's crash reporter, so a failure on a real device — which the simulator CI cannot reproduce —
 * arrives with the stage that failed and the HTTP status AVPlayer or the CDN gave.
 */
object IosPlaybackDiagnostics {
    var reporter: ((tipo: String, mensaje: String, detalle: String) -> Unit)? = null

    private const val MAX_REPORTS_PER_SESSION = 15
    private var sent = 0

    fun report(
        tipo: String,
        mensaje: String,
        detalle: String,
    ) {
        Logger.e("IosPlaybackDiagnostics", "$tipo: $mensaje\n$detalle")
        if (sent >= MAX_REPORTS_PER_SESSION) return
        sent++
        runCatching { reporter?.invoke(tipo, mensaje, detalle) }
    }
}
