package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AdminBookingLocal
import com.divehub.app.data.remote.dto.UpdateBookingStatusDto
import com.divehub.app.data.remote.dto.toAdminBookingLocal
import com.google.gson.reflect.TypeToken

class AdminBookingsRepository(private val graph: AppGraph) {

    private val gson get() = graph.gson

    suspend fun loadAll(): List<AdminBookingLocal> {
        val raw = graph.tokenStore.getAdminBookingsJson() ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AdminBookingLocal>>() {}.type
            gson.fromJson<List<AdminBookingLocal>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveAll(items: List<AdminBookingLocal>) {
        graph.tokenStore.setAdminBookingsJson(gson.toJson(items))
    }

    /** Loads from `GET /admin/bookings` and persists to cache (offline fallback). */
    suspend fun syncFromRemote(centerId: String? = null): List<AdminBookingLocal> {
        val list = graph.bookingApi().listAdminBookings(centerId).map { it.toAdminBookingLocal() }
        saveAll(list)
        return list
    }

    /**
     * Tries server sync; on failure returns last [loadAll] cache and a message (offline / network).
     */
    suspend fun syncFromRemoteWithFallback(centerId: String? = null): Pair<List<AdminBookingLocal>, String?> {
        return try {
            Pair(syncFromRemote(centerId), null)
        } catch (e: Exception) {
            Pair(loadAll(), e.message ?: e::class.java.simpleName)
        }
    }

    suspend fun updateBookingStatusRemote(bookingId: String, status: String) {
        graph.bookingApi().updateAdminBookingStatus(
            bookingId,
            UpdateBookingStatusDto(status = status),
        )
    }

    /** Instructor schedule — `GET /instructor/bookings` (not stored in admin cache). */
    suspend fun loadInstructorSchedule(): List<AdminBookingLocal> =
        graph.bookingApi().listInstructorBookings().map { it.toAdminBookingLocal() }
}

