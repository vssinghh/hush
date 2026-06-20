package com.hush.app.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hush.app.data.db.HushDatabase
import com.hush.app.data.db.dao.NotificationLogDao
import com.hush.app.data.db.dao.RuleDao
import com.hush.app.data.db.entity.RuleEntity
import com.hush.app.data.db.entity.toDomain
import com.hush.app.domain.model.RuleAction
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
import java.time.LocalTime
import java.time.format.DateTimeParseException
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AdversarialTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var evaluateNotificationUseCase: EvaluateNotificationUseCase

    @Inject
    lateinit var ruleDao: RuleDao

    @Inject
    lateinit var logDao: NotificationLogDao

    @Inject
    lateinit var db: HushDatabase

    private lateinit var context: Context

    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        db.clearAllTables()
    }

    @After
    fun tearDown() = runBlocking {
        db.clearAllTables()
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

    /**
     * T5_ADV_01: Verify that an invalid regex pattern (e.g. missing closing bracket)
     * in an inverted BLOCK rule evaluates safely without crashing, but evaluates to true under inversion,
     * thereby matching and blocking the notification.
     */
    @Test
    fun testAdversarial_InvalidRegexInvertedRule_EvaluatesToTrue() = runBlocking {
        val rule = RuleEntity(
            id = 501L,
            name = "Inverted Invalid Regex",
            enabled = true,
            originalPrompt = "Block Slack unless title matches regex [a-z",
            appPackage = "com.slack",
            appDisplayName = "Slack",
            matchField = "TITLE",
            matchType = "REGEX",
            matchPattern = "[a-z", // Invalid regex
            isInverted = true,     // Inverted
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        // Title matches standard text. The regex parser will throw an exception,
        // which gets caught by runCatching and returns false.
        // Under inversion (isInverted = true), this false is negated to true.
        // Therefore, the block rule matches and blocks the notification.
        val isBlocked = simulateNotificationPost("com.slack", "Slack", "Hello World", "Content", null)
        assertTrue("Notification should be blocked because the invalid regex match evaluates to false and is negated to true", isBlocked)
    }

    /**
     * T5_ADV_02: Verify that a global inverted sender BLOCK rule ("Block everything unless sender is Manager")
     * will block system alerts (where package is com.android.systemui and sender is null)
     * because null sender does not match "Manager", which negates to true under inversion.
     */
    @Test
    fun testAdversarial_GlobalInvertedSenderRule_BlocksSystemAlerts() = runBlocking {
        val rule = RuleEntity(
            id = 502L,
            name = "Global Inverted Sender Rule",
            enabled = true,
            originalPrompt = "Block all notifications unless sender is Manager",
            appPackage = null, // Global
            appDisplayName = null,
            matchField = "SENDER",
            matchType = "CONTAINS",
            matchPattern = "Manager",
            isInverted = true, // Inverted
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        // System notification has null sender
        val isBlocked = simulateNotificationPost("com.android.systemui", "System UI", "Low Battery", "Plug in your charger", null)
        assertTrue("Global inverted sender rule blocks system notifications with null sender", isBlocked)
    }

    /**
     * T5_ADV_03: Verify that priority ties in the database (two rules matching the same notification
     * with the same priority value) are evaluated deterministically by sorting by priority ASC, id ASC.
     */
    @Test
    fun testAdversarial_PriorityTies_AreDeterministic() = runBlocking {
        // Insert Rule B (BLOCK WhatsApp) with larger ID
        val ruleB = RuleEntity(
            id = 5032L,
            name = "Block WhatsApp",
            enabled = true,
            originalPrompt = "Block WhatsApp",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = "",
            isInverted = false,
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 5,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Insert Rule A (ALLOW WhatsApp) with smaller ID
        val ruleA = RuleEntity(
            id = 5031L,
            name = "Allow WhatsApp",
            enabled = true,
            originalPrompt = "Allow WhatsApp",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = "",
            isInverted = false,
            action = "ALLOW",
            timeStart = null,
            timeEnd = null,
            priority = 5,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Insert them in any order
        ruleDao.insertRule(ruleB)
        ruleDao.insertRule(ruleA)

        // Even though Rule B was inserted first, Rule A has a smaller ID (5031 < 5032).
        // Since we order by priority ASC, id ASC, Rule A should be evaluated first.
        val action = evaluateNotificationUseCase.execute("com.whatsapp", "WhatsApp", "Hey", "Message", null)
        assertEquals("Should evaluate to ALLOW because Rule A has a smaller ID", RuleAction.ALLOW, action)
    }

    /**
     * T5_ADV_04: Verify that overnight time ranges (e.g. 23:00 to 06:00) evaluate boundaries inclusively.
     */
    @Test
    fun testAdversarial_OvernightTimeWindow_ExactBoundaries() = runBlocking {
        val rule = RuleEntity(
            id = 504L,
            name = "Overnight Block",
            enabled = true,
            originalPrompt = "Block Instagram at night",
            appPackage = "com.instagram.android",
            appDisplayName = "Instagram",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = null,
            isInverted = false,
            action = "BLOCK",
            timeStart = "23:00",
            timeEnd = "06:00",
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        // Boundary 1: Exactly 23:00 (Start of overnight window) -> should block
        val blockedAtStart = simulateNotificationPost("com.instagram.android", "Instagram", "Alert", "Msg", null, LocalTime.of(23, 0))
        assertTrue("Should be blocked at exactly 23:00", blockedAtStart)

        // Boundary 2: Exactly 06:00 (End of overnight window) -> should block
        val blockedAtEnd = simulateNotificationPost("com.instagram.android", "Instagram", "Alert", "Msg", null, LocalTime.of(6, 0))
        assertTrue("Should be blocked at exactly 06:00", blockedAtEnd)

        // Boundary 3: Exactly 06:01 (Just after overnight window) -> should NOT block
        val blockedAfterEnd = simulateNotificationPost("com.instagram.android", "Instagram", "Alert", "Msg", null, LocalTime.of(6, 1))
        assertFalse("Should not be blocked at 06:01", blockedAfterEnd)
    }

    /**
     * T5_ADV_05: Verify that if a rule has an invalid time format in the database (e.g., "12:00 PM"),
     * mapping the entity to domain models does NOT crash the evaluation flow, but instead catches
     * the DateTimeParseException, skips the corrupt rule, and allows other rules to work.
     */
    @Test
    fun testAdversarial_MalformedTimeFormatInDatabase_IsSkippedSafely() = runBlocking {
        // 1. Insert a corrupt rule with invalid time format
        val corruptRule = RuleEntity(
            id = 505L,
            name = "Malformed Time Rule",
            enabled = true,
            originalPrompt = "Block Slack",
            appPackage = "com.slack",
            appDisplayName = "Slack",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = null,
            isInverted = false,
            action = "BLOCK",
            timeStart = "12:00 PM", // Invalid format (should be HH:mm format "12:00")
            timeEnd = "13:00",
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(corruptRule)

        // 2. Insert a valid rule that comes after the corrupt one
        val validRule = RuleEntity(
            id = 506L,
            name = "Valid Rule",
            enabled = true,
            originalPrompt = "Block Telegram",
            appPackage = "org.telegram.messenger",
            appDisplayName = "Telegram",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = null,
            isInverted = false,
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 10,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(validRule)

        // Verify getActiveRules does not throw and filters out the corrupt rule, but keeps the valid one
        val activeRules = try {
            ruleDao.getActiveRules().mapNotNull { it.toDomain() }
        } catch (e: Exception) {
            fail("Should not throw any exception when mapping corrupt rules, but threw: $e")
            emptyList()
        }

        assertEquals("Should have exactly 1 mapped active rule", 1, activeRules.size)
        assertEquals("The mapped rule should be the valid one", 506L, activeRules[0].id)

        // Also test through the evaluation flow that Telegram gets blocked but Slack does not (since corrupt rule is ignored)
        val isSlackBlocked = simulateNotificationPost("com.slack", "Slack", "Title", "Text", null)
        assertFalse("Slack notification should not be blocked because the Slack rule is corrupt and was skipped", isSlackBlocked)

        val isTelegramBlocked = simulateNotificationPost("org.telegram.messenger", "Telegram", "Title", "Text", null)
        assertTrue("Telegram notification should be blocked because the Telegram rule is valid", isTelegramBlocked)
    }

    /**
     * T5_ADV_06: Verify that one-sided time windows (e.g. timeStart is specified,
     * but timeEnd is null) are evaluated correctly.
     */
    @Test
    fun testAdversarial_OneSidedTimeWindow_EvaluatedCorrectly() = runBlocking {
        val rule = RuleEntity(
            id = 506L,
            name = "One Sided Time Block",
            enabled = true,
            originalPrompt = "Block Slack after 22:00",
            appPackage = "com.slack",
            appDisplayName = "Slack",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = null,
            isInverted = false,
            action = "BLOCK",
            timeStart = "22:00",
            timeEnd = null, // One-sided
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        // Evaluate at 12:00 PM. Since it starts at 22:00, it should NOT block.
        val blockedAt12 = simulateNotificationPost("com.slack", "Slack", "Title", "Text", null, LocalTime.of(12, 0))
        assertFalse("Notification should NOT be blocked at 12:00 PM", blockedAt12)

        // Evaluate at 23:00. It is after 22:00, so it SHOULD block.
        val blockedAt23 = simulateNotificationPost("com.slack", "Slack", "Title", "Text", null, LocalTime.of(23, 0))
        assertTrue("Notification SHOULD be blocked at 23:00", blockedAt23)
    }

    /**
     * T5_ADV_07: Verify that a rule matching MatchField.ANY with an empty pattern ("")
     * does not evaluate to matched when all fields (title, text, sender) are null.
     */
    @Test
    fun testAdversarial_EmptyPatternAnyWithNullFields_DoesNotMatch() = runBlocking {
        val rule = RuleEntity(
            id = 508L,
            name = "Empty Pattern ANY Rule",
            enabled = true,
            originalPrompt = "Block Slack",
            appPackage = "com.slack",
            appDisplayName = "Slack",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = "", // Empty pattern
            isInverted = false,
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        // When all evaluated fields are null, textToEvaluate must be null, so it shouldn't match.
        val isBlocked = simulateNotificationPost("com.slack", "Slack", null, null, null)
        assertFalse("Notification should not be blocked because null fields shouldn't match empty pattern", isBlocked)
    }
}
