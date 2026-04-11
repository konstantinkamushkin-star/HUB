package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.CourseListItemDto
import retrofit2.http.GET
import retrofit2.http.Query

interface CoursesApi {
    @GET("courses")
    suspend fun listCourses(
        @Query("diveCenterId") diveCenterId: String? = null,
    ): List<CourseListItemDto>
}
