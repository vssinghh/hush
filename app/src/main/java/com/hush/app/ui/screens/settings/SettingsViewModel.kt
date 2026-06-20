package com.hush.app.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hush.app.domain.permission.PermissionManager
import com.hush.app.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val permissionManager: PermissionManager
) : ViewModel() {

    private val _isNotificationActive = MutableStateFlow(false)
    val isNotificationActive: StateFlow<Boolean> = _isNotificationActive.asStateFlow()

    private val _isVoiceActive = MutableStateFlow(false)
    val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

    fun refreshPermissions() {
        _isNotificationActive.value = permissionManager.hasNotificationAccess()
        _isVoiceActive.value = permissionManager.hasMicrophonePermission()
    }

    fun pruneDatabase(policy: String) {
        val days = when (policy) {
            "7 Days" -> 7L
            "30 Days" -> 30L
            "90 Days" -> 90L
            else -> return
        }
        val threshold = Instant.now().minus(days, ChronoUnit.DAYS)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyRepository.deleteLogsOlderThan(threshold)
                Log.d("HushPruning", "Manual retention pruning triggered: deleted logs older than $threshold")
            } catch (e: Exception) {
                Log.e("HushPruning", "Error pruning database", e)
            }
        }
    }
}
