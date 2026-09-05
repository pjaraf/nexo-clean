package com.nexo.tv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nexo.tv.data.AppConfig
import com.nexo.tv.data.Catalog
import com.nexo.tv.data.RemoteConfig
import com.nexo.tv.data.XtreamClient
import com.nexo.tv.ui.HubScreen
import com.nexo.tv.ui.LoginScreen
import com.nexo.tv.ui.SplashScreen
import com.nexo.tv.ui.UpdateGate

private enum class AppScreen { Loading, Login, Hub }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        keepAwakeWhileVisible()
        com.nexo.tv.data.AppConfig.init(this)
        com.nexo.tv.player.StreamBridge.start()

        val bootUser = intent.getStringExtra("user")
        val bootPass = intent.getStringExtra("pass")
        val bootServer = intent.getStringExtra("server")
        val hasBoot = !bootUser.isNullOrBlank() && !bootPass.isNullOrBlank()
        val start = if (hasBoot || Session.isLoggedIn) AppScreen.Loading else AppScreen.Login

        setContent {
            var screen by remember { mutableStateOf(start) }
            var splashMsg by remember { mutableStateOf("Iniciando…") }
            var lastBackAt by remember { mutableLongStateOf(0L) }

            // Sincronizar configuración remota desde GitHub en background
            LaunchedEffect(Unit) {
                com.nexo.tv.data.AppConfig.sync(this@MainActivity)
            }

            LaunchedEffect(screen) {
                if (screen != AppScreen.Loading) return@LaunchedEffect
                splashMsg = "Conectando…"
                val ok = when {
                    Session.isLoggedIn -> XtreamClient.login(
                        Session.username,
                        Session.password,
                        preferredServer = Session.server
                    )
                    hasBoot -> XtreamClient.login(
                        bootUser!!.trim(),
                        bootPass!!,
                        preferredServer = bootServer ?: Session.server
                    )
                    else -> false
                }
                if (!ok) {
                    Catalog.clear()
                    screen = AppScreen.Login
                    return@LaunchedEffect
                }
                splashMsg = "Cargando películas y series…"
                Catalog.preload()
                screen = AppScreen.Hub
            }

            // Doble atrás para salir (en Hub o Login)
            BackHandler(enabled = screen == AppScreen.Hub || screen == AppScreen.Login) {
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2000L) {
                    exitNexoCompletely()
                } else {
                    lastBackAt = now
                    Toast.makeText(
                        this@MainActivity,
                        "Pulsa atrás otra vez para salir",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            var remoteCfg by remember { mutableStateOf(AppConfig.current) }
            DisposableEffect(Unit) {
                val listener: (RemoteConfig) -> Unit = { updated ->
                    remoteCfg = updated
                }
                AppConfig.addListener(listener)
                onDispose {
                    AppConfig.removeListener(listener)
                }
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    remoteCfg.maintenance.enabled -> {
                        // Pantalla de Mantenimiento en vivo
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0C0D12))
                                .zIndex(50f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFFFF6A1A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "N",
                                        color = Color.White,
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    text = remoteCfg.maintenance.title.ifBlank { "Mantenimiento del Sistema" },
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = remoteCfg.maintenance.message.ifBlank { "Estamos realizando mejoras. Volvemos en breve." },
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    else -> {
                        when (screen) {
                            AppScreen.Loading -> SplashScreen(subtitle = splashMsg)
                            AppScreen.Login -> LoginScreen(onSuccess = { screen = AppScreen.Loading })
                            AppScreen.Hub -> HubScreen(
                                onLogout = {
                                    Catalog.clear()
                                    Session.logout()
                                    screen = AppScreen.Login
                                }
                            )
                        }
                    }
                }

                // Aviso global flotante en vivo (en la parte superior de la pantalla)
                if (!remoteCfg.maintenance.enabled && remoteCfg.announcement.enabled && remoteCfg.announcement.message.isNotBlank()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, start = 80.dp, end = 80.dp)
                            .zIndex(40f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xE61E2230))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📢 ${remoteCfg.announcement.title.ifBlank { "AVISO" }}: ",
                                color = Color(0xFFFF6A1A),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = remoteCfg.announcement.message,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                UpdateGate()
            }
        }
    }

    override fun onUserLeaveHint() {
        // Home del mando: salir. No salir si abrimos Live/Movie/Series.
        super.onUserLeaveHint()
        if (AppExit.suppressHomeExit) {
            AppExit.suppressHomeExit = false
            return
        }
        exitNexoCompletely()
    }

    override fun onResume() {
        super.onResume()
        AppExit.suppressHomeExit = false
    }
}
