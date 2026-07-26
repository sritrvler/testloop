package com.testloop.app.domain

import java.time.Instant

data class ChatConversation(
    val id: String,
    val otherUserId: String,
    val otherUsername: String
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val sentAt: Instant,
    val text: String?,
    val isMine: Boolean,
    val decryptionFailed: Boolean = false
)
