# Milestone 1: Room Database and Dagger Hilt DI Design Report

## Summary
This report outlines the architecture, configuration requirements, and Kotlin skeletons to successfully wire up Room DB and Dagger Hilt DI for Milestone 1 of the Hush app, ensuring a clean separation of concerns and robust local persistence.

---

## 1. Configuration & Gradle Requirements

To implement Hilt and Room in an Android 15 (target SDK 35, min SDK 33) project, the following build configuration must be established in the Gradle build files.

### 1.1 Project-level Build Configuration (`build.gradle.kts`)
Configure the required Hilt and Kotlin Symbol Processing (KSP) plugins in the root build file:

```kotlin
// Root build.gradle.kts
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false // Matches Kotlin version
}
```

### 1.2 Module-level Build Configuration (`app/build.gradle.kts`)
Apply the plugins and add the runtime and compiler dependencies:

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.hush.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hush.app"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Configures Room schema export
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Dagger Hilt DI
    val hiltVersion = "2.51.1"
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coroutines & Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Android Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
}
```

### 1.3 Android Manifest Declaration (`AndroidManifest.xml`)
Hilt requires the Application class to be registered in the manifest:

```xml
<!-- app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.hush.app">

    <application
        android:name=".HushApp"
        android:allowBackup="false"
        android:icon="@drawable/ic_hush"
        android:label="@string/app_name"
        android:theme="@style/Theme.Hush">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

---

## 2. Room Persistence Architecture & Schema

Hush persists two primary data models locally: **Rules** (user configuration for notifications) and **Notification Logs** (history of intercepted alerts). 

### 2.1 Schema Design Decisions
1. **Foreign Key Constraint**: `NotificationLogEntity` references `RuleEntity` using its primary key (`matchedRuleId`).
2. **Cascading Behavior**: To prevent history loss, the foreign key uses `onDelete = ForeignKey.SET_NULL`. When a rule is deleted, the log entry persists with a `null` ID, keeping the cached `matchedRuleName` string intact for user context.
3. **Index Optimization**: `matchedRuleId` is indexed to optimize history queries that filter or join by rule ID.
4. **Time Representation**: Times for rule schedules (`timeStart`, `timeEnd`) are stored in `LocalTime` format, using converters for String serialization (`HH:mm`), which handles timezone-independent scheduling.
5. **Instant Representation**: Creation and update timestamps are converted to Long epoch-milliseconds for database storage.

---

## 3. Kotlin Code Templates / Skeletons

Below are the complete, compile-ready class skeletons following Clean Architecture guidelines.

### 3.1 Dagger Hilt Application Class
File path: `app/src/main/java/com/hush/app/HushApp.kt`

```kotlin
package com.hush.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize logging, analytics, or background services
    }
}
```

### 3.2 Domain Models & Enums
File paths: 
- `app/src/main/java/com/hush/app/domain/model/Rule.kt`
- `app/src/main/java/com/hush/app/domain/model/NotificationEvent.kt`

```kotlin
// domain/model/Rule.kt
package com.hush.app.domain.model

import java.time.Instant
import java.time.LocalTime

data class Rule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val originalPrompt: String,
    val appPackage: String?,
    val appDisplayName: String?,
    val matchField: MatchField,
    val matchType: MatchType,
    val matchPattern: String?,
    val isInverted: Boolean,
    val action: RuleAction,
    val timeStart: LocalTime?,
    val timeEnd: LocalTime?,
    val priority: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class RuleAction {
    ALLOW, BLOCK, MUTE
}

enum class MatchField {
    TITLE, TEXT, SENDER, ANY
}

enum class MatchType {
    CONTAINS, REGEX, EXACT
}
```

```kotlin
// domain/model/NotificationEvent.kt
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
```

### 3.3 Room Entities (with Domain Mappers)
File paths:
- `app/src/main/java/com/hush/app/data/db/entity/RuleEntity.kt`
- `app/src/main/java/com/hush/app/data/db/entity/NotificationLogEntity.kt`

```kotlin
// data/db/entity/RuleEntity.kt
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
fun RuleEntity.toDomain(): Rule = Rule(
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
```

```kotlin
// data/db/entity/NotificationLogEntity.kt
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
    text = text,
    sender = sender,
    timestamp = timestamp.toEpochMilli(),
    actionTaken = actionTaken.name,
    matchedRuleId = matchedRuleId,
    matchedRuleName = matchedRuleName
)
```

### 3.4 Room Type Converters
File path: `app/src/main/java/com/hush/app/data/db/RoomConverters.kt`

```kotlin
package com.hush.app.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalTime

class RoomConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun instantToTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @TypeConverter
    fun fromTimeString(value: String?): LocalTime? {
        return value?.let { LocalTime.parse(it) }
    }

    @TypeConverter
    fun timeToString(time: LocalTime?): String? {
        return time?.toString()
    }
}
```

### 3.5 Room DAOs
File paths:
- `app/src/main/java/com/hush/app/data/db/dao/RuleDao.kt`
- `app/src/main/java/com/hush/app/data/db/dao/NotificationLogDao.kt`

```kotlin
// data/db/dao/RuleDao.kt
package com.hush.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hush.app.data.db.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY priority ASC")
    fun getAllRulesFlow(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun getActiveRules(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getRuleById(id: Long): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("SELECT MAX(priority) FROM rules")
    suspend fun getMaxPriority(): Int?
}
```

```kotlin
// data/db/dao/NotificationLogDao.kt
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
```

