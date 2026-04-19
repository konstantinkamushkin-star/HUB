package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.SupportTicketCreateRequest
import com.divehub.app.data.remote.dto.SupportTicketCreateResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface SupportApi {
    @POST("chat/support/tickets")
    suspend fun createTicket(@Body body: SupportTicketCreateRequest): SupportTicketCreateResponse
}
