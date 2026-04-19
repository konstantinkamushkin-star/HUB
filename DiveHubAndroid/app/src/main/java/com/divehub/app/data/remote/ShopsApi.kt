package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.ShopCommerceListEnvelope
import com.divehub.app.data.remote.dto.ShopCommerceOneEnvelope
import com.divehub.app.data.remote.dto.ShopDetailResponseDto
import com.divehub.app.data.remote.dto.ShopOrderRemoteDto
import com.divehub.app.data.remote.dto.ShopProductRemoteDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ShopsApi {
    @GET("v1/shops/{id}")
    suspend fun getShop(@Path("id") id: String): ShopDetailResponseDto

    @GET("v1/shops/{shopId}/products")
    suspend fun listShopProducts(@Path("shopId") shopId: String): ShopCommerceListEnvelope<ShopProductRemoteDto>

    @POST("v1/shops/{shopId}/products")
    suspend fun saveShopProduct(
        @Path("shopId") shopId: String,
        @Body body: ShopProductRemoteDto,
    ): ShopCommerceOneEnvelope<ShopProductRemoteDto>

    @GET("v1/shops/{shopId}/orders")
    suspend fun listShopOrders(@Path("shopId") shopId: String): ShopCommerceListEnvelope<ShopOrderRemoteDto>

    @POST("v1/shops/{shopId}/orders")
    suspend fun saveShopOrder(
        @Path("shopId") shopId: String,
        @Body body: ShopOrderRemoteDto,
    ): ShopCommerceOneEnvelope<ShopOrderRemoteDto>
}
