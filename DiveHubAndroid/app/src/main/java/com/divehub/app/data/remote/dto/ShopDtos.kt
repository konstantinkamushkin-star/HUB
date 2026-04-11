package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** `GET v1/shops/:id` — обёртка ответа бэкенда */
data class ShopDetailResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: ShopV1DetailDto? = null,
)

data class ShopV1DetailDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("website") val website: String? = null,
    @SerializedName("averageRating") val averageRating: Double? = null,
    @SerializedName("reviewCount") val reviewCount: Int? = null,
    @SerializedName("serviceAvailable") val serviceAvailable: Boolean? = null,
)
