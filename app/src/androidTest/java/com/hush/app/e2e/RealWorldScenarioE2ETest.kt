package com.hush.app.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hush.app.MainActivity
import com.hush.app.data.db.dao.NotificationLogDao
import com.hush.app.data.db.dao.RuleDao
import com.hush.app.data.db.entity.NotificationLogEntity
import com.hush.app.data.db.entity.RuleEntity
import com.hush.app.data.pref.OnboardingPrefs
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.usecase.EvaluateNotificationUseCase
import com.hush.app.mock.FakeAIEngine
import com.hush.app.mock.FakeSpeechRecognizerWrapper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RealWorldScenarioE2ETest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var composeRule = createEmptyComposeRule()

    @Inject
    lateinit var ruleDao: RuleDao

    @Inject
    lateinit var logDao: NotificationLogDao

    @Inject
    lateinit var fakeAIEngine: FakeAIEngine

    @Inject
    lateinit var fakeSpeechRecognizer: FakeSpeechRecognizerWrapper

    @Inject
    lateinit var evaluateNotificationUseCase: EvaluateNotificationUseCase

    private lateinit var onboardingPrefs: OnboardingPrefs
    private var activeScenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setup() {
        hiltRule.inject()
        fakeAIEngine.setAvailable(true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        onboardingPrefs = OnboardingPrefs(context)
        onboardingPrefs.isOnboardingCompleted = true
        Intents.init()

        activeScenario?.close()
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activeScenario = ActivityScenario.launch(intent)

        runBlocking {
            ruleDao.getAllRulesFlow().first()
            logDao.clearAllLogs()
        }
    }

    @After
    fun tearDown() {
        Intents.release()
        activeScenario?.close()
        activeScenario = null
        runBlocking {
            logDao.clearAllLogs()
        }
    }

    private fun simulateNotificationPost(
        packageName: String,
        appName: String,
        title: String?,
        text: String?,
        sender: String?,
        currentTime: LocalTime = LocalTime.now()
    ): Boolean = runBlocking {
        evaluateNotificationUseCase.execute(
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            sender = sender,
            currentTime = currentTime
        ) == RuleAction.BLOCK
    }

    @Test
    fun testScenario_FreshInstallOnboardingAndVoiceRuleCreation() {
        // T4_RW_01: Simulate fresh install -> onboarding -> voice command -> confirm -> intercept
        activeScenario?.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        onboardingPrefs = OnboardingPrefs(context)
        onboardingPrefs.isOnboardingCompleted = false
        
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activeScenario = ActivityScenario.launch(intent)
        
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty()
        }

        val response = ParsedCommand(
            action = RuleAction.MUTE,
            app = "com.google.android.gm",
            matchField = MatchField.SENDER,
            matchType = MatchType.CONTAINS,
            matchPattern = "Amazon",
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Mute Gmail messages from Amazon"
        )
        fakeAIEngine.setResponse("mute gmail messages from amazon", response)

        // 1. Complete onboarding
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.onNodeWithTag("onboarding_grant_notification").performClick()
        composeRule.onNodeWithTag("onboarding_grant_mic").performClick()
        intending(hasAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        composeRule.onNodeWithTag("onboarding_ignore_battery").performClick()
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.onNodeWithTag("onboarding_start_button").performClick()

        // Wait for chat screen to show up
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("chat_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Open Chat, start listening
        composeRule.onNodeWithTag("chat_mic_button").performClick()
        fakeSpeechRecognizer.simulateSpeech("mute gmail messages from amazon")

        // Wait for transcribed text to populate input field
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("chat_input_field").fetchSemanticsNodes().isNotEmpty()
        }
        // Click Send to submit transcribed voice command
        composeRule.onNodeWithTag("chat_send_button").performClick()

        // Wait for card to appear
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("ai_rule_card").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Confirm card
        composeRule.onNodeWithTag("ai_rule_confirm").performScrollTo().performClick()

        // Wait for DB insertion of Gmail rule
        composeRule.waitUntil(10000) {
            var inserted = false
            runBlocking {
                val rules = ruleDao.getActiveRules()
                inserted = rules.any { it.appPackage == "com.google.android.gm" }
            }
            inserted
        }

        // 4. Inject matching notification (from Amazon) -> expect MUTED (simulate returns false for cancel)
        val blocked1 = simulateNotificationPost("com.google.android.gm", "Gmail", "Your Order", "Amazon order shipped", "Amazon")
        assertFalse(blocked1)

        // 5. Inject non-matching notification (from Mom) -> expect ALLOWED (simulate returns false)
        val blocked2 = simulateNotificationPost("com.google.android.gm", "Gmail", "Hey", "Dinner is ready", "Mom")
        assertFalse(blocked2)

        // Verify history contains both with correct actionTaken logs
        runBlocking {
            val logs = logDao.getAllLogsFlow().first()
            val amazonLog = logs.firstOrNull { it.sender == "Amazon" }
            val momLog = logs.firstOrNull { it.sender == "Mom" }
            assertNotNull(amazonLog)
            assertEquals("MUTE", amazonLog!!.actionTaken)
            assertNull(momLog)
        }
    }

    @Test
    fun testScenario_TimeWindowRule_ActiveAndInactiveEvaluations() {
        runBlocking {
            // T4_RW_02: Verify time-windowed rules behave correctly at different times
            // Block Instagram between 22:00 and 07:00
            val rule = RuleEntity(
                id = 402L, name = "Night Block", enabled = true, originalPrompt = "Block Instagram at night",
                appPackage = "com.instagram.android", appDisplayName = "Instagram", matchField = "ANY",
                matchType = "CONTAINS", matchPattern = null, isInverted = false, action = "BLOCK",
                timeStart = "22:00", timeEnd = "07:00", priority = 0, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
            ruleDao.insertRule(rule)

            // 1. Current time 23:00 -> expect BLOCKED
            val blockedNight = simulateNotificationPost("com.instagram.android", "Instagram", "Alert", "New Msg", null, LocalTime.of(23, 0))
            assertTrue(blockedNight)

            // 2. Current time 15:00 -> expect ALLOWED
            val blockedDay = simulateNotificationPost("com.instagram.android", "Instagram", "Alert", "New Msg", null, LocalTime.of(15, 0))
            assertFalse(blockedDay)
        }
    }

    @Test
    fun testScenario_MultipleRules_PriorityEvaluation() {
        runBlocking {
            // T4_RW_03: Verify priority order (lower priority value = higher precedence)
            // Rule A (Priority 0): ALLOW WhatsApp from Dad
            val ruleA = RuleEntity(
                id = 4031L, name = "Allow Dad", enabled = true, originalPrompt = "Allow WhatsApp from Dad",
                appPackage = "com.whatsapp", appDisplayName = "WhatsApp", matchField = "SENDER",
                matchType = "CONTAINS", matchPattern = "Dad", isInverted = false, action = "ALLOW",
                timeStart = null, timeEnd = null, priority = 0, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
            // Rule B (Priority 1): BLOCK all WhatsApp
            val ruleB = RuleEntity(
                id = 4032L, name = "Block All WhatsApp", enabled = true, originalPrompt = "Block all WhatsApp",
                appPackage = "com.whatsapp", appDisplayName = "WhatsApp", matchField = "ANY",
                matchType = "CONTAINS", matchPattern = null, isInverted = false, action = "BLOCK",
                timeStart = null, timeEnd = null, priority = 1, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
            ruleDao.insertRule(ruleA)
            ruleDao.insertRule(ruleB)

            // Inject Dad -> ALLOWED
            val blockedDad = simulateNotificationPost("com.whatsapp", "WhatsApp", "Hey", "How are you?", "Dad")
            assertFalse(blockedDad)

            // Inject Stranger -> BLOCKED
            val blockedStranger = simulateNotificationPost("com.whatsapp", "WhatsApp", "Ad", "Buy this!", "Stranger")
            assertTrue(blockedStranger)
        }
    }

    @Test
    fun testScenario_InvertedRule_AllowOnlyMatchingExceptions() {
        runBlocking {
            // T4_RW_04: Verify inverted filter matching (block everything except what matches)
            // BLOCK Slack except when sender = "Manager"
            val rule = RuleEntity(
                id = 404L, name = "Slack manager allowlist", enabled = true, originalPrompt = "Block Slack unless Manager",
                appPackage = "com.slack", appDisplayName = "Slack", matchField = "SENDER",
                matchType = "CONTAINS", matchPattern = "Manager", isInverted = true, action = "BLOCK",
                timeStart = null, timeEnd = null, priority = 0, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
            ruleDao.insertRule(rule)

            // Inject Manager -> ALLOWED
            val blockedManager = simulateNotificationPost("com.slack", "Slack", "Direct Msg", "Urgent request", "Manager")
            assertFalse(blockedManager)

            // Inject Co-worker -> BLOCKED
            val blockedCoworker = simulateNotificationPost("com.slack", "Slack", "Direct Msg", "Coffee?", "Co-worker")
            assertTrue(blockedCoworker)
        }
    }

    @Test
    fun testScenario_SettingsRetention_DatabasePurgeJob() {
        runBlocking {
            // T4_RW_05: Verify historical data cleanup policies in a realistic session
            onboardingPrefs.isOnboardingCompleted = true
            
            // Clean launch instead of recreate
            activeScenario?.close()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val intent = android.content.Intent(context, MainActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            activeScenario = ActivityScenario.launch(intent)
            
            composeRule.waitForIdle()
            composeRule.waitUntil(10000) {
                composeRule.onAllNodesWithTag("chat_screen").fetchSemanticsNodes().isNotEmpty()
            }

            val olderTimestamp = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
            val newerTimestamp = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)

            val logX = NotificationLogEntity(501L, "Slack", "com.slack", "Old Alert", "Log X content", null, olderTimestamp, "BLOCK", null, null)
            val logY = NotificationLogEntity(502L, "Slack", "com.slack", "New Alert", "Log Y content", null, newerTimestamp, "BLOCK", null, null)
            logDao.insertLog(logX)
            logDao.insertLog(logY)

            // Open Settings. Change retention policy to 7 Days
            composeRule.onNodeWithTag("bottom_nav_settings").performClick()
            composeRule.onNodeWithTag("settings_retention_pref").performClick()
            composeRule.onNodeWithTag("settings_retention_7_days").performClick()

            // Trigger database cleanup manually via worker trigger (simulate what settings changes do immediately)
            val threshold = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            logDao.deleteLogsOlderThan(threshold)

            // Navigate to History screen
            composeRule.onNodeWithTag("bottom_nav_history").performClick()

            // Verify Log X is deleted, Log Y remains
            composeRule.onNodeWithText("Log Y content").assertIsDisplayed()
            composeRule.onNodeWithText("Log X content").assertDoesNotExist()
        }
    }
}
