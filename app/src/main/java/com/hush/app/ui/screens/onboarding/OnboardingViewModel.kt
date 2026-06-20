package com.hush.app.ui.screens.onboarding

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.hush.app.domain.permission.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissionManager: PermissionManager
) : ViewModel() {

    var hasNotificationAccess by mutableStateOf(false)
        private set

    var hasMicrophonePermission by mutableStateOf(false)
        private set

    var isBatteryExempt by mutableStateOf(false)
        private set

    var isNotificationAccessDenied by mutableStateOf(false)
        private set

    var isNotificationPermissionRequested by mutableStateOf(false)

    fun refreshPermissions() {
        hasNotificationAccess = permissionManager.hasNotificationAccess()
        hasMicrophonePermission = permissionManager.hasMicrophonePermission()
        isBatteryExempt = permissionManager.isBatteryExempt()
        isNotificationAccessDenied = permissionManager.isNotificationAccessDenied()
    }

    fun requestNotificationAccess(context: Context) {
        isNotificationPermissionRequested = true
        permissionManager.requestNotificationAccess(context)
        refreshPermissions()
    }

    fun requestMicrophonePermission(launcher: ManagedActivityResultLauncher<String, Boolean>) {
        permissionManager.requestMicrophonePermission(launcher)
        refreshPermissions()
    }

    fun requestBatteryExemption(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        permissionManager.requestBatteryExemption(launcher)
        refreshPermissions()
    }

    fun denyNotificationAccess() {
        permissionManager.setNotificationAccessDenied(true)
        refreshPermissions()
    }
}
