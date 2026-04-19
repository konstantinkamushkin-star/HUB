package com.divehub.app.data.remote.dto

data class AdminCourseLocal(
    val id: String,
    val diveCenterId: String,
    val name: String,
    val level: String? = null,
    val description: String? = null,
    val status: String = "active",
    val updatedAt: String,
    /** Minutes; from server `duration` or local overlay. */
    val durationMinutes: Int? = null,
)

