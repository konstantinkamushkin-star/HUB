package com.divehub.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.ChatRepository
import com.divehub.app.data.remote.dto.ChatConversationDto
import com.divehub.app.data.remote.dto.ChatMessageDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class ChatUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val conversations: List<ChatConversationDto> = emptyList(),
    val selectedConversation: ChatConversationDto? = null,
    val messages: List<ChatMessageDto> = emptyList(),
    val loadingMessages: Boolean = false,
    val loadingOlder: Boolean = false,
    val hasMore: Boolean = false,
    val nextBefore: String? = null,
    val detailError: String? = null,
    val meId: String? = null,
)

class ChatViewModel(private val repo: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var pollJob: Job? = null

    init {
        refreshConversations()
        viewModelScope.launch {
            _state.value = _state.value.copy(meId = repo.currentUser()?.id)
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repo.conversations() }
                .onSuccess { _state.value = _state.value.copy(loading = false, conversations = it) }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Load error") }
        }
    }

    fun openOrCreateConversation(friendId: String) {
        viewModelScope.launch {
            runCatching { repo.openUserConversation(friendId) }
                .onSuccess { conv ->
                    _state.value = _state.value.copy(selectedConversation = conv)
                    loadMessages(conv.id, reset = true)
                    startPolling(conv.id)
                    refreshConversations()
                }
        }
    }

    fun selectConversation(conversation: ChatConversationDto) {
        _state.value = _state.value.copy(selectedConversation = conversation)
        loadMessages(conversation.id, reset = true)
        startPolling(conversation.id)
    }

    fun backToList() {
        stopPolling()
        _state.value = _state.value.copy(
            selectedConversation = null,
            messages = emptyList(),
            detailError = null,
            hasMore = false,
            nextBefore = null,
        )
        refreshConversations()
    }

    fun loadMessages(conversationId: String, reset: Boolean = false) {
        viewModelScope.launch {
            if (reset) {
                _state.value = _state.value.copy(loadingMessages = true, detailError = null)
            }
            runCatching { repo.messagesPage(conversationId = conversationId) }
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        loadingMessages = false,
                        messages = page.messages,
                        hasMore = page.hasMore,
                        nextBefore = page.nextBefore,
                        detailError = null,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loadingMessages = false,
                        detailError = e.message ?: "Failed to load messages",
                    )
                }
        }
    }

    fun loadOlder() {
        val conv = _state.value.selectedConversation ?: return
        val before = _state.value.nextBefore ?: return
        if (_state.value.loadingOlder) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingOlder = true)
            runCatching { repo.messagesPage(conversationId = conv.id, before = before) }
                .onSuccess { page ->
                    val merged = (page.messages + _state.value.messages).distinctBy { it.id }
                    _state.value = _state.value.copy(
                        loadingOlder = false,
                        messages = merged,
                        hasMore = page.hasMore,
                        nextBefore = page.nextBefore,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(loadingOlder = false)
                }
        }
    }

    fun send(text: String) {
        val conv = _state.value.selectedConversation ?: return
        val msg = text.trim()
        if (msg.isEmpty()) return
        val localId = "local-${System.currentTimeMillis()}"
        val optimistic = ChatMessageDto(
            id = localId,
            conversationId = conv.id,
            senderType = "user",
            senderId = _state.value.meId,
            senderName = "You",
            content = msg,
            messageType = "text",
            createdAt = Instant.now().toString(),
            localSending = true,
            localFailed = false,
        )
        _state.value = _state.value.copy(messages = _state.value.messages + optimistic)
        viewModelScope.launch {
            runCatching { repo.sendText(conv.id, msg) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        messages = _state.value.messages.map {
                            if (it.id == localId) it.copy(localSending = false, localFailed = false) else it
                        },
                    )
                    loadMessages(conv.id, reset = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        messages = _state.value.messages.map {
                            if (it.id == localId) it.copy(localSending = false, localFailed = true) else it
                        },
                        detailError = e.message ?: "Failed to send message",
                    )
                }
        }
    }

    fun retryFailedMessage(messageId: String) {
        val conv = _state.value.selectedConversation ?: return
        val failed = _state.value.messages.firstOrNull { it.id == messageId && it.localFailed } ?: return
        val text = failed.content?.trim().orEmpty()
        if (text.isEmpty()) return
        _state.value = _state.value.copy(
            messages = _state.value.messages.map {
                if (it.id == messageId) it.copy(localFailed = false, localSending = true) else it
            },
            detailError = null,
        )
        viewModelScope.launch {
            runCatching { repo.sendText(conv.id, text) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        messages = _state.value.messages.filterNot { it.id == messageId },
                    )
                    loadMessages(conv.id, reset = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        messages = _state.value.messages.map {
                            if (it.id == messageId) it.copy(localFailed = true, localSending = false) else it
                        },
                        detailError = e.message ?: "Failed to resend message",
                    )
                }
        }
    }

    private fun startPolling(conversationId: String) {
        stopPolling()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(4000)
                val selectedId = _state.value.selectedConversation?.id ?: break
                if (selectedId != conversationId) break
                runCatching { repo.messagesPage(conversationId = conversationId, limit = 25) }
                    .onSuccess { page ->
                        val current = _state.value.messages
                        if (current.isEmpty()) {
                            _state.value = _state.value.copy(messages = page.messages)
                        } else {
                            val merged = (current + page.messages).distinctBy { it.id }.sortedBy { it.createdAt ?: "" }
                            _state.value = _state.value.copy(messages = merged)
                        }
                    }
                runCatching { repo.conversations() }
                    .onSuccess { _state.value = _state.value.copy(conversations = it) }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(ChatRepository(graph)) as T
            }
        }
    }
}
