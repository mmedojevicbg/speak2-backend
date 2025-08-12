package com.mmedojevic

import java.sql.Timestamp
import java.util.UUID
import java.time.Instant

data class ChatMessage(
    val id: UUID = UUID.randomUUID(),
    val chatId: UUID,
    val senderId: UUID,
    val message: String,
    val sentAt: Timestamp = Timestamp.from(Instant.now())
)