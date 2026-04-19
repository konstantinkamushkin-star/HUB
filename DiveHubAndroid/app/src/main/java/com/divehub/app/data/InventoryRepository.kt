package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.InventoryItemLocal
import com.divehub.app.data.remote.dto.MaintenanceTicketLocal
import com.google.gson.reflect.TypeToken

class InventoryRepository(private val graph: AppGraph) {
    private val gson get() = graph.gson

    suspend fun loadItems(): List<InventoryItemLocal> {
        val raw = graph.tokenStore.getInventoryItemsJson()
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<InventoryItemLocal>>() {}.type
            gson.fromJson<List<InventoryItemLocal>>(raw, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveItems(items: List<InventoryItemLocal>) {
        graph.tokenStore.setInventoryItemsJson(gson.toJson(items))
    }

    suspend fun loadTickets(): List<MaintenanceTicketLocal> {
        val raw = graph.tokenStore.getInventoryTicketsJson()
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<MaintenanceTicketLocal>>() {}.type
            gson.fromJson<List<MaintenanceTicketLocal>>(raw, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveTickets(tickets: List<MaintenanceTicketLocal>) {
        graph.tokenStore.setInventoryTicketsJson(gson.toJson(tickets))
    }

    suspend fun syncFromRemote(centerId: String): Pair<List<InventoryItemLocal>, List<MaintenanceTicketLocal>> {
        val api = graph.partnerAdminApi()
        val items = api.listInventoryItems(centerId)
        val tickets = api.listInventoryTickets(centerId)
        saveItems(items)
        saveTickets(tickets)
        return items to tickets
    }

    suspend fun syncFromRemoteOrCache(centerId: String): Pair<List<InventoryItemLocal>, List<MaintenanceTicketLocal>> =
        runCatching { syncFromRemote(centerId) }.getOrElse { loadItems() to loadTickets() }

    suspend fun upsertItemRemote(centerId: String, item: InventoryItemLocal): InventoryItemLocal {
        val api = graph.partnerAdminApi()
        val body = item.copy(id = insertIdForApi(item.id))
        return api.upsertInventoryItem(centerId, body)
    }

    suspend fun upsertTicketRemote(centerId: String, ticket: MaintenanceTicketLocal): MaintenanceTicketLocal {
        val api = graph.partnerAdminApi()
        val body = ticket.copy(id = insertIdForApi(ticket.id))
        return api.upsertInventoryTicket(centerId, body)
    }

    suspend fun deleteItemRemote(centerId: String, itemId: String) {
        val api = graph.partnerAdminApi()
        api.deleteInventoryItem(itemId)
        syncFromRemote(centerId)
    }

    suspend fun checkInItemRemote(centerId: String, itemId: String) {
        val items = loadItems()
        val cur = items.firstOrNull { it.id == itemId } ?: return
        val cleared = cur.copy(
            status = "available",
            issuedToName = null,
            dueAt = null,
            checkoutNotes = null,
            checkoutHandedOffBy = null,
            checkoutHandedOffAt = null,
        )
        upsertItemRemote(centerId, cleared)
    }

    suspend fun checkInItem(itemId: String) {
        val items = loadItems().map { it ->
            if (it.id != itemId) {
                it
            } else {
                it.copy(
                    status = "available",
                    issuedToName = null,
                    dueAt = null,
                    checkoutNotes = null,
                    checkoutHandedOffBy = null,
                    checkoutHandedOffAt = null,
                )
            }
        }
        saveItems(items)
    }

    suspend fun deleteItemAndRelatedTickets(itemId: String) {
        saveItems(loadItems().filter { it.id != itemId })
        saveTickets(loadTickets().filter { it.itemId != itemId })
    }

    /** Use server id when present; empty string triggers INSERT on Nest. */
    private fun insertIdForApi(localId: String): String =
        if (localId.isNotBlank() && SERVER_UUID.matches(localId)) localId else ""

    companion object {
        private val SERVER_UUID =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    }
}
