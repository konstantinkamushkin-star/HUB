package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** GET /courses */
data class CourseListItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("level") val level: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("diveCenterId") val diveCenterId: String? = null,
)
