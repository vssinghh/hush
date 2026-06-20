package com.hush.app.domain.model

import java.time.Instant
import java.time.LocalTime

data class Rule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val originalPrompt: String,
    val appPackage: String?,
    val appDisplayName: String?,
    val matchField: MatchField,
    val matchType: MatchType,
    val matchPattern: String?,
    val isInverted: Boolean,
    val action: RuleAction,
    val timeStart: LocalTime?,
    val timeEnd: LocalTime?,
    val priority: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class RuleAction {
    ALLOW, BLOCK, MUTE
}

enum class MatchField {
    TITLE, TEXT, SENDER, ANY
}

enum class MatchType {
    CONTAINS, REGEX, EXACT
}
