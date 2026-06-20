package com.hush.app.domain.model

import java.time.Instant

data class NotificationEvent(
    val id: Long = 0,
    val appName: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val sender: String?,
    val timestamp: Instant,
    val actionTaken: RuleAction,
    val matchedRuleId: Long?,
    val matchedRuleName: String?
)
