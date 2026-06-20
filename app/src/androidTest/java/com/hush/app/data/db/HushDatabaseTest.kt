package com.hush.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hush.app.data.db.dao.RuleDao
import com.hush.app.data.db.entity.RuleEntity
import com.hush.app.data.db.entity.NotificationLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class HushDatabaseTest {

    private lateinit var db: HushDatabase
    private lateinit var ruleDao: RuleDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HushDatabase::class.java).build()
        ruleDao = db.ruleDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeRuleAndReadInList() = runBlocking {
        val rule = RuleEntity(
            id = 1L,
            name = "Mute WhatsApp except Alice",
            enabled = true,
            originalPrompt = "mute whatsapp except alice",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = "SENDER",
            matchType = "EXACT",
            matchPattern = "Alice",
            isInverted = true,
            action = "MUTE",
            timeStart = null,
            timeEnd = null,
            priority = 1,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)
        val retrieved = ruleDao.getRuleById(1L)
        assertNotNull(retrieved)
        assertEquals("Mute WhatsApp except Alice", retrieved?.name)
    }

    @Test
    @Throws(Exception::class)
    fun testRuleDaoCRUD() = runBlocking {
        val rule1 = RuleEntity(
            id = 10L,
            name = "Rule 1",
            enabled = true,
            originalPrompt = "prompt 1",
            appPackage = "pkg.1",
            appDisplayName = "App 1",
            matchField = "TITLE",
            matchType = "CONTAINS",
            matchPattern = "pattern 1",
            isInverted = false,
            action = "BLOCK",
            timeStart = null,
            timeEnd = null,
            priority = 5,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val rule2 = RuleEntity(
            id = 20L,
            name = "Rule 2",
            enabled = false,
            originalPrompt = "prompt 2",
            appPackage = "pkg.2",
            appDisplayName = "App 2",
            matchField = "TEXT",
            matchType = "REGEX",
            matchPattern = "pattern 2",
            isInverted = false,
            action = "MUTE",
            timeStart = null,
            timeEnd = null,
            priority = 2,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Test insert and getMaxPriority
        assertEquals(null, ruleDao.getMaxPriority())
        ruleDao.insertRule(rule1)
        ruleDao.insertRule(rule2)
        assertEquals(5, ruleDao.getMaxPriority())

        // Test getAllRulesFlow (ordered by priority ASC: rule2 priority 2, rule1 priority 5)
        val allRules = ruleDao.getAllRulesFlow().first()
        assertEquals(2, allRules.size)
        assertEquals(20L, allRules[0].id)
        assertEquals(10L, allRules[1].id)

        // Test getActiveRules (only rule1, since rule2 is disabled)
        val activeRules = ruleDao.getActiveRules()
        assertEquals(1, activeRules.size)
        assertEquals(10L, activeRules[0].id)

        // Test updateRule
        val updatedRule = rule1.copy(name = "Updated Rule 1", enabled = false)
        ruleDao.updateRule(updatedRule)
        val retrievedUpdated = ruleDao.getRuleById(10L)
        assertNotNull(retrievedUpdated)
        assertEquals("Updated Rule 1", retrievedUpdated?.name)
        assertEquals(false, retrievedUpdated?.enabled)

        // Test deleteRule
        ruleDao.deleteRule(updatedRule)
        assertEquals(null, ruleDao.getRuleById(10L))

        // Test deleteRuleById
        ruleDao.deleteRuleById(20L)
        assertEquals(null, ruleDao.getRuleById(20L))
        assertEquals(0, ruleDao.getAllRulesFlow().first().size)
    }

    @Test
    @Throws(Exception::class)
    fun testNotificationLogDaoCRUD() = runBlocking {
        val logDao = db.notificationLogDao()

        // Insert rule first due to foreign key constraint
        val rule = RuleEntity(
            id = 100L,
            name = "Rule for Log",
            enabled = true,
            originalPrompt = "prompt",
            appPackage = "pkg",
            appDisplayName = "App",
            matchField = "TEXT",
            matchType = "CONTAINS",
            matchPattern = "match",
            isInverted = false,
            action = "MUTE",
            timeStart = null,
            timeEnd = null,
            priority = 1,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)

        val log1 = NotificationLogEntity(
            id = 1L,
            appName = "App One",
            packageName = "pkg.one",
            title = "Title One",
            text = "Hello World text",
            sender = "Sender One",
            timestamp = 1000L,
            actionTaken = "MUTE",
            matchedRuleId = 100L,
            matchedRuleName = "Rule for Log"
        )
        val log2 = NotificationLogEntity(
            id = 2L,
            appName = "App Two",
            packageName = "pkg.two",
            title = "Title Two",
            text = "Goodbye World text",
            sender = "Sender Two",
            timestamp = 2000L,
            actionTaken = "BLOCK",
            matchedRuleId = null,
            matchedRuleName = null
        )

        // Test insert and getAllLogsFlow (ordered by timestamp DESC)
        logDao.insertLog(log1)
        logDao.insertLog(log2)

        val allLogs = logDao.getAllLogsFlow().first()
        assertEquals(2, allLogs.size)
        assertEquals(2L, allLogs[0].id) // timestamp 2000L first
        assertEquals(1L, allLogs[1].id) // timestamp 1000L second

        // Test getLogsByActionFlow
        val muteLogs = logDao.getLogsByActionFlow("MUTE").first()
        assertEquals(1, muteLogs.size)
        assertEquals(1L, muteLogs[0].id)

        // Test searchLogsFlow
        val searchResults = logDao.searchLogsFlow("Goodbye").first()
        assertEquals(1, searchResults.size)
        assertEquals(2L, searchResults[0].id)

        // Test deleteLogsOlderThan
        logDao.deleteLogsOlderThan(1500L)
        val remainingLogs = logDao.getAllLogsFlow().first()
        assertEquals(1, remainingLogs.size)
        assertEquals(2L, remainingLogs[0].id)

        // Test clearAllLogs
        logDao.clearAllLogs()
        assertEquals(0, logDao.getAllLogsFlow().first().size)
    }
}

