package com.hush.app.data.repository

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import com.hush.app.domain.permission.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PermissionManager {

    override fun hasNotificationAccess(): Boolean {
        val cn = ComponentName(context, "com.hush.app.service.HushNotificationListener")
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    override fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isBatteryExempt(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    override fun isNotificationAccessDenied(): Boolean {
        if (hasNotificationAccess()) {
            setNotificationAccessDenied(false)
            return false
        }
        val prefs = context.getSharedPreferences("hush_preferences", Context.MODE_PRIVATE)
        return prefs.getBoolean("notification_access_denied", false)
    }

    override fun requestNotificationAccess(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun requestMicrophonePermission(launcher: ManagedActivityResultLauncher<String, Boolean>) {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun requestBatteryExemption(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        launcher.launch(intent)
    }

    override fun setNotificationAccessDenied(denied: Boolean) {
        val prefs = context.getSharedPreferences("hush_preferences", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notification_access_denied", denied).apply()
    }
}
