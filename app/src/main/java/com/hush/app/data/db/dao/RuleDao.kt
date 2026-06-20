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
    @Query("SELECT * FROM rules ORDER BY priority ASC, id ASC")
    fun getAllRulesFlow(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority ASC, id ASC")
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
