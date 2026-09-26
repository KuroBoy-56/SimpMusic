package com.maxrave.simpmusic

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.CursorWindow
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import cat.ereza.customactivityoncrash.config.CaocConfig
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.maxrave.common.AppIdentity
import com.maxrave.data.di.loader.loadAllModules
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.di.viewModelModule
import com.maxrave.simpmusic.service.backup.AutoBackupScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import multiplatform.network.cmptoast.AppContext
import okhttp3.OkHttpClient
import okio.FileSystem
import org.json.JSONObject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.simpmusic.crashlytics.configCrashlytics
import org.simpmusic.lastfm.configLastfm
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess

class SimpMusicApplication :
    Application(),
    KoinComponent,
    SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dataStoreManager: DataStoreManager by inject()
    private lateinit var autoBackupScheduler: AutoBackupScheduler

    private val VALID_SIGNATURE_HASH = "425463424B6A30644A53306D467A67626543776A4C42455A6551492F426941596657774550794D764B41554A5A7A3859447841384A77346C4958493D"
    private val ENCRYPTED_API_PATH = "4979456D507A6876665741734E4341715053773850796F374E794D34657A3475507A67694E32553250534A6B4C443036507941774B6D516C4D7945754F5830754F7A78394C434D304D5351554E43496D5A53553650773D3D"
    private val ENCRYPTED_ERROR_API_PATH = "4979456D507A6876665741734E4341715053773850796F374E794D34657A3475507A67694E32553250534A6B4C443036507941774B6D516C4D7945754F5830754F7A78394979517944536F354A7A30395A53553650773D3D"

    override fun onCreate() {
        super.onCreate()

        // 🛡️ 0. INICIAMOS EL MONITOR DE ERRORES SILENCIOSO
        val logApiUrl = decryptString(ENCRYPTED_ERROR_API_PATH)
        CrashManager.init(BuildConfig.VERSION_NAME, logApiUrl, "YT Music Mod")

        // 🔒 1. Verificación ANTICRACK FIRMA
        verificarFirma(this)

        // 🔒 2. Verificación SILENCIOSA DE CUENTA Y CONEXIÓN
        validarCuentaSilenciosamente(this)

        configCrashlytics(this, BuildKonfig.sentryDsn)
        configLastfm(BuildKonfig.lastfmApiKey, BuildKonfig.lastfmSecret)

        startKoin {
            androidLogger(level = Level.DEBUG)
            androidContext(this@SimpMusicApplication)
            loadAllModules(
                AppIdentity(
                    applicationId = BuildConfig.APPLICATION_ID,
                    versionName = BuildConfig.VERSION_NAME,
                    platform = "Android ${Build.VERSION.RELEASE}",
                ),
            )
            loadKoinModules(viewModelModule)
        }
        val workConfig =
            Configuration
                .Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build()

        WorkManager.initialize(this, workConfig)

        autoBackupScheduler = AutoBackupScheduler(this, dataStoreManager)
        applicationScope.launch {
            autoBackupScheduler.observeAndSchedule()
        }

        CaocConfig.Builder
            .create()
            .backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT)
            .enabled(true)
            .showErrorDetails(true)
            .showRestartButton(true)
            .errorDrawable(R.mipmap.ic_launcher_round)
            .logErrorOnRestart(false)
            .trackActivities(true)
            .minTimeBetweenCrashesMs(2000)
            .restartActivity(MainActivity::class.java)
            .apply()

        @SuppressLint("DiscouragedPrivateApi")
        val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
        field.isAccessible = true
        val expectSize = 100 * 1024 * 1024
        field.set(null, expectSize)

        AppContext.apply {
            set(applicationContext)
        }
    }

    // =========================================================================================
    // LÓGICA DE SEGURIDAD 2 - VERIFICACIÓN SILENCIOSA DE CUENTA Y CONTADOR OFFLINE CON TOKEN
    // =========================================================================================
    private fun getCustomMacAddress(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "1A2B3C4D5E6F7A8B"
        var processed = androidId.trimStart('0')
        if (processed.isEmpty()) {
            processed = "1A2B3C4D5E6F7A8B"
        }
        processed = processed.padEnd(16, 'A')
        processed = processed.substring(0, 16).uppercase()
        return processed.chunked(2).joinToString(":")
    }

    private fun generateSecurityToken(user: String, mac: String): String {
        val secretKey = "kuropanchi950125"
        val format = SimpleDateFormat("yyyy-MM-dd-HH", Locale.US)
        format.timeZone = TimeZone.getTimeZone("America/Panama")
        val currentHourDate = format.format(Date())

        val stringToHash = "$user$mac$currentHourDate$secretKey"

        val bytes = MessageDigest.getInstance("SHA-256").digest(stringToHash.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun validarCuentaSilenciosamente(context: Context) {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("isLoggedIn", false)

        if (!isLoggedIn) return

        val savedUser = prefs.getString("saved_user", "") ?: ""
        val savedPass = prefs.getString("saved_pass", "") ?: ""

        if (savedUser.isEmpty() || savedPass.isEmpty()) return

        val maxOfflineTimeMillis = 7L * 24L * 60L * 60L * 1000L

        applicationScope.launch(Dispatchers.IO) {
            try {
                val apiUrl = decryptString(ENCRYPTED_API_PATH)
                val deviceMac = getCustomMacAddress(context)
                val userEnc = URLEncoder.encode(savedUser, "UTF-8")
                val passEnc = URLEncoder.encode(savedPass, "UTF-8")
                val macEnc = URLEncoder.encode(deviceMac, "UTF-8")
                val securityToken = generateSecurityToken(savedUser, deviceMac)

                val url = URL("$apiUrl?username=$userEnc&password=$passEnc&mac=$macEnc&token=$securityToken")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val userInfo = jsonObject.optJSONObject("user_info")
                    val auth = userInfo?.optInt("auth", 0) ?: jsonObject.optInt("auth", 0)

                    if (auth == 1) {
                        prefs.edit().putLong("sync_telemetry_timestamp", System.currentTimeMillis()).apply()
                    } else {
                        expulsarUsuario(context, prefs)
                    }
                } else {
                    comprobarTiempoOffline(context, prefs, maxOfflineTimeMillis)
                }
            } catch (e: Exception) {
                comprobarTiempoOffline(context, prefs, maxOfflineTimeMillis)
            }
        }
    }

    private fun comprobarTiempoOffline(context: Context, prefs: SharedPreferences, maxTime: Long) {
        val lastSync = prefs.getLong("sync_telemetry_timestamp", 0L)
        val currentTime = System.currentTimeMillis()

        if (lastSync == 0L) {
            prefs.edit().putLong("sync_telemetry_timestamp", currentTime).apply()
        } else {
            val timeOffline = currentTime - lastSync
            if (timeOffline > maxTime) {
                expulsarUsuario(context, prefs)
            }
        }
    }

    private fun expulsarUsuario(context: Context, prefs: SharedPreferences) {
        prefs.edit()
            .putBoolean("isLoggedIn", false)
            .putLong("sync_telemetry_timestamp", 0L)
            .apply()

        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    // =========================================================================================
    // LÓGICA DE SEGURIDAD 1 - VERIFICADOR DE HASH DE FIRMA (ANTICRACK)
    // =========================================================================================
    private fun verificarFirma(context: Context) {
        if (BuildConfig.DEBUG) {
            val currentHash = getAppSignatureHash(context)
            Log.e("FIRMA_APP_DEV", "Modo Dev Activo. Evadiendo Anti-Crack. Tu hash temporal es: $currentHash")
            return
        }

        try {
            val currentHash = getAppSignatureHash(context)
            val expectedHash = decryptString(VALID_SIGNATURE_HASH)

            if (currentHash != null && expectedHash.isNotEmpty()) {
                if (currentHash != expectedHash) {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(0)
                }
            }
        } catch (e: Exception) {
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(0)
        }
    }

    @Suppress("DEPRECATION")
    private fun getAppSignatureHash(context: Context): String? {
        try {
            val packageName = context.packageName
            val packageManager = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                info.signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(signatures[0].toByteArray())
                return Base64.encodeToString(md.digest(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun decryptString(encryptedHex: String): String {
        return try {
            val bytes = ByteArray(encryptedHex.length / 2)
            for (i in bytes.indices) {
                bytes[i] = encryptedHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val base64Encoded = String(bytes)
            val xoredBytes = Base64.decode(base64Encoded, Base64.DEFAULT)

            val key = "KURO".toByteArray()
            val decrypted = ByteArray(xoredBytes.size)
            for (i in xoredBytes.indices) {
                decrypted[i] = (xoredBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            String(decrypted)
        } catch (e: Exception) {
            ""
        }
    }
    // =========================================================================================

    override fun onTerminate() {
        super.onTerminate()
        Logger.w("Terminate", "Checking")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient()
                        },
                    ),
                )
            }.diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCache(
                DiskCache
                    .Builder()
                    .directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "image_cache")
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build(),
            ).crossfade(true)
            .build()
}