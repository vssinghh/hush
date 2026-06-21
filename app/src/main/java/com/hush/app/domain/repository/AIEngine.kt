package com.hush.app.domain.repository

import com.hush.app.domain.model.ParsedCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class AIStatus {
    CHECKING,       // Initial check in progress
    READY,          // Model is downloaded and available
    NOT_DOWNLOADED, // Device supports it but model needs downloading
    DOWNLOADING,    // Model download in progress
    NOT_SUPPORTED,  // Device/OS doesn't support AICore at all
    ERROR           // Transient error, retryable
}

interface AIEngine {
    fun isAvailable(): Boolean
    val status: StateFlow<AIStatus>
    val downloadProgress: StateFlow<Int> // 0-100
    val errorMessage: StateFlow<String?> // User-facing error details
    suspend fun parseCommand(prompt: String): ParsedCommand
    suspend fun downloadModel()
    suspend fun recheckAvailability()
}
