package com.hush.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SpeechRecognizerWrapper {
    val state: Flow<SpeechState>
    fun startListening()
    fun stopListening()
}
