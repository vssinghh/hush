package com.hush.app.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.permission.PermissionManager
import com.hush.app.domain.repository.AppInfo
import com.hush.app.domain.repository.HistoryRepository
import com.hush.app.domain.repository.PackageResolver
import com.hush.app.domain.usecase.EvaluateNotificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class TestResult(
    val action: RuleAction,
    val appName: String,
    val title: String?,
    val text: String?,
    val sender: String?
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val permissionManager: PermissionManager,
    private val evaluateNotificationUseCase: EvaluateNotificationUseCase,
    private val packageResolver: PackageResolver
) : ViewModel() {

    private val _isNotificationActive = MutableStateFlow(false)
    val isNotificationActive: StateFlow<Boolean> = _isNotificationActive.asStateFlow()

    private val _isVoiceActive = MutableStateFlow(false)
    val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = packageResolver.getInstalledApps()
                .sortedBy { it.displayName.lowercase() }
        }
    }

    fun refreshPermissions() {
        _isNotificationActive.value = permissionManager.hasNotificationAccess()
        _isVoiceActive.value = permissionManager.hasMicrophonePermission()
    }

    fun testRule(packageName: String, appName: String, title: String?, text: String?, sender: String?) {
        viewModelScope.launch {
            try {
                val action = evaluateNotificationUseCase.execute(
                    packageName = packageName,
                    appName = appName,
                    title = title?.takeIf { it.isNotBlank() },
                    text = text?.takeIf { it.isNotBlank() },
                    sender = sender?.takeIf { it.isNotBlank() },
                    logResult = false
                )
                _testResult.value = TestResult(
                    action = action,
                    appName = appName,
                    title = title,
                    text = text,
                    sender = sender
                )
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Test rule evaluation failed", e)
            }
        }
    }

    fun clearTestResult() {
        _testResult.value = null
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

