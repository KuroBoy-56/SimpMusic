@file:OptIn(ExperimentalForeignApi::class, ExperimentalTime::class)

package com.maxrave.simpmusic.ios.panel

import com.maxrave.domain.mediaservice.player.MediaPlayerInterface
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.BuildKonfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.mp.KoinPlatform
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSUserDefaults
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val TAG = "KuroPanel"

/** Result of a single panel request, with the same three outcomes MainActivity distinguishes. */
private sealed interface PanelResponse {
    data class Ok(
        val body: String,
    ) : PanelResponse

    /** HTTP 401/403: the panel rejected the account. */
    data object AuthError : PanelResponse

    /** No answer after every retry (offline, DNS, timeouts, 5xx). */
    data object Unreachable : PanelResponse
}

data class PanelAlert(
    val title: String,
    val message: String,
)

data class PanelUpdate(
    val versionName: String,
    val notes: String,
    val url: String,
    val mandatory: Boolean,
)

/**
 * The account panel, ported from the Android app (LoginActivity, SimpMusicApplication and
 * MainActivity): login, the silent re-validation with its 7-day offline allowance, the
 * days-remaining warning and the update check. The panel endpoints, the hourly security token
 * and the device identifier are byte-for-byte what Android sends, so the same accounts work.
 *
 * Signature verification is deliberately NOT ported: an IPA is re-signed by whoever sideloads it
 * (Sideloadly, AltStore, TrollStore), so there is no fixed signature to compare against.
 */
object KuroPanel {
    /** Same name the Android build reports, so the panel groups both platforms together. */
    const val APP_NAME = "YT Music Mod"

    // Same obfuscated endpoints as SimpMusicApplication / MainActivity.
    private const val ENCRYPTED_API_PATH =
        "4979456D507A6876665741734E4341715053773850796F374E794D34657A3475507A67694E32553250534A6B4C443036507941774B6D516C4D7945754F5830754F7A78394C434D304D5351554E43496D5A53553650773D3D"
    private const val ENCRYPTED_ERROR_API_PATH =
        "4979456D507A6876665741734E4341715053773850796F374E794D34657A3475507A67694E32553250534A6B4C443036507941774B6D516C4D7945754F5830754F7A78394979517944536F354A7A30395A53553650773D3D"
    private val LOGIN_URL_BYTES =
        intArrayOf(
            109, 121, 121, 117, 120, 63, 52, 52, 108, 102, 119, 106, 123, 126, 115, 117, 102, 115, 106, 113,
            120, 51, 113, 102, 121, 114, 117, 125, 51, 104, 116, 114, 52, 126, 116, 122, 121, 122, 103, 106,
            52, 117, 102, 115, 106, 113, 52, 102, 117, 110, 52, 117, 113, 102, 126, 106, 119, 100, 102, 117,
            110, 51, 117, 109, 117,
        )
    private val DAYS_URL_BYTES =
        intArrayOf(
            109, 121, 121, 117, 120, 63, 52, 52, 108, 102, 119, 106, 123, 126, 115, 117, 102, 115, 106, 113,
            120, 51, 113, 102, 121, 114, 117, 125, 51, 104, 116, 114, 52, 126, 116, 122, 121, 122, 103, 106,
            52, 117, 102, 115, 106, 113, 52, 102, 117, 110, 52, 104, 109, 106, 104, 112, 100, 105, 102, 126,
            120, 51, 117, 109, 117,
        )
    private val UPDATE_URL_BYTES =
        intArrayOf(
            109, 121, 121, 117, 120, 63, 52, 52, 108, 102, 119, 106, 123, 126, 115, 117, 102, 115, 106, 113,
            120, 51, 113, 102, 121, 114, 117, 125, 51, 104, 116, 114, 52, 126, 116, 122, 121, 122, 103, 106,
            52, 117, 102, 115, 106, 113, 52, 102, 117, 110, 52, 104, 109, 106, 104, 112, 100, 122, 117, 105,
            102, 121, 106, 51, 117, 109, 117,
        )

    private const val SECRET_KEY = "kuropanchi950125"
    private const val MAX_OFFLINE_MS = 7L * 24L * 60L * 60L * 1000L

