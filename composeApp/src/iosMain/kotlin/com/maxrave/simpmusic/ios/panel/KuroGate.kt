@file:OptIn(ExperimentalForeignApi::class)

package com.maxrave.simpmusic.ios.panel

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.maxrave.simpmusic.BuildKonfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/**
 * Shows the panel login until an account is signed in, then [content] — Android's
 * LoginActivity → MainActivity hand-off. While the app is open it re-validates the account,
 * warns when the subscription has three days or fewer left, and offers updates; a rejected
 * account drops back to the login screen.
 */
@Composable
fun KuroGate(content: @Composable () -> Unit) {
    val loggedIn by KuroPanel.loggedIn.collectAsState()
    if (!loggedIn) {
        KuroLoginScreen()
    } else {
        content()
        KuroSessionChecks()
    }
}

@Composable
private fun KuroSessionChecks() {
    var daysAlert by remember { mutableStateOf<PanelAlert?>(null) }
    var update by remember { mutableStateOf<PanelUpdate?>(null) }

    LaunchedEffect(Unit) {
        // The UI is up: start-up finished, so a later death is no longer a failed launch.
        delay(5000)
        KuroCrashReporter.launchCompleted()
    }
    LaunchedEffect(Unit) { KuroPanel.revalidate() }
    LaunchedEffect(Unit) { daysAlert = KuroPanel.daysLeftAlert() }
    LaunchedEffect(Unit) {
        delay(600)
        update = KuroPanel.updateInfo()
    }

    daysAlert?.let { alert ->
        AlertDialog(
            onDismissRequest = { daysAlert = null },
            title = { Text(alert.title, fontWeight = FontWeight.Bold) },
            text = { Text(alert.message) },
            confirmButton = { TextButton(onClick = { daysAlert = null }) { Text("ENTENDIDO") } },
        )
    }
    update?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!info.mandatory) update = null },
            properties = DialogProperties(dismissOnBackPress = !info.mandatory, dismissOnClickOutside = !info.mandatory),
            title = {
                Text(
                    if (info.mandatory) "Actualización obligatoria" else "Nueva versión ${info.versionName}".trim(),
                    fontWeight = FontWeight.Bold,
                    color = if (info.mandatory) Color(0xFFE50914) else Color.Unspecified,
                )
            },
            text = { Text(info.notes) },
            confirmButton = {
                TextButton(onClick = {
                    NSURL.URLWithString(info.url)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
                    if (!info.mandatory) update = null
                }) { Text("ACTUALIZAR") }
            },
            dismissButton =
                if (info.mandatory) {
                    null
                } else {
                    { TextButton(onClick = { update = null }) { Text("MÁS TARDE") } }
                },
        )
    }
}

@Composable
private fun KuroLoginScreen() {
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color(0xFF0F0F0F) else Color(0xFFF5F5F5)
    val card = if (dark) Color(0xFF1A1A1A) else Color.White
    val textPrimary = if (dark) Color.White else Color.Black
    val textSecondary = if (dark) Color(0xFFAAAAAA) else Color(0xFF606060)
    val inputBg = if (dark) Color(0xFF272727) else Color(0xFFF0F0F0)
    val hint = if (dark) Color(0xFF757575) else Color(0xFF9E9E9E)
    val brandRed = Color(0xFFFF0000)

    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    val logo = remember { launchLogo() }

    val cardAlpha by animateFloatAsState(if (success) 0f else 1f, tween(300))
    val logoScale by animateFloatAsState(if (success) 3.2f else 1f, tween(600, easing = FastOutSlowInEasing))

    LaunchedEffect(success) {
        if (success) {
            delay(1800)
            KuroPanel.completeLogin()
        }
    }
    LaunchedEffect(Unit) {
        // Nothing heavier than this screen runs before sign-in, so start-up is done.
        delay(3000)
        KuroCrashReporter.launchCompleted()
    }

    Box(
        Modifier.fillMaxSize().background(bg).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        if (success && logo != null) {
            Image(logo, contentDescription = null, modifier = Modifier.size(80.dp).scale(logoScale))
        }
        Column(
            Modifier
                .alpha(cardAlpha)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .background(card, RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (logo != null && !success) {
                    Image(logo, contentDescription = null, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.size(10.dp))
                }
                Text("KuroMusic", color = textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Tu música, tu estilo, en cualquier momento. 🎧🔥",
                color = textSecondary,
                fontSize = 15.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            val fieldColors =
                TextFieldDefaults.colors(
                    focusedContainerColor = inputBg,
                    unfocusedContainerColor = inputBg,
                    disabledContainerColor = inputBg,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedPlaceholderColor = hint,
                    unfocusedPlaceholderColor = hint,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = brandRed,
                )
            TextField(
                value = user,
                onValueChange = { user = it },
                placeholder = { Text("usuario") },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            TextField(
                value = pass,
                onValueChange = { pass = it },
                placeholder = { Text("contraseña") },
                singleLine = true,
                enabled = !loading,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = {
                    EyeIcon(
                        open = showPass,
                        color = textPrimary,
                        modifier =
                            Modifier.size(40.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showPass = !showPass },
                    )
                },
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            error?.let {
                Text(it, color = brandRed, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp))
            }
            Button(
                onClick = {
                    val u = user.trim()
                    val p = pass.trim()
                    if (u.isEmpty() || p.isEmpty()) {
                        error = "Por favor, complete los campos"
                        return@Button
                    }
                    error = null
                    loading = true
                    scope.launch {
                        val result = KuroPanel.login(u, p)
                        loading = false
                        if (result == "SUCCESS") success = true else error = result
                    }
                },
                enabled = !loading && !success,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = brandRed,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF8B0000),
                        disabledContentColor = Color.White,
                    ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (loading) "CARGANDO..." else "ENTRAR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("V${BuildKonfig.versionName}", color = textSecondary, fontSize = 12.sp)
        }
    }
}

/** Android's `EyeDrawable`: an eye outline, struck through while the password is hidden. */
@Composable
private fun EyeIcon(
    open: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.padding(8.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path =
            Path().apply {
                moveTo(w * 0.15f, h / 2)
                quadraticTo(w / 2, h * 0.15f, w * 0.85f, h / 2)
                quadraticTo(w / 2, h * 0.85f, w * 0.15f, h / 2)
            }
        drawPath(path, color, style = stroke)
        drawCircle(color, radius = w * 0.15f, style = stroke)
        if (!open) drawLine(color, Offset(w * 0.15f, h * 0.15f), Offset(w * 0.85f, h * 0.85f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

/** The launch-screen emblem from the asset catalog, so the login shows the same logo. */
private fun launchLogo(): ImageBitmap? =
    runCatching {
        val image = UIImage.imageNamed("LaunchLogo") ?: return null
        val data = UIImagePNGRepresentation(image) ?: return null
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isEmpty()) return null
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
