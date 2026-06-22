package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.AddTripMemberBody
import com.divehub.app.data.remote.dto.CreateGroupTripBody
import com.divehub.app.data.remote.dto.DiscoverNearbyDto
import com.divehub.app.data.remote.dto.GroupTripDto
import com.divehub.app.data.remote.dto.ReportLocationBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GroupTripsApi {
    @GET("group-trips")
    suspend fun list(): List<GroupTripDto>

    @GET("group-trips/{id}")
    suspend fun get(@Path("id") id: String): GroupTripDto

    @POST("group-trips")
    suspend fun create(@Body body: CreateGroupTripBody): GroupTripDto

    @POST("group-trips/{id}/members")
    suspend fun addMember(
        @Path("id") id: String,
        @Body body: AddTripMemberBody,
    ): GroupTripDto
}

interface LocationApi {
    @POST("users/me/location")
    suspend fun reportLocation(@Body body: ReportLocationBody): Map<String, Any?>

    @GET("users/discover/nearby")
    suspend fun discoverNearby(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Double = 100.0,
    ): List<DiscoverNearbyDto>
}
