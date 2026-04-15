package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.GearProfileStored
import com.google.gson.reflect.TypeToken

class GearProfilesRepository(private val graph: AppGraph) {

    private val gson get() = graph.gson

    suspend fun loadAll(): List<GearProfileStored> {
        val raw = graph.tokenStore.getGearProfilesJson() ?: return emptyList()
        return try {
            val type = object : TypeToken<List<GearProfileStored>>() {}.type
            gson.fromJson<List<GearProfileStored>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveAll(profiles: List<GearProfileStored>) {
        graph.tokenStore.setGearProfilesJson(gson.toJson(profiles))
    }
}
