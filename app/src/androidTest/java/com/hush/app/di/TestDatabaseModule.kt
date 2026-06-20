package com.hush.app.di

import android.content.Context
import androidx.room.Room
import com.hush.app.data.db.HushDatabase
import com.hush.app.data.db.dao.NotificationLogDao
import com.hush.app.data.db.dao.RuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideInMemoryDB(@ApplicationContext context: Context): HushDatabase {
        return Room.inMemoryDatabaseBuilder(context, HushDatabase::class.java)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON;")
                }
            })
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideRuleDao(db: HushDatabase): RuleDao = db.ruleDao()

    @Provides
    fun provideLogDao(db: HushDatabase): NotificationLogDao = db.notificationLogDao()
}
