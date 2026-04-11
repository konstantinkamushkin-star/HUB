package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class NotificationsListResponse(
    @SerializedName("notifications") val notifications: List<AppNotificationDto> = emptyList(),
)

data class AppNotificationDto(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("isRead") val isRead: Boolean = false,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("actionURL") val actionUrl: String? = null,
)
