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
    @SerializedName("modules") val modules: List<CourseModuleWriteDto> = emptyList(),
    @SerializedName("prerequisites") val prerequisites: List<String> = emptyList(),
    @SerializedName("photos") val photos: List<String> = emptyList(),
    @SerializedName("instructorIds") val instructorIds: List<String> = emptyList(),
)

/** Response row from POST/PATCH/GET course (subset used by the partner UI). */
data class CourseModuleDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("moduleType") val moduleType: String = "theory",
    @SerializedName("order") val order: Int? = null,
)

data class CourseModuleWriteDto(
    val id: String? = null,
    val title: String,
    val description: String? = null,
    val duration: Int = 1,
    val moduleType: String = "theory",
    val order: Int = 0,
)

data class CourseRemoteDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("level") val level: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("diveCenterId") val diveCenterId: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("trainingSystems") val trainingSystems: List<String>? = null,
    @SerializedName("modules") val modules: List<CourseModuleDto>? = null,
    @SerializedName("prerequisites") val prerequisites: List<String>? = null,
    @SerializedName("instructorIds") val instructorIds: List<String>? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
)
