package com.hush.app.ui.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hush.app.domain.model.Rule
import com.hush.app.domain.repository.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository
) : ViewModel() {

    private val toggleMutex = Mutex()

    val rulesList: StateFlow<List<Rule>> = ruleRepository.getAllRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleRuleEnabled(rule: Rule) {
        viewModelScope.launch {
            toggleMutex.withLock {
                val latestRule = ruleRepository.getRuleById(rule.id)
                if (latestRule != null) {
                    ruleRepository.updateRule(latestRule.copy(enabled = !latestRule.enabled))
                }
            }
        }
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch {
            ruleRepository.deleteRule(rule)
        }
    }

    fun updateRule(rule: Rule) {
        viewModelScope.launch {
            ruleRepository.updateRule(rule)
        }
    }
}
