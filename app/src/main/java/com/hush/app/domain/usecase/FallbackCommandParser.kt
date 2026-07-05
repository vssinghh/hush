package com.hush.app.domain.usecase

import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.AppInfo
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic, regex-based command parser used when Gemini Nano is not
 * available on the device. Handles the common command shapes so the app is
 * fully usable without on-device AI:
 *
 *   "Mute Instagram", "Block Slack after 6pm", "Silence promos",
 *   "Mute WhatsApp except from Bob", "Block emails containing invoice",
 *   "Mute Slack between 10pm and 7am"
 */
@Singleton
class FallbackCommandParser @Inject constructor() {

    fun parse(prompt: String, installedApps: List<AppInfo>): ParsedCommand {
        val text = prompt.trim()
        val lower = text.lowercase()

        val action = parseAction(lower)
        val (timeStart, timeEnd, lowerWithoutTime) = parseTimeWindow(lower)

        // "except (from) X" → inverted rule
        val exceptMatch = EXCEPT_REGEX.find(lowerWithoutTime)
        val exceptPattern = exceptMatch?.groupValues?.get(2)?.trim()?.trimEnd('.', '!', ',')
        val isFromException = exceptMatch?.groupValues?.get(1)?.isNotBlank() == true

        // "containing/with/about X" → content pattern
        val containsMatch = CONTAINS_REGEX.find(lowerWithoutTime)
        val containsPattern = containsMatch?.groupValues?.get(1)?.trim()?.trimEnd('.', '!', ',')

        val app = findApp(lowerWithoutTime, installedApps)

        // Well-known content categories ("promos", "promotions", "sales", "offers")
        val categoryPattern = CATEGORY_KEYWORDS.entries
            .firstOrNull { (keyword, _) -> Regex("\\b$keyword\\b").containsMatchIn(lowerWithoutTime) }
            ?.value

        val (matchField, matchType, matchPattern, isInverted) = when {
            exceptPattern != null -> Quad(
                if (isFromException) MatchField.SENDER else MatchField.ANY,
                MatchType.CONTAINS,
                exceptPattern,
                true
            )
            containsPattern != null -> Quad(MatchField.ANY, MatchType.CONTAINS, containsPattern, false)
            categoryPattern != null -> Quad(MatchField.ANY, MatchType.CONTAINS, categoryPattern, false)
            else -> Quad(MatchField.ANY, MatchType.CONTAINS, null, false)
        }

        if (app == null && matchPattern == null) {
            throw IllegalArgumentException(
                "Couldn't understand that command. Try something like \"Mute Instagram\" or \"Block Slack after 6pm\"."
            )
        }

        return ParsedCommand(
            action = action,
            app = app?.packageName,
            matchField = matchField,
            matchType = matchType,
            matchPattern = matchPattern,
            isInverted = isInverted,
            timeStart = timeStart,
            timeEnd = timeEnd,
            summary = buildSummary(action, app, matchField, matchPattern, isInverted, timeStart, timeEnd)
        )
    }

    private fun parseAction(lower: String): RuleAction = when {
        BLOCK_REGEX.containsMatchIn(lower) -> RuleAction.BLOCK
        ALLOW_REGEX.containsMatchIn(lower) -> RuleAction.ALLOW
        else -> RuleAction.MUTE
    }

    private fun findApp(lower: String, installedApps: List<AppInfo>): AppInfo? {
        // Longest display-name match wins ("Slack" should not match inside "slacker")
        return installedApps
            .filter { it.displayName.length >= 3 }
            .filter { app ->
                Regex("\\b${Regex.escape(app.displayName.lowercase())}\\b").containsMatchIn(lower)
            }
            .maxByOrNull { it.displayName.length }
    }

