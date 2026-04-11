package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.DiveCenterBriefDto
import com.divehub.app.data.remote.dto.UserDto
import retrofit2.http.GET
import retrofit2.http.Path

interface PartnerAdminApi {
    @GET("admin/centers/managed")
    suspend fun listManagedCenters(): List<DiveCenterBriefDto>

    @GET("admin/centers/{centerId}/instructors")
    suspend fun listCenterInstructors(@Path("centerId") centerId: String): List<UserDto>
}
