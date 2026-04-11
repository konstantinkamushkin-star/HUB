package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SubmitPartnerRegistrationRequestDto(
    @SerializedName("kind") val kind: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("contactEmail") val contactEmail: String,
    @SerializedName("contactPhone") val contactPhone: String,
    @SerializedName("country") val country: String,
    @SerializedName("city") val city: String,
    @SerializedName("address") val address: String? = null,
    @SerializedName("website") val website: String? = null,
    @SerializedName("shopType") val shopType: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("personalDataConsent") val personalDataConsent: Boolean,
    @SerializedName("personalDataConsentText") val personalDataConsentText: String,
)

data class PartnerRegistrationResponseDto(
    @SerializedName("message") val message: String = "",
    @SerializedName("diveCenterId") val diveCenterId: String? = null,
    @SerializedName("shopId") val shopId: String? = null,
    @SerializedName("verificationRequestId") val verificationRequestId: String? = null,
)
