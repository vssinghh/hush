package com.hush.app.mock

import com.hush.app.domain.repository.SpeechRecognizerWrapper
import com.hush.app.domain.repository.SpeechState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSpeechRecognizerWrapper @Inject constructor() : SpeechRecognizerWrapper {
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: Flow<SpeechState> = _state

    override fun startListening() {
        _state.value = SpeechState.Listening
    }

    override fun stopListening() {
        _state.value = SpeechState.Idle
    }

    fun simulateSpeech(text: String) {
        _state.value = SpeechState.WaveformUpdate(0.4f)
        _state.value = SpeechState.WaveformUpdate(0.8f)
        _state.value = SpeechState.PartialResult(text.substring(0, text.length / 2))
        _state.value = SpeechState.FinalResult(text)
    }

    fun simulateError(code: Int) {
        _state.value = SpeechState.Error(code)
    }
}
