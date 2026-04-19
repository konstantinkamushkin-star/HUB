package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.CourseListItemDto
import com.divehub.app.data.remote.dto.CourseRemoteDto
import com.divehub.app.data.remote.dto.CourseWriteRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CoursesApi {
    @GET("courses")
    suspend fun listCourses(
        @Query("diveCenterId") diveCenterId: String? = null,
    ): List<CourseListItemDto>

    @POST("courses")
    suspend fun createCourse(@Body body: CourseWriteRequestDto): CourseRemoteDto

    @PATCH("courses/{id}")
    suspend fun patchCourse(
        @Path("id") id: String,
        @Body body: CourseWriteRequestDto,
    ): CourseRemoteDto

    @DELETE("courses/{id}")
    suspend fun deleteCourse(@Path("id") id: String)
}
