package com.nexo.tv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager

object Device {
    fun isTv(context: Context): Boolean {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
            !pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        ) return true
        return false
    }
}
