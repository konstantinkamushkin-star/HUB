package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.CountriesEnvelopeDto
import com.divehub.app.data.remote.dto.DiveSiteDto
import com.divehub.app.data.remote.dto.DiveCenterSearchResultDto
import com.divehub.app.data.remote.dto.ShopSearchResultDto
import retrofit2.http.GET
import retrofit2.http.Query

interface ExploreApi {
    @GET("v1/dive-sites/countries")
    suspend fun countries(): CountriesEnvelopeDto

    @GET("dive-sites")
    suspend fun diveSites(
        @Query("language") language: String = "en",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 80,
    ): List<DiveSiteDto>

    /** Same as iOS: list without user location uses popular, not /search (which requires lat/lng). */
    @GET("v1/dive-centers/popular")
    suspend fun diveCenters(
        @Query("limit") limit: Int = 80,
    ): DiveCenterSearchResultDto

    @GET("v1/shops/popular")
    suspend fun shops(
        @Query("limit") limit: Int = 80,
    ): ShopSearchResultDto
}