    /** Returns (timeStart, timeEnd, prompt with the time expression removed). */
    private fun parseTimeWindow(lower: String): Triple<LocalTime?, LocalTime?, String> {
        BETWEEN_REGEX.find(lower)?.let { m ->
            val start = parseClock(m.groupValues[1])
            val end = parseClock(m.groupValues[2])
            if (start != null && end != null) {
                return Triple(start, end, lower.replace(m.value, " "))
            }
        }
        AFTER_REGEX.find(lower)?.let { m ->
            parseClock(m.groupValues[1])?.let { start ->
                return Triple(start, null, lower.replace(m.value, " "))
            }
        }
        BEFORE_REGEX.find(lower)?.let { m ->
            parseClock(m.groupValues[1])?.let { end ->
                return Triple(null, end, lower.replace(m.value, " "))
            }
        }
        return Triple(null, null, lower)
    }

    /** Parses "6pm", "6 pm", "10:30pm", "22:00", "7am". */
    private fun parseClock(raw: String): LocalTime? {
        val m = CLOCK_REGEX.find(raw.trim()) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val meridiem = m.groupValues[3]
        when (meridiem) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun buildSummary(
        action: RuleAction,
        app: AppInfo?,
        matchField: MatchField,
        matchPattern: String?,
        isInverted: Boolean,
        timeStart: LocalTime?,
        timeEnd: LocalTime?
    ): String {
        val verb = when (action) {
            RuleAction.BLOCK -> "Block"
            RuleAction.MUTE -> "Mute"
            RuleAction.ALLOW -> "Allow"
        }
        val target = app?.displayName ?: "all apps"
        val sb = StringBuilder("$verb $target notifications")
        if (matchPattern != null) {
            val fieldLabel = if (matchField == MatchField.SENDER) "from" else "matching"
            sb.append(if (isInverted) " except $fieldLabel \"$matchPattern\"" else " $fieldLabel \"$matchPattern\"")
        }
        if (timeStart != null && timeEnd != null) {
            sb.append(" between ${formatTime(timeStart)} and ${formatTime(timeEnd)}")
        } else if (timeStart != null) {
            sb.append(" after ${formatTime(timeStart)}")
        } else if (timeEnd != null) {
            sb.append(" before ${formatTime(timeEnd)}")
        }
        return sb.toString()
    }

    private fun formatTime(t: LocalTime): String {
        val hour12 = when {
            t.hour == 0 -> 12
            t.hour > 12 -> t.hour - 12
            else -> t.hour
        }
        val suffix = if (t.hour < 12) "AM" else "PM"
        return if (t.minute == 0) "$hour12 $suffix" else "$hour12:${"%02d".format(t.minute)} $suffix"
    }

    private data class Quad(
        val field: MatchField,
        val type: MatchType,
        val pattern: String?,
        val inverted: Boolean
    )

    companion object {
        private val BLOCK_REGEX = Regex("\\b(block|stop|dismiss|kill|remove)\\b")
        private val ALLOW_REGEX = Regex("\\b(allow|unmute|unblock|let through|whitelist)\\b")
        private val EXCEPT_REGEX = Regex("\\bexcept\\s+(from\\s+)?(.+)$")
        private val CONTAINS_REGEX = Regex("\\b(?:containing|that contain[s]?|with the word[s]?|mentioning|about)\\s+[\"']?([\\w@.\\- ]+?)[\"']?$")
        private val BETWEEN_REGEX = Regex("\\bbetween\\s+([\\w:]+\\s?(?:am|pm)?)\\s+(?:and|-|to)\\s+([\\w:]+\\s?(?:am|pm)?)")
        private val AFTER_REGEX = Regex("\\b(?:after|past|from)\\s+(\\d{1,2}(?::\\d{2})?\\s?(?:am|pm)?)(?=\\s|$)")
        private val BEFORE_REGEX = Regex("\\b(?:before|until|till)\\s+(\\d{1,2}(?::\\d{2})?\\s?(?:am|pm)?)(?=\\s|$)")
        private val CLOCK_REGEX = Regex("(\\d{1,2})(?::(\\d{2}))?\\s?(am|pm)?")

        private val CATEGORY_KEYWORDS = mapOf(
            "promos" to "promo",
            "promotions" to "promo",
            "promotional" to "promo",
            "sales" to "sale",
            "offers" to "offer",
            "deals" to "deal",
            "marketing" to "marketing",
            "spam" to "spam",
            "otp" to "otp",
            "newsletters" to "newsletter"
        )
    }
}
