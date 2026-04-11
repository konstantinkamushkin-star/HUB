package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.RegisterPushTokenRequest
import com.divehub.app.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface UsersApi {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): UserDto

    @POST("users/me/push-token")
    suspend fun registerPushToken(@Body body: RegisterPushTokenRequest): Map<String, Boolean>
}
