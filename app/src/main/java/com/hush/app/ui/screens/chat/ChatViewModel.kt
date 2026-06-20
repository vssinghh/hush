package com.hush.app.ui.screens.chat

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.Rule
import com.hush.app.domain.permission.PermissionManager
import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.PackageResolver
import com.hush.app.domain.repository.RuleRepository
import com.hush.app.domain.repository.SpeechRecognizerWrapper
import com.hush.app.domain.repository.SpeechState
import com.hush.app.domain.usecase.ParseCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    val aiEngine: AIEngine,
    val speechRecognizerWrapper: SpeechRecognizerWrapper,
    val ruleRepository: RuleRepository,
    val parseCommandUseCase: ParseCommandUseCase,
    val packageResolver: PackageResolver,
    val permissionManager: PermissionManager
) : ViewModel() {

    val mockMessages = mutableStateListOf(
        "Welcome to Hush! Speak or type a filtering command (e.g., 'Mute Instagram').",
        "Mute WhatsApp notifications except from Bob."
    )

    val proposedRule = mutableStateOf<ParsedCommand?>(null)
    val errorMessage = mutableStateOf<String?>(null)

    val isListening = mutableStateOf(false)
    val textState = mutableStateOf("")
    val amplitudes = mutableStateListOf<Float>()

    private var aiJob: Job? = null

    init {
        resetAmplitudes()
        collectSpeechState()
    }

    private fun resetAmplitudes() {
        amplitudes.clear()
        repeat(15) { amplitudes.add(0.1f) }
    }

    private fun collectSpeechState() {
        viewModelScope.launch {
            speechRecognizerWrapper.state.collect { state ->
                when (state) {
                    is SpeechState.Idle -> {
                        isListening.value = false
                    }
                    is SpeechState.Listening -> {
                        isListening.value = true
                        errorMessage.value = null
                    }
                    is SpeechState.WaveformUpdate -> {
                        // Normalize RMS dB range [-2f, 10f] into [0.1f, 1.0f]
                        val normalized = ((state.amplitude + 2f) / 12f).coerceIn(0.1f, 1.0f)
                        if (amplitudes.size >= 15) {
                            amplitudes.removeAt(0)
                        }
                        amplitudes.add(normalized)
                    }
                    is SpeechState.PartialResult -> {
                        textState.value = state.text
                    }
                    is SpeechState.FinalResult -> {
                        textState.value = state.text
                        isListening.value = false
                    }
                    is SpeechState.Error -> {
                        isListening.value = false
                        errorMessage.value = "Speech recognition error: ${state.code}"
                    }
                }
            }
        }
    }

    fun toggleListening() {
        if (isListening.value) {
            speechRecognizerWrapper.stopListening()
        } else {
            resetAmplitudes()
            speechRecognizerWrapper.startListening()
        }
    }

    fun handleSend(prompt: String) {
        if (prompt.isBlank()) return
        mockMessages.add(prompt)
        textState.value = ""

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            try {
                val result = parseCommandUseCase(prompt)
                if (result.summary == "MALFORMED_JSON_TRIGGER") {
                    errorMessage.value = "Failed to parse command"
                } else {
                    proposedRule.value = result
                    errorMessage.value = null
                }
            } catch (e: Exception) {
                errorMessage.value = "AI Engine error: ${e.message}"
            }
        }
    }

    fun confirmProposedRule() {
        val rule = proposedRule.value ?: return
        viewModelScope.launch {
            try {
                val appDisplayName = rule.app?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() }
                val priority = ruleRepository.getNextPriority()
                val entity = Rule(
                    name = rule.summary,
                    enabled = true,
                    originalPrompt = rule.summary,
                    appPackage = rule.app,
                    appDisplayName = appDisplayName,
                    matchField = rule.matchField,
                    matchType = rule.matchType,
                    matchPattern = rule.matchPattern,
                    isInverted = rule.isInverted,
                    action = rule.action,
                    timeStart = rule.timeStart,
                    timeEnd = rule.timeEnd,
                    priority = priority,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                ruleRepository.insertRule(entity)
                mockMessages.add("Rule created successfully")
                proposedRule.value = null
            } catch (e: Exception) {
                errorMessage.value = "Failed to save rule: ${e.message}"
            }
        }
    }

    fun cancelProposedRule() {
        proposedRule.value = null
    }
}

