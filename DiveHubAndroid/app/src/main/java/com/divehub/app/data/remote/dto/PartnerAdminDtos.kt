package com.divehub.app.data.remote.dto

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class DiveCenterBriefDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
)

data class CreateTripRequestDto(
    @SerializedName("diveCenterId") val diveCenterId: String,
    @SerializedName("tripType") val tripType: String,
    @SerializedName("country") val country: String,
    @SerializedName("region") val region: String? = null,
    @SerializedName("startDate") val startDate: String,
    @SerializedName("endDate") val endDate: String,
    @SerializedName("description") val description: String,
    @SerializedName("totalSpots") val totalSpots: Int,
    @SerializedName("minimumCertificationLevel") val minimumCertificationLevel: String? = null,
    @SerializedName("minimumDives") val minimumDives: Int? = null,
    @SerializedName("nitroxAvailable") val nitroxAvailable: Boolean? = null,
    @SerializedName("equipmentRentalAvailable") val equipmentRentalAvailable: Boolean? = null,
    @SerializedName("hotelId") val hotelId: String? = null,
    @SerializedName("yachtId") val yachtId: String? = null,
    @SerializedName("hotelLabel") val hotelLabel: String? = null,
    @SerializedName("yachtLabel") val yachtLabel: String? = null,
    @SerializedName("groupLeaderId") val groupLeaderId: String? = null,
    @SerializedName("programDays") val programDays: JsonArray? = null,
    @SerializedName("additionalExpenses") val additionalExpenses: JsonArray? = null,
    @SerializedName("priceDetails") val priceDetails: JsonObject? = null,
    @SerializedName("photoUrls") val photoUrls: List<String>? = null,
    @SerializedName("availableCourseIds") val availableCourseIds: List<String>? = null,
)

/** PATCH /trips/:id — same fields as create without diveCenterId. */
data class UpdateTripRequestDto(
    @SerializedName("tripType") val tripType: String,
    @SerializedName("country") val country: String,
    @SerializedName("region") val region: String? = null,
    @SerializedName("startDate") val startDate: String,
    @SerializedName("endDate") val endDate: String,
    @SerializedName("description") val description: String,
    @SerializedName("totalSpots") val totalSpots: Int,
    @SerializedName("minimumCertificationLevel") val minimumCertificationLevel: String? = null,
    @SerializedName("minimumDives") val minimumDives: Int? = null,
    @SerializedName("nitroxAvailable") val nitroxAvailable: Boolean? = null,
    @SerializedName("equipmentRentalAvailable") val equipmentRentalAvailable: Boolean? = null,
    @SerializedName("hotelId") val hotelId: String? = null,
    @SerializedName("yachtId") val yachtId: String? = null,
    @SerializedName("hotelLabel") val hotelLabel: String? = null,
    @SerializedName("yachtLabel") val yachtLabel: String? = null,
    @SerializedName("groupLeaderId") val groupLeaderId: String? = null,
    @SerializedName("programDays") val programDays: JsonArray? = null,
    @SerializedName("additionalExpenses") val additionalExpenses: JsonArray? = null,
    @SerializedName("priceDetails") val priceDetails: JsonObject? = null,
    @SerializedName("photoUrls") val photoUrls: List<String>? = null,
    @SerializedName("availableCourseIds") val availableCourseIds: List<String>? = null,
)

data class TripCreatedResponseDto(
    @SerializedName("id") val id: String,
)