### 3.6 Room Database Definition
File path: `app/src/main/java/com/hush/app/data/db/HushDatabase.kt`

```kotlin
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
    exportSchema = false // Enabled later for production migrations
)
@TypeConverters(RoomConverters::class)
abstract class HushDatabase : RoomDatabase() {
    
    abstract fun ruleDao(): RuleDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        const val DATABASE_NAME = "hush_database"
    }
}
```

---

## 4. Dagger Hilt DI Modules

Provider modules bridge Room database access to the repository implementations.

### 4.1 Database Provisioning Module
File path: `app/src/main/java/com/hush/app/di/DatabaseModule.kt`

```kotlin
package com.hush.app.di

import android.content.Context
import androidx.room.Room
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
        .fallbackToDestructiveMigration() // Suitable for initial iterations and development
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
```

### 4.2 Repositories Bindings Module
This module maps domain layer interfaces to data layer implementation files.
File path: `app/src/main/java/com/hush/app/di/RepositoryModule.kt`

```kotlin
package com.hush.app.di

import com.hush.app.data.repository.HistoryRepositoryImpl
import com.hush.app.data.repository.RuleRepositoryImpl
import com.hush.app.domain.repository.HistoryRepository
import com.hush.app.domain.repository.RuleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRuleRepository(
        ruleRepositoryImpl: RuleRepositoryImpl
    ): RuleRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        historyRepositoryImpl: HistoryRepositoryImpl
    ): HistoryRepository
}
```

---

## 5. Domain Repository Interfaces & Data Layer Implementations

To make the Hilt configuration compile-ready, we define the repository interfaces (domain) and their concrete implementations (data) that Hilt injects.

### 5.1 Repository Interfaces (Domain Layer)
File paths:
- `app/src/main/java/com/hush/app/domain/repository/RuleRepository.kt`
- `app/src/main/java/com/hush/app/domain/repository/HistoryRepository.kt`

```kotlin
// domain/repository/RuleRepository.kt
package com.hush.app.domain.repository

import com.hush.app.domain.model.Rule
import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    fun getAllRules(): Flow<List<Rule>>
    suspend fun getActiveRules(): List<Rule>
    suspend fun getRuleById(id: Long): Rule?
    suspend fun insertRule(rule: Rule): Long
    suspend fun updateRule(rule: Rule)
    suspend fun deleteRule(rule: Rule)
    suspend fun deleteRuleById(id: Long)
    suspend fun getNextPriority(): Int
}
```

```kotlin
// domain/repository/HistoryRepository.kt
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
```

### 5.2 Repository Implementations (Data Layer)
File paths:
- `app/src/main/java/com/hush/app/data/repository/RuleRepositoryImpl.kt`
- `app/src/main/java/com/hush/app/data/repository/HistoryRepositoryImpl.kt`

```kotlin
// data/repository/RuleRepositoryImpl.kt
package com.hush.app.data.repository

import com.hush.app.data.db.dao.RuleDao
import com.hush.app.data.db.entity.toDomain
import com.hush.app.data.db.entity.toEntity
import com.hush.app.domain.model.Rule
import com.hush.app.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepositoryImpl @Inject constructor(
    private val ruleDao: RuleDao
) : RuleRepository {

    override fun getAllRules(): Flow<List<Rule>> {
        return ruleDao.getAllRulesFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getActiveRules(): List<Rule> {
        return ruleDao.getActiveRules().map { it.toDomain() }
    }

    override suspend fun getRuleById(id: Long): Rule? {
        return ruleDao.getRuleById(id)?.toDomain()
    }

    override suspend fun insertRule(rule: Rule): Long {
        return ruleDao.insertRule(rule.toEntity())
    }

    override suspend fun updateRule(rule: Rule) {
        ruleDao.updateRule(rule.toEntity())
    }

    override suspend fun deleteRule(rule: Rule) {
        ruleDao.deleteRule(rule.toEntity())
    }

    override suspend fun deleteRuleById(id: Long) {
        ruleDao.deleteRuleById(id)
    }

    override suspend fun getNextPriority(): Int {
        val maxPriority = ruleDao.getMaxPriority()
        return (maxPriority ?: 0) + 1
    }
}
```

```kotlin
// data/repository/HistoryRepositoryImpl.kt
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
```

---

## 6. Verification and Testing

To verify the correct setup of Room database and the dependency injection bindings, unit and instrumented tests must be implemented.

### 6.1 Room Database Instrumented Test Template
File path: `app/src/androidTest/java/com/hush/app/data/db/HushDatabaseTest.kt`

```kotlin
package com.hush.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hush.app.data.db.dao.RuleDao
import com.hush.app.data.db.entity.RuleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class HushDatabaseTest {

    private lateinit var db: HushDatabase
    private lateinit var ruleDao: RuleDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HushDatabase::class.java).build()
        ruleDao = db.ruleDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeRuleAndReadInList() = runBlocking {
        val rule = RuleEntity(
            id = 1L,
            name = "Mute WhatsApp except Alice",
            enabled = true,
            originalPrompt = "mute whatsapp except alice",
            appPackage = "com.whatsapp",
            appDisplayName = "WhatsApp",
            matchField = "SENDER",
            matchType = "EXACT",
            matchPattern = "Alice",
            isInverted = true,
            action = "MUTE",
            timeStart = null,
            timeEnd = null,
            priority = 1,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.insertRule(rule)
        val retrieved = ruleDao.getRuleById(1L)
        assertNotNull(retrieved)
        assertEquals("Mute WhatsApp except Alice", retrieved?.name)
    }
}
```
