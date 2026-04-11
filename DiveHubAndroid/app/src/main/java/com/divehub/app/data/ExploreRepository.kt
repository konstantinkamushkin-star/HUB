package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.ExploreDiveSite
import com.divehub.app.data.remote.dto.toExploreDiveSite

class ExploreRepository(private val graph: AppGraph) {
    suspend fun getDiveSites(language: String, page: Int = 1, limit: Int = 80): List<ExploreDiveSite> {
        val api = graph.exploreApi()
        return api.diveSites(language = language, page = page, limit = limit)
            .map { it.toExploreDiveSite() }
    }

    suspend fun getDiveCenters(limit: Int = 80): List<ExploreDiveSite> {
        val api = graph.exploreApi()
        return api.diveCenters(limit = limit).data.map { it.toExploreDiveSite() }
    }

    suspend fun getShops(limit: Int = 80): List<ExploreDiveSite> {
        val api = graph.exploreApi()
        return api.shops(limit = limit).data.map { it.toExploreDiveSite() }
    }

    suspend fun getCountries(): List<String> {
        val api = graph.exploreApi()
        val res = api.countries()
        return if (res.success) res.data.map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
    }
}
