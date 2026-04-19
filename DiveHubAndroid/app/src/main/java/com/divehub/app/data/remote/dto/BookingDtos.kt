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

/**
 * Flexible payment payload for `GET /api/bookings` (DB allows `{}`).
 */
data class BookingPaymentFlexibleDto(
    @SerializedName("method") val method: String? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("transactionId") val transactionId: String? = null,
    @SerializedName("paidAt") val paidAt: String? = null,
)

/** User booking row from `GET /api/bookings` (same core fields as create response; extra JSON fields ignored by Gson). */
data class UserBookingDto(
    @SerializedName("id") val id: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("diveCenterId") val diveCenterId: String,
    @SerializedName("serviceId") val serviceId: String,
    @SerializedName("diveSiteId") val diveSiteId: String? = null,
    @SerializedName("instructorId") val instructorId: String? = null,
    @SerializedName("date") val date: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("participantsCount") val participantsCount: Int? = null,
    @SerializedName("payment") val payment: BookingPaymentFlexibleDto? = null,
    @SerializedName("status") val status: String,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String? = null,
)

/** Same parsing as iOS `Booking.manualVerifiedPriceText` / `manualVerificationNote`. */
fun UserBookingDto.manualVerifiedPriceFromNotes(): String? =
    notes?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("manual_verified_price=") }
        ?.substringAfter("manual_verified_price=")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

fun UserBookingDto.manualCenterNoteFromNotes(): String? =
    notes?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("manual_note=") }
        ?.substringAfter("manual_note=")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** Backend `UpdateBookingStatusDto` — `PATCH /admin/bookings/:id/status`. */
data class UpdateBookingStatusDto(
    @SerializedName("status") val status: String,
    @SerializedName("finalPriceAmount") val finalPriceAmount: Double? = null,
    @SerializedName("finalPriceCurrency") val finalPriceCurrency: String? = null,
    @SerializedName("manualVerificationNote") val manualVerificationNote: String? = null,
)

/** Normalize `date` from DB (ISO date or timestamptz string) for `LocalDate.parse`. */
fun normalizeBookingDateForUi(raw: String): String {
    val t = raw.trim()
    val idx = t.indexOf('T')
    return if (idx in 1 until t.length) t.substring(0, idx.coerceAtMost(10)) else t.take(10)
}

fun UserBookingDto.toAdminBookingLocal(): AdminBookingLocal =
    AdminBookingLocal(
        id = id,
        diveCenterId = diveCenterId,
        serviceId = serviceId,
        date = normalizeBookingDateForUi(date),
        startTime = startTime,
        participantsCount = participantsCount ?: 0,
        amount = payment?.amount ?: 0.0,
        status = status,
        createdAt = createdAt,
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
