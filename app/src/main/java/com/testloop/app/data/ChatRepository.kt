package com.testloop.app.data

import com.testloop.app.domain.ChatConversation
import com.testloop.app.domain.ChatMessage
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val currentConversation: StateFlow<ChatConversation?>
    val messages: StateFlow<List<ChatMessage>>
    val isLoading: StateFlow<Boolean>
    val error: StateFlow<String?>

    suspend fun prepare(): Result<Unit>
    suspend fun openConversation(otherUserId: String, otherUsername: String): Result<ChatConversation>
    suspend fun refreshMessages(): Result<Unit>
    suspend fun sendMessage(text: String): Result<Unit>
    fun clearError()
    fun closeConversation()
}
