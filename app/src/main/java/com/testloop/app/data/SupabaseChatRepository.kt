package com.testloop.app.data

import android.content.Context
import android.util.Log
import com.testloop.app.domain.ChatConversation
import com.testloop.app.domain.ChatMessage
import com.testloop.app.security.ChatCrypto
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.selectAsFlow
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseChatRepository(
    context: Context,
    private val auth: AuthRepository
) : ChatRepository {
    private companion object {
        const val TAG = "TestLoopChat"
    }
    private val supabase = SupabaseClientProvider.client
    private val crypto = ChatCrypto(context)

    private val _currentConversation = MutableStateFlow<ChatConversation?>(null)
    override val currentConversation: StateFlow<ChatConversation?> = _currentConversation.asStateFlow()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private var registeredKeyUserId: String? = null
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var realtimeJob: Job? = null

    override suspend fun prepare(): Result<Unit> = runCatching {
        ensureKeyRegistered(requireUserId())
    }.onFailure {
        Log.e(TAG, "prepare failed", it)
        _error.value = it.message ?: "Could not prepare encrypted chat"
    }

    override suspend fun openConversation(otherUserId: String, otherUsername: String): Result<ChatConversation> = runCatching {
        _isLoading.value = true
        clearError()
        val userId = requireUserId()
        ensureKeyRegistered(userId)
        val conversation = supabase.postgrest.rpc(
            "get_or_create_chat_conversation",
            ChatConversationParams(otherUserId)
        ).decodeAs<ChatConversationRow>().toDomain(otherUserId, otherUsername)
        _currentConversation.value = conversation
        startRealtimeSubscription(conversation)
        refreshMessages().getOrThrow()
        conversation
    }.onFailure {
        Log.e(TAG, "openConversation failed for target=$otherUserId", it)
        _error.value = it.message ?: "Could not open the conversation"
    }
        .also { _isLoading.value = false }

    override suspend fun refreshMessages(): Result<Unit> = runCatching {
        val conversation = _currentConversation.value ?: return@runCatching Unit
        val userId = requireUserId()
        ensureKeyRegistered(userId)
        val rows = supabase.from("chat_messages").select {
            filter { eq("conversation_id", conversation.id) }
        }.decodeList<ChatMessageRow>()
        _messages.value = rows.map { it.toDomain(userId, crypto) }
    }.onFailure {
        Log.e(TAG, "refreshMessages failed", it)
        _error.value = it.message ?: "Could not refresh messages"
    }

    override suspend fun sendMessage(text: String): Result<Unit> = runCatching {
        val cleanText = text.trim()
        require(cleanText.isNotBlank()) { "Message cannot be empty" }
        require(cleanText.length <= 2000) { "Message is too long" }
        val conversation = _currentConversation.value ?: error("Open a conversation first")
        val userId = requireUserId()
        ensureKeyRegistered(userId)
        val recipientKey = supabase.from("chat_key_bundles").select {
            filter { eq("user_id", conversation.otherUserId) }
        }.decodeList<ChatKeyBundleRow>().firstOrNull()
            ?: error("${conversation.otherUsername} has not opened chat on this device yet")
        val senderKey = supabase.from("chat_key_bundles").select {
            filter { eq("user_id", userId) }
        }.decodeSingle<ChatKeyBundleRow>()
        val messageId = UUID.randomUUID().toString()
        val sentAt = Instant.now().toString()
        val aad = associatedData(conversation.id, messageId, userId, conversation.otherUserId)
        supabase.from("chat_messages").insert(
            ChatMessageInsert(
                id = messageId,
                conversationId = conversation.id,
                senderId = userId,
                recipientId = conversation.otherUserId,
                keyVersion = minOf(senderKey.keyVersion, recipientKey.keyVersion),
                ciphertextForSender = crypto.encrypt(cleanText, senderKey.publicKey, aad),
                ciphertextForRecipient = crypto.encrypt(cleanText, recipientKey.publicKey, aad),
                createdAt = sentAt
            )
        )
        refreshMessages().getOrThrow()
    }.onFailure {
        Log.e(TAG, "sendMessage failed", it)
        _error.value = it.message ?: "Could not send message"
    }

    override fun clearError() { _error.value = null }

    override fun closeConversation() {
        realtimeJob?.cancel()
        realtimeJob = null
        _currentConversation.value = null
        _messages.value = emptyList()
        clearError()
    }

    private suspend fun ensureKeyRegistered(userId: String) {
        if (registeredKeyUserId == userId) return
        supabase.from("chat_key_bundles").upsert(
            ChatKeyBundleUpsert(
                userId = userId,
                algorithm = "HPKE_X25519_HKDF_SHA256_AES256_GCM",
                keyVersion = 1,
                publicKey = crypto.publicKeyBase64
            )
        ) { onConflict = "user_id" }
        registeredKeyUserId = userId
    }

    @OptIn(SupabaseExperimental::class)
    private fun startRealtimeSubscription(conversation: ChatConversation) {
        realtimeJob?.cancel()
        realtimeJob = realtimeScope.launch {
            runCatching {
                supabase.from("chat_messages").selectAsFlow(ChatMessageRow::id).collect { rows ->
                    val userId = requireUserId()
                    _messages.value = rows
                        .filter { it.conversationId == conversation.id }
                        .map { it.toDomain(userId, crypto) }
                }
            }.onFailure { Log.e(TAG, "realtime subscription failed", it) }
        }
    }

    private fun requireUserId(): String = auth.account.value?.id
        ?.takeIf { it.isNotBlank() && it != "pending" }
        ?: error("Please sign in again")

    private fun associatedData(conversationId: String, messageId: String, senderId: String, recipientId: String): String =
        "testloop-chat-v1|$conversationId|$messageId|$senderId|$recipientId"
}

