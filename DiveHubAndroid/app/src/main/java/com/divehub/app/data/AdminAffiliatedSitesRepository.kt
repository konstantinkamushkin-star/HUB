package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AdminAffiliatedSitesLocal
import com.google.gson.reflect.TypeToken

class AdminAffiliatedSitesRepository(private val graph: AppGraph) {

    private val gson get() = graph.gson

    suspend fun loadAll(): List<AdminAffiliatedSitesLocal> {
        val raw = graph.tokenStore.getAdminAffiliatedSitesJson() ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AdminAffiliatedSitesLocal>>() {}.type
            gson.fromJson<List<AdminAffiliatedSitesLocal>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveCenterSites(centerId: String, siteIds: List<String>) {
        val current = loadAll().toMutableList()
        val idx = current.indexOfFirst { it.centerId == centerId }
        val next = AdminAffiliatedSitesLocal(centerId = centerId, siteIds = siteIds.distinct().sorted())
        if (idx >= 0) current[idx] = next else current.add(next)
        graph.tokenStore.setAdminAffiliatedSitesJson(gson.toJson(current))
    }

    suspend fun getCenterSites(centerId: String): List<String> =
        loadAll().firstOrNull { it.centerId == centerId }?.siteIds ?: emptyList()
}

