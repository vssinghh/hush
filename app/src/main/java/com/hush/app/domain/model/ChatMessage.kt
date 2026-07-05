package com.hush.app.domain.model

import java.time.LocalTime

enum class ChatRole {
    USER, ASSISTANT
}

data class ChatMessage(
    val text: String,
    val role: ChatRole,
    val time: LocalTime = LocalTime.now()
)
