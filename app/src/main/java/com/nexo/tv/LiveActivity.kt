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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.nexo.tv.data.LiveChannel
import com.nexo.tv.data.XtreamClient
import com.nexo.tv.player.StreamBridge
import com.nexo.tv.player.VlcEngine
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

class LiveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent.getStringExtra(EXTRA_USER)?.let { Session.username = it }
        intent.getStringExtra(EXTRA_PASS)?.let { Session.password = it }
        intent.getStringExtra(EXTRA_SERVER)?.let { if (it.isNotBlank()) Session.server = it }
        StreamBridge.start()
        val engine = VlcEngine(this)
        setContent {
            var channels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
            var index by remember { mutableIntStateOf(0) }
            var loading by remember { mutableStateOf(true) }
            var status by remember { mutableStateOf("Cargando…") }
            var showBanner by remember { mutableStateOf(false) }
            var bannerTick by remember { mutableIntStateOf(0) }
            val focus = remember { FocusRequester() }

            DisposableEffect(engine) {
                engine.onPlaying = { status = "Reproduciendo" }
                engine.onError = { status = "Error de reproducción" }
                onDispose { engine.release() }
            }

            fun revealBanner() {
                showBanner = true
                bannerTick++
            }

            fun playChannel(ch: LiveChannel, instant: Boolean) {
                val remote = XtreamClient.liveUrl(ch.id)
                val toPlay = if (remote.startsWith("http://", true)) remote else StreamBridge.wrap(remote)
                android.util.Log.i("LiveActivity", "play $remote -> $toPlay")
                status = ch.name
                revealBanner()
                if (instant) engine.playNow(toPlay) else engine.playZap(toPlay)
            }

            LaunchedEffect(bannerTick) {
                if (bannerTick == 0) return@LaunchedEffect
                delay(3500)
                showBanner = false
            }

            LaunchedEffect(Unit) {
                channels = runCatching { XtreamClient.liveChannels() }.getOrDefault(emptyList())
                loading = false
                android.util.Log.i("LiveActivity", "channels=${channels.size} server=${Session.server}")
                val first = channels.firstOrNull()
                if (first != null) {
                    playChannel(first, instant = true)
                } else {
                    status = "Sin canales"
                }
                delay(120)
                runCatching { focus.requestFocus() }
            }

            val current = channels.getOrNull(index)

            fun zap(delta: Int) {
                if (channels.isEmpty()) return
                index = (index + delta + channels.size) % channels.size
                playChannel(channels[index], instant = false)
            }

            BackHandler { finish() }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        if (e.nativeKeyEvent.repeatCount > 0) return@onKeyEvent true
                        when (e.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                            AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                            AndroidKeyEvent.KEYCODE_PAGE_DOWN -> {
                                zap(1); true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_UP,
                            AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                            AndroidKeyEvent.KEYCODE_PAGE_UP -> {
                                zap(-1); true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_INFO -> {
                                if (current != null) revealBanner()
                                true
                            }
                            else -> false
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
                            engine.attach(this)
                        }
                    },
                    update = { engine.attach(it) },
                    modifier = Modifier.fillMaxSize()
                )

                AnimatedVisibility(
                    visible = showBanner && current != null,
                    enter = slideInHorizontally { -it } + fadeIn(),
                    exit = slideOutHorizontally { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    val ch = current
                    if (ch != null) {
                        ChannelSideBanner(number = index + 1, channel = ch)
                    }
                }

                if (channels.isEmpty() && !loading) {
                    Text(
                        text = status,
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"
        const val EXTRA_SERVER = "server"
    }
}

@Composable
private fun ChannelSideBanner(number: Int, channel: LiveChannel) {
    Row(
        Modifier
            .widthIn(min = 220.dp, max = 340.dp)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xEE0A0A0A),
                        Color(0xCC141414),
                        Color(0x00000000)
                    )
                ),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )
            .padding(start = 18.dp, end = 28.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            val icon = channel.streamIcon
            if (!icon.isNullOrBlank()) {
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            } else {
                Text(
                    text = "%03d".format(number),
                    color = Color(0xFFDE5B17),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.widthIn(max = 200.dp)) {
            Text(
                text = "%03d".format(number),
                color = Color(0xFFDE5B17),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
