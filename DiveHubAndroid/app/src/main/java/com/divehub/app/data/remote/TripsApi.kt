package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.CreateTripRequestDto
import com.divehub.app.data.remote.dto.ImportTripUrlRequestDto
import com.divehub.app.data.remote.dto.ImportTripUrlResponseDto
import com.divehub.app.data.remote.dto.TripCreatedResponseDto
import com.divehub.app.data.remote.dto.TripJoinResponseDto
import com.divehub.app.data.remote.dto.TripListItemDto
import com.divehub.app.data.remote.dto.UpdateTripRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TripsApi {
    @POST("trips/import/url")
    suspend fun importTripFromUrl(@Body body: ImportTripUrlRequestDto): ImportTripUrlResponseDto

    @POST("trips")
    suspend fun createTrip(@Body body: CreateTripRequestDto): TripCreatedResponseDto

    @PATCH("trips/{id}")
    suspend fun updateTrip(
        @Path("id") id: String,
        @Body body: UpdateTripRequestDto,
    ): TripCreatedResponseDto

    @DELETE("trips/{id}")
    suspend fun deleteTrip(@Path("id") id: String)

    @GET("trips")
    suspend fun listTrips(
        @Query("tripType") tripType: String? = null,
        @Query("country") country: String? = null,
        @Query("availableSpots") availableSpots: String? = null,
        @Query("organizerId") organizerId: String? = null,
    ): List<TripListItemDto>

    @GET("trips/{id}")
    suspend fun getTrip(@Path("id") id: String): TripListItemDto

    @POST("trips/{id}/join")
    suspend fun joinTrip(@Path("id") id: String): TripJoinResponseDto
}
