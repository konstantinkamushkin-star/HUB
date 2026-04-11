package com.divehub.app.data.repository

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.ShopV1DetailDto

class ShopRepository(private val graph: AppGraph) {
    suspend fun getShop(id: String): ShopV1DetailDto {
        val res = graph.shopsApi().getShop(id)
        if (!res.success || res.data == null) {
            error("Shop response invalid")
        }
        return res.data
    }
}
