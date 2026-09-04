package com.nexo.tv

import android.os.Bundle
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexo.tv.player.StreamBridge
import com.nexo.tv.player.VlcEngine
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

class VodActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        keepAwakeWhileVisible()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        StreamBridge.start()
        val engine = VlcEngine(this)

        setContent {
            var showHud by remember { mutableStateOf(true) }
            var playing by remember { mutableStateOf(true) }
            var position by remember { mutableLongStateOf(0L) }
            var duration by remember { mutableLongStateOf(0L) }
            var toast by remember { mutableStateOf<String?>(null) }
            var hudTick by remember { mutableStateOf(0) }
            val rootFocus = remember { FocusRequester() }
            val playFocus = remember { FocusRequester() }

            fun bumpHud() {
                showHud = true
                hudTick++
            }

            DisposableEffect(engine) {
                engine.onPlaying = {
                    playing = true
                    duration = engine.lengthMs()
                }
                // HTTP directo: VLC puede seek con Range. HTTPS va por StreamBridge.
                if (url.isNotBlank()) engine.playVod(StreamBridge.maybeWrap(url))
                onDispose { engine.release() }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    position = engine.timeMs()
                    val len = engine.lengthMs()
                    if (len > 0) duration = len
                    playing = engine.isPlaying
                    delay(500)
                }
            }

            LaunchedEffect(hudTick) {
                if (!showHud) return@LaunchedEffect
                delay(8000)
                showHud = false
            }

            LaunchedEffect(toast) {
                if (toast == null) return@LaunchedEffect
                delay(1800)
                toast = null
            }

            LaunchedEffect(showHud) {
                delay(60)
                if (showHud) {
                    runCatching { playFocus.requestFocus() }
                } else {
                    runCatching { rootFocus.requestFocus() }
                }
            }

            BackHandler {
                if (showHud) showHud = false else finish()
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusRequester(rootFocus)
                    .focusProperties { canFocus = !showHud }
                    .focusable()
                    // Preview: solo intercepta teclas cuando el HUD está oculto
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (e.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                        val code = e.nativeKeyEvent.keyCode
                        if (showHud) {
                            // Con HUD visible: no robar DPAD (para moverse entre botones)
                            // Solo teclas multimedia globales
                            when (code) {
                                AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    engine.togglePause(); playing = engine.isPlaying; bumpHud(); true
                                }
                                AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    engine.resume(); playing = true; bumpHud(); true
                                }
                                AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    engine.pause(); playing = false; bumpHud(); true
                                }
                                AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                    engine.seekBy(10_000); bumpHud(); true
                                }
                                AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                                    engine.seekBy(-10_000); bumpHud(); true
                                }
                                else -> false
                            }
                        } else {
                            when (code) {
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                AndroidKeyEvent.KEYCODE_ENTER,
                                AndroidKeyEvent.KEYCODE_DPAD_UP,
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                                AndroidKeyEvent.KEYCODE_INFO,
                                AndroidKeyEvent.KEYCODE_MENU,
                                AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    bumpHud(); true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                                AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                                    engine.seekBy(-10_000); bumpHud(); true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                                AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                    engine.seekBy(10_000); bumpHud(); true
                                }
                                else -> false
                            }
                        }
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        VLCVideoLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            keepScreenOn = true
                            isFocusable = false
                            isFocusableInTouchMode = false
                            engine.attach(this)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusProperties { canFocus = false }
                )

                AnimatedVisibility(
                    visible = showHud,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .focusGroup()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xEE000000))
                                )
                            )
                            .padding(horizontal = 28.dp, vertical = 22.dp)
                    ) {
                        val progress = if (duration > 0) {
                            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFFDE5B17),
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(position), color = Color.White, fontSize = 14.sp)
                            Text(formatTime(duration), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .focusGroup(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HudButton(Icons.Filled.Replay10, "−10s") {
                                engine.seekBy(-10_000); bumpHud()
                            }
                            HudButton(
                                icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                label = if (playing) "Pausa" else "Play",
                                focusRequester = playFocus
                            ) {
                                engine.togglePause()
                                playing = engine.isPlaying
                                bumpHud()
                            }
                            HudButton(Icons.Filled.Forward10, "+10s") {
                                engine.seekBy(10_000); bumpHud()
                            }
                            HudButton(Icons.Filled.Translate, "Audio") {
                                toast = engine.cycleAudioTrack() ?: "Sin pistas de audio"
                                bumpHud()
                            }
                            HudButton(Icons.Filled.Subtitles, "Subs") {
                                toast = engine.cycleSubtitleTrack()
                                bumpHud()
                            }
                            HudButton(Icons.Filled.AspectRatio, "Pantalla") {
                                toast = engine.cycleAspectMode()
                                bumpHud()
                            }
                        }
                    }
                }

                toast?.let { msg ->
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }
}

@Composable
private fun HudButton(
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged {
                    isFocused = it.isFocused
                }
                .clip(CircleShape)
                .background(if (isFocused) Color(0xFFDE5B17) else Color.White.copy(alpha = 0.18f))
                .clickable(onClick = onClick)
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (isFocused) Color(0xFFFF6A1A) else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
