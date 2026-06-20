package com.hush.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hush.app.data.db.dao.NotificationLogDao
import com.hush.app.data.db.dao.RuleDao
import com.hush.app.data.db.entity.NotificationLogEntity
import com.hush.app.data.db.entity.RuleEntity

@Database(
    entities = [RuleEntity::class, NotificationLogEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class HushDatabase : RoomDatabase() {
    
    abstract fun ruleDao(): RuleDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        const val DATABASE_NAME = "hush_database"
    }
}
