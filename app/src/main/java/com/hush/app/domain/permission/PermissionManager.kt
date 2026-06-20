package com.hush.app.domain.permission

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult

interface PermissionManager {
    fun hasNotificationAccess(): Boolean
    fun hasMicrophonePermission(): Boolean
    fun isBatteryExempt(): Boolean
    fun isNotificationAccessDenied(): Boolean

    fun requestNotificationAccess(context: Context)
    fun requestMicrophonePermission(launcher: ManagedActivityResultLauncher<String, Boolean>)
    fun requestBatteryExemption(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>)
    
    fun setNotificationAccessDenied(denied: Boolean)
}
