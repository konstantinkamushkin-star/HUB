package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FriendRequestDto(
    @SerializedName("id") val id: String,
    @SerializedName("user") val user: UserDto,
    @SerializedName("createdAt") val createdAt: String? = null,
)

data class SendFriendRequestBody(
    @SerializedName("userId") val userId: String,
)

data class EmptyOkResponse(
    @SerializedName("ok") val ok: Boolean? = null,
)
