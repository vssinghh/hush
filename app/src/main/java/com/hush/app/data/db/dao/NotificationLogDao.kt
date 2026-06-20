package com.hush.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hush.app.data.db.entity.NotificationLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<NotificationLogEntity>>

    @Query("SELECT * FROM notification_logs WHERE actionTaken = :action ORDER BY timestamp DESC")
    fun getLogsByActionFlow(action: String): Flow<List<NotificationLogEntity>>

    @Query("SELECT * FROM notification_logs WHERE appName LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchLogsFlow(query: String): Flow<List<NotificationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: NotificationLogEntity): Long

    @Query("DELETE FROM notification_logs WHERE timestamp < :threshold")
    suspend fun deleteLogsOlderThan(threshold: Long)

    @Query("DELETE FROM notification_logs")
    suspend fun clearAllLogs()
}
