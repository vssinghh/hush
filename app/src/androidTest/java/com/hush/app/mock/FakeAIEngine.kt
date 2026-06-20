package com.hush.app.mock

import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.AIEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAIEngine @Inject constructor() : AIEngine {
    private val responses = mutableMapOf<String, ParsedCommand>()
    private var available = true

    fun setResponse(prompt: String, command: ParsedCommand) {
        responses[prompt.trim().lowercase()] = command
    }

    fun setAvailable(available: Boolean) {
        this.available = available
    }

    override fun isAvailable(): Boolean = available

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
