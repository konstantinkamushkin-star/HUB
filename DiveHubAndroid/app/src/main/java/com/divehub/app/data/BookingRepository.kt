package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.BookingCreateDto
import com.divehub.app.data.remote.dto.AdminBookingLocal
import com.divehub.app.data.remote.dto.UserBookingDto

class BookingRepository(private val graph: AppGraph) {
    suspend fun listMine(): Result<List<UserBookingDto>> =
        runCatching { graph.bookingApi().listBookings() }

    suspend fun create(body: BookingCreateDto): Result<BookingCreateDto> =
        runCatching { graph.bookingApi().createBooking(body) }
            .onSuccess { created ->
                val repo = AdminBookingsRepository(graph)
                val current = repo.loadAll()
                val row = AdminBookingLocal(
                    id = created.id,
                    diveCenterId = created.diveCenterId,
                    serviceId = created.serviceId,
                    date = created.date,
                    startTime = created.startTime,
                    participantsCount = created.participants.size,
                    amount = created.payment.amount,
                    status = created.status,
                    createdAt = created.createdAt,
                )
                repo.saveAll(listOf(row) + current.filter { it.id != row.id })
            }
}
