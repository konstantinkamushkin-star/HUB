package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ImportTripUrlRequestDto(
    @SerializedName("url") val url: String,
    @SerializedName("diveCenterId") val diveCenterId: String,
)

data class ImportTripUrlResponseDto(
    @SerializedName("tripId") val tripId: String,
    @SerializedName("warnings") val warnings: List<String>? = null,
)
