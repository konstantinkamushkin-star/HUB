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
    val issuedToName: String? = null,
    val dueAt: String? = null,
    val checkoutNotes: String? = null,
    val checkoutHandedOffBy: String? = null,
    val checkoutHandedOffAt: String? = null,
    val createdAt: String,
)

data class MaintenanceTicketLocal(
    val id: String,
    val itemId: String,
    val itemName: String,
    val title: String,
    val status: String,
    val priority: String,
    val description: String? = null,
    val checklist: List<String> = emptyList(),
    val signedBy: String? = null,
    val signedAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val events: List<MaintenanceTicketEventLocal> = emptyList(),
    val createdAt: String,
)

data class MaintenanceTicketEventLocal(
    val type: String,
    val at: String,
    val note: String? = null,
)

