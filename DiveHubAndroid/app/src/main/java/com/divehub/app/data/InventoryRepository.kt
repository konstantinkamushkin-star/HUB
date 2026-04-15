package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.InventoryItemLocal
import com.divehub.app.data.remote.dto.MaintenanceTicketLocal
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.util.UUID

class InventoryRepository(private val graph: AppGraph) {
    private val gson get() = graph.gson

    suspend fun loadItems(): List<InventoryItemLocal> {
        val raw = graph.tokenStore.getInventoryItemsJson()
        if (raw.isNullOrBlank()) {
            val seeded = defaultItems()
            saveItems(seeded)
            return seeded
        }
        return try {
            val type = object : TypeToken<List<InventoryItemLocal>>() {}.type
            gson.fromJson<List<InventoryItemLocal>>(raw, type) ?: defaultItems()
        } catch (_: Exception) {
            defaultItems()
        }
    }

    suspend fun saveItems(items: List<InventoryItemLocal>) {
        graph.tokenStore.setInventoryItemsJson(gson.toJson(items))
    }

    suspend fun loadTickets(): List<MaintenanceTicketLocal> {
        val raw = graph.tokenStore.getInventoryTicketsJson()
        if (raw.isNullOrBlank()) {
            val seeded = defaultTickets()
            saveTickets(seeded)
            return seeded
        }
        return try {
            val type = object : TypeToken<List<MaintenanceTicketLocal>>() {}.type
            gson.fromJson<List<MaintenanceTicketLocal>>(raw, type) ?: defaultTickets()
        } catch (_: Exception) {
            defaultTickets()
        }
    }

    suspend fun saveTickets(tickets: List<MaintenanceTicketLocal>) {
        graph.tokenStore.setInventoryTicketsJson(gson.toJson(tickets))
    }

    private fun nowIso() = Instant.now().toString()

    private fun defaultItems(): List<InventoryItemLocal> = listOf(
        InventoryItemLocal(
            id = UUID.randomUUID().toString(),
            name = "Aqualung Wetsuit 5mm L",
            category = "wetsuit",
            status = "available",
            condition = "good",
            location = "Main warehouse",
            size = "L",
            createdAt = nowIso(),
        ),
        InventoryItemLocal(
            id = UUID.randomUUID().toString(),
            name = "Scubapro Regulator MK25",
            category = "regulator",
            status = "issued",
            condition = "good",
            location = "Rental desk",
            createdAt = nowIso(),
        ),
        InventoryItemLocal(
            id = UUID.randomUUID().toString(),
            name = "Mares BCD Rover M",
            category = "bcd",
            status = "maintenance",
            condition = "needs_service",
            location = "Service room",
            size = "M",
            createdAt = nowIso(),
        ),
    )

    private fun defaultTickets(): List<MaintenanceTicketLocal> = listOf(
        MaintenanceTicketLocal(
            id = UUID.randomUUID().toString(),
            itemId = "seed_reg_1",
            itemName = "Scubapro Regulator MK25",
            title = "Annual regulator service",
            status = "open",
            priority = "high",
            createdAt = nowIso(),
        ),
    )
}

