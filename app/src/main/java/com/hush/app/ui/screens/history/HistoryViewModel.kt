package com.hush.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hush.app.domain.model.NotificationEvent
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow<RuleAction?>(null)
    val selectedFilter: StateFlow<RuleAction?> = _selectedFilter.asStateFlow()

    val historyLogs: StateFlow<List<NotificationEvent>> =
        combine(
            _searchQuery,
            _selectedFilter
        ) { query, filter ->
            query to filter
        }.flatMapLatest { (query, filter) ->

            val flow =
                if (query.isBlank()) {
                    historyRepository.getAllLogs()
                } else {
                    historyRepository.searchLogs(query)
                }

            flow.map { logs ->
                filter?.let { action ->
                    logs.filter { it.actionTaken == action }
                } ?: logs
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFilter(filter: RuleAction) {
        _selectedFilter.value =
            if (_selectedFilter.value == filter) {
                null
            } else {
                filter
            }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAllLogs()
        }
    }
}
