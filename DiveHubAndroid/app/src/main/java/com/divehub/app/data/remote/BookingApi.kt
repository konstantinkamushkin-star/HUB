package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.BookingCreateDto
import retrofit2.http.Body
import retrofit2.http.POST

interface BookingApi {
    @POST("bookings")
    suspend fun createBooking(@Body body: BookingCreateDto): BookingCreateDto
}
