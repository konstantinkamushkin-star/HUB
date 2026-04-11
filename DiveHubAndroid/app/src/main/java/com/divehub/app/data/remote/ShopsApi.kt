package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.ShopDetailResponseDto
import retrofit2.http.GET
import retrofit2.http.Path

interface ShopsApi {
    @GET("v1/shops/{id}")
    suspend fun getShop(@Path("id") id: String): ShopDetailResponseDto
}
