package com.hush.app.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hush.app.MainActivity
import com.hush.app.data.pref.OnboardingPrefs
import com.hush.app.domain.permission.PermissionManager
import com.hush.app.mock.FakeAIEngine
import com.hush.app.mock.FakePermissionManager
import com.hush.app.mock.FakeSpeechRecognizerWrapper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppFoundationE2ETest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var composeRule = createEmptyComposeRule()

    @Inject
    lateinit var fakeAIEngine: FakeAIEngine

    @Inject
    lateinit var fakeSpeechRecognizer: FakeSpeechRecognizerWrapper

    @Inject
    lateinit var permissionManager: PermissionManager

    private lateinit var onboardingPrefs: OnboardingPrefs
    private var activeScenario: ActivityScenario<MainActivity>? = null

    private fun getScenario(): ActivityScenario<MainActivity> {
        return activeScenario ?: error("Scenario not launched")
    }

    private fun recreateActivityAndWait(tag: String, freshLaunch: Boolean = true) {
        if (freshLaunch) {
            activeScenario?.close()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            activeScenario = ActivityScenario.launch(intent)
        } else {
            getScenario().recreate()
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(15000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("hush_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        onboardingPrefs = OnboardingPrefs(context)
        hiltRule.inject()
        Intents.init()
        (permissionManager as? FakePermissionManager)?.apply {
            notificationGranted = false
            microphoneGranted = false
            batteryExempt = false
            notificationDenied = false
        }
        fakeAIEngine.setAvailable(true)
    }

    @After
    fun tearDown() {
        Intents.release()
        activeScenario?.close()
        activeScenario = null
    }

    @Test
    fun testOnboardingFlow_GrantAllPermissions_NavigatesToChat() {
        // T1_F1_01: Verify onboarding flow completion
        onboardingPrefs.isOnboardingCompleted = false
        
        // Re-launch activity for fresh onboarding state
        recreateActivityAndWait("onboarding_screen")

        // 1. Verify Onboarding displays (Onboarding Title or tag)
        composeRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        
        // 2. Click "Next" button
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        
        // 3. Click "Grant Notification Access" button (Simulate grant)
        composeRule.onNodeWithTag("onboarding_grant_notification").performClick()
        
        // 4. Click "Grant Microphone Permission" button (Simulate grant)
        composeRule.onNodeWithTag("onboarding_grant_mic").performClick()
        
        // 5. Click "Exclude from Battery Optimization" button (Simulate ignore)
        intending(hasAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        composeRule.onNodeWithTag("onboarding_ignore_battery").performClick()
        
        // Click "Continue" button to navigate to AICoreStep
        composeRule.onNodeWithTag("onboarding_next_button").performClick()

        // 6. Click "Get Started" button
        composeRule.onNodeWithTag("onboarding_start_button").performClick()

        // Expected Result: Onboarding flag is updated in local preferences. App navigates to Chat screen.
        assertTrue(onboardingPrefs.isOnboardingCompleted)
        composeRule.onNodeWithTag("chat_screen").assertIsDisplayed()
    }

    @Test
    fun testBottomNav_SwitchTabs_RendersCorrectScreens() {
        // T1_F1_02: Verify that bottom navigation switches views
        onboardingPrefs.isOnboardingCompleted = true
        recreateActivityAndWait("bottom_nav_rules")

        // 1. Click "Rules" icon in bottom nav. Verify Rules screen.
        composeRule.onNodeWithTag("bottom_nav_rules").performClick()
        composeRule.onNodeWithTag("rules_screen").assertIsDisplayed()

        // 2. Click "History" icon in bottom nav. Verify History screen.
        composeRule.onNodeWithTag("bottom_nav_history").performClick()
        composeRule.onNodeWithTag("history_screen").assertIsDisplayed()

        // 3. Click "Settings" icon in bottom nav. Verify Settings screen.
        composeRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // 4. Click "Chat" icon in bottom nav. Verify Chat screen.
        composeRule.onNodeWithTag("bottom_nav_chat").performClick()
        composeRule.onNodeWithTag("chat_screen").assertIsDisplayed()
    }

    @Test
    fun testSettingsScreen_DisplaysPermissionStatus() {
        // T1_F1_03: Verify Settings screen indicators reflect permission states
        onboardingPrefs.isOnboardingCompleted = true
        (permissionManager as FakePermissionManager).apply {
            notificationGranted = true
            microphoneGranted = true
        }
        recreateActivityAndWait("bottom_nav_settings")

        // Open Settings screen
        composeRule.onNodeWithTag("bottom_nav_settings").performClick()

        // Observe statuses for Notification Interception and Voice Input
        // Expected Result: UI renders active status badges
        composeRule.onNodeWithTag("settings_notification_status").assertTextContains("Active")
        composeRule.onNodeWithTag("settings_voice_status").assertTextContains("Active")
    }

    @Test
    fun testSettingsScreen_ToggleTheme_ThemeChangesAndPersists() {
        // T1_F1_04: Verify dark/light theme options can be set and persist
        onboardingPrefs.isOnboardingCompleted = true
        recreateActivityAndWait("bottom_nav_settings")

        // Open Settings screen
        composeRule.onNodeWithTag("bottom_nav_settings").performClick()

        // Click Theme Preference and select Dark Theme
        composeRule.onNodeWithTag("settings_theme_pref").performClick()
        composeRule.onNodeWithTag("settings_theme_dark_option").performClick()

        // Re-create Activity
        recreateActivityAndWait("settings_theme_pref", freshLaunch = false)

        // Verify the theme remains set to Dark Theme
        composeRule.onNodeWithTag("settings_theme_pref").assertTextContains("Dark Theme")
    }

    @Test
    fun testSettingsScreen_ToggleLightTheme_ThemeChangesAndPersists() {
        onboardingPrefs.isOnboardingCompleted = true
        recreateActivityAndWait("bottom_nav_settings")

        // Open Settings screen
        composeRule.onNodeWithTag("bottom_nav_settings").performClick()

        // Click Theme Preference and select Light Theme
        composeRule.onNodeWithTag("settings_theme_pref").performClick()
        composeRule.onNodeWithTag("settings_theme_light_option").performClick()

        // Re-create Activity
        recreateActivityAndWait("settings_theme_pref", freshLaunch = false)

        // Verify the theme remains set to Light Theme
        composeRule.onNodeWithTag("settings_theme_pref").assertTextContains("Light Theme")
    }

    @Test
    fun testAppLaunch_OnboardingAlreadyCompleted_LaunchesToChatDirectly() {
        // T1_F1_05: Verify subsequent launches bypass onboarding
        onboardingPrefs.isOnboardingCompleted = true
        recreateActivityAndWait("chat_screen")

        // Launch MainActivity and verify Chat is immediately visible, Onboarding is not.
        composeRule.onNodeWithTag("chat_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_screen").assertDoesNotExist()
    }

    @Test
    fun testOnboarding_DenyNotificationAccess_ShowsRationaleAndDisablesNext() {
        // T2_F1_01: Verify permission denial blocks progression
        onboardingPrefs.isOnboardingCompleted = false
        recreateActivityAndWait("onboarding_screen")

        // Navigate to notification step
        composeRule.onNodeWithTag("onboarding_next_button").performClick()

        // Click onboarding_grant_notification (to set isNotificationPermissionRequested = true)
        composeRule.onNodeWithTag("onboarding_grant_notification").performClick()

        // Set (permissionManager as FakePermissionManager).apply { notificationGranted = false; notificationDenied = true }
        (permissionManager as FakePermissionManager).apply {
            notificationGranted = false
            notificationDenied = true
        }

        // Recreate activity using recreateActivityAndWait("onboarding_screen", freshLaunch = false) to trigger ON_RESUME
        recreateActivityAndWait("onboarding_screen", freshLaunch = false)

        // Navigate to permissions step again as activity recreation resets compose-level currentStep state to 0
        composeRule.onNodeWithTag("onboarding_next_button").performClick()

        // Assert that the rationale is shown and the next button is disabled
        composeRule.onNodeWithTag("onboarding_deny_rationale").assertExists()
        composeRule.onNodeWithTag("onboarding_next_button").assertIsNotEnabled()
    }

    @Test
    fun testThemeChange_MidSessionSystemThemeSwitch() {
        // T2_F1_02: Verify app does not crash and updates colors immediately when system theme toggles
        onboardingPrefs.isOnboardingCompleted = true
        recreateActivityAndWait("bottom_nav_settings")

        // Simulate system theme change via configuration or manual theme trigger
        composeRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeRule.onNodeWithTag("settings_theme_pref").performClick()
        composeRule.onNodeWithTag("settings_theme_system_option").performClick()

        // Expected Result: Instantly updates color state without crash or losing screen state
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun testAppLaunch_GeminiNanoUnsupported_DisplaysWarningBanner() {
        // T2_F1_03: Verify UI handles devices lacking AICore support
        fakeAIEngine.setAvailable(false)
        onboardingPrefs.isOnboardingCompleted = true
        recreateActivityAndWait("ai_unsupported_banner")

        // Expected Result: Persistent banner is shown and chat buttons are disabled
        composeRule.onNodeWithTag("ai_unsupported_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_send_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("chat_mic_button").assertIsNotEnabled()
    }

    @Test
    fun testActivityRecreation_SettingsStatePreserved() {
        // T2_F1_04: Verify settings state survives recreation
        onboardingPrefs.isOnboardingCompleted = true
        recreateActivityAndWait("bottom_nav_settings")

        // Open Settings, set history retention to 90 Days
        composeRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeRule.onNodeWithTag("settings_retention_pref").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_retention_90_days").performScrollTo().performClick()

        // Recreate activity
        recreateActivityAndWait("settings_retention_pref", freshLaunch = false)

        // Expected Result: Retention preference remains 90 Days
        composeRule.onNodeWithTag("settings_retention_pref").assertTextContains("90 Days")
    }

    @Test
    fun testOnboarding_BatteryOptimizationRejected_AllowsProgressWithWarning() {
        // T2_F1_05: Verify battery optimization denial does not block onboarding
        onboardingPrefs.isOnboardingCompleted = false
        recreateActivityAndWait("onboarding_screen")

        // Go to onboarding
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.onNodeWithTag("onboarding_grant_notification").performClick()
        composeRule.onNodeWithTag("onboarding_grant_mic").performClick()

        // Simulate user denying/rejecting the request
        intending(hasAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        composeRule.onNodeWithTag("onboarding_ignore_battery").performClick()

        // Expected Result: Warning popup appears. Dismissing allows progress.
        composeRule.onNodeWithTag("onboarding_battery_warning").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_battery_warning_dismiss").performClick()

        // Verify next button is now enabled
        composeRule.onNodeWithTag("onboarding_next_button").assertIsEnabled()
    }
}
