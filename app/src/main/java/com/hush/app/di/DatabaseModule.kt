package com.hush.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hush.app.data.db.HushDatabase
import com.hush.app.data.db.dao.NotificationLogDao
import com.hush.app.data.db.dao.RuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): HushDatabase {
        return Room.databaseBuilder(
            context,
            HushDatabase::class.java,
            HushDatabase.DATABASE_NAME
        )
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys = ON;")
            }
        })
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideRuleDao(database: HushDatabase): RuleDao {
        return database.ruleDao()
    }

    @Provides
    @Singleton
    fun provideNotificationLogDao(database: HushDatabase): NotificationLogDao {
        return database.notificationLogDao()
    }
}
