package com.hush.app.data.repository

import com.hush.app.data.db.dao.NotificationLogDao
import com.hush.app.data.db.entity.toDomain
import com.hush.app.data.db.entity.toEntity
import com.hush.app.domain.model.NotificationEvent
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val notificationLogDao: NotificationLogDao
) : HistoryRepository {

    override fun getAllLogs(): Flow<List<NotificationEvent>> {
        return notificationLogDao.getAllLogsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getLogsByAction(action: RuleAction): Flow<List<NotificationEvent>> {
        return notificationLogDao.getLogsByActionFlow(action.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchLogs(query: String): Flow<List<NotificationEvent>> {
        return notificationLogDao.searchLogsFlow(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertLog(log: NotificationEvent): Long {
        return notificationLogDao.insertLog(log.toEntity())
    }

    override suspend fun deleteLogsOlderThan(threshold: Instant) {
        notificationLogDao.deleteLogsOlderThan(threshold.toEpochMilli())
    }

    override suspend fun clearAllLogs() {
        notificationLogDao.clearAllLogs()
    }
}
