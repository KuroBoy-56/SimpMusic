package com.maxrave.simpmusic

import android.Manifest
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eygraber.uri.toKmpUriOrNull
import com.maxrave.common.FIRST_TIME_MIGRATION
import com.maxrave.common.SELECTED_LANGUAGE
import com.maxrave.common.STATUS_DONE
import com.maxrave.common.SUPPORTED_LANGUAGE
import com.maxrave.common.SUPPORTED_LOCATION
import com.maxrave.domain.data.model.intent.GenericIntent
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.ToastType
import com.maxrave.media3.di.setServiceActivitySession
import com.maxrave.simpmusic.di.viewModelModule
import com.maxrave.simpmusic.service.rss.RssFeedNotifyWork
import com.maxrave.simpmusic.service.test.notification.NotifyWork
import com.maxrave.simpmusic.utils.ComposeResUtils
import com.maxrave.simpmusic.service.backup.MediaAudioConfig
import com.maxrave.simpmusic.viewModel.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.android.ext.android.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import org.simpmusic.crashlytics.pushPlayerError
import pub.devrel.easypermissions.EasyPermissions
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    val viewModel: SharedViewModel by inject()
    val mediaPlayerHandler by inject<MediaPlayerHandler>()
    val dataStoreManager: DataStoreManager by inject()

    private var mBound = false
    private var shouldUnbind = false
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                setServiceActivitySession(this@MainActivity, MainActivity::class.java, service)
                mBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mBound = false
            }
        }

    private fun getCustomMacAddress(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "1A2B3C4D5E6F7A8B"
        var processed = androidId.trimStart('0')
        if (processed.isEmpty()) {
            processed = "1A2B3C4D5E6F7A8B"
        }
        processed = processed.padEnd(16, 'A')
        processed = processed.substring(0, 16).uppercase()
        return processed.chunked(2).joinToString(":")
    }

    override fun onStart() {
        super.onStart()
        startMusicService()
    }

    override fun onStop() {
        super.onStop()
        if (shouldUnbind) {
            unbindService(serviceConnection)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.setIntent(
            GenericIntent(
                action = intent.action,
                data = (intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri())?.toKmpUriOrNull(),
                type = intent.type,
            ),
        )
    }

    @ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!MediaAudioConfig.checkAudioRoute(this)) {
            finishAffinity()
            exitProcess(0)
            return
        }

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("isLoggedIn", false)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        loadKoinModules(
            module {
                single<AppCompatActivity> { this@MainActivity }
            },
        )
        unloadKoinModules(viewModelModule)
        loadKoinModules(viewModelModule)

        if (viewModel.recreateActivity.value || viewModel.isServiceRunning) {
            viewModel.activityRecreateDone()
        } else {
            startMusicService()
        }

        val data = (intent?.data ?: intent?.getStringExtra(Intent.EXTRA_TEXT)?.toUri())?.toKmpUriOrNull()
        if (data != null) {
            viewModel.setIntent(
                GenericIntent(
                    action = intent.action,
                    data = data,
                    type = intent.type,
                ),
            )
        }

        if (getString(FIRST_TIME_MIGRATION) != STATUS_DONE) {
            if (SUPPORTED_LANGUAGE.codes.contains(Locale.getDefault().toLanguageTag())) {
                putString(SELECTED_LANGUAGE, Locale.getDefault().toLanguageTag())
                if (SUPPORTED_LOCATION.items.contains(Locale.getDefault().country)) {
                    putString("location", Locale.getDefault().country)
                } else {
                    putString("location", "US")
                }
            } else {
                putString(SELECTED_LANGUAGE, "en-US")
            }
            getString(SELECTED_LANGUAGE)?.let {
                val localeList = LocaleListCompat.forLanguageTags(it)
                AppCompatDelegate.setApplicationLocales(localeList)
                putString(FIRST_TIME_MIGRATION, STATUS_DONE)
            }
        }
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() !=
            getString(
                SELECTED_LANGUAGE,
            )
        ) {
            putString(SELECTED_LANGUAGE, AppCompatDelegate.getApplicationLocales().toLanguageTags())
        }

        enableEdgeToEdge(
            navigationBarStyle =
                SystemBarStyle.dark(
                    scrim = Color.Transparent.toArgb(),
                ),
            statusBarStyle =
                SystemBarStyle.dark(
                    scrim = Color.Transparent.toArgb(),
                ),
        )

        viewModel.checkIsRestoring()

        val request =
            PeriodicWorkRequestBuilder<NotifyWork>(
                12L,
                TimeUnit.HOURS,
            ).addTag("Worker Test")
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "Artist Worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        lifecycleScope.launch {
            dataStoreManager.blogNotificationEnabled.collect { enabled ->
                if (enabled == DataStoreManager.TRUE) {
                    val rssRequest =
                        PeriodicWorkRequestBuilder<RssFeedNotifyWork>(
                            24L,
                            TimeUnit.HOURS,
                        ).addTag("Blog RSS Worker")
                            .setConstraints(
                                Constraints
                                    .Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build(),
                            ).build()
                    WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                        "Blog RSS Worker",
                        ExistingPeriodicWorkPolicy.KEEP,
                        rssRequest,
                    )
                } else {
                    WorkManager.getInstance(this@MainActivity).cancelUniqueWork("Blog RSS Worker")
                }
            }
        }

        if (!EasyPermissions.hasPermissions(this, Manifest.permission.POST_NOTIFICATIONS)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val doNotAsk = getString("notification_permission_do_not_ask")
                if (doNotAsk != "true") {
                    val wasAsked = getString("notification_permission_asked")
                    if (wasAsked != "true") {
                        EasyPermissions.requestPermissions(
                            this,
                            runBlocking { ComposeResUtils.getResString(ComposeResUtils.StringType.NOTIFICATION_REQUEST) },
                            1,
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                        putString("notification_permission_asked", "true")
                    } else {
                        viewModel.showNotificationPermissionDialog()
                    }
                }
            }
        }
        viewModel.getLocation()

        verificarDiasRestantes()

        setContent {
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val user = prefs.getString("saved_user", "") ?: ""
                    val pass = prefs.getString("saved_pass", "") ?: ""

                    try {
                        val deviceMac = getCustomMacAddress()
                        val userEnc = URLEncoder.encode(user, "UTF-8")
                        val passEnc = URLEncoder.encode(pass, "UTF-8")
                        val macEnc = URLEncoder.encode(deviceMac, "UTF-8")

                        val encryptedBytes = intArrayOf(
                            109, 121, 121, 117, 120, 63, 52, 52, 108, 102, 119, 106, 123, 126, 115, 117, 102, 115, 106, 113,
                            120, 51, 113, 102, 121, 114, 117, 125, 51, 104, 116, 114, 52, 126, 116, 122, 121, 122, 103, 106,
                            52, 117, 102, 115, 106, 113, 52, 102, 117, 110, 52, 117, 113, 102, 126, 106, 119, 100, 102, 117,
                            110, 51, 117, 109, 117
                        )
                        val urlBuilder = java.lang.StringBuilder()
                        for (byteVal in encryptedBytes) {
                            urlBuilder.append((byteVal - 5).toChar())
                        }

                        val urlReal = urlBuilder.toString()
                        val urlString = "$urlReal?username=$userEnc&password=$passEnc&mac=$macEnc"

                        val url = URL(urlString)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 8000
                        connection.readTimeout = 8000

                        val responseCode = connection.responseCode

                        if (responseCode == 200) {
                            val response = connection.inputStream.bufferedReader().use { it.readText() }
                            val jsonObject = JSONObject(response)
                            val userInfo = jsonObject.optJSONObject("user_info")

                            val auth = userInfo?.optInt("auth", 0) ?: 0
                            val status = userInfo?.optString("status", "Active") ?: "Active"

                            val isInvalidStatus = status.equals("Expired", ignoreCase = true) ||
                                status.equals("Banned", ignoreCase = true) ||
                                status.equals("Disabled", ignoreCase = true)

                            if (auth == 0 || isInvalidStatus) {
                                withContext(Dispatchers.Main) {
                                    prefs.edit().clear().apply()
                                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                                    finish()
                                }
                            }
                        } else if (responseCode == 401 || responseCode == 403) {
                            withContext(Dispatchers.Main) {
                                prefs.edit().clear().apply()
                                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                                finish()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            App(viewModel)
        }
    }

    private fun verificarDiasRestantes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val usuarioGuardado = prefs.getString("saved_user", "") ?: ""

                if (usuarioGuardado.isEmpty()) return@launch

                val userEnc = URLEncoder.encode(usuarioGuardado, "UTF-8")

                val encryptedBytes = intArrayOf(109, 121, 121, 117, 120, 63, 52, 52, 108, 102, 119, 106, 123, 126, 115, 117, 102, 115, 106, 113, 120, 51, 113, 102, 121, 114, 117, 125, 51, 104, 116, 114, 52, 126, 116, 122, 121, 122, 103, 106, 52, 117, 102, 115, 106, 113, 52, 102, 117, 110, 52, 104, 109, 106, 104, 112, 100, 105, 102, 126, 120, 51, 117, 109, 117)
                val urlBuilder = java.lang.StringBuilder()
                for (byteVal in encryptedBytes) {
                    urlBuilder.append((byteVal - 5).toChar())
                }

                val urlString = "${urlBuilder.toString()}?username=$userEnc"

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)

                    if (jsonObject.optString("status") == "success") {
                        val diasRestantes = jsonObject.optInt("days_left", -1)

                        if (diasRestantes in 0..3) {
                            val titulo = jsonObject.optString("alert_title", "¡AVISO!")
                            val mensaje = jsonObject.optString("alert_msg", "Tu suscripción está por vencer.")

                            withContext(Dispatchers.Main) {
                                if (!isFinishing && !isDestroyed) {
                                    mostrarAlertaDias(titulo, mensaje)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun mostrarAlertaDias(titulo: String, mensaje: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 80, 60, 80)
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#191C24"))
                cornerRadius = 60f
                setStroke(5, android.graphics.Color.parseColor("#ff3e3e"))
            }
        }

        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_dialog_alert)
            setColorFilter(android.graphics.Color.parseColor("#ff3e3e"))
            layoutParams = LinearLayout.LayoutParams(160, 160).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 50
            }
        }

        val titleView = TextView(this).apply {
            text = titulo
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 30
            }
        }

        val messageView = TextView(this).apply {
            text = mensaje
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 60
            }
        }

        val button = Button(this).apply {
            text = "ENTENDIDO"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#ff3e3e"))
                cornerRadius = 25f
            }
            layoutParams = LinearLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.6).toInt(),
                130
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setOnClickListener {
                dialog.dismiss()
            }
        }

        layout.addView(iconView)
        layout.addView(titleView)
        layout.addView(messageView)
        layout.addView(button)

        dialog.setContentView(layout)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
        }

        dialog.setCancelable(false)
        dialog.show()
    }

    override fun onDestroy() {
        val shouldStopMusicService = viewModel.shouldStopMusicService()
        if (shouldStopMusicService && shouldUnbind && isFinishing) {
            viewModel.isServiceRunning = false
        }
        unloadKoinModules(viewModelModule)
        super.onDestroy()
    }

    override fun onRestart() {
        super.onRestart()
        viewModel.activityRecreate()
    }

    private fun startMusicService() {
        com.maxrave.media3.di
            .startService(this@MainActivity, serviceConnection)
        mediaPlayerHandler.pushPlayerError = { it ->
            pushPlayerError(it)
        }
        mediaPlayerHandler.showToast = { type ->
            viewModel.makeToast(
                when (type) {
                    is ToastType.ExplicitContent -> {
                        runBlocking { ComposeResUtils.getResString(ComposeResUtils.StringType.EXPLICIT_CONTENT_BLOCKED) }
                    }

                    is ToastType.PlayerError -> {
                        runBlocking { ComposeResUtils.getResString(ComposeResUtils.StringType.TIME_OUT_ERROR, type.error) }
                    }
                },
            )
        }
        viewModel.isServiceRunning = true
        shouldUnbind = true
    }

    private fun putString(
        key: String,
        value: String,
    ) {
        viewModel.putString(key, value)
    }

    private fun getString(key: String): String? = viewModel.getString(key)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.activityRecreate()
    }
}