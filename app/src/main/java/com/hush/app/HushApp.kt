package com.hush.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HushApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
