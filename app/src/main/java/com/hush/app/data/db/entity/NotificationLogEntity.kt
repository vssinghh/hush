package com.hush.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hush.app.domain.model.NotificationEvent
import com.hush.app.domain.model.RuleAction
import java.time.Instant

@Entity(
    tableName = "notification_logs",
    foreignKeys = [
        ForeignKey(
            entity = RuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchedRuleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["matchedRuleId"])]
)
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val sender: String?,
    val timestamp: Long,
    val actionTaken: String, // String representation of RuleAction enum
    val matchedRuleId: Long?,
    val matchedRuleName: String?
)

// Data-to-Domain Mapper Extensions
fun NotificationLogEntity.toDomain(): NotificationEvent = NotificationEvent(
    id = id,
    appName = appName,
    packageName = packageName,
    title = title,
    text = text,
    sender = sender,
    timestamp = Instant.ofEpochMilli(timestamp),
    actionTaken = RuleAction.valueOf(actionTaken),
    matchedRuleId = matchedRuleId,
    matchedRuleName = matchedRuleName
)

fun NotificationEvent.toEntity(): NotificationLogEntity = NotificationLogEntity(
    id = id,
    appName = appName,
    packageName = packageName,
    title = title,
    text = text?.take(7000),
    sender = sender,
    timestamp = timestamp.toEpochMilli(),
    actionTaken = actionTaken.name,
    matchedRuleId = matchedRuleId,
    matchedRuleName = matchedRuleName
)
