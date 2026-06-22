package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** GET v1/dive-sites/regions?country=… */
data class RegionsEnvelopeDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<String> = emptyList(),
)
