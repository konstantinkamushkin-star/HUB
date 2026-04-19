package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AffiliatedSitesResponseDto(
    @SerializedName("siteIds") val siteIds: List<String> = emptyList(),
)

data class AffiliatedSitesWriteDto(
    @SerializedName("siteIds") val siteIds: List<String>,
)
