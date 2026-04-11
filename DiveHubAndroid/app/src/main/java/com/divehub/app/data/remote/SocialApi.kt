package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.EmptyOkResponse
import com.divehub.app.data.remote.dto.FriendRequestDto
import com.divehub.app.data.remote.dto.SendFriendRequestBody
import com.divehub.app.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SocialApi {
    @GET("users/search")
    suspend fun searchUsers(@Query("query") query: String): List<UserDto>

    @GET("friends")
    suspend fun friends(): List<UserDto>

    @GET("friends/requests/sent")
    suspend fun sentRequests(): List<FriendRequestDto>

    @GET("friends/requests/received")
    suspend fun receivedRequests(): List<FriendRequestDto>

    @POST("friends/requests")
    suspend fun sendRequest(@Body body: SendFriendRequestBody): EmptyOkResponse

    @POST("friends/requests/{userId}/accept")
    suspend fun acceptRequest(@Path("userId") userId: String): EmptyOkResponse

    @DELETE("friends/requests/{friendshipId}")
    suspend fun declineRequest(@Path("friendshipId") friendshipId: String): EmptyOkResponse
}
