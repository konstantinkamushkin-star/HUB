package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.CenterServiceDto
import com.divehub.app.data.remote.dto.CreateCenterServiceDto
import com.divehub.app.data.remote.dto.UpdateCenterServiceDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CenterServicesApi {
    @GET("center-services")
    suspend fun listByCenter(
        @Query("diveCenterId") diveCenterId: String,
        @Query("includeInactive") includeInactive: String? = null,
    ): List<CenterServiceDto>

    @POST("center-services")
    suspend fun createService(@Body body: CreateCenterServiceDto): CenterServiceDto

    @PATCH("center-services/{serviceId}")
    suspend fun updateService(
        @Path("serviceId") serviceId: String,
        @Body body: UpdateCenterServiceDto,
    ): CenterServiceDto

    @DELETE("center-services/{serviceId}")
    suspend fun deleteService(@Path("serviceId") serviceId: String)
}
