package com.hush.app.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
import com.hush.app.mock.FakeAIEngine
import com.hush.app.domain.usecase.EvaluateNotificationUseCase
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
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CrossFeatureE2ETest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var composeRule = createEmptyComposeRule()

    @Inject
    lateinit var evaluateNotificationUseCase: EvaluateNotificationUseCase

    @Inject
    lateinit var ruleDao: RuleDao

    @Inject
    lateinit var logDao: NotificationLogDao

    @Inject
    lateinit var fakeAIEngine: FakeAIEngine

    @Inject
    lateinit var fakePackageResolver: com.hush.app.mock.FakePackageResolver

    private lateinit var onboardingPrefs: OnboardingPrefs
    private var activeScenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setup() {
        hiltRule.inject()
        fakeAIEngine.setAvailable(true)
        fakePackageResolver.setInstalledApps(mapOf(
            "whatsapp" to "com.whatsapp",
            "slack" to "com.slack",
            "instagram" to "com.instagram.android",
            "gmail" to "com.google.android.gm"
        ))
        val context = ApplicationProvider.getApplicationContext<Context>()
        onboardingPrefs = OnboardingPrefs(context)
        onboardingPrefs.isOnboardingCompleted = true
        Intents.init()
        
        activeScenario?.close()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activeScenario = ActivityScenario.launch(intent)
        
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("chat_screen").fetchSemanticsNodes().isNotEmpty()
        }

        runBlocking {
            ruleDao.getAllRulesFlow().first()
            logDao.clearAllLogs()
        }
    }

    @After
    fun tearDown() = runBlocking {
        logDao.clearAllLogs()
        Intents.release()
        activeScenario?.close()
        activeScenario = null
    }

    private fun simulateNotificationPost(
        packageName: String,
        appName: String,
        title: String?,
        text: String?,
        sender: String?
    ): Boolean = runBlocking {
        evaluateNotificationUseCase.execute(packageName, appName, title, text, sender) == RuleAction.BLOCK
    }

    @Test
    fun testCombination_AICreation_To_ImmediateNotificationInterception() {
        // T3_CF_01: Verify that a rule generated via AI is immediately active in the interceptor service
        val response = ParsedCommand(
            action = RuleAction.BLOCK,
            app = "com.instagram.android",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Block Instagram"
        )
        fakeAIEngine.setResponse("block instagram", response)

        // Type and confirm in Chat
        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("block instagram")
        composeRule.onNodeWithTag("chat_send_button").performClick()

        // Wait for card to appear
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("ai_rule_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("ai_rule_confirm").performScrollTo().performClick()

        // Wait for DB insertion
        composeRule.waitUntil(10000) {
            var inserted = false
            runBlocking {
                val rules = ruleDao.getActiveRules()
                inserted = rules.any { it.appPackage == "com.instagram.android" }
            }
            inserted
        }

        // Inject notification immediately
        val isCanceled = simulateNotificationPost("com.instagram.android", "Instagram", "Alert", "New Like", null)

        assertTrue(isCanceled)
    }

    @Test
    fun testCombination_RulesUiToggle_To_NotificationInterception() {
        runBlocking {
            // T3_CF_02: Verify that disabling a rule in the Rules tab immediately modifies NLS interception behaviors
            val rule = RuleEntity(
                id = 201L, name = "Block Gmail", enabled = true, originalPrompt = "Block Gmail",
                appPackage = "com.google.android.gm", appDisplayName = "Gmail", matchField = "ANY",
                matchType = "CONTAINS", matchPattern = null, isInverted = false, action = "BLOCK",
                timeStart = null, timeEnd = null, priority = 0, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
            ruleDao.insertRule(rule)

            // 1. Post notification -> verify BLOCKED
            val block1 = simulateNotificationPost("com.google.android.gm", "Gmail", "Alert", "Work Email", null)
            assertTrue(block1)

            // 2. Go to Rules Screen, toggle Gmail rule off
            composeRule.onNodeWithTag("bottom_nav_rules").performClick()
            composeRule.onNodeWithTag("rule_toggle_201").performClick()

            // Wait for DB to reflect that Gmail rule is disabled
            composeRule.waitUntil(10000) {
                var isDisabled = false
                runBlocking {
                    val r = ruleDao.getRuleById(201L)
                    isDisabled = r != null && !r.enabled
                }
                isDisabled
            }

            // 3. Post another notification -> verify ALLOWED
            val block2 = simulateNotificationPost("com.google.android.gm", "Gmail", "Alert", "Work Email", null)
            assertFalse(block2)
        }
    }

    @Test
    fun testCombination_OnboardingCompletion_To_AIChatFlow() {
        // T3_CF_03: Verify that completing the onboarding flow leads immediately to a functioning Chat screen
        activeScenario?.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        onboardingPrefs = OnboardingPrefs(context)
        onboardingPrefs.isOnboardingCompleted = false

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activeScenario = ActivityScenario.launch(intent)
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty()
        }

        val response = ParsedCommand(
            action = RuleAction.MUTE,
            app = "com.slack",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Mute Slack"
        )
        fakeAIEngine.setResponse("mute slack", response)

        // Complete Onboarding
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

        // Immediately send chat command
        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("mute slack")
        composeRule.onNodeWithTag("chat_send_button").performClick()

        // Wait for card to appear
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("ai_rule_card").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("ai_rule_card").assertIsDisplayed()
    }

    @Test
    fun testCombination_AICreation_To_RulesListAndEdit() {
        // T3_CF_04: Verify rules created via AI are editable inside the Rules tab
        val response = ParsedCommand(
            action = RuleAction.MUTE,
            app = "com.whatsapp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = null,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Mute WhatsApp"
        )
        fakeAIEngine.setResponse("mute whatsapp", response)

        // Send and confirm rule in Chat
        composeRule.onNodeWithTag("chat_input_field").performTextReplacement("mute whatsapp")
        composeRule.onNodeWithTag("chat_send_button").performClick()

        // Wait for card
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithTag("ai_rule_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("ai_rule_confirm").performScrollTo().performClick()

        // Wait for DB insertion
        composeRule.waitUntil(10000) {
            var inserted = false
            runBlocking {
                val rules = ruleDao.getActiveRules()
                inserted = rules.any { it.appPackage == "com.whatsapp" }
            }
            inserted
        }

        // Open Rules screen
        composeRule.onNodeWithTag("bottom_nav_rules").performClick()

        // Wait for the card to be displayed in Rules
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Mute WhatsApp").fetchSemanticsNodes().isNotEmpty()
        }

        // Click WhatsApp card to edit
        composeRule.onNodeWithText("Mute WhatsApp").performClick()

        // Change action from MUTE to BLOCK and save
        composeRule.onNodeWithTag("rule_edit_action_block").performClick()
        composeRule.onNodeWithTag("rule_edit_save_button").performClick()

        // Verify action is updated in database
        runBlocking {
            val rules = ruleDao.getActiveRules()
            val whatsappRule = rules.firstOrNull { it.appPackage == "com.whatsapp" }
            assertNotNull(whatsappRule)
            assertEquals("BLOCK", whatsappRule!!.action)
        }
    }

    @Test
    fun testCombination_NotificationInterception_To_RealTimeHistoryLog() {
        // T3_CF_05: Verify intercepted notifications update the active History tab in real-time
        val rule = RuleEntity(
            id = 205L, name = "Block Slack", enabled = true, originalPrompt = "Block Slack",
            appPackage = "com.slack", appDisplayName = "Slack", matchField = "ANY",
            matchType = "CONTAINS", matchPattern = null, isInverted = false, action = "BLOCK",
            timeStart = null, timeEnd = null, priority = 0, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
        )
        runBlocking { ruleDao.insertRule(rule) }

        // Open History Screen
        composeRule.onNodeWithTag("bottom_nav_history").performClick()

        // Inject notification
        simulateNotificationPost("com.slack", "Slack", "Boss", "Get to work", null)

        // Verify history list updates instantly in UI
        composeRule.waitUntil(10000) {
            composeRule.onAllNodesWithText("Get to work").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Get to work").assertIsDisplayed()
    }

    @Test
    fun testCombination_RuleDeletion_To_HistoryLogsGracefulDisplay() {
        runBlocking {
            // T3_CF_06: Verify history details render safely after the associated rule is deleted
            val rule = RuleEntity(
                id = 206L, name = "Temp Rule", enabled = true, originalPrompt = "Temp Rule",
                appPackage = "com.slack", appDisplayName = "Slack", matchField = "ANY",
                matchType = "CONTAINS", matchPattern = null, isInverted = false, action = "BLOCK",
                timeStart = null, timeEnd = null, priority = 0, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
            ruleDao.insertRule(rule)

            // Log containing rule reference
            val log = NotificationLogEntity(
                id = 2061L, appName = "Slack", packageName = "com.slack", title = "Alert", text = "Rule deletion test",
                sender = null, timestamp = System.currentTimeMillis(), actionTaken = "BLOCK", matchedRuleId = 206L, matchedRuleName = "Temp Rule"
            )
            logDao.insertLog(log)

            // Delete the rule
            composeRule.onNodeWithTag("bottom_nav_rules").performClick()
            composeRule.onNodeWithTag("rule_card_206").performTouchInput { swipeLeft() }

            // Wait for rule card deletion in UI/DB
            composeRule.waitUntil(10000) {
                composeRule.onAllNodesWithTag("rule_card_206").fetchSemanticsNodes().isEmpty()
            }

            // Open History and tap detail
            composeRule.onNodeWithTag("bottom_nav_history").performClick()

            // Wait for history item to be displayed
            composeRule.waitUntil(10000) {
                composeRule.onAllNodesWithText("Rule deletion test").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Rule deletion test").performClick()

            // Expected Result: Detail renders fallback placeholder "Rule deleted" instead of crashing
            composeRule.onNodeWithTag("history_detail_dialog").assertIsDisplayed()
            composeRule.onNodeWithText("Triggered by Rule: Rule deleted").assertIsDisplayed()
        }
    }
}
