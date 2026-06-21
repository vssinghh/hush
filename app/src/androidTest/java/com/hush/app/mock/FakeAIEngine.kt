package com.hush.app.mock

import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.AIStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAIEngine @Inject constructor() : AIEngine {
    private val responses = mutableMapOf<String, ParsedCommand>()
    private var available = true

    private val _status = MutableStateFlow(AIStatus.READY)
    override val status: StateFlow<AIStatus> = _status.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    override val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setResponse(prompt: String, command: ParsedCommand) {
        responses[prompt.trim().lowercase()] = command
    }

    fun setAvailable(available: Boolean) {
        this.available = available
        _status.value = if (available) AIStatus.READY else AIStatus.NOT_SUPPORTED
    }

    override fun isAvailable(): Boolean = available

    override suspend fun downloadModel() {
        _status.value = AIStatus.DOWNLOADING
        _downloadProgress.value = 100
        _status.value = AIStatus.READY
        available = true
    }

    override suspend fun recheckAvailability() {
        _status.value = if (available) AIStatus.READY else AIStatus.NOT_SUPPORTED
    }

    override suspend fun parseCommand(prompt: String): ParsedCommand {
        if (!available) throw IllegalStateException("AICore unavailable")
        return responses[prompt.trim().lowercase()] ?: ParsedCommand(
            action = RuleAction.ALLOW,
            app = null,
            matchField = MatchField.ANY,
            matchType = MatchType.CONTAINS,
            matchPattern = prompt,
            isInverted = false,
            timeStart = null,
            timeEnd = null,
            summary = "Fallback: $prompt"
        )
    }
}

