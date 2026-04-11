package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.TripListItemDto

class TripsRepository(private val graph: AppGraph) {
    suspend fun listTrips(onlyAvailable: Boolean = false): List<TripListItemDto> {
        val api = graph.tripsApi()
        return api.listTrips(
            availableSpots = if (onlyAvailable) "true" else null,
        )
    }

    suspend fun getTrip(id: String): TripListItemDto {
        return graph.tripsApi().getTrip(id)
    }
}
