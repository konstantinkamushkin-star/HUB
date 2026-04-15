package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CertificationDto(
    @SerializedName("id") val id: String,
    @SerializedName("agency") val agency: String,
    @SerializedName("level") val level: String,
    @SerializedName("cardImageUrl") val cardImageUrl: String? = null,
    @SerializedName("issueDate") val issueDate: String? = null,
    @SerializedName("verificationStatus") val verificationStatus: String? = null,
    @SerializedName("instructorNumber") val instructorNumber: String? = null,
)

data class CreateCertificationRequest(
    @SerializedName("agency") val agency: String,
    @SerializedName("level") val level: String,
    @SerializedName("issueDate") val issueDate: String,
    @SerializedName("instructorNumber") val instructorNumber: String? = null,
    @SerializedName("cardImageUrl") val cardImageUrl: String? = null,
    @SerializedName("verificationStatus") val verificationStatus: String? = null,
)
