package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SupportTicketClientMetadata(
    @SerializedName("appVersion") val appVersion: String? = null,
    @SerializedName("build") val build: String? = null,
    @SerializedName("os") val os: String? = null,
    @SerializedName("locale") val locale: String? = null,
)

data class SupportTicketCreateRequest(
    @SerializedName("subject") val subject: String,
    @SerializedName("body") val body: String,
    @SerializedName("category") val category: String,
    @SerializedName("conversationId") val conversationId: String? = null,
    @SerializedName("metadata") val metadata: SupportTicketClientMetadata? = null,
)

data class SupportTicketCreateResponse(
    @SerializedName("success") val success: Boolean? = null,
)
