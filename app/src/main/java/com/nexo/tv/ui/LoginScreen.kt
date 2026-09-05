package com.nexo.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.tv.R
import com.nexo.tv.Session
import com.nexo.tv.data.CodeAuth
import com.nexo.tv.data.CodeAuthResult
import kotlinx.coroutines.launch

private val Ember = Color(0xFFFF6A1A)
private val EmberDeep = Color(0xFFDE5B17)
private val Ink = Color(0xFF07060A)
private val InkWarm = Color(0xFF1A0E08)
private val Panel = Color(0x88140E0C)

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(Session.accessCode) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }

    val initialFocus = remember { FocusRequester() }

    fun submit() {
        if (busy) return
        val clean = code.trim().filter { it.isDigit() }
        if (clean.length != 6) {
            error = "Ingresa los 6 dígitos del código"
            return
        }
        busy = true
        error = null
        scope.launch {
            val res = CodeAuth.validateAndLogin(clean, context)
            busy = false
            when (res) {
                is CodeAuthResult.Success -> onSuccess()
                is CodeAuthResult.Error -> error = res.message
            }
        }
    }

    fun appendDigit(d: String) {
        if (code.length < 6) {
            code += d
            error = null
            if (code.length == 6) {
                submit()
            }
        }
    }

    fun deleteDigit() {
        if (code.isNotEmpty()) {
            code = code.dropLast(1)
            error = null
        }
    }

    LaunchedEffect(Unit) {
        appeared = true
        try {
            initialFocus.requestFocus()
        } catch (_: Throwable) {}
    }

    val formAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(600),
        label = "formAlpha"
    )
    val formLift by animateFloatAsState(
        targetValue = if (appeared) 0f else 20f,
        animationSpec = tween(600),
        label = "formLift"
    )

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_0, AndroidKeyEvent.KEYCODE_NUMPAD_0 -> { appendDigit("0"); true }
                        AndroidKeyEvent.KEYCODE_1, AndroidKeyEvent.KEYCODE_NUMPAD_1 -> { appendDigit("1"); true }
                        AndroidKeyEvent.KEYCODE_2, AndroidKeyEvent.KEYCODE_NUMPAD_2 -> { appendDigit("2"); true }
                        AndroidKeyEvent.KEYCODE_3, AndroidKeyEvent.KEYCODE_NUMPAD_3 -> { appendDigit("3"); true }
                        AndroidKeyEvent.KEYCODE_4, AndroidKeyEvent.KEYCODE_NUMPAD_4 -> { appendDigit("4"); true }
                        AndroidKeyEvent.KEYCODE_5, AndroidKeyEvent.KEYCODE_NUMPAD_5 -> { appendDigit("5"); true }
                        AndroidKeyEvent.KEYCODE_6, AndroidKeyEvent.KEYCODE_NUMPAD_6 -> { appendDigit("6"); true }
                        AndroidKeyEvent.KEYCODE_7, AndroidKeyEvent.KEYCODE_NUMPAD_7 -> { appendDigit("7"); true }
                        AndroidKeyEvent.KEYCODE_8, AndroidKeyEvent.KEYCODE_NUMPAD_8 -> { appendDigit("8"); true }
                        AndroidKeyEvent.KEYCODE_9, AndroidKeyEvent.KEYCODE_NUMPAD_9 -> { appendDigit("9"); true }
                        AndroidKeyEvent.KEYCODE_DEL -> { deleteDigit(); true }
                        AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (code.length == 6) { submit(); true } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        LoginBackdrop()

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 20.dp)
                .alpha(formAlpha)
                .padding(top = formLift.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header NEXO
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_nexo_logo),
                    contentDescription = "NEXO",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "NEXO",
                    color = Ember,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 6.sp
                )
            }
            Text(
                text = "Tu TV, al instante",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(18.dp))

            // Tarjeta de Código
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Panel)
                    .border(1.dp, Color(0x33FF6A1A), RoundedCornerShape(22.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Conectar por código",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ingresa tu código de 6 dígitos",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(16.dp))

                // Casillas de los 6 dígitos
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 6) {
                        val digit = code.getOrNull(i)?.toString().orEmpty()
                        val isCurrent = (code.length == i)
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (digit.isNotEmpty()) Color(0x33FF6A1A) else Color(0x22111111))
                                .border(
                                    width = if (isCurrent) 2.5.dp else if (digit.isNotEmpty()) 2.dp else 1.dp,
                                    color = if (isCurrent) Ember else if (digit.isNotEmpty()) Ember.copy(alpha = 0.85f) else Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (digit.isNotEmpty()) {
                                Text(
                                    text = digit,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                            } else if (isCurrent) {
                                Box(
                                    Modifier
                                        .width(14.dp)
                                        .height(2.5.dp)
                                        .background(Ember, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }
                }

                // Mensajes de error o carga
                if (busy) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Ember,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Verificando código…",
                            color = Ember,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        error!!,
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Teclado numérico en pantalla para control remoto
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Fila 1: 1, 2, 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumPadKey("1", Modifier.weight(1f), focusRequester = initialFocus) { appendDigit("1") }
                        NumPadKey("2", Modifier.weight(1f)) { appendDigit("2") }
                        NumPadKey("3", Modifier.weight(1f)) { appendDigit("3") }
                    }
                    // Fila 2: 4, 5, 6
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumPadKey("4", Modifier.weight(1f)) { appendDigit("4") }
                        NumPadKey("5", Modifier.weight(1f)) { appendDigit("5") }
                        NumPadKey("6", Modifier.weight(1f)) { appendDigit("6") }
                    }
                    // Fila 3: 7, 8, 9
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumPadKey("7", Modifier.weight(1f)) { appendDigit("7") }
                        NumPadKey("8", Modifier.weight(1f)) { appendDigit("8") }
                        NumPadKey("9", Modifier.weight(1f)) { appendDigit("9") }
                    }
                    // Fila 4: Borrar, 0, Conectar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumPadKey(
                            label = "⌫",
                            modifier = Modifier.weight(1f),
                            bg = Color(0x28FFFFFF),
                            fontSize = 18.sp
                        ) { deleteDigit() }

                        NumPadKey("0", Modifier.weight(1f)) { appendDigit("0") }

                        NumPadKey(
                            label = "Conectar",
                            modifier = Modifier.weight(1.3f),
                            bg = if (code.length == 6) EmberDeep else Color(0x33FF6A1A),
                            fontSize = 13.sp
                        ) { submit() }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumPadKey(
    label: String,
    modifier: Modifier = Modifier,
    bg: Color = Color(0x33222228),
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(44.dp)
            .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.05f)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun LoginBackdrop() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val drift by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val sweep by pulse.animateFloat(
        initialValue = -0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(InkWarm, Ink, Color(0xFF040308)),
                startY = 0f,
                endY = h
            )
        )

        // Ember glow — esquina superior derecha
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Ember.copy(alpha = 0.42f * glow),
                    EmberDeep.copy(alpha = 0.18f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.82f + drift * 0.04f), h * 0.18f),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * (0.82f + drift * 0.04f), h * 0.18f)
        )

        // Glow cálido inferior izquierdo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF8A3D).copy(alpha = 0.28f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.12f - drift * 0.03f), h * 0.78f),
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = Offset(w * (0.12f - drift * 0.03f), h * 0.78f)
        )

        // Franja diagonal de luz
        val beam = Path().apply {
            val x = w * sweep
            moveTo(x - w * 0.08f, 0f)
            lineTo(x + w * 0.02f, 0f)
            lineTo(x + w * 0.18f, h)
            lineTo(x + w * 0.08f, h)
            close()
        }
        drawPath(
            path = beam,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Ember.copy(alpha = 0.07f * glow),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        // Círculos ornamentales tenues
        drawCircle(
            color = Ember.copy(alpha = 0.06f * glow),
            radius = w * 0.38f,
            center = Offset(w * 0.85f, h * 0.15f),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(
            color = Ember.copy(alpha = 0.035f * glow),
            radius = w * 0.46f,
            center = Offset(w * 0.85f, h * 0.15f),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
