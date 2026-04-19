package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** GET /courses */
data class CourseListItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("level") val level: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("diveCenterId") val diveCenterId: String? = null,
    /** Total duration (minutes), matches backend `courses.duration`. */
    @SerializedName("duration") val duration: Int? = null,
)

/**
 * POST/PATCH `/courses` — matches `CoursesService.createCourse` / `updateCourse` body
 * (camelCase JSON).
 */
data class CourseWriteRequestDto(
    @SerializedName("diveCenterId") val diveCenterId: String? = null,
    @SerializedName("name") val name: String,
    @SerializedName("level") val level: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("duration") val duration: Int,
    @SerializedName("trainingSystems") val trainingSystems: List<String> = emptyList(),
    @SerializedName("modules") val modules: List<String> = emptyList(),
    @SerializedName("prerequisites") val prerequisites: List<String> = emptyList(),
    @SerializedName("photos") val photos: List<String> = emptyList(),
    @SerializedName("instructorIds") val instructorIds: List<String> = emptyList(),
)

/** Response row from POST/PATCH/GET course (subset used by the partner UI). */
data class CourseRemoteDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("level") val level: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("diveCenterId") val diveCenterId: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
)
