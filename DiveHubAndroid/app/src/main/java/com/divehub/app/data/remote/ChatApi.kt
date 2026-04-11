package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.ChatConversationDto
import com.divehub.app.data.remote.dto.ChatMessagesPageDto
import com.divehub.app.data.remote.dto.OpenConversationRequest
import com.divehub.app.data.remote.dto.SendMessageRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {
    @GET("chat/conversations")
    suspend fun conversations(): List<ChatConversationDto>

    @POST("chat/conversations")
    suspend fun openConversation(@Body body: OpenConversationRequest): ChatConversationDto

    @GET("chat/{conversationId}/messages")
    suspend fun messages(
        @Path("conversationId") conversationId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 40,
    ): ChatMessagesPageDto

    @POST("chat/messages")
    suspend fun sendMessage(@Body body: SendMessageRequest)
}
