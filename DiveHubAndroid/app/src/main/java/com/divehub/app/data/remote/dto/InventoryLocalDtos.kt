package com.divehub.app.data.remote.dto

data class InventoryItemLocal(
    val id: String,
    val name: String,
    val category: String,
    val status: String,
    val condition: String,
    val location: String? = null,
    val size: String? = null,
    val notes: String? = null,
    val createdAt: String,
)

data class MaintenanceTicketLocal(
    val id: String,
    val itemId: String,
    val itemName: String,
    val title: String,
    val status: String,
    val priority: String,
    val createdAt: String,
)

