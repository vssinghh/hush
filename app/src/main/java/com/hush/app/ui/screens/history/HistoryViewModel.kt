package com.hush.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hush.app.domain.model.NotificationEvent
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow("All") // "All", "BLOCK", "MUTE", "ALLOW"
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    val historyLogs: StateFlow<List<NotificationEvent>> = combine(
        _searchQuery,
        _selectedTab
    ) { query, tab ->
        Pair(query, tab)
    }.flatMapLatest { (query, tab) ->
        val baseFlow = if (query.isBlank()) {
            if (tab == "All") {
                historyRepository.getAllLogs()
            } else {
                val action = runCatching { RuleAction.valueOf(tab) }.getOrNull()
                if (action != null) {
                    historyRepository.getLogsByAction(action)
                } else {
                    historyRepository.getAllLogs()
                }
            }
        } else {
            historyRepository.searchLogs(query).map { list ->
                if (tab == "All") {
                    list
                } else {
                    val action = runCatching { RuleAction.valueOf(tab) }.getOrNull()
                    if (action != null) {
                        list.filter { it.actionTaken == action }
                    } else {
                        list
                    }
                }
            }
        }
        baseFlow
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTab(tab: String) {
        _selectedTab.value = tab
    }
}
