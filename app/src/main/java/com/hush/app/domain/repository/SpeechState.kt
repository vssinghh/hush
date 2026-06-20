package com.hush.app.domain.repository

sealed class SpeechState {
    object Idle : SpeechState()
    object Listening : SpeechState()
    data class WaveformUpdate(val amplitude: Float) : SpeechState()
    data class PartialResult(val text: String) : SpeechState()
    data class FinalResult(val text: String) : SpeechState()
    data class Error(val code: Int) : SpeechState()
}
