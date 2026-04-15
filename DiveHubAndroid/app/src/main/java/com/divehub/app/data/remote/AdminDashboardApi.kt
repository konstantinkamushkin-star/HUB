package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.AdminOverviewDto
import com.divehub.app.data.remote.dto.ErrorStatsDto
import retrofit2.http.GET

interface AdminDashboardApi {
    @GET("admin/dashboard/overview")
    suspend fun overview(): AdminOverviewDto

    @GET("admin/error-stats")
    suspend fun errorStats(): ErrorStatsDto
}
