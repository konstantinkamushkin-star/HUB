package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CreateReviewRequest(
    @SerializedName("reviewableType") val reviewableType: String,
    @SerializedName("reviewableId") val reviewableId: String,
    val rating: Int,
    val text: String,
    val language: String? = "en",
)

data class ReviewDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("userName") val userName: String? = null,
    @SerializedName("rating") val rating: Int,
    @SerializedName("text") val text: String,
    @SerializedName("createdAt") val createdAt: String? = null,
)
