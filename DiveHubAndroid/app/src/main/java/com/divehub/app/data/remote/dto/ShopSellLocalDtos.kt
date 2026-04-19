package com.divehub.app.data.remote.dto

data class ShopProductLocal(
    val id: String,
    val shopId: String,
    val name: String,
    val price: Double,
    val stock: Int,
    val status: String = "active",
    val updatedAt: String,
)

data class ShopOrderLocal(
    val id: String,
    val shopId: String,
    val customerName: String,
    val itemCount: Int,
    val total: Double,
    val status: String = "new",
    val createdAt: String,
)

