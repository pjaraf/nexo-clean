package com.nexo.tv.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nexo.tv.update.AppUpdater
import com.nexo.tv.update.UpdateInfo

@Composable
fun UpdateGate(enabled: Boolean = true) {
    if (!enabled) return
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        info = AppUpdater.check()
    }

    // Al detectar actualización, descarga e instala automáticamente
    LaunchedEffect(info, retryTrigger) {
        val currentInfo = info ?: return@LaunchedEffect
        if (activity != null && !AppUpdater.canInstallPackages(ctx)) {
            status = "Activa “Instalar apps desconocidas” para actualizar NEXO"
            AppUpdater.openInstallPermission(activity)
            return@LaunchedEffect
        }
        downloading = true
        status = null
        val file = AppUpdater.download(ctx, currentInfo) { progress = it }
        downloading = false
        if (file == null) {
            status = "No se pudo descargar la actualización"
        } else {
            status = "Abriendo instalador…"
            AppUpdater.install(ctx, file)
        }
    }

    val update = info ?: return

    Dialog(onDismissRequest = { /* No cancelar la actualización automática */ }) {
        Column(
            Modifier
                .width(420.dp)
                .background(Color(0xFF161616), RoundedCornerShape(18.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Actualizando Nexo TV",
                color = Color(0xFFFF6A1A),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Nueva versión ${update.versionName}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (update.changelog.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    update.changelog,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(18.dp))
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color(0xFFDE5B17),
                    trackColor = Color(0xFF333333)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Descargando automáticamente… $progress%",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            } else if (status != null && status!!.startsWith("Abriendo")) {
                Text(
                    status!!,
                    color = Color(0xFFFFB74D),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            status?.let { msg ->
                if (!msg.startsWith("Abriendo")) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Si falta permiso o falló la descarga, botón para resolverlo con el mando
            if (!downloading && status != null && !status!!.startsWith("Abriendo")) {
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (activity != null && !AppUpdater.canInstallPackages(ctx)) {
                        Button(
                            onClick = { AppUpdater.openInstallPermission(activity) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDE5B17)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.tvFocus(RoundedCornerShape(10.dp))
                        ) {
                            Text("Abrir Ajustes", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { retryTrigger++ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDE5B17)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.tvFocus(RoundedCornerShape(10.dp))
                        ) {
                            Text("Reintentar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
