package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** `GET /api/admin/centers/:id/gear` */
data class AdminGearRemoteDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String? = null,
    @SerializedName("manufacturer") val manufacturer: String? = null,
    @SerializedName("status") val status: String? = null,
)

data class AdminGearCreateRequestDto(
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String? = null,
    @SerializedName("manufacturer") val manufacturer: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("condition") val condition: String? = null,
)

data class AdminGearPatchStatusDto(
    @SerializedName("status") val status: String,
)
