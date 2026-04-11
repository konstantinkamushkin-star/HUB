package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FeedListResponse(
    @SerializedName("items") val items: List<FeedPostDto> = emptyList(),
    @SerializedName("hasMore") val hasMore: Boolean = false,
    @SerializedName("nextCursor") val nextCursor: String? = null,
)

data class FeedPostDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("type") val type: String,
    @SerializedName("content") val content: String? = null,
    @SerializedName("diveLogId") val diveLogId: String? = null,
    @SerializedName("diveLog") val diveLog: DiveLogLiteDto? = null,
    @SerializedName("photos") val photos: List<String> = emptyList(),
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("comments") val comments: Int = 0,
    @SerializedName("isLiked") val isLiked: Boolean = false,
    @SerializedName("createdAt") val createdAt: String? = null,
)

data class CreateFeedPostRequest(
    @SerializedName("type") val type: String,
    @SerializedName("content") val content: String? = null,
    @SerializedName("photos") val photos: List<String>? = null,
    @SerializedName("diveLogId") val diveLogId: String? = null,
)

data class FeedCommentDto(
    @SerializedName("id") val id: String,
    @SerializedName("content") val content: String,
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
)

data class FeedCommentRequest(
    @SerializedName("content") val content: String,
)

data class UploadMediaResponse(
    @SerializedName("path") val path: String? = null,
    @SerializedName("url") val url: String? = null,
)

data class DiveLogLiteDto(
    @SerializedName("id") val id: String,
    @SerializedName("diveSiteId") val diveSiteId: String? = null,
    @SerializedName("diveSiteName") val diveSiteName: String? = null,
    @SerializedName("date") val date: String? = null,
    @SerializedName("maxDepth") val maxDepth: Double? = null,
    @SerializedName("averageDepth") val averageDepth: Double? = null,
    @SerializedName("waterTemperature") val waterTemperature: Double? = null,
    @SerializedName("visibility") val visibility: Double? = null,
    @SerializedName("current") val current: String? = null,
    @SerializedName("diveType") val diveType: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("photoUrls") val photoUrls: List<String>? = null,
)
