package com.hush.app.domain.repository

import com.hush.app.domain.model.NotificationEvent
import com.hush.app.domain.model.RuleAction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface HistoryRepository {
    fun getAllLogs(): Flow<List<NotificationEvent>>
    fun getLogsByAction(action: RuleAction): Flow<List<NotificationEvent>>
    fun searchLogs(query: String): Flow<List<NotificationEvent>>
    suspend fun insertLog(log: NotificationEvent): Long
    suspend fun deleteLogsOlderThan(threshold: Instant)
    suspend fun clearAllLogs()
}
