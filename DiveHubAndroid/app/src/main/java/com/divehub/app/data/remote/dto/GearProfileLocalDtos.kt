package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Local-only gear profiles (same idea as iOS `UserDefaults` / `gear_profiles`). */
data class GearProfileStored(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("items") val items: List<GearProfileItemStored> = emptyList(),
    @SerializedName("createdAtMs") val createdAtMs: Long,
    @SerializedName("updatedAtMs") val updatedAtMs: Long,
)

data class GearProfileItemStored(
    @SerializedName("id") val id: String,
    @SerializedName("category") val category: String,
    @SerializedName("size") val size: String,
    @SerializedName("notes") val notes: String? = null,
)
