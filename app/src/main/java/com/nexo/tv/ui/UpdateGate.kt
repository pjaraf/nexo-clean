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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nexo.tv.update.AppUpdater
import com.nexo.tv.update.UpdateInfo
import kotlinx.coroutines.launch

@Composable
fun UpdateGate(enabled: Boolean = true) {
    if (!enabled) return
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        info = AppUpdater.check()
    }

    val update = info ?: return

    Dialog(onDismissRequest = { if (!update.mandatory && !downloading) info = null }) {
        Column(
            Modifier
                .width(420.dp)
                .background(Color(0xFF161616), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text("Actualización disponible", color = Color(0xFFFF6A1A), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Nueva versión ${update.versionName}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (update.changelog.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(update.changelog, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
            }
            if (downloading) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFDE5B17)
                )
                Text("$progress%", color = Color.Gray, fontSize = 13.sp)
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!update.mandatory && !downloading) {
                    TextButton(onClick = { info = null }) {
                        Text("Después", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    enabled = !downloading,
                    onClick = {
                        scope.launch {
                            if (activity != null && !AppUpdater.canInstallPackages(ctx)) {
                                status = "Activa “Instalar apps desconocidas” para NEXO"
                                AppUpdater.openInstallPermission(activity)
                                return@launch
                            }
                            downloading = true
                            status = null
                            val file = AppUpdater.download(ctx, update) { progress = it }
                            downloading = false
                            if (file == null) {
                                status = "No se pudo descargar la actualización"
                            } else {
                                AppUpdater.install(ctx, file)
                                info = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDE5B17))
                ) {
                    Text(if (downloading) "Descargando…" else "Actualizar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
