package com.hush.app.domain.usecase

import com.hush.app.domain.model.*
import com.hush.app.domain.repository.HistoryRepository
import com.hush.app.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class FakeRuleRepository : RuleRepository {
    var rules: List<Rule> = emptyList()
    override fun getAllRules(): Flow<List<Rule>> = throw UnsupportedOperationException()
    override suspend fun getActiveRules(): List<Rule> = rules.filter { it.enabled }.sortedBy { it.priority }
    override suspend fun getRuleById(id: Long): Rule? = rules.find { it.id == id }
    override suspend fun insertRule(rule: Rule): Long = throw UnsupportedOperationException()
    override suspend fun updateRule(rule: Rule) = throw UnsupportedOperationException()
    override suspend fun deleteRule(rule: Rule) = throw UnsupportedOperationException()
    override suspend fun deleteRuleById(id: Long) = throw UnsupportedOperationException()
    override suspend fun getNextPriority(): Int = 0
}

class FakeHistoryRepository : HistoryRepository {
    val loggedEvents = mutableListOf<NotificationEvent>()
    override fun getAllLogs(): Flow<List<NotificationEvent>> = throw UnsupportedOperationException()
    override fun getLogsByAction(action: RuleAction): Flow<List<NotificationEvent>> = throw UnsupportedOperationException()
    override fun searchLogs(query: String): Flow<List<NotificationEvent>> = throw UnsupportedOperationException()
    override suspend fun insertLog(log: NotificationEvent): Long {
        loggedEvents.add(log)
        return loggedEvents.size.toLong()
    }
    override suspend fun deleteLogsOlderThan(threshold: Instant) = throw UnsupportedOperationException()
    override suspend fun clearAllLogs() {
        loggedEvents.clear()
    }
}

class EvaluateNotificationUseCaseTest {

    private val ruleRepository = FakeRuleRepository()
    private val historyRepository = FakeHistoryRepository()
    private val useCase = EvaluateNotificationUseCase(ruleRepository, historyRepository)

    private fun createRule(timeStart: LocalTime?, timeEnd: LocalTime?): Rule {
        return Rule(
            id = 1L,
            name = "Test Time Window Rule",
            enabled = true,
            originalPrompt = "Block Whatsapp",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = "",
            isInverted = false,
            action = RuleAction.BLOCK,
            timeStart = timeStart,
            timeEnd = timeEnd,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    // --- Standard Daytime Window (09:00 - 17:00) ---

    @Test
    fun testDaytimeWindow_Inside() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(9, 0), LocalTime.of(17, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, action)
    }

    @Test
    fun testDaytimeWindow_Outside_Before() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(9, 0), LocalTime.of(17, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(8, 59))
        assertEquals(RuleAction.ALLOW, action)
    }