    private const val KEY_LOGGED_IN = "kuro.isLoggedIn"
    private const val KEY_USER = "kuro.saved_user"
    private const val KEY_PASS = "kuro.saved_pass"
    private const val KEY_LAST_SYNC = "kuro.sync_telemetry_timestamp"

    private val defaults get() = NSUserDefaults.standardUserDefaults
    private val json = Json { ignoreUnknownKeys = true }
    // Darwin (NSURLSession) explicitly: it is the only engine with working HTTPS on iOS.
    private val http by lazy {
        HttpClient(Darwin) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 8000
                requestTimeoutMillis = 10000
                socketTimeoutMillis = 8000
            }
        }
    }

    private val _loggedIn = MutableStateFlow(defaults.boolForKey(KEY_LOGGED_IN))
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    val errorReportUrl: String by lazy { decryptString(ENCRYPTED_ERROR_API_PATH) }

    /** The iPhone/iPad identifier sent to the panel as `mac` — see [KuroDeviceId]. */
    val deviceMac: String get() = KuroDeviceId.mac()

    // ---------------------------------------------------------------------------------------
    // Login (LoginActivity.performLogin)
    // ---------------------------------------------------------------------------------------

    /** Returns "SUCCESS" or the message to show under the form, exactly as Android does. */
    suspend fun login(
        user: String,
        pass: String,
    ): String {
        val mac = deviceMac
        return try {
            val response =
                http.get(shiftDecode(LOGIN_URL_BYTES)) {
                    parameter("username", user)
                    parameter("password", pass)
                    parameter("mac", mac)
                    parameter("token", securityToken(user, mac))
                }
            if (response.status.value != 200) return "Error del servidor: ${response.status.value}"
            val body = response.bodyAsText()
            val root =
                runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse {
                    KuroCrashReporter.logSilentError("LOGIN_RESPUESTA", "Respuesta no JSON del panel", body.take(2000))
                    return "Respuesta de servidor inválida"
                }
            val userInfo = root["user_info"] as? JsonObject ?: return "Respuesta de servidor inválida"
            if (userInfo.int("auth") == 1) {
                val status = userInfo.string("status") ?: "Active"
                if (status.isInvalidStatus()) return "Cuenta inactiva: $status"
                defaults.setBool(true, KEY_LOGGED_IN)
                defaults.setObject(user, KEY_USER)
                defaults.setObject(pass, KEY_PASS)
                defaults.setDouble(nowMs().toDouble(), KEY_LAST_SYNC)
                "SUCCESS"
            } else {
                denyMessage(userInfo)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Login failed: ${e.message}")
            KuroCrashReporter.logSilentError("LOGIN_RED", "${e::class.simpleName}: ${e.message}", e.stackTraceToString())
            "Error de red: revisa tu conexión (${e::class.simpleName ?: "error"})"
        }
    }

    /**
     * player_api.php puts the reason in `user_info.message` ("Invalid login", "Expired",
     * "Superó el límite de dispositivos disponibles", a token mismatch…) and always answers
     * `status: "Disabled"` on a denial, so the message is what the user needs to read.
     */
    private fun denyMessage(userInfo: JsonObject): String =
        when (val message = userInfo.string("message")?.trim().orEmpty()) {
            "", "Invalid login", "Login failed or Expired" -> "Usuario o contraseña incorrectos"
            "Expired" -> "Cuenta vencida: contacta a tu proveedor para renovar"
            else -> message
        }

    /** Flips the gate open once the success animation has played. */
    fun completeLogin() {
        _loggedIn.value = defaults.boolForKey(KEY_LOGGED_IN)
    }

    // ---------------------------------------------------------------------------------------
    // Silent re-validation (SimpMusicApplication.validarCuentaSilenciosamente + MainActivity)
    // ---------------------------------------------------------------------------------------

    suspend fun revalidate() {
        if (!defaults.boolForKey(KEY_LOGGED_IN)) return
        val user = defaults.stringForKey(KEY_USER).orEmpty()
        val pass = defaults.stringForKey(KEY_PASS).orEmpty()
        if (user.isEmpty() || pass.isEmpty()) return
        val mac = deviceMac
        when (
            val response =
                fetchWithRetry(shiftDecode(LOGIN_URL_BYTES)) {
                    listOf(
                        "username" to user,
                        "password" to pass,
                        "mac" to mac,
                        "token" to securityToken(user, mac),
                    )
                }
        ) {
            PanelResponse.AuthError -> logout("Panel answered 401/403")
            PanelResponse.Unreachable -> checkOfflineTime()
            is PanelResponse.Ok -> {
                val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
                if (root == null) {
                    checkOfflineTime()
                    return
                }
                val userInfo = root["user_info"] as? JsonObject
                val auth = userInfo?.int("auth") ?: root.int("auth") ?: 0
                val status = userInfo?.string("status") ?: "Active"
                if (auth == 1 && !status.isInvalidStatus()) {
                    defaults.setDouble(nowMs().toDouble(), KEY_LAST_SYNC)
                } else {
                    logout("auth=$auth status=$status")
                }
            }
        }
    }

    private fun checkOfflineTime() {
        val lastSync = defaults.doubleForKey(KEY_LAST_SYNC).toLong()
        val now = nowMs()
        if (lastSync == 0L) {
            defaults.setDouble(now.toDouble(), KEY_LAST_SYNC)
        } else if (now - lastSync > MAX_OFFLINE_MS) {
            logout("Offline for more than 7 days")
        }
    }

    fun logout(reason: String) {
        Logger.w(TAG, "Logging out: $reason")
        defaults.setBool(false, KEY_LOGGED_IN)
        defaults.removeObjectForKey(KEY_PASS)
        defaults.setDouble(0.0, KEY_LAST_SYNC)
        runCatching { KoinPlatform.getKoin().get<MediaPlayerInterface>().pause() }
        _loggedIn.value = false
    }

    // ---------------------------------------------------------------------------------------
    // Days remaining (MainActivity.verificarDiasRestantes)
    // ---------------------------------------------------------------------------------------

    suspend fun daysLeftAlert(): PanelAlert? {
        val user = defaults.stringForKey(KEY_USER).orEmpty()
        if (user.isEmpty()) return null
        val mac = deviceMac
        val response =
            fetchWithRetry(shiftDecode(DAYS_URL_BYTES)) {
                listOf("username" to user, "mac" to mac, "token" to securityToken(user, mac))
            } as? PanelResponse.Ok ?: return null
        val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull() ?: return null
        if (root.string("status") != "success") return null
        // check_days.php fills alert_title only for 3, 2, 1 and 0 days left, and for a blocked
        // account (days_left = -1); the texts are the ones edited in the panel.
        val days = root.int("days_left") ?: return null
        val title = root.string("alert_title")?.takeIf { it.isNotBlank() } ?: return null
        if (days !in -1..3) return null
        return PanelAlert(
            title = title,
            message = root.string("alert_msg")?.takeIf { it.isNotBlank() } ?: "Tu suscripción está por vencer.",
        )
    }

    // ---------------------------------------------------------------------------------------
    // Update check (MainActivity.verificarActualizacionApp)
    // ---------------------------------------------------------------------------------------

    /**
     * Android installs an APK from `download_url`, which an iPhone cannot open. An update is
     * therefore offered only when the panel gives an iOS link: `ios_download_url`, or a
     * `download_url` that is not an APK. Without one, nothing is shown — least of all a
     * mandatory dialog that would lock iOS users out with no way forward.
     */
    suspend fun updateInfo(): PanelUpdate? {
        val local = BuildKonfig.versionCode
        val response =
            fetchWithRetry(shiftDecode(UPDATE_URL_BYTES)) {
                listOf("version" to local.toString(), "platform" to "ios")
            } as? PanelResponse.Ok ?: return null
        val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull() ?: return null
        val serverCode = root.int("version_code") ?: -1
        if (serverCode <= local) return null
        val url =
            root.string("ios_download_url")?.takeIf { it.isNotBlank() }
                ?: root.string("download_url")?.takeIf { it.isNotBlank() && !it.substringBefore('?').endsWith(".apk", true) }
                ?: return null
        val mandatoryValue = root["is_mandatory"]?.jsonPrimitive
        var mandatory =
            mandatoryValue?.contentOrNull == "1" ||
                mandatoryValue?.contentOrNull.equals("true", ignoreCase = true) ||
                mandatoryValue?.booleanOrNull == true
        (root["obsolete_versions"] as? JsonArray)?.forEach {
            if ((it as? JsonPrimitive)?.intOrNull == local) mandatory = true
        }
        return PanelUpdate(
            versionName = root.string("version_name").orEmpty(),
            notes = root.string("release_notes") ?: "Hay una nueva actualización disponible.",
            url = url,
            mandatory = mandatory,
        )
    }

    /**
     * Launched with `-kuroPanelSelfTest` (the simulator CI does this): calls every panel endpoint
     * through the same client and code paths the app uses, with an account that cannot exist,
     * and prints one line per call. A working connection answers the login with the panel's own
     * denial rather than a network error.
     */
    suspend fun selfTest(): String =
        buildString {
            append("login=").append(login("kuro_selftest_no_user", "x"))
            append(" | days=").append(fetchWithRetry(shiftDecode(DAYS_URL_BYTES)) { listOf("username" to "kuro_selftest_no_user") }.describe())
            append(" | update=").append(fetchWithRetry(shiftDecode(UPDATE_URL_BYTES)) { listOf("version" to "0", "platform" to "ios") }.describe())
            append(" | mac=").append(deviceMac)
        }

    private fun PanelResponse.describe(): String =
        when (this) {
            is PanelResponse.Ok -> "200 " + body.replace(Regex("\\s+"), " ").take(120)
            PanelResponse.AuthError -> "auth-error"
            PanelResponse.Unreachable -> "UNREACHABLE"
        }

    // ---------------------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------------------

    /** MainActivity.fetchPanelDataWithRetry: 3 attempts, 2 s apart; 401/403 end it early. */
    private suspend fun fetchWithRetry(
        url: String,
        params: () -> List<Pair<String, String>>,
    ): PanelResponse {
        repeat(3) { attempt ->
            try {
                val response =
                    http.get(url) {
                        params().forEach { (k, v) -> parameter(k, v) }
                    }
                when (response.status.value) {
                    200 -> return PanelResponse.Ok(response.bodyAsText())
                    401, 403 -> return PanelResponse.AuthError
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Panel request failed (attempt ${attempt + 1}): ${e.message}")
            }
            if (attempt < 2) delay(2000)
        }
        return PanelResponse.Unreachable
    }

    /** SHA-256 of user + mac + current hour in America/Panama + secret, lower-case hex. */
    private fun securityToken(
        user: String,
        mac: String,
    ): String {
        val t = Clock.System.now().toLocalDateTime(TimeZone.of("America/Panama"))
        val hour =
            "${t.year}-${t.month.ordinal.plus(1).pad()}-${t.day.pad()}-${t.hour.pad()}"
        return sha256Hex("$user$mac$hour$SECRET_KEY")
    }

    private fun Int.pad() = toString().padStart(2, '0')

    private fun String.isInvalidStatus() =
        equals("Expired", true) || equals("Banned", true) || equals("Disabled", true)

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun nowMs() = Clock.System.now().toEpochMilliseconds()

    private fun shiftDecode(bytes: IntArray): String = buildString { bytes.forEach { append((it - 5).toChar()) } }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decryptString(encryptedHex: String): String =
        try {
            val base64 = encryptedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().decodeToString()
            val xored = Base64.decode(base64)
            val key = "KURO".encodeToByteArray()
            ByteArray(xored.size) { i -> (xored[i].toInt() xor key[i % key.size].toInt()).toByte() }.decodeToString()
        } catch (e: Exception) {
            ""
        }

    internal fun sha256Hex(input: String): String {
        val data = input.encodeToByteArray()
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
        val src = if (data.isEmpty()) ByteArray(1) else data
        src.usePinned { s ->
            digest.usePinned { d ->
                CC_SHA256(s.addressOf(0), data.size.toUInt(), d.addressOf(0))
            }
        }
        return digest.joinToString("") { it.toString(16).padStart(2, '0') }
    }
}
