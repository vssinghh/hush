package com.hush.app.domain.repository

import com.hush.app.domain.model.ParsedCommand

interface AIEngine {
    fun isAvailable(): Boolean
    suspend fun parseCommand(prompt: String): ParsedCommand
}
