package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class BookingParticipantDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phoneNumber") val phoneNumber: String? = null,
    @SerializedName("certificationLevel") val certificationLevel: String? = null,
    @SerializedName("isFriend") val isFriend: Boolean = false,
    @SerializedName("friendUserId") val friendUserId: String? = null,
)

data class BookingGearRentalDto(
    @SerializedName("id") val id: String,
    @SerializedName("gearItemId") val gearItemId: String,
    @SerializedName("gearName") val gearName: String,
    @SerializedName("size") val size: String,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("price") val price: Double,
)

data class BookingPaymentDto(
    @SerializedName("method") val method: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: String,
    @SerializedName("status") val status: String,
    @SerializedName("transactionId") val transactionId: String? = null,
    @SerializedName("paidAt") val paidAt: String? = null,
)

/** Request body aligned with iOS `Booking` + `NetworkService.createBooking`. */
data class BookingCreateDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("diveCenterId") val diveCenterId: String,
    @SerializedName("serviceId") val serviceId: String,
    @SerializedName("diveSiteId") val diveSiteId: String? = null,
    @SerializedName("instructorId") val instructorId: String? = null,
    @SerializedName("date") val date: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("participants") val participants: List<BookingParticipantDto>,
    @SerializedName("gearRental") val gearRental: List<BookingGearRentalDto>? = null,
    @SerializedName("payment") val payment: BookingPaymentDto,
    @SerializedName("status") val status: String = "pending",
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
)
