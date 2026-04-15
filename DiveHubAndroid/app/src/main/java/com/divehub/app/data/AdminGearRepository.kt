package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AdminGearItemLocal
import com.google.gson.reflect.TypeToken
import java.util.UUID

class AdminGearRepository(private val graph: AppGraph) {

    private val gson get() = graph.gson

    suspend fun loadAll(): List<AdminGearItemLocal> {
        val raw = graph.tokenStore.getAdminGearItemsJson()
        if (raw.isNullOrBlank()) {
            val seeded = defaultSeed()
            saveAll(seeded)
            return seeded
        }
        return try {
            val type = object : TypeToken<List<AdminGearItemLocal>>() {}.type
            gson.fromJson<List<AdminGearItemLocal>>(raw, type)?.ifEmpty { defaultSeed() } ?: defaultSeed()
        } catch (_: Exception) {
            defaultSeed()
        }
    }

    suspend fun saveAll(items: List<AdminGearItemLocal>) {
        graph.tokenStore.setAdminGearItemsJson(gson.toJson(items))
    }

    private fun defaultSeed(): List<AdminGearItemLocal> = listOf(
        AdminGearItemLocal(
            id = UUID.randomUUID().toString(),
            name = "Apeks XTX50 Regulator",
            category = "regulator",
            manufacturer = "Apeks",
            status = "available",
        ),
        AdminGearItemLocal(
            id = UUID.randomUUID().toString(),
            name = "Scubapro BCD Hydros Pro",
            category = "bcd",
            manufacturer = "Scubapro",
            status = "issued",
        ),
        AdminGearItemLocal(
            id = UUID.randomUUID().toString(),
            name = "5mm Wetsuit Men L",
            category = "wetsuit",
            manufacturer = "Aqualung",
            status = "maintenance",
        ),
        AdminGearItemLocal(
            id = UUID.randomUUID().toString(),
            name = "Mares X-Vision Mask",
            category = "mask",
            manufacturer = "Mares",
            status = "scrapped",
        ),
    )
}

