package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AdminAffiliatedSitesLocal
import com.divehub.app.data.remote.dto.AffiliatedSitesWriteDto
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

    /** `GET /admin/centers/:id/affiliated-sites`; on failure returns last cached [getCenterSites]. */
    suspend fun loadRemoteOrCache(centerId: String): List<String> {
        return try {
            val ids = graph.partnerAdminApi().getAffiliatedSites(centerId).siteIds
            persistCenterSites(centerId, ids)
            ids
        } catch (_: Exception) {
            getCenterSites(centerId)
        }
    }

    /** `PATCH /admin/centers/:id/affiliated-sites`; mirrors to TokenStore; on API error keeps local copy and rethrows. */
    suspend fun saveCenterSites(centerId: String, siteIds: List<String>) {
        val sorted = siteIds.distinct().sorted()
        try {
            val res = graph.partnerAdminApi().patchAffiliatedSites(
                centerId,
                AffiliatedSitesWriteDto(siteIds = sorted),
            )
            persistCenterSites(centerId, res.siteIds)
        } catch (e: Exception) {
            persistCenterSites(centerId, sorted)
            throw e
        }
    }

    private suspend fun persistCenterSites(centerId: String, siteIds: List<String>) {
        val current = loadAll().toMutableList()
        val idx = current.indexOfFirst { it.centerId == centerId }
        val next = AdminAffiliatedSitesLocal(centerId = centerId, siteIds = siteIds.distinct().sorted())
        if (idx >= 0) current[idx] = next else current.add(next)
        graph.tokenStore.setAdminAffiliatedSitesJson(gson.toJson(current))
    }

    suspend fun getCenterSites(centerId: String): List<String> =
        loadAll().firstOrNull { it.centerId == centerId }?.siteIds ?: emptyList()
}