    @Test
    fun testDaytimeWindow_Outside_After() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(9, 0), LocalTime.of(17, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(17, 1))
        assertEquals(RuleAction.ALLOW, action)
    }

    @Test
    fun testDaytimeWindow_BoundaryStart() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(9, 0), LocalTime.of(17, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(9, 0))
        assertEquals(RuleAction.BLOCK, action)
    }

    @Test
    fun testDaytimeWindow_BoundaryEnd() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(9, 0), LocalTime.of(17, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(17, 0))
        assertEquals(RuleAction.BLOCK, action)
    }

    // --- Overnight Window (22:00 - 07:00) ---

    @Test
    fun testOvernightWindow_Inside_BeforeMidnight() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(23, 0))
        assertEquals(RuleAction.BLOCK, action)
    }

    @Test
    fun testOvernightWindow_Inside_AfterMidnight() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(3, 0))
        assertEquals(RuleAction.BLOCK, action)
    }

    @Test
    fun testOvernightWindow_Outside_Daytime() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.ALLOW, action)
    }

    @Test
    fun testOvernightWindow_BoundaryStart() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(22, 0))
        assertEquals(RuleAction.BLOCK, action)
    }

    @Test
    fun testOvernightWindow_BoundaryEnd() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(7, 0))
        assertEquals(RuleAction.BLOCK, action)
    }

    @Test
    fun testOvernightWindow_Outside_BeforeStart() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(21, 59))
        assertEquals(RuleAction.ALLOW, action)
    }

    @Test
    fun testOvernightWindow_Outside_AfterEnd() = runBlocking {
        ruleRepository.rules = listOf(createRule(LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(7, 1))
        assertEquals(RuleAction.ALLOW, action)
    }

    @Test
    fun testInversion_Match() = runBlocking {
        val rule = Rule(
            id = 10L,
            name = "Inverted Block Rule",
            enabled = true,
            originalPrompt = "Block unless urgent",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TEXT,
            matchType = MatchType.CONTAINS,
            matchPattern = "urgent",
            isInverted = true,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        ruleRepository.rules = listOf(rule)

        // text does not contain "urgent" -> matches inverted rule -> BLOCK
        val action1 = useCase.execute("com.whatsapp", "WhatsApp", "title", "hello world", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, action1)

        // text contains "urgent" -> does not match inverted rule -> ALLOW
        val action2 = useCase.execute("com.whatsapp", "WhatsApp", "title", "urgent message", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.ALLOW, action2)
    }

    @Test
    fun testAppPackageMatching() = runBlocking {
        val rule = Rule(
            id = 11L,
            name = "WhatsApp block",
            enabled = true,
            originalPrompt = "Block WhatsApp",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = "",
            isInverted = false,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        ruleRepository.rules = listOf(rule)

        // com.whatsapp -> matches -> BLOCK
        val action1 = useCase.execute("com.whatsapp", "WhatsApp", "title", "text", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, action1)

        // org.telegram.messenger -> different package -> no match -> ALLOW
        val action2 = useCase.execute("org.telegram.messenger", "Telegram", "title", "text", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.ALLOW, action2)
    }

    @Test
    fun testLoggingLogic_MatchesAndNoMatch() = runBlocking {
        historyRepository.clearAllLogs()

        val rule = Rule(
            id = 12L,
            name = "Log test rule",
            enabled = true,
            originalPrompt = "Block WhatsApp",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = "secret",
            isInverted = false,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        ruleRepository.rules = listOf(rule)

        // No match -> ALLOW -> should not insert a log
        val actionNoMatch = useCase.execute("com.whatsapp", "WhatsApp", "title", "ordinary message", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.ALLOW, actionNoMatch)
        assertEquals(0, historyRepository.loggedEvents.size)

        // Match -> BLOCK -> should insert a log
        val actionMatch = useCase.execute("com.whatsapp", "WhatsApp", "title", "this is secret", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, actionMatch)
        assertEquals(1, historyRepository.loggedEvents.size)
        assertEquals(12L, historyRepository.loggedEvents[0].matchedRuleId)
    }

    @Test
    fun testRegexMatching() = runBlocking {
        val rule = Rule(
            id = 13L,
            name = "Regex block",
            enabled = true,
            originalPrompt = "Block digits",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TEXT,
            matchType = MatchType.REGEX,
            matchPattern = "\\d+",
            isInverted = false,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        ruleRepository.rules = listOf(rule)

        // text with digits -> matches -> BLOCK
        val action1 = useCase.execute("com.whatsapp", "WhatsApp", "title", "code 1234", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, action1)

        // text without digits -> no match -> ALLOW
        val action2 = useCase.execute("com.whatsapp", "WhatsApp", "title", "code only letters", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.ALLOW, action2)
    }

    @Test
    fun testPriorityMatching_LowerPriorityRunsFirst() = runBlocking {
        val ruleA = Rule(
            id = 14L,
            name = "High priority number block",
            enabled = true,
            originalPrompt = "Block",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = "test",
            isInverted = false,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 10,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val ruleB = Rule(
            id = 15L,
            name = "Low priority number mute",
            enabled = true,
            originalPrompt = "Mute",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = "test",
            isInverted = false,
            action = RuleAction.MUTE,
            timeStart = null,
            timeEnd = null,
            priority = 5,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        ruleRepository.rules = listOf(ruleA, ruleB)

        // should match ruleB (priority 5) instead of ruleA (priority 10) -> MUTE
        val action = useCase.execute("com.whatsapp", "WhatsApp", "title", "test message", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.MUTE, action)
    }

    // --- CHALLENGER ADDITIONAL TESTS ---

    // 1. Overnight cross-midnight time ranges: exhaustively test boundaries and null values
    @Test
    fun testOvernight_NullTimeStartOrEnd_Skipped() = runBlocking {
        // Rule with timeStart but no timeEnd
        val rule1 = createRule(LocalTime.of(22, 0), null)
        // Rule with no timeStart but timeEnd
        val rule2 = createRule(null, LocalTime.of(7, 0))

        ruleRepository.rules = listOf(rule1)
        val action1 = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(23, 0))
        // Since rule1 has null timeEnd, time range check is skipped, meaning it matches and blocks
        assertEquals(RuleAction.BLOCK, action1)

        ruleRepository.rules = listOf(rule2)
        val action2 = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(3, 0))
        // Since rule2 has null timeStart, time range check is skipped, meaning it matches and blocks
        assertEquals(RuleAction.BLOCK, action2)
    }

    @Test
    fun testTimeWindow_ExactEqualTimes() = runBlocking {
        // Start and end are exactly equal (e.g., 12:00 to 12:00)
        val rule = createRule(LocalTime.of(12, 0), LocalTime.of(12, 0))
        ruleRepository.rules = listOf(rule)

        // At 12:00 -> should match and BLOCK
        val actionAt = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, actionAt)

        // At 12:01 -> should NOT match and ALLOW
        val actionAfter = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(12, 1))
        assertEquals(RuleAction.ALLOW, actionAfter)

        // At 11:59 -> should NOT match and ALLOW
        val actionBefore = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(11, 59))
        assertEquals(RuleAction.ALLOW, actionBefore)
    }

    @Test
    fun testOvernightWindow_MidnightEdgeCases() = runBlocking {
        // Overnight range from 23:00 to 01:00
        val rule = createRule(LocalTime.of(23, 0), LocalTime.of(1, 0))
        ruleRepository.rules = listOf(rule)

        // Exactly at midnight (00:00) -> should BLOCK
        val actionMidnight = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(0, 0))
        assertEquals(RuleAction.BLOCK, actionMidnight)

        // Exactly at start (23:00) -> should BLOCK
        val actionStart = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(23, 0))
        assertEquals(RuleAction.BLOCK, actionStart)

        // Exactly at end (01:00) -> should BLOCK
        val actionEnd = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(1, 0))
        assertEquals(RuleAction.BLOCK, actionEnd)

        // One minute before start (22:59) -> should ALLOW
        val actionBefore = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(22, 59))
        assertEquals(RuleAction.ALLOW, actionBefore)

        // One minute after end (01:01) -> should ALLOW
        val actionAfter = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(1, 1))
        assertEquals(RuleAction.ALLOW, actionAfter)
    }

    @Test
    fun testTimeWindow_StressTestAllMinutesExhaustive() = runBlocking {
        val standardStart = LocalTime.of(9, 0)
        val standardEnd = LocalTime.of(17, 0)
        val standardRule = createRule(standardStart, standardEnd)

        val overnightStart = LocalTime.of(22, 0)
        val overnightEnd = LocalTime.of(7, 0)
        val overnightRule = createRule(overnightStart, overnightEnd)

        for (h in 0..23) {
            for (m in 0..59) {
                val time = LocalTime.of(h, m)
                
                // Assert for standard (09:00 to 17:00)
                ruleRepository.rules = listOf(standardRule)
                val expectedStandard = if (h in 9..17) {
                    if (h == 9) true
                    else if (h == 17) m == 0
                    else true
                } else {
                    false
                }
                val standardAction = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, time)
                val standardMatched = standardAction == RuleAction.BLOCK
                assertEquals("Failed standard time window check at $time", expectedStandard, standardMatched)

                // Assert for overnight (22:00 to 07:00)
                ruleRepository.rules = listOf(overnightRule)
                val expectedOvernight = if (h >= 22 || h < 7) {
                    true
                } else if (h == 7) {
                    m == 0
                } else {
                    false
                }
                val overnightAction = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, time)
                val overnightMatched = overnightAction == RuleAction.BLOCK
                assertEquals("Failed overnight time window check at $time", expectedOvernight, overnightMatched)
            }
        }
    }

    // 2. Regex patterns matching and edge cases (invalid regex patterns)
    @Test
    fun testRegex_InvalidPatterns_DoesNotCrash() = runBlocking {
        // Test various malformed/invalid regex patterns
        val invalidPatterns = listOf("[", "(", "*", "\\", "?", "++", "[a-z")
        
        for (pattern in invalidPatterns) {
            val rule = Rule(
                id = 100L,
                name = "Invalid Regex Rule",
                enabled = true,
                originalPrompt = "Block invalid",
                appPackage = "com.whatsapp",
                appDisplayName = "WhatsApp",
                matchField = MatchField.TEXT,
                matchType = MatchType.REGEX,
                matchPattern = pattern,
                isInverted = false,
                action = RuleAction.BLOCK,
                timeStart = null,
                timeEnd = null,
                priority = 0,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            ruleRepository.rules = listOf(rule)
            
            // Should catch error and return ALLOW (since match fails)
            val action = useCase.execute("com.whatsapp", "WhatsApp", "title", "hello world", null, LocalTime.of(12, 0))
            assertEquals("Should not crash and should default to ALLOW for pattern: $pattern", RuleAction.ALLOW, action)
        }
    }

    @Test
    fun testRegex_InvalidPatterns_Inverted_Blocks() = runBlocking {
        // If regex is invalid and inverted is true: runCatching returns false (no match), inverted converts to true -> BLOCKS
        val rule = Rule(
            id = 101L,
            name = "Invalid Regex Inverted Rule",
            enabled = true,
            originalPrompt = "Block inverted invalid",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TEXT,
            matchType = MatchType.REGEX,
            matchPattern = "[",
            isInverted = true,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        ruleRepository.rules = listOf(rule)
        
        val action = useCase.execute("com.whatsapp", "WhatsApp", "title", "hello world", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, action)
    }

    @Test
    fun testRegex_NullOrEmptyFields() = runBlocking {
        val ruleEmptyPattern = Rule(
            id = 102L,
            name = "Empty Pattern Regex",
            enabled = true,
            originalPrompt = "Block empty",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TEXT,
            matchType = MatchType.REGEX,
            matchPattern = "",
            isInverted = false,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        ruleRepository.rules = listOf(ruleEmptyPattern)
        // Empty regex matches any non-null string -> BLOCK
        val action1 = useCase.execute("com.whatsapp", "WhatsApp", "title", "hello", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, action1)
        
        // Null text to evaluate: does not match -> ALLOW
        val actionNullText = useCase.execute("com.whatsapp", "WhatsApp", "title", null, null, LocalTime.of(12, 0))
        assertEquals(RuleAction.ALLOW, actionNullText)
    }

    @Test
    fun testRegex_ComplexMatchPatterns() = runBlocking {
        val rule = Rule(
            id = 103L,
            name = "Complex Regex",
            enabled = true,
            originalPrompt = "Complex regex block",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TEXT,
            matchType = MatchType.REGEX,
            matchPattern = "^[A-Z][a-z]+ \\d{3,4}$", // e.g. "John 123" or "Alex 4567"
            isInverted = false,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        ruleRepository.rules = listOf(rule)
        
        // Valid matches
        assertEquals(RuleAction.BLOCK, useCase.execute("com.whatsapp", "WhatsApp", "title", "John 123", null, LocalTime.of(12, 0)))
        assertEquals(RuleAction.BLOCK, useCase.execute("com.whatsapp", "WhatsApp", "title", "Alex 4567", null, LocalTime.of(12, 0)))

        // Non-matches
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", "title", "john 123", null, LocalTime.of(12, 0))) // lowercase
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", "title", "John 12", null, LocalTime.of(12, 0)))  // too few digits
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", "title", "John 12345", null, LocalTime.of(12, 0))) // too many digits
    }

    // 3. Priority sorting logic
    @Test
    fun testPriority_MultipleMatchingRules_AppliesLowestPriorityValueFirst() = runBlocking {
        // Priority 1 rule: ALLOW
        val rule1 = Rule(
            id = 201L,
            name = "Priority 1 rule",
            enabled = true,
            originalPrompt = "Allow WhatsApp",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TEXT,
            matchType = MatchType.CONTAINS,
            matchPattern = "urgent",
            isInverted = false,
            action = RuleAction.ALLOW,
            timeStart = null,
            timeEnd = null,
            priority = 1,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // Priority 2 rule: BLOCK
        val rule2 = Rule(
            id = 202L,
            name = "Priority 2 rule",
            enabled = true,
            originalPrompt = "Block WhatsApp",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TEXT,
            matchType = MatchType.CONTAINS,
            matchPattern = "urgent",
            isInverted = false,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 2,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // Rule 1 should be checked first and result in ALLOW
        ruleRepository.rules = listOf(rule2, rule1) // check sorting regardless of repository order
        val action = useCase.execute("com.whatsapp", "WhatsApp", "title", "urgent", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.ALLOW, action)
    }

    @Test
    fun testPriority_SamePriority_AppliesFirstInRepositoryOrder() = runBlocking {
        val rule1 = createRule(null, null).copy(id = 203L, action = RuleAction.BLOCK, priority = 5)
        val rule2 = createRule(null, null).copy(id = 204L, action = RuleAction.MUTE, priority = 5)

        // Repository returns rule1 first -> should BLOCK
        ruleRepository.rules = listOf(rule1, rule2)
        assertEquals(RuleAction.BLOCK, useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(12, 0)))

        // Repository returns rule2 first -> should MUTE
        ruleRepository.rules = listOf(rule2, rule1)
        assertEquals(RuleAction.MUTE, useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(12, 0)))
    }

    @Test
    fun testPriority_DisabledRules_AreIgnored() = runBlocking {
        // Disabled rule with priority 1 (BLOCK)
        val rule1 = createRule(null, null).copy(id = 205L, enabled = false, action = RuleAction.BLOCK, priority = 1)
        // Enabled rule with priority 2 (MUTE)
        val rule2 = createRule(null, null).copy(id = 206L, enabled = true, action = RuleAction.MUTE, priority = 2)

        ruleRepository.rules = listOf(rule1, rule2)
        // Should ignore rule1 and apply rule2 -> MUTE
        assertEquals(RuleAction.MUTE, useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(12, 0)))
    }

    // 4. Inverted matching
    @Test
    fun testInverted_ContainsMatch_And_NoMatch() = runBlocking {
        val rule = Rule(
            id = 301L,
            name = "Inverted Contains Rule",
            enabled = true,
            originalPrompt = "Block unless contains",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TEXT,
            matchType = MatchType.CONTAINS,
            matchPattern = "promo",
            isInverted = true,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        ruleRepository.rules = listOf(rule)

        // text contains "promo" -> matches normal -> inverted -> false -> ALLOW
        val action1 = useCase.execute("com.whatsapp", "WhatsApp", "title", "this is a promo message", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.ALLOW, action1)

        // text does not contain "promo" -> does not match normal -> inverted -> true -> BLOCK
        val action2 = useCase.execute("com.whatsapp", "WhatsApp", "title", "regular message", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, action2)
    }

    @Test
    fun testInverted_ExactMatch_And_NoMatch() = runBlocking {
        val rule = Rule(
            id = 302L,
            name = "Inverted Exact Rule",
            enabled = true,
            originalPrompt = "Block unless exact",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TEXT,
            matchType = MatchType.EXACT,
            matchPattern = "Ping",
            isInverted = true,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        ruleRepository.rules = listOf(rule)

        // exact match "Ping" (case-insensitive in EXACT) -> matches normal -> inverted -> false -> ALLOW
        val action1 = useCase.execute("com.whatsapp", "WhatsApp", "title", "ping", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.ALLOW, action1)

        // partial match "Ping here" -> does not match exact -> inverted -> true -> BLOCK
        val action2 = useCase.execute("com.whatsapp", "WhatsApp", "title", "ping here", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, action2)
    }

    @Test
    fun testInverted_NullFieldEvaluated() = runBlocking {
        val rule = Rule(
            id = 303L,
            name = "Inverted Title Rule",
            enabled = true,
            originalPrompt = "Block unless title contains urgent",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TITLE,
            matchType = MatchType.CONTAINS,
            matchPattern = "urgent",
            isInverted = true,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        ruleRepository.rules = listOf(rule)

        // title is null -> does not match normal -> inverted -> true -> BLOCK
        val action = useCase.execute("com.whatsapp", "WhatsApp", null, "hello", null, LocalTime.of(12, 0))
        assertEquals(RuleAction.BLOCK, action)
    }

    // --- ADDITIONAL STRESS TESTS AND ORACLES BY CHALLENGER ---

    @Test
    fun testOvernightCrossMidnight_Nanoseconds() = runBlocking {
        // Range: 22:00:00 to 07:00:00
        val rule = createRule(LocalTime.of(22, 0, 0), LocalTime.of(7, 0, 0))
        ruleRepository.rules = listOf(rule)

        // 21:59:59.999999999 -> ALLOW (just before start)
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(21, 59, 59, 999999999)))

        // 22:00:00.000000001 -> BLOCK (just after start)
        assertEquals(RuleAction.BLOCK, useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(22, 0, 0, 1)))

        // 06:59:59.999999999 -> BLOCK (just before end)
        assertEquals(RuleAction.BLOCK, useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(6, 59, 59, 999999999)))

        // 07:00:00.000000001 -> ALLOW (just after end)
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, LocalTime.of(7, 0, 0, 1)))
    }

    @Test
    fun testTimeWindowStressTesting() = runBlocking {
        val random = java.util.Random(42) // fixed seed for reproducibility
        
        // Generate random time ranges and test
        for (i in 1..1000) {
            val startSec = random.nextInt(24 * 3600)
            val endSec = random.nextInt(24 * 3600)
            val currentSec = random.nextInt(24 * 3600)
            
            val start = LocalTime.ofSecondOfDay(startSec.toLong())
            val end = LocalTime.ofSecondOfDay(endSec.toLong())
            val current = LocalTime.ofSecondOfDay(currentSec.toLong())
            
            // Expected by alternative oracle:
            val expectedInWindow = if (startSec <= endSec) {
                currentSec >= startSec && currentSec <= endSec
            } else {
                currentSec >= startSec || currentSec <= endSec
            }
            
            val rule = createRule(start, end)
            ruleRepository.rules = listOf(rule)
            
            val action = useCase.execute("com.whatsapp", "WhatsApp", "hello", "world", null, current)
            val actualInWindow = (action == RuleAction.BLOCK)
            
            assertEquals("Failed for start=$start, end=$end, current=$current", expectedInWindow, actualInWindow)
        }
    }

    @Test
    fun testAnyFieldNullMatching() = runBlocking {
        val rule = Rule(
            id = 401L,
            name = "Match ANY Rule",
            enabled = true,
            originalPrompt = "Block on any matching",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = "test",
            isInverted = false,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        ruleRepository.rules = listOf(rule)

        // 1. ALL nullable fields are null -> does not contain "test" -> ALLOW
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", null, null, null, LocalTime.of(12, 0)))

        // 2. Title has "test" -> BLOCK
        assertEquals(RuleAction.BLOCK, useCase.execute("com.whatsapp", "WhatsApp", "test", null, null, LocalTime.of(12, 0)))

        // 3. Text has "test" -> BLOCK
        assertEquals(RuleAction.BLOCK, useCase.execute("com.whatsapp", "WhatsApp", null, "test", null, LocalTime.of(12, 0)))

        // 4. Sender has "test" -> BLOCK
        assertEquals(RuleAction.BLOCK, useCase.execute("com.whatsapp", "WhatsApp", null, null, "test", LocalTime.of(12, 0)))
    }

    @Test
    fun testPrioritySortingRobustness() = runBlocking {
        val rule1 = Rule(
            id = 301L, name = "Priority 10 - block", enabled = true, originalPrompt = "",
            appPackage = "com.whatsapp", appDisplayName = "WhatsApp", matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS, matchPattern = "test", isInverted = false,
            action = RuleAction.BLOCK, timeStart = null, timeEnd = null, priority = 10,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )
        val rule2 = Rule(
            id = 302L, name = "Priority 5 - mute", enabled = true, originalPrompt = "",
            appPackage = "com.whatsapp", appDisplayName = "WhatsApp", matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS, matchPattern = "test", isInverted = false,
            action = RuleAction.MUTE, timeStart = null, timeEnd = null, priority = 5,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )
        val rule3 = Rule(
            id = 303L, name = "Priority -1 - allow", enabled = true, originalPrompt = "",
            appPackage = "com.whatsapp", appDisplayName = "WhatsApp", matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS, matchPattern = "test", isInverted = false,
            action = RuleAction.ALLOW, timeStart = null, timeEnd = null, priority = -1,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )
        val rule4 = Rule(
            id = 304L, name = "Priority 5 duplicate - allow", enabled = true, originalPrompt = "",
            appPackage = "com.whatsapp", appDisplayName = "WhatsApp", matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS, matchPattern = "test", isInverted = false,
            action = RuleAction.ALLOW, timeStart = null, timeEnd = null, priority = 5,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )

        // Case A: rules = [rule1, rule2, rule3] -> sorted priority sequence: -1, 5, 10
        // Expect match on rule3 (ALLOW)
        ruleRepository.rules = listOf(rule1, rule2, rule3)
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", "title", "test message", null, LocalTime.of(12, 0)))

        // Case B: rules = [rule1, rule2] -> sorted priority sequence: 5, 10
        // Expect match on rule2 (MUTE)
        ruleRepository.rules = listOf(rule1, rule2)
        assertEquals(RuleAction.MUTE, useCase.execute("com.whatsapp", "WhatsApp", "title", "test message", null, LocalTime.of(12, 0)))

        // Case C: rules = [rule2, rule4] -> both have priority 5. Sorted order maintains stable sorting or repository ordering.
        ruleRepository.rules = listOf(rule2, rule4)
        assertEquals(RuleAction.MUTE, useCase.execute("com.whatsapp", "WhatsApp", "title", "test message", null, LocalTime.of(12, 0)))

        ruleRepository.rules = listOf(rule4, rule2)
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", "title", "test message", null, LocalTime.of(12, 0)))
    }

    @Test
    fun testInvertedMatchingEdgeCases() = runBlocking {
        val rule = Rule(
            id = 501L,
            name = "Inverted exact match",
            enabled = true,
            originalPrompt = "Block unless exact",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = MatchField.TITLE,
            matchType = MatchType.EXACT,
            matchPattern = "urgent",
            isInverted = true,
            action = RuleAction.BLOCK,
            timeStart = null,
            timeEnd = null,
            priority = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // 1. Text is "urgent" -> match matches, inverted is false -> ALLOW
        ruleRepository.rules = listOf(rule)
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", "urgent", "text", null, LocalTime.of(12, 0)))

        // 2. Text is "UrGent" (case insensitivity check) -> match matches, inverted is false -> ALLOW
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", "UrGent", "text", null, LocalTime.of(12, 0)))

        // 3. Text is "not urgent" -> match fails, inverted is true -> BLOCK
        assertEquals(RuleAction.BLOCK, useCase.execute("com.whatsapp", "WhatsApp", "not urgent", "text", null, LocalTime.of(12, 0)))

        // 4. Text is null -> match fails, inverted is true -> BLOCK
        assertEquals(RuleAction.BLOCK, useCase.execute("com.whatsapp", "WhatsApp", null, "text", null, LocalTime.of(12, 0)))

        // 5. Pattern is null and inverted:
        // pattern = null -> matchMatches = true -> inverted = false -> ALLOW
        val nullPatternRule = rule.copy(id = 502L, matchPattern = null)
        ruleRepository.rules = listOf(nullPatternRule)
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", "urgent", "text", null, LocalTime.of(12, 0)))
        assertEquals(RuleAction.ALLOW, useCase.execute("com.whatsapp", "WhatsApp", null, "text", null, LocalTime.of(12, 0)))
    }
}

