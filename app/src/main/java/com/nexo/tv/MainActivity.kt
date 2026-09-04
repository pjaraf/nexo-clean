package com.nexo.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nexo.tv.data.Catalog
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

        val bootUser = intent.getStringExtra("user")
        val bootPass = intent.getStringExtra("pass")
        val bootServer = intent.getStringExtra("server")
        val hasBoot = !bootUser.isNullOrBlank() && !bootPass.isNullOrBlank()
        val start = if (hasBoot || Session.isLoggedIn) AppScreen.Loading else AppScreen.Login

        setContent {
            var screen by remember { mutableStateOf(start) }
            var splashMsg by remember { mutableStateOf("Iniciando…") }

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

            Box(Modifier.fillMaxSize()) {
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
                if (screen == AppScreen.Login || screen == AppScreen.Hub) {
                    UpdateGate()
                }
            }
        }
    }
}
