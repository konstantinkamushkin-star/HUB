package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Row for booking wizard service step (from API or merged course). */
data class BookingServiceOption(
    val id: String,
    val name: String,
    val priceAmount: Double,
    val currency: String,
    val durationMin: Int,
    val subtitleExtra: String? = null,
)

/** Gear rental line item when a catalog exists (no public diver catalog API yet — list may be empty). */
data class BookingGearOption(
    val id: String,
    val name: String,
    val size: String,
    val price: Double,
)

data class CenterServicePriceDto(
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("currency") val currency: String = "USD",
)

/** Mirrors backend `CenterServicesService.mapRow`. */
data class CenterServiceDto(
    @SerializedName("id") val id: String,
    @SerializedName("diveCenterId") val diveCenterId: String? = null,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("pricingUnit") val pricingUnit: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("maxParticipants") val maxParticipants: Int = 0,
    @SerializedName("requirements") val requirements: List<String>? = null,
    @SerializedName("includedItems") val includedItems: List<String>? = null,
    @SerializedName("ownGearDiscountPercent") val ownGearDiscountPercent: Double? = null,
    @SerializedName("groupDiscountThreshold") val groupDiscountThreshold: Int? = null,
    @SerializedName("groupDiscountPercent") val groupDiscountPercent: Double? = null,
    @SerializedName("nightDiveSurchargeAmount") val nightDiveSurchargeAmount: Double? = null,
    @SerializedName("privateInstructorSurchargeAmount") val privateInstructorSurchargeAmount: Double? = null,
    @SerializedName("isActive") val isActive: Boolean = true,
    @SerializedName("price") val price: CenterServicePriceDto? = null,
)

data class CreateCenterServiceDto(
    @SerializedName("diveCenterId") val diveCenterId: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("serviceType") val serviceType: String? = null,
    @SerializedName("basePriceAmount") val basePriceAmount: Double,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("pricingUnit") val pricingUnit: String? = null,
    @SerializedName("durationMinutes") val durationMinutes: Int? = null,
    @SerializedName("maxParticipants") val maxParticipants: Int? = null,
    @SerializedName("requirements") val requirements: List<String>? = null,
    @SerializedName("includedItems") val includedItems: List<String>? = null,
    @SerializedName("ownGearDiscountPercent") val ownGearDiscountPercent: Double? = null,
    @SerializedName("groupDiscountThreshold") val groupDiscountThreshold: Int? = null,
    @SerializedName("groupDiscountPercent") val groupDiscountPercent: Double? = null,
    @SerializedName("nightDiveSurchargeAmount") val nightDiveSurchargeAmount: Double? = null,
    @SerializedName("privateInstructorSurchargeAmount") val privateInstructorSurchargeAmount: Double? = null,
    @SerializedName("isActive") val isActive: Boolean? = null,
)

data class UpdateCenterServiceDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("serviceType") val serviceType: String? = null,
    @SerializedName("basePriceAmount") val basePriceAmount: Double? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("pricingUnit") val pricingUnit: String? = null,
    @SerializedName("durationMinutes") val durationMinutes: Int? = null,
    @SerializedName("maxParticipants") val maxParticipants: Int? = null,
    @SerializedName("requirements") val requirements: List<String>? = null,
    @SerializedName("includedItems") val includedItems: List<String>? = null,
    @SerializedName("ownGearDiscountPercent") val ownGearDiscountPercent: Double? = null,
    @SerializedName("groupDiscountThreshold") val groupDiscountThreshold: Int? = null,
    @SerializedName("groupDiscountPercent") val groupDiscountPercent: Double? = null,
    @SerializedName("nightDiveSurchargeAmount") val nightDiveSurchargeAmount: Double? = null,
    @SerializedName("privateInstructorSurchargeAmount") val privateInstructorSurchargeAmount: Double? = null,
    @SerializedName("isActive") val isActive: Boolean? = null,
)

fun CenterServiceDto.toBookingServiceOption(): BookingServiceOption = BookingServiceOption(
    id = id,
    name = name,
    priceAmount = price?.amount ?: 0.0,
    currency = price?.currency?.trim()?.takeIf { it.isNotEmpty() } ?: "USD",
    durationMin = duration,
    subtitleExtra = description?.trim()?.takeIf { it.isNotEmpty() },
)
