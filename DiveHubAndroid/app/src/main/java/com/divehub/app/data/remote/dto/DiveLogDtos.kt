package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DiveLogDto(
    @SerializedName("id") val id: String,
    @SerializedName("date") val date: String,
    @SerializedName("duration") val duration: Int,
    @SerializedName("maxDepth") val maxDepth: Double,
    @SerializedName("averageDepth") val averageDepth: Double? = null,
    @SerializedName("waterTemperature") val waterTemperature: Double? = null,
    @SerializedName("visibility") val visibility: Double? = null,
    @SerializedName("current") val current: String? = null,
    @SerializedName("diveType") val diveType: String? = null,
    @SerializedName("notes") val notes: String? = null,
    /** Backend JSONB may be null; Gson would fail if this were non-null with default. */
    @SerializedName("photoUrls") val photoUrls: List<String>? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
)

data class CreateDiveLogRequest(
    @SerializedName("date") val date: String,
    @SerializedName("duration") val duration: Int,
    @SerializedName("maxDepth") val maxDepth: Double,
    @SerializedName("averageDepth") val averageDepth: Double? = null,
    @SerializedName("waterTemperature") val waterTemperature: Double? = null,
    @SerializedName("visibility") val visibility: Double? = null,
    @SerializedName("current") val current: String? = null,
    @SerializedName("diveType") val diveType: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("photoUrls") val photoUrls: List<String>? = null,
    @SerializedName("isPublished") val isPublished: Boolean = false,
)
