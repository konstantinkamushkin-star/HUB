package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.CenterServiceDto
import retrofit2.http.GET
import retrofit2.http.Query

interface CenterServicesApi {
    @GET("center-services")
    suspend fun listByCenter(@Query("diveCenterId") diveCenterId: String): List<CenterServiceDto>
}
