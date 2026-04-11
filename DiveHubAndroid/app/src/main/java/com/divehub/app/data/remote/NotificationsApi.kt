package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.NotificationsListResponse
import com.divehub.app.data.remote.dto.OkDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface NotificationsApi {
    @GET("notifications")
    suspend fun list(): NotificationsListResponse

    @POST("notifications/read-all")
    suspend fun markAllRead(): OkDto

    @DELETE("notifications/{id}")
    suspend fun delete(@Path("id") id: String)
}
