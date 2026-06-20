package com.hush.app

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hush.app.data.pref.OnboardingPrefs
import com.hush.app.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val onboardingPrefs: OnboardingPrefs,
    private val historyRepository: HistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hush_preferences", Context.MODE_PRIVATE)

    private val _themeOption = MutableStateFlow(
        prefs.getString("theme_option", "System Default") ?: "System Default"
    )
    val themeOption: StateFlow<String> = _themeOption.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "theme_option") {
            _themeOption.value = prefs.getString("theme_option", "System Default") ?: "System Default"
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        val retentionPolicy = prefs.getString("retention_policy", "30 Days") ?: "30 Days"
        val days = when (retentionPolicy) {
            "7 Days" -> 7L
            "30 Days" -> 30L
            "90 Days" -> 90L
            else -> null
        }
        if (days != null) {
            pruneDatabaseOnStartup(days)
        }
    }

    private fun pruneDatabaseOnStartup(days: Long) {
        val threshold = java.time.Instant.now().minus(days, java.time.temporal.ChronoUnit.DAYS)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyRepository.deleteLogsOlderThan(threshold)
                android.util.Log.d("HushPruning", "Database retention pruning triggered: deleted logs older than $threshold")
            } catch (e: Exception) {
                android.util.Log.e("HushPruning", "Error during database retention pruning", e)
            }
        }
    }

    val isOnboardingCompleted: Boolean
        get() = onboardingPrefs.isOnboardingCompleted

    fun setOnboardingCompleted(completed: Boolean) {
        onboardingPrefs.isOnboardingCompleted = completed
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}
