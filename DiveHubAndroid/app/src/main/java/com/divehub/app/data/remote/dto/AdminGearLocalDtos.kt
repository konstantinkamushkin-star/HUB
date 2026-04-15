package com.divehub.app.data.remote.dto

data class AdminGearItemLocal(
    val id: String,
    val name: String,
    val category: String,
    val manufacturer: String? = null,
    val status: String = "available",
)

