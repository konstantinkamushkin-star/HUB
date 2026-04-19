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
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("price") val price: CenterServicePriceDto? = null,
)

fun CenterServiceDto.toBookingServiceOption(): BookingServiceOption = BookingServiceOption(
    id = id,
    name = name,
    priceAmount = price?.amount ?: 0.0,
    currency = price?.currency?.trim()?.takeIf { it.isNotEmpty() } ?: "USD",
    durationMin = duration,
    subtitleExtra = description?.trim()?.takeIf { it.isNotEmpty() },
)
