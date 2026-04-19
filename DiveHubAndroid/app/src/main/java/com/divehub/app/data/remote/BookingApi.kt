package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.BookingCreateDto
import com.divehub.app.data.remote.dto.PaymentIntentRequestDto
import com.divehub.app.data.remote.dto.PaymentIntentResponseDto
import com.divehub.app.data.remote.dto.UpdateBookingStatusDto
import com.divehub.app.data.remote.dto.UserBookingDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BookingApi {
    @GET("bookings")
    suspend fun listBookings(): List<UserBookingDto>

    @POST("bookings")
    suspend fun createBooking(@Body body: BookingCreateDto): BookingCreateDto

    @POST("bookings/payment-intent")
    suspend fun createPaymentIntent(@Body body: PaymentIntentRequestDto): PaymentIntentResponseDto

    @GET("admin/bookings")
    suspend fun listAdminBookings(@Query("centerId") centerId: String? = null): List<UserBookingDto>

    @PATCH("admin/bookings/{bookingId}/status")
    suspend fun updateAdminBookingStatus(
        @Path("bookingId") bookingId: String,
        @Body body: UpdateBookingStatusDto,
    ): UserBookingDto

    @GET("instructor/bookings")
    suspend fun listInstructorBookings(): List<UserBookingDto>
}
