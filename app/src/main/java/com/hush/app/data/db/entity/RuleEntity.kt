package com.hush.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.Rule
import com.hush.app.domain.model.RuleAction
import java.time.Instant
import java.time.LocalTime

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val originalPrompt: String,
    val appPackage: String?,
    val appDisplayName: String?,
    val matchField: String, // String representation of MatchField enum
    val matchType: String,  // String representation of MatchType enum
    val matchPattern: String?,
    val isInverted: Boolean,
    val action: String,     // String representation of RuleAction enum
    val timeStart: String?, // ISO-8601 time string e.g., "22:00"
    val timeEnd: String?,   // ISO-8601 time string e.g., "07:00"
    val priority: Int,
    val createdAt: Long,
    val updatedAt: Long
)

// Data-to-Domain Mapper Extensions
fun RuleEntity.toDomain(): Rule? {
    return try {
        Rule(
            id = id,
            name = name,
            enabled = enabled,
            originalPrompt = originalPrompt,
            appPackage = appPackage,
            appDisplayName = appDisplayName,
            matchField = MatchField.valueOf(matchField),
            matchType = MatchType.valueOf(matchType),
            matchPattern = matchPattern,
            isInverted = isInverted,
            action = RuleAction.valueOf(action),
            timeStart = timeStart?.let { LocalTime.parse(it) },
            timeEnd = timeEnd?.let { LocalTime.parse(it) },
            priority = priority,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    } catch (e: java.time.format.DateTimeParseException) {
        android.util.Log.e("RuleEntity", "Skipping rule $id due to malformed time string", e)
        null
    }
}

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    name = name,
    enabled = enabled,
    originalPrompt = originalPrompt,
    appPackage = appPackage,
    appDisplayName = appDisplayName,
    matchField = matchField.name,
    matchType = matchType.name,
    matchPattern = matchPattern,
    isInverted = isInverted,
    action = action.name,
    timeStart = timeStart?.toString(),
    timeEnd = timeEnd?.toString(),
    priority = priority,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)
