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
            entities.mapNotNull { it.toDomain() }
        }
    }

    override suspend fun getActiveRules(): List<Rule> {
        return ruleDao.getActiveRules().mapNotNull { it.toDomain() }
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
