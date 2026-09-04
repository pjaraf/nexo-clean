package com.nexo.tv

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Process
import kotlin.system.exitProcess

/**
 * Cierre total de Nexo.
 * [suppressHomeExit] evita matar la app al abrir Live/Movie/Series desde el Hub.
 */
object AppExit {
    @Volatile var suppressHomeExit: Boolean = false

    fun openChildActivity(block: () -> Unit) {
        suppressHomeExit = true
        try {
            block()
        } catch (t: Throwable) {
            suppressHomeExit = false
            throw t
        }
    }
}

/** Cierra por completo Nexo (proceso principal + :vlc). */
fun Context.exitNexoCompletely() {
    try {
        (this as? Activity)?.let { act ->
            try {
                act.finishAndRemoveTask()
            } catch (_: Throwable) {
                act.finishAffinity()
            }
        }
    } catch (_: Throwable) {}
    try {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.runningAppProcesses
            ?.filter { it.processName.startsWith(packageName) }
            ?.forEach { proc ->
                try {
                    Process.killProcess(proc.pid)
                } catch (_: Throwable) {}
            }
    } catch (_: Throwable) {}
    try {
        Process.killProcess(Process.myPid())
    } catch (_: Throwable) {}
    exitProcess(0)
}
