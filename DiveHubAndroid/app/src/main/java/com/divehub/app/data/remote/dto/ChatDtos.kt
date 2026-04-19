package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatMessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("conversationId") val conversationId: String,
    @SerializedName("senderType") val senderType: String? = null,
    @SerializedName("senderId") val senderId: String? = null,
    @SerializedName("senderName") val senderName: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("messageType") val messageType: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    val localSending: Boolean = false,
    val localFailed: Boolean = false,
)

data class ChatConversationDto(
    @SerializedName("id") val id: String,
    @SerializedName("participants") val participants: List<String> = emptyList(),
    @SerializedName("peerDisplayName") val peerDisplayName: String? = null,
    @SerializedName("lastMessage") val lastMessage: ChatMessageDto? = null,
    @SerializedName("unreadCount") val unreadCount: Int = 0,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("topicId") val topicId: String? = null,
)

data class OpenAppSupportTopicRequest(
    @SerializedName("title") val title: String? = null,
    @SerializedName("topicId") val topicId: String? = null,
)

data class ChatMessagesPageDto(
    @SerializedName("messages") val messages: List<ChatMessageDto> = emptyList(),
    @SerializedName("hasMore") val hasMore: Boolean = false,
    @SerializedName("nextBefore") val nextBefore: String? = null,
)

data class OpenConversationRequest(
    @SerializedName("peerType") val peerType: String = "user",
    @SerializedName("peerId") val peerId: String,
)

data class SendMessageRequest(
    @SerializedName("conversationId") val conversationId: String,
    @SerializedName("content") val content: String,
    @SerializedName("messageType") val messageType: String = "text",
)
