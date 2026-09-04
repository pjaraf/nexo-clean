package com.nexo.tv

import android.app.Application

class NexoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
    }
}
