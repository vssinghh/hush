package com.hush.app.domain.usecase

import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.RuleRepository
import com.hush.app.domain.repository.HistoryRepository
import com.hush.app.domain.model.NotificationEvent
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvaluateNotificationUseCase @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val historyRepository: HistoryRepository
) {
    suspend fun execute(
        packageName: String,
        appName: String,
        title: String?,
        text: String?,
        sender: String?,
        currentTime: LocalTime = LocalTime.now()
    ): RuleAction {
        val rules = ruleRepository.getActiveRules()
        var matchedRuleId: Long? = null
        var matchedRuleName: String? = null
        var action = RuleAction.ALLOW

        for (rule in rules) {
            val appMatches = rule.appPackage == null || rule.appPackage == packageName
            if (!appMatches) continue

            // Time range checking
            val inWindow = when {
                rule.timeStart != null && rule.timeEnd != null -> {
                    if (rule.timeStart.isAfter(rule.timeEnd)) {
                        // overnight range e.g. 22:00 to 07:00
                        !currentTime.isBefore(rule.timeStart) || !currentTime.isAfter(rule.timeEnd)
                    } else {
                        // normal range e.g. 09:00 to 17:00
                        !currentTime.isBefore(rule.timeStart) && !currentTime.isAfter(rule.timeEnd)
                    }
                }
                rule.timeStart != null -> {
                    !currentTime.isBefore(rule.timeStart)
                }
                rule.timeEnd != null -> {
                    !currentTime.isAfter(rule.timeEnd)
                }
                else -> true
            }
            if (!inWindow) continue

            var fieldMatches = false
            val textToEvaluate = when (rule.matchField) {
                MatchField.TITLE -> title
                MatchField.TEXT -> text
                MatchField.SENDER -> sender
                MatchField.ANY -> {
                    if (title == null && text == null && sender == null) {
                        null
                    } else {
                        listOfNotNull(title, text, sender).joinToString(" ")
                    }
                }
            }

            val pattern = rule.matchPattern
            if (pattern != null && textToEvaluate != null) {
                fieldMatches = when (rule.matchType) {
                    MatchType.CONTAINS -> textToEvaluate.contains(pattern, ignoreCase = true)
                    MatchType.EXACT -> textToEvaluate.equals(pattern, ignoreCase = true)
                    MatchType.REGEX -> runCatching { Regex(pattern).containsMatchIn(textToEvaluate) }.getOrDefault(false)
                }
            } else if (pattern == null) {
                fieldMatches = true
            }

            if (rule.isInverted) {
                fieldMatches = !fieldMatches
            }

            if (fieldMatches) {
                matchedRuleId = rule.id
                matchedRuleName = rule.name
                action = rule.action
                break
            }
        }

        // Log history (Only log when matchedRuleId != null)
        if (matchedRuleId != null) {
            val event = NotificationEvent(
                appName = appName,
                packageName = packageName,
                title = title ?: "No Title",
                text = text ?: "No Content",
                sender = sender,
                timestamp = Instant.now(),
                actionTaken = action,
                matchedRuleId = matchedRuleId,
                matchedRuleName = matchedRuleName
            )
            historyRepository.insertLog(event)
        }

        return action
    }
}
