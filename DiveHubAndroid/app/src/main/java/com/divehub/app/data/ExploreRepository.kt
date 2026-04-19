package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.DiveCenterInstructorDto
import com.divehub.app.data.remote.dto.DiveCenterItemDto
import com.divehub.app.data.remote.dto.DiveSiteContributionMineDto
import com.divehub.app.data.remote.dto.ExploreDiveSite
import com.divehub.app.data.remote.dto.toExploreDiveSite
import com.google.gson.JsonObject

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

    suspend fun getDiveCenterById(id: String): DiveCenterItemDto? {
        val env = graph.exploreApi().getDiveCenter(id)
        return env.data?.takeIf { env.success }
    }

    suspend fun listDiveCenterInstructors(centerId: String): List<DiveCenterInstructorDto> =
        graph.exploreApi().listDiveCenterInstructors(centerId)

    suspend fun getShops(limit: Int = 80): List<ExploreDiveSite> {
        val api = graph.exploreApi()
        return api.shops(limit = limit).data.map { it.toExploreDiveSite() }
    }

    suspend fun getCountries(): List<String> {
        val api = graph.exploreApi()
        val res = api.countries()
        return if (res.success) res.data.map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
    }

    suspend fun listMyDiveSiteContributions(limit: Int = 30): List<DiveSiteContributionMineDto> {
        val res = graph.exploreApi().listMyDiveSiteContributions(limit)
        return if (res.success) res.data else emptyList()
    }

    suspend fun submitDiveSiteCorrection(diveSiteId: String, message: String) {
        val body = JsonObject().apply {
            addProperty("type", "correction")
            addProperty("diveSiteId", diveSiteId)
            addProperty("message", message)
            add("proposedData", JsonObject())
        }
        val res = graph.exploreApi().submitDiveSiteContribution(body)
        if (res.get("success")?.asBoolean != true) {
            throw IllegalStateException("Contribution rejected")
        }
    }

    suspend fun submitNewDiveSite(
        name: String,
        latitude: Double,
        longitude: Double,
        description: String?,
        message: String?,
    ) {
        val proposed = JsonObject().apply {
            addProperty("name", name)
            addProperty("latitude", latitude)
            addProperty("longitude", longitude)
            if (!description.isNullOrBlank()) addProperty("description", description.trim())
        }
        val body = JsonObject().apply {
            addProperty("type", "new_site")
            add("proposedData", proposed)
            val m = message?.trim()
            if (!m.isNullOrEmpty()) addProperty("message", m)
        }
        val res = graph.exploreApi().submitDiveSiteContribution(body)
        if (res.get("success")?.asBoolean != true) {
            throw IllegalStateException("Contribution rejected")
        }
    }
}
