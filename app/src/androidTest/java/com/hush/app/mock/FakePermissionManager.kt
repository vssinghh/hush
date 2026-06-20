package com.hush.app.mock

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import com.hush.app.domain.permission.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakePermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PermissionManager {
    var notificationGranted = false
    var microphoneGranted = false
    var batteryExempt = false
    var notificationDenied = false

    private val prefs by lazy {
        context.getSharedPreferences("hush_preferences", Context.MODE_PRIVATE)
    }

    private fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }

    override fun hasNotificationAccess(): Boolean = isOnboardingCompleted() || notificationGranted
    override fun hasMicrophonePermission(): Boolean = isOnboardingCompleted() || microphoneGranted
    override fun isBatteryExempt(): Boolean = isOnboardingCompleted() || batteryExempt
    override fun isNotificationAccessDenied(): Boolean = notificationDenied

    override fun requestNotificationAccess(context: Context) {
        notificationGranted = true
    }

    override fun requestMicrophonePermission(launcher: ManagedActivityResultLauncher<String, Boolean>) {
        microphoneGranted = true
    }

    override fun requestBatteryExemption(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        batteryExempt = true
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        launcher.launch(intent)
    }

    override fun setNotificationAccessDenied(denied: Boolean) {
        notificationDenied = denied
    }
}
