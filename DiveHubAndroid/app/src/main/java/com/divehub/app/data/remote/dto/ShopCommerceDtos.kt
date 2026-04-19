package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ShopCommerceListEnvelope<T>(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("data") val data: List<T> = emptyList(),
)

data class ShopCommerceOneEnvelope<T>(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("data") val data: T? = null,
)

data class ShopProductRemoteDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("shopId") val shopId: String? = null,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: Double,
    @SerializedName("stock") val stock: Int,
    @SerializedName("status") val status: String,
    @SerializedName("updatedAt") val updatedAt: String? = null,
)

data class ShopOrderRemoteDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("shopId") val shopId: String? = null,
    @SerializedName("customerName") val customerName: String,
    @SerializedName("itemCount") val itemCount: Int,
    @SerializedName("total") val total: Double,
    @SerializedName("status") val status: String,
    @SerializedName("createdAt") val createdAt: String? = null,
)
