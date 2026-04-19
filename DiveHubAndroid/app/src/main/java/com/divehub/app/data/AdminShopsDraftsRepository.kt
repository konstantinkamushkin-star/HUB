package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AdminShopDraftLocal
import com.google.gson.reflect.TypeToken

class AdminShopsDraftsRepository(private val graph: AppGraph) {

    private val gson get() = graph.gson

    suspend fun loadDrafts(): List<AdminShopDraftLocal> {
        val raw = graph.tokenStore.getAdminShopDraftsJson() ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AdminShopDraftLocal>>() {}.type
            gson.fromJson<List<AdminShopDraftLocal>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun upsertDraft(draft: AdminShopDraftLocal) {
        val list = loadDrafts().toMutableList()
        val idx = list.indexOfFirst { it.id == draft.id }
        if (idx >= 0) list[idx] = draft else list.add(draft)
        graph.tokenStore.setAdminShopDraftsJson(gson.toJson(list))
    }

    suspend fun deleteDraft(id: String) {
        val next = loadDrafts().filterNot { it.id == id }
        graph.tokenStore.setAdminShopDraftsJson(if (next.isEmpty()) null else gson.toJson(next))
    }
}
