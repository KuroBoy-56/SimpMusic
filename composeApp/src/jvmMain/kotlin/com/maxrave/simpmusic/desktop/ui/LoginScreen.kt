package com.maxrave.simpmusic.desktop.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxrave.simpmusic.desktop.auth.AuthManager
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.logo

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val isNightMode = isSystemInDarkTheme()
    val bgColor = if (isNightMode) Color(0xFF0F0F0F) else Color(0xFFF5F5F5)
    val cardColor = if (isNightMode) Color(0xFF1A1A1A) else Color.White
    val textColorPrimary = if (isNightMode) Color.White else Color.Black
    val textColorSecondary = if (isNightMode) Color(0xFFAAAAAA) else Color(0xFF606060)
    val inputBgColor = if (isNightMode) Color(0xFF272727) else Color(0xFFF0F0F0)
    val inputHintColor = if (isNightMode) Color(0xFF757575) else Color(0xFF9E9E9E)
    val brandRed = Color(0xFFFF0000)
    val brandRedDark = Color(0xFFB30000)

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(cardColor)
                .padding(horizontal = 35.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                
                Image(
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 10.dp)
                )

                Text(
                    text = "YouTube Music",
                    fontSize = 24.sp,
                    color = textColorPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Tu música, tu estilo, en cualquier momento. \uD83C\uDFA7\uD83D\uDD25",
                fontSize = 15.sp,
                color = textColorSecondary,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 30.dp)
            )

            CustomTextField(
                value = username,
                onValueChange = { username = it },
                hint = "usuario",
                textColorPrimary = textColorPrimary,
                inputBgColor = inputBgColor,
                inputHintColor = inputHintColor,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            CustomTextField(
                value = password,
                onValueChange = { password = it },
                hint = "contraseña",
                isPassword = true,
                textColorPrimary = textColorPrimary,
                inputBgColor = inputBgColor,
                inputHintColor = inputHintColor,
                modifier = Modifier.padding(bottom = 25.dp)
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF5252),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 15.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isLoading) brandRedDark else brandRed)
                    .clickable(enabled = !isLoading) {
                        if (username.isBlank() || password.isBlank()) {
                            errorMessage = "Por favor, complete los campos"
                            return@clickable
                        }
                        errorMessage = ""
                        isLoading = true

                        coroutineScope.launch {
                            val result = AuthManager.login(username.trim(), password.trim())
                            if (result.isSuccess) {
                                onLoginSuccess()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "Error"
                                isLoading = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLoading) "CARGANDO..." else "ENTRAR",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Text(
                text = "V1.5.0g",
                fontSize = 12.sp,
                color = textColorSecondary,
                modifier = Modifier.padding(top = 25.dp)
            )
        }
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    isPassword: Boolean = false,
    textColorPrimary: Color,
    inputBgColor: Color,
    inputHintColor: Color,
    modifier: Modifier = Modifier
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = textColorPrimary, fontSize = 16.sp),
        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        cursorBrush = SolidColor(textColorPrimary),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(inputBgColor)
            .padding(horizontal = 25.dp, vertical = 20.dp)
    ) { innerTextField ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(text = hint, color = inputHintColor, fontSize = 16.sp)
                }
                innerTextField()
            }
            if (isPassword) {
                Spacer(modifier = Modifier.width(8.dp))
                EyeIconCompose(
                    isOpen = isPasswordVisible,
                    color = textColorPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { isPasswordVisible = !isPasswordVisible }
                )
            }
        }
    }
}

@Composable
fun EyeIconCompose(isOpen: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        val stroke = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val path = Path().apply {
            moveTo(w * 0.15f, cy)
            quadraticBezierTo(cx, h * 0.15f, w * 0.85f, cy)
            quadraticBezierTo(cx, h * 0.85f, w * 0.15f, cy)
        }
        drawPath(path = path, color = color, style = stroke)

        drawCircle(
            color = color,
            radius = w * 0.15f,
            center = Offset(cx, cy),
            style = stroke
        )

        if (!isOpen) {
            drawLine(
                color = color,
                start = Offset(w * 0.15f, h * 0.15f),
                end = Offset(w * 0.85f, h * 0.85f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
    }
}
