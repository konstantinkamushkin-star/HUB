package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AdminGearCreateRequestDto
import com.divehub.app.data.remote.dto.AdminGearItemLocal
import com.divehub.app.data.remote.dto.AdminGearPatchStatusDto
import com.divehub.app.data.remote.dto.AdminGearRemoteDto
import com.google.gson.reflect.TypeToken

class AdminGearRepository(private val graph: AppGraph) {

    private val gson get() = graph.gson

    suspend fun loadAll(): List<AdminGearItemLocal> {
        val raw = graph.tokenStore.getAdminGearItemsJson()
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AdminGearItemLocal>>() {}.type
            gson.fromJson<List<AdminGearItemLocal>>(raw, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveAll(items: List<AdminGearItemLocal>) {
        graph.tokenStore.setAdminGearItemsJson(gson.toJson(items))
    }

    suspend fun syncFromRemote(centerId: String): List<AdminGearItemLocal> {
        val api = graph.partnerAdminApi()
        val remote = api.listCenterGear(centerId)
        val mapped = remote.map { it.toLocal() }
        graph.tokenStore.setAdminGearItemsJson(gson.toJson(mapped))
        return mapped
    }

    suspend fun syncFromRemoteOrCache(centerId: String): List<AdminGearItemLocal> =
        runCatching { syncFromRemote(centerId) }.getOrElse { loadAll() }

    suspend fun createRemote(centerId: String, name: String, category: String, manufacturer: String?) {
        val api = graph.partnerAdminApi()
        api.createCenterGear(
            centerId,
            AdminGearCreateRequestDto(
                name = name,
                category = category.ifBlank { "other" },
                manufacturer = manufacturer,
                status = "available",
                condition = "good",
            ),
        )
        syncFromRemote(centerId)
    }

    suspend fun patchStatusRemote(gearId: String, status: String, centerId: String) {
        val api = graph.partnerAdminApi()
        api.patchGearStatus(gearId, AdminGearPatchStatusDto(status))
        syncFromRemote(centerId)
    }

    private fun AdminGearRemoteDto.toLocal() = AdminGearItemLocal(
        id = id,
        name = name,
        category = category?.trim()?.ifBlank { "other" } ?: "other",
        manufacturer = manufacturer?.trim()?.takeIf { it.isNotEmpty() },
        status = status?.trim()?.ifBlank { "available" } ?: "available",
    )
}