@Serializable
private data class ChatConversationParams(
    @SerialName("target_user_id") val targetUserId: String
)

@Serializable
private data class ChatConversationRow(
    val id: String,
    @SerialName("participant_a") val participantA: String,
    @SerialName("participant_b") val participantB: String
) {
    fun toDomain(otherUserId: String, otherUsername: String) = ChatConversation(id, otherUserId, otherUsername)
}

@Serializable
private data class ChatKeyBundleUpsert(
    @SerialName("user_id") val userId: String,
    val algorithm: String,
    @SerialName("key_version") val keyVersion: Int,
    @SerialName("public_key") val publicKey: String
)

@Serializable
private data class ChatKeyBundleRow(
    @SerialName("user_id") val userId: String,
    @SerialName("key_version") val keyVersion: Int,
    @SerialName("public_key") val publicKey: String
)

@Serializable
private data class ChatMessageInsert(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("key_version") val keyVersion: Int,
    @SerialName("ciphertext_for_sender") val ciphertextForSender: String,
    @SerialName("ciphertext_for_recipient") val ciphertextForRecipient: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class ChatMessageRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("key_version") val keyVersion: Int,
    @SerialName("ciphertext_for_sender") val ciphertextForSender: String,
    @SerialName("ciphertext_for_recipient") val ciphertextForRecipient: String,
    @SerialName("created_at") val createdAt: String
) {
    fun toDomain(userId: String, crypto: ChatCrypto): ChatMessage {
        val aad = "testloop-chat-v1|$conversationId|$id|$senderId|$recipientId"
        val ciphertext = if (senderId == userId) ciphertextForSender else ciphertextForRecipient
        val decrypted = runCatching { crypto.decrypt(ciphertext, aad) }
        return ChatMessage(
            id = id,
            senderId = senderId,
            sentAt = runCatching { Instant.parse(createdAt) }.getOrElse { Instant.EPOCH },
            text = decrypted.getOrNull(),
            isMine = senderId == userId,
            decryptionFailed = decrypted.isFailure
        )
    }
}
