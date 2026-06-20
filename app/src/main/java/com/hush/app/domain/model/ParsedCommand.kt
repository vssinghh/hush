package com.hush.app.domain.model

import java.time.LocalTime

data class ParsedCommand(
    val action: RuleAction,
    val app: String?,
    val matchField: MatchField,
    val matchType: MatchType,
    val matchPattern: String?,
    val isInverted: Boolean,
    val timeStart: LocalTime?,
    val timeEnd: LocalTime?,
    val summary: String
)
