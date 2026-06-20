package com.hush.app.e2e

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hush.app.MainActivity
import com.hush.app.data.db.HushDatabase
import com.hush.app.data.db.dao.NotificationLogDao
import com.hush.app.data.db.dao.RuleDao
import com.hush.app.data.db.entity.NotificationLogEntity
import com.hush.app.data.db.entity.RuleEntity
import com.hush.app.data.pref.OnboardingPrefs
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RuleManagementHistoryE2ETest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupClass() {
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            OnboardingPrefs(context).isOnboardingCompleted = true
        }
    }

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var ruleDao: RuleDao

    @Inject
    lateinit var logDao: NotificationLogDao

    @Inject
    lateinit var db: HushDatabase

    @Before
    fun setup() {
        hiltRule.inject()

        // Wait for either the main screen rules tab or the onboarding screen to appear
        composeRule.waitUntil(15000) {
            composeRule.onAllNodesWithTag("bottom_nav_rules").fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty()
        }

        if (composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("onboarding_next_button").performClick()
            composeRule.onNodeWithTag("onboarding_next_button").performClick()
            composeRule.onNodeWithTag("onboarding_start_button").performClick()
            composeRule.waitForIdle()
        }

        composeRule.waitUntil(30000) {
            composeRule.onAllNodesWithTag("bottom_nav_rules").fetchSemanticsNodes().isNotEmpty()
        }

        runBlocking {
            db.clearAllTables()
        }
    }

    @After
    fun tearDown() = runBlocking {
        db.clearAllTables()
    }

    @Test
    fun testRules_ListsRulesAndTogglesEnabledState(): Unit = runBlocking {
        // T1_F4_01: Verify that saved rules appear in the Rules screen and can be enabled/disabled
        val rule = RuleEntity(
            id = 101L,
            name = "Mute Slack",
            enabled = true,
            originalPrompt = "Mute Slack",
            appPackage = "com.slack",
            appDisplayName = "Slack",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = null,
            isInverted = false,
            action = "MUTE",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        // Open Rules screen
        composeRule.onNodeWithTag("bottom_nav_rules").performClick()

        // Verify rule details match DB
        composeRule.onNodeWithTag("rule_card_101").assertIsDisplayed()
        composeRule.onNodeWithText("Mute Slack").assertIsDisplayed()

        // Click toggle switch to turn it off
        composeRule.onNodeWithTag("rule_toggle_101").performClick()

        // Verify database updates enabled to false
        var updatedRule: RuleEntity? = null
        composeRule.waitUntil(5000) {
            runBlocking {
                updatedRule = ruleDao.getRuleById(101L)
                updatedRule != null && !updatedRule!!.enabled
            }
        }
        assertNotNull(updatedRule)
        assertFalse(updatedRule!!.enabled)
    }

    @Test
    fun testRules_SwipeToDeleteRule_RemovesFromDB(): Unit = runBlocking {
        // T1_F4_02: Verify that swiping a rule card in the list deletes the rule
        val rule = RuleEntity(
            id = 102L,
            name = "Block Gmail",
            enabled = true,
            originalPrompt = "Block Gmail",
            appPackage = "com.google.android.gm",
            appDisplayName = "Gmail",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = null,
            isInverted = false,
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        // Open Rules screen
        composeRule.onNodeWithTag("bottom_nav_rules").performClick()

        // Swipe left on the rule card
        composeRule.onNodeWithTag("rule_card_102").performTouchInput {
            swipeLeft()
        }

        // Verify the rule card is removed from UI and DB
        composeRule.onNodeWithTag("rule_card_102").assertDoesNotExist()
        val deletedRule = ruleDao.getRuleById(102L)
        assertNull(deletedRule)
    }

    @Test
    fun testRules_TapRule_OpensDetailDialog(): Unit = runBlocking {
        // T1_F4_03: Verify that clicking a rule card opens a detail popup with metadata
        val rule = RuleEntity(
            id = 103L,
            name = "Mute WhatsApp",
            enabled = true,
            originalPrompt = "Mute WhatsApp",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = null,
            isInverted = false,
            action = "MUTE",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        // Open Rules screen
        composeRule.onNodeWithTag("bottom_nav_rules").performClick()

        // Click the rule card
        composeRule.onNodeWithTag("rule_card_103").performClick()

        // Expected Result: Detail dialog opens
        composeRule.onNodeWithTag("rule_detail_dialog").assertIsDisplayed()
        composeRule.onNode(hasText("Mute WhatsApp") and hasAnyAncestor(hasTestTag("rule_detail_dialog"))).assertIsDisplayed()
        composeRule.onNodeWithText("Package: com.whatsapp").assertIsDisplayed()

        // Dismiss dialog
        composeRule.onNodeWithText("Close").performClick()
    }

    @Test
    fun testHistory_ListsLogsAndFiltersByTabs(): Unit = runBlocking {
        // T1_F4_04: Verify history logs render and can be filtered by Action tab
        val log1 = NotificationLogEntity(1L, "Gmail", "com.gmail", "Title 1", "Allowed text", null, System.currentTimeMillis(), "ALLOW", null, null)
        val log2 = NotificationLogEntity(2L, "WhatsApp", "com.whatsapp", "Title 2", "Blocked text", null, System.currentTimeMillis(), "BLOCK", null, null)
        val log3 = NotificationLogEntity(3L, "Slack", "com.slack", "Title 3", "Muted text", null, System.currentTimeMillis(), "MUTE", null, null)
        logDao.insertLog(log1)
        logDao.insertLog(log2)
        logDao.insertLog(log3)

        // Open History screen
        composeRule.onNodeWithTag("bottom_nav_history").performClick()

        // Verify all 3 items are present
        composeRule.onNodeWithTag("history_list").onChildren().assertCountEquals(3)

        // Click "Blocked" tab
        composeRule.onNodeWithTag("history_tab_blocked").performClick()

        // Verify only blocked item is visible
        composeRule.onNodeWithText("Blocked text").assertIsDisplayed()
        composeRule.onNodeWithText("Allowed text").assertDoesNotExist()

        // Click "All" tab
        composeRule.onNodeWithTag("history_tab_all").performClick()

        // Verify all 3 visible
        composeRule.onNodeWithTag("history_list").onChildren().assertCountEquals(3)
    }

    @Test
    fun testHistory_TapItem_OpensDetailModal(): Unit = runBlocking {
        // T1_F4_05: Verify tapping a history log entry displays details including matching rule
        val rule = RuleEntity(
            id = 10L, name = "Block Spam", enabled = true, originalPrompt = "Block Spam",
            appPackage = null, appDisplayName = null, matchField = "ANY", matchType = "CONTAINS",
            matchPattern = "Spam", isInverted = false, action = "BLOCK", timeStart = null, timeEnd = null,
            priority = 0, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        val log = NotificationLogEntity(
            id = 4L,
            appName = "Slack",
            packageName = "com.slack",
            title = "Promo",
            text = "Spam content",
            sender = "Promo Bot",
            timestamp = System.currentTimeMillis(),
            actionTaken = "BLOCK",
            matchedRuleId = 10L,
            matchedRuleName = "Block Spam"
        )
        logDao.insertLog(log)

        // Open History
        composeRule.onNodeWithTag("bottom_nav_history").performClick()

        // Click on the log item
        composeRule.onNodeWithText("Spam content").performClick()

        // Expected Result: Detail modal opens, showing rule name
        composeRule.onNodeWithTag("history_detail_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Triggered by Rule: Block Spam").assertIsDisplayed()

        // Dismiss dialog
        composeRule.onNodeWithText("Close").performClick()
    }

    @Test
    fun testRulesScreen_EmptyState_DisplaysIllustration(): Unit = runBlocking {
        // T2_F4_01: Verify Rules screen handles empty list state cleanly
        // Open Rules screen
        composeRule.onNodeWithTag("bottom_nav_rules").performClick()

        // Expected Result: empty state graphic and text "No active rules" are rendered
        composeRule.onNodeWithTag("rules_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("No active rules").assertIsDisplayed()
    }

    @Test
    fun testHistoryScreen_PagingAndLoadPerformanceStress(): Unit = runBlocking {
        // T2_F4_02: Verify history screen lists thousands of items without freezing
        // Pre-populate with 1500 items
        for (i in 1..1500) {
            val log = NotificationLogEntity(
                id = i.toLong(),
                appName = "App $i",
                packageName = "com.app.$i",
                title = "Title $i",
                text = "Message $i",
                sender = null,
                timestamp = System.currentTimeMillis() - i * 1000,
                actionTaken = "ALLOW",
                matchedRuleId = null,
                matchedRuleName = null
            )
            logDao.insertLog(log)
        }

        // Open History screen
        composeRule.onNodeWithTag("bottom_nav_history").performClick()

        // Scroll rapidly to the bottom of the list
        composeRule.onNodeWithTag("history_list").performGesture {
            swipeUp()
            swipeUp()
            swipeUp()
        }

        // Verify the UI did not crash and is still displaying items
        composeRule.onNodeWithTag("history_list").assertIsDisplayed()
    }

    @Test
    fun testRules_RapidToggles_DoesNotDeadlockDB(): Unit = runBlocking {
        // T2_F4_03: Verify rapid user toggle inputs do not cause Room database deadlocks
        val rule = RuleEntity(
            id = 105L,
            name = "Mute WhatsApp",
            enabled = true,
            originalPrompt = "Mute WhatsApp",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = null,
            isInverted = false,
            action = "MUTE",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        // Navigate to Rules screen
        composeRule.onNodeWithTag("bottom_nav_rules").performClick()

        // Rapidly tap the switch 8 times
        val toggleNode = composeRule.onNodeWithTag("rule_toggle_105")
        for (i in 1..8) {
            toggleNode.performClick()
        }

        // Verify no crash and final state
        val updatedRule = ruleDao.getRuleById(105L)
        assertNotNull(updatedRule)
    }

    @Test
    fun testHistorySearch_SpecialCharacters_LiteralMatch(): Unit = runBlocking {
        // T2_F4_04: Verify history search bar query handles literal strings and regex safety
        val log1 = NotificationLogEntity(11L, "App", "com.app", "Test", "Contains * symbol", null, System.currentTimeMillis(), "ALLOW", null, null)
        val log2 = NotificationLogEntity(12L, "App", "com.app", "Test", "Contains [ symbol", null, System.currentTimeMillis(), "ALLOW", null, null)
        logDao.insertLog(log1)
        logDao.insertLog(log2)

        // Open History
        composeRule.onNodeWithTag("bottom_nav_history").performClick()

        // Type '*' in search
        composeRule.onNodeWithTag("history_search_input").performTextInput("*")

        // Expected Result: Only '*' is returned, no crash
        composeRule.onNodeWithText("Contains * symbol").assertIsDisplayed()
        composeRule.onNodeWithText("Contains [ symbol").assertDoesNotExist()
    }

    @Test
    fun testSettings_ChangeRetention_TriggersImmediatePruning(): Unit = runBlocking {
        // T2_F4_05: Verify that reducing retention immediately prunes the database
        // Older log: 10 days ago (10 * 24 * 60 * 60 * 1000 = 864,000,000 ms)
        val olderTimestamp = System.currentTimeMillis() - (10L * 24 * 60 * 60 * 1000)
        val newerTimestamp = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)

        val log1 = NotificationLogEntity(21L, "Slack", "com.slack", "Old", "Old log text", null, olderTimestamp, "BLOCK", null, null)
        val log2 = NotificationLogEntity(22L, "Slack", "com.slack", "New", "New log text", null, newerTimestamp, "BLOCK", null, null)
        logDao.insertLog(log1)
        logDao.insertLog(log2)

        android.util.Log.d("TEST_DIAG", "olderTimestamp: $olderTimestamp, newerTimestamp: $newerTimestamp")
        android.util.Log.d("TEST_DIAG", "DB logs before click: ${logDao.getAllLogsFlow().first()}")

        // Change retention period in Settings from "30 Days" to "7 Days"
        composeRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeRule.onNodeWithTag("settings_retention_pref").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_retention_7_days").performScrollTo().performClick()
        composeRule.waitForIdle()

        android.util.Log.d("TEST_DIAG", "DB logs immediately after click: ${logDao.getAllLogsFlow().first()}")

        // Verify the 10-day-old log is removed from DB, 3-day-old remains
        var remainingLogs = logDao.getAllLogsFlow().first()
        var attempts = 0
        while (remainingLogs.any { it.id == 21L } && attempts < 20) {
            kotlinx.coroutines.delay(100)
            remainingLogs = logDao.getAllLogsFlow().first()
            attempts++
        }

        android.util.Log.d("TEST_DIAG", "DB logs after retry loop: $remainingLogs")

        assertTrue(remainingLogs.none { it.id == 21L })
        assertTrue(remainingLogs.any { it.id == 22L })
    }
}
