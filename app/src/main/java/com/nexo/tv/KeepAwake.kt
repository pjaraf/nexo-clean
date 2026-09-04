package com.nexo.tv

import android.content.Context
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Evita que el TV Box entre en suspensión mientras NEXO está en primer plano.
 */
class KeepAwake(private val activity: ComponentActivity) : DefaultLifecycleObserver {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(owner: LifecycleOwner) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume(owner: LifecycleOwner) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        acquire()
    }

    override fun onPause(owner: LifecycleOwner) {
        releaseLock()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        releaseLock()
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun acquire() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "nexo:keepawake"
            ).also {
                it.setReferenceCounted(false)
                it.acquire(6 * 60 * 60 * 1000L)
            }
        } catch (_: Throwable) {}
    }

    private fun releaseLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Throwable) {}
        wakeLock = null
    }
}

fun ComponentActivity.keepAwakeWhileVisible() {
    lifecycle.addObserver(KeepAwake(this))
}
