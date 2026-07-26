package com.testloop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.testloop.app.domain.ChatConversation
import com.testloop.app.domain.ChatMessage
import com.testloop.app.ui.theme.Brand100
import com.testloop.app.ui.theme.Brand600
import com.testloop.app.ui.theme.Ink500
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    conversation: ChatConversation?,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: suspend () -> Result<Unit>,
    onSend: suspend (String) -> Result<Unit>,
    onClearError: () -> Unit
) {
    if (conversation == null) {
        MissingScreen(onBack)
        return
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(conversation.id) {
        while (isActive) {
            onRefresh()
            delay(4_000)
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TestLoopTopBar(
            title = "@${conversation.otherUsername}",
            subtitle = "Encrypted chat",
            onBack = onBack,
            actionContent = {
                IconButton(onClick = { scope.launch { onRefresh() } }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh messages")
                }
            }
        )
        Row(
            Modifier.fillMaxWidth().background(Brand100).padding(horizontal = Gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Brand600, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Messages are encrypted on your devices.", style = MaterialTheme.typography.labelMedium, color = Ink500)
        }
        if (error != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = onClearError) { Text("×", style = MaterialTheme.typography.titleLarge) }
            }
        }
        if (isLoading && messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(Gutter), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Brand600, modifier = Modifier.size(36.dp))
                Text("Start a private conversation", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text("Only you and @${conversation.otherUsername} can decrypt these messages.", color = Ink500, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Gutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message -> ChatBubble(message) }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(Gutter), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(2000); onClearError() },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write a message") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty() && !sending) {
                        sending = true
                        scope.launch {
                            onSend(text).onSuccess { draft = "" }
                            sending = false
                        }
                    }
                },
                enabled = draft.isNotBlank() && !sending,
                modifier = Modifier.padding(bottom = 2.dp)
            ) { Text("Send") }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.isMine) Brand100 else MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .background(bubbleColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text ?: if (message.decryptionFailed) "Unable to decrypt this message on this device." else "Message unavailable",
                color = if (message.decryptionFailed) Ink500 else Color.Unspecified,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = message.sentAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                color = Ink500,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
