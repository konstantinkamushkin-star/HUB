package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** GET v1/dive-sites/countries */
data class CountriesEnvelopeDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<String> = emptyList(),
)
