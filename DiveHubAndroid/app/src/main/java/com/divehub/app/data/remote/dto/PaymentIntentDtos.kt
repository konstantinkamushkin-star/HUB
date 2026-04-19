package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PaymentIntentRequestDto(
    @SerializedName("diveCenterId") val diveCenterId: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: String,
)

data class PaymentIntentResponseDto(
    @SerializedName("clientSecret") val clientSecret: String?,
    @SerializedName("publishableKey") val publishableKey: String?,
)
