package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AdminBookingLocal
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
}

