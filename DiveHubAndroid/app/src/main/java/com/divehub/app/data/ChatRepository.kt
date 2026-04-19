package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.ChatConversationDto
import com.divehub.app.data.remote.dto.ChatMessageDto
import com.divehub.app.data.remote.dto.ChatMessagesPageDto
import com.divehub.app.data.remote.dto.OpenAppSupportTopicRequest
import com.divehub.app.data.remote.dto.OpenConversationRequest
import com.divehub.app.data.remote.dto.UserDto

class ChatRepository(private val graph: AppGraph) {
    suspend fun conversations(): List<ChatConversationDto> = graph.chatApi().conversations()

    suspend fun openConversation(peerId: String, peerType: String = "user"): ChatConversationDto =
        graph.chatApi().openConversation(OpenConversationRequest(peerType = peerType, peerId = peerId))

    suspend fun openUserConversation(userId: String): ChatConversationDto =
        openConversation(peerId = userId, peerType = "user")

    suspend fun openAppSupportTopic(title: String?) =
        graph.chatApi().openAppSupportTopic(
            OpenAppSupportTopicRequest(
                title = title?.takeIf { it.isNotBlank() },
                topicId = null,
            ),
        )

    suspend fun messagesPage(
        conversationId: String,
        before: String? = null,
        limit: Int = 40,
    ): ChatMessagesPageDto = graph.chatApi().messages(conversationId = conversationId, before = before, limit = limit)

    suspend fun sendText(conversationId: String, content: String) {
        graph.chatApi().sendMessage(
            com.divehub.app.data.remote.dto.SendMessageRequest(
                conversationId = conversationId,
                content = content,
            ),
        )
    }

    suspend fun currentUser(): UserDto? {
        val json = graph.tokenStore.getUserJson() ?: return null
        return try {
            graph.gson.fromJson(json, UserDto::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
