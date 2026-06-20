package com.hush.app.data.pref

import android.content.Context
import android.content.SharedPreferences

class OnboardingPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hush_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).commit()
        }
}
