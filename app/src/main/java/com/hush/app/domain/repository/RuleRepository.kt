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
