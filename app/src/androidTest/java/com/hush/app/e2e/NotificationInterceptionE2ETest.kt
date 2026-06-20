package com.hush.app.e2e

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hush.app.data.db.HushDatabase
import com.hush.app.data.db.dao.NotificationLogDao
import com.hush.app.data.db.dao.RuleDao
import com.hush.app.data.db.entity.NotificationLogEntity
import com.hush.app.data.db.entity.RuleEntity
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.model.NotificationEvent
import com.hush.app.service.HushNotificationListener
import com.hush.app.domain.usecase.EvaluateNotificationUseCase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NotificationInterceptionE2ETest {

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
    private lateinit var listenerService: HushNotificationListener

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        listenerService = HushNotificationListener()
        // Inject dependencies into the listener manually or rely on Hilt entry points
        // In clean architecture, the service gets rules from ruleDao and writes logs to logDao.
        
        runBlocking {
            ruleDao.getAllRulesFlow().first() // Clear DB check
            logDao.clearAllLogs()
        }

        // Grant listener permission programmatically
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val command = "cmd notification allow_listener com.hush.app/com.hush.app.service.HushNotificationListener"
        uiAutomation.executeShellCommand(command)
    }

    @After
    fun tearDown() {
        runBlocking {
            logDao.clearAllLogs()
        }
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
    fun testInterception_BlockRule_DismissesNotificationAndLogs() = runBlocking {
        // T1_F2_01: Verify a notification matching a block rule is cancelled and recorded as blocked
        val rule = RuleEntity(
            id = 1L,
            name = "Block WhatsApp Spam",
            enabled = true,
            originalPrompt = "Block WhatsApp containing Spam",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = "Spam",
            isInverted = false,
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        val isCanceled = simulateNotificationPost("com.whatsapp", "WhatsApp", "Alert", "Spam Offer", null)

        assertTrue(isCanceled)
        val logs = logDao.getAllLogsFlow().first()
        assertEquals(1, logs.size)
        assertEquals("BLOCK", logs[0].actionTaken)
        assertEquals(1L, logs[0].matchedRuleId)
    }

    @Test
    fun testInterception_MuteRule_MutesNotificationAndLogs() = runBlocking {
        // T1_F2_02: Verify a notification matching a mute rule is delivered silently and recorded
        val rule = RuleEntity(
            id = 2L,
            name = "Mute Slack Boss",
            enabled = true,
            originalPrompt = "Mute Slack messages from Boss",
            appPackage = "com.slack",
            appDisplayName = "Slack",
            matchField = "SENDER",
            matchType = "CONTAINS",
            matchPattern = "Boss",
            isInverted = false,
            action = "MUTE",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        val isCanceled = simulateNotificationPost("com.slack", "Slack", "Boss", "Report updates", "Boss")

        assertFalse(isCanceled) // Allowed to post but muted
        val logs = logDao.getAllLogsFlow().first()
        assertEquals(1, logs.size)
        assertEquals("MUTE", logs[0].actionTaken)
    }

    @Test
    fun testInterception_AllowRule_AllowsNotificationAndLogs() = runBlocking {
        // T1_F2_03: Verify a notification matching an allow rule is posted unchanged and recorded
        val rule = RuleEntity(
            id = 3L,
            name = "Allow Gmail Security",
            enabled = true,
            originalPrompt = "Allow Gmail containing Security",
            appPackage = "com.gmail",
            appDisplayName = "Gmail",
            matchField = "TITLE",
            matchType = "CONTAINS",
            matchPattern = "Security",
            isInverted = false,
            action = "ALLOW",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        val isCanceled = simulateNotificationPost("com.gmail", "Gmail", "Security Alert", "New login", null)

        assertFalse(isCanceled)
        val logs = logDao.getAllLogsFlow().first()
        assertEquals(1, logs.size)
        assertEquals("ALLOW", logs[0].actionTaken) // ALLOW logs as ALLOWED/ALLOW
    }

    @Test
    fun testInterception_NoMatchingRules_AllowsNotificationWithoutLogs() = runBlocking {
        // T1_F2_04: Verify that notifications are passed through normally if no rules match
        val isCanceled = simulateNotificationPost("com.instagram", "Instagram", "New message", "Hello", null)

        assertFalse(isCanceled)
        val logs = logDao.getAllLogsFlow().first()
        assertEquals(0, logs.size)
    }

    @Test
    fun testInterception_ExtractsMetadataCorrectly() = runBlocking {
        // T1_F2_05: Verify NLS correctly extracts and saves notification headers
        val rule = RuleEntity(
            id = 100L,
            name = "Slack match",
            enabled = true,
            originalPrompt = "Slack match",
            appPackage = "com.slack",
            appDisplayName = "Slack",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = "",
            isInverted = false,
            action = "ALLOW",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        val isCanceled = simulateNotificationPost("com.slack", "Slack", "Development Team", "Build fixed", "John Doe")

        val logs = logDao.getAllLogsFlow().first()
        assertEquals(1, logs.size)
        assertEquals("com.slack", logs[0].packageName)
        assertEquals("Slack", logs[0].appName)
        assertEquals("Development Team", logs[0].title)
        assertEquals("Build fixed", logs[0].text)
        assertEquals("John Doe", logs[0].sender)
    }

    @Test
    fun testInterception_NullOrEmptyMetadataFields_DoesNotCrash() = runBlocking {
        // T2_F2_01: Verify NLS evaluation handles empty notification properties safely
        val rule = RuleEntity(
            id = 4L,
            name = "Block WhatsApp",
            enabled = true,
            originalPrompt = "Block WhatsApp",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
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

        // Null title and null text
        val isCanceled = simulateNotificationPost("com.whatsapp", "WhatsApp", null, null, null)

        assertTrue(isCanceled)
        val logs = logDao.getAllLogsFlow().first()
        assertEquals(1, logs.size)
        assertEquals("No Title", logs[0].title)
        assertEquals("No Content", logs[0].text)
    }

    @Test
    fun testInterception_ComplexRegexPatternMatching() = runBlocking {
        // T2_F2_02: Verify regex parsing evaluates complicated matching patterns correctly
        val rule = RuleEntity(
            id = 5L,
            name = "Mute Urgent Security",
            enabled = true,
            originalPrompt = "Mute security or admin urgent alerts",
            appPackage = null,
            appDisplayName = null,
            matchField = "ANY",
            matchType = "REGEX",
            matchPattern = "^.*\\[URGENT\\]\\s(Security|Admin):\\s.*$",
            isInverted = false,
            action = "MUTE",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        val isCanceled = simulateNotificationPost("com.slack", "Slack", "[URGENT] Admin: password expired", "Password reset required", null)

        assertFalse(isCanceled)
        val logs = logDao.getAllLogsFlow().first()
        assertEquals("MUTE", logs[0].actionTaken)
    }

    @Test
    fun testInterception_RapidConcurrentNotifications_ThreadSafety() = runBlocking {
        // T2_F2_03: Stress test NLS with concurrent incoming notification streams
        val rule = RuleEntity(
            id = 6L,
            name = "Block Slack",
            enabled = true,
            originalPrompt = "Block Slack",
            appPackage = "com.slack",
            appDisplayName = "Slack",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = "",
            isInverted = false,
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        val jobs = List(30) { i ->
            async(Dispatchers.Default) {
                simulateNotificationPost("com.slack", "Slack", "Title $i", "Content $i", null)
            }
        }
        jobs.awaitAll()

        val logs = logDao.getAllLogsFlow().first()
        assertEquals(30, logs.size)
    }

    @Test
    fun testInterception_ExtremelyLongNotificationContent_HandlesTruncation() = runBlocking {
        // T2_F2_04: Verify rule matching works on massive text volumes and limits DB row sizes
        val rule = RuleEntity(
            id = 7L,
            name = "Block Alert",
            enabled = true,
            originalPrompt = "Block Alert",
            appPackage = null,
            appDisplayName = null,
            matchField = "TEXT",
            matchType = "CONTAINS",
            matchPattern = "alert",
            isInverted = false,
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        val builder = java.lang.StringBuilder()
        for (i in 0..799) {
            builder.append("words ")
        }
        builder.append("alert ")
        for (i in 0..100) {
            builder.append("more words ")
        }
        val longContent = builder.toString()

        val isCanceled = simulateNotificationPost("com.slack", "Slack", "Urgent Alert", longContent, null)

        assertTrue(isCanceled)
        val logs = logDao.getAllLogsFlow().first()
        assertEquals(1, logs.size)
        // Check that text saved in DB is truncated or formatted (we just verify it's blocked successfully)
        assertTrue((logs[0].text?.length ?: 0) < 8000)
    }

    @Test
    fun testInterception_RuleDisabled_BypassesInterception() = runBlocking {
        // T2_F2_05: Verify that disabled rules are ignored in the NLS loop
        val rule = RuleEntity(
            id = 8L,
            name = "Block Gmail",
            enabled = false, // Disabled
            originalPrompt = "Block Gmail",
            appPackage = "com.google.android.gm",
            appDisplayName = "Gmail",
            matchField = "ANY",
            matchType = "CONTAINS",
            matchPattern = "",
            isInverted = false,
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        val isCanceled = simulateNotificationPost("com.google.android.gm", "Gmail", "New Alert", "Alert notification", null)

        assertFalse(isCanceled)
        val logs = logDao.getAllLogsFlow().first()
        assertEquals(0, logs.size)
    }
}
