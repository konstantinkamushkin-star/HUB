package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.ShopOrderLocal
import com.divehub.app.data.remote.dto.ShopOrderRemoteDto
import com.divehub.app.data.remote.dto.ShopProductLocal
import com.divehub.app.data.remote.dto.ShopProductRemoteDto
import com.google.gson.reflect.TypeToken
import java.time.Instant

class ShopSellRepository(private val graph: AppGraph) {
    private val gson get() = graph.gson

    suspend fun loadProducts(shopId: String): List<ShopProductLocal> {
        val raw = graph.tokenStore.getShopProductsJson().orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ShopProductLocal>>() {}.type
            val all = gson.fromJson<List<ShopProductLocal>>(raw, type).orEmpty()
            val cleaned = all.filterNot { isLegacySeedProductId(it.id) }
            if (cleaned.size != all.size) {
                graph.tokenStore.setShopProductsJson(gson.toJson(cleaned))
            }
            cleaned.filter { it.shopId == shopId }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveProducts(shopId: String, items: List<ShopProductLocal>) {
        val all = loadAllProductsMutable()
        all.removeAll { it.shopId == shopId }
        all.addAll(items)
        graph.tokenStore.setShopProductsJson(gson.toJson(all))
    }

    suspend fun syncFromRemote(shopId: String) {
        val api = graph.shopsApi()
        val remoteP = api.listShopProducts(shopId).data
        val remoteO = api.listShopOrders(shopId).data
        val products = remoteP.map { it.toLocal(shopId) }
        val orders = remoteO.map { it.toLocal(shopId) }
        val allP = loadAllProductsMutable()
        allP.removeAll { it.shopId == shopId }
        allP.addAll(products)
        graph.tokenStore.setShopProductsJson(gson.toJson(allP))
        val allO = loadAllOrdersMutable()
        allO.removeAll { it.shopId == shopId }
        allO.addAll(orders)
        graph.tokenStore.setShopOrdersJson(gson.toJson(allO))
    }

    suspend fun syncFromRemoteOrCache(shopId: String) {
        runCatching { syncFromRemote(shopId) }
    }

    suspend fun upsertProduct(product: ShopProductLocal) {
        val api = graph.shopsApi()
        val body = product.toRemoteDto()
        val env = api.saveShopProduct(product.shopId, body)
        val saved = env.data ?: return syncFromRemote(product.shopId)
        mergeOneProduct(saved.toLocal(product.shopId))
    }

    suspend fun createProduct(
        shopId: String,
        name: String,
        price: Double,
        stock: Int,
        status: String,
    ): ShopProductLocal {
        val api = graph.shopsApi()
        val body = ShopProductRemoteDto(
            id = null,
            shopId = shopId,
            name = name,
            price = price,
            stock = stock,
            status = status,
            updatedAt = null,
        )
        val saved = api.saveShopProduct(shopId, body).data
            ?: throw IllegalStateException("Shop product save returned no data")
        val local = saved.toLocal(shopId)
        mergeOneProduct(local)
        return local
    }

    suspend fun loadOrders(shopId: String): List<ShopOrderLocal> {
        val raw = graph.tokenStore.getShopOrdersJson().orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ShopOrderLocal>>() {}.type
            val all = gson.fromJson<List<ShopOrderLocal>>(raw, type).orEmpty()
            val cleaned = all.filterNot { isLegacySeedOrderId(it.id) }
            if (cleaned.size != all.size) {
                graph.tokenStore.setShopOrdersJson(gson.toJson(cleaned))
            }
            cleaned.filter { it.shopId == shopId }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveOrders(shopId: String, items: List<ShopOrderLocal>) {
        val all = loadAllOrdersMutable()
        all.removeAll { it.shopId == shopId }
        all.addAll(items)
        graph.tokenStore.setShopOrdersJson(gson.toJson(all))
    }

    suspend fun upsertOrder(order: ShopOrderLocal) {
        val api = graph.shopsApi()
        val body = order.toRemoteDto()
        val env = api.saveShopOrder(order.shopId, body)
        val saved = env.data ?: return syncFromRemote(order.shopId)
        mergeOneOrder(saved.toLocal(order.shopId))
    }

    suspend fun createOrder(
        shopId: String,
        customerName: String,
        itemCount: Int,
        total: Double,
        status: String,
    ): ShopOrderLocal {
        val api = graph.shopsApi()
        val body = ShopOrderRemoteDto(
            id = null,
            shopId = shopId,
            customerName = customerName,
            itemCount = itemCount,
            total = total,
            status = status,
            createdAt = null,
        )
        val saved = api.saveShopOrder(shopId, body).data
            ?: throw IllegalStateException("Shop order save returned no data")
        val local = saved.toLocal(shopId)
        mergeOneOrder(local)
        return local
    }

    private suspend fun mergeOneProduct(p: ShopProductLocal) {
        val all = loadAllProductsMutable()
        val idx = all.indexOfFirst { it.id == p.id && it.shopId == p.shopId }
        if (idx >= 0) all[idx] = p else all.add(p)
        graph.tokenStore.setShopProductsJson(gson.toJson(all))
    }

    private suspend fun mergeOneOrder(o: ShopOrderLocal) {
        val all = loadAllOrdersMutable()
        val idx = all.indexOfFirst { it.id == o.id && it.shopId == o.shopId }
        if (idx >= 0) all[idx] = o else all.add(o)
        graph.tokenStore.setShopOrdersJson(gson.toJson(all))
    }

    private suspend fun loadAllProductsMutable(): MutableList<ShopProductLocal> {
        val raw = graph.tokenStore.getShopProductsJson().orEmpty()
        if (raw.isBlank()) return mutableListOf()
        return try {
            val type = object : TypeToken<List<ShopProductLocal>>() {}.type
            gson.fromJson<List<ShopProductLocal>>(raw, type).orEmpty().toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private suspend fun loadAllOrdersMutable(): MutableList<ShopOrderLocal> {
        val raw = graph.tokenStore.getShopOrdersJson().orEmpty()
        if (raw.isBlank()) return mutableListOf()
        return try {
            val type = object : TypeToken<List<ShopOrderLocal>>() {}.type
            gson.fromJson<List<ShopOrderLocal>>(raw, type).orEmpty().toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun isLegacySeedProductId(id: String): Boolean =
        id == "seed_product_bcd" || id == "seed_product_fins"

    private fun isLegacySeedOrderId(id: String): Boolean =
        id == "seed_order_1" || id == "seed_order_2"

    private fun nowIso(): String = Instant.now().toString()

    private fun ShopProductRemoteDto.toLocal(shopId: String) = ShopProductLocal(
        id = id ?: "",
        shopId = shopId,
        name = name,
        price = price,
        stock = stock,
        status = status,
        updatedAt = updatedAt ?: nowIso(),
    )

    private fun ShopOrderRemoteDto.toLocal(shopId: String) = ShopOrderLocal(
        id = id ?: "",
        shopId = shopId,
        customerName = customerName,
        itemCount = itemCount,
        total = total,
        status = status,
        createdAt = createdAt ?: nowIso(),
    )

    private fun ShopProductLocal.toRemoteDto() = ShopProductRemoteDto(
        id = id.takeIf { it.isNotBlank() && SERVER_UUID.matches(it) },
        shopId = shopId,
        name = name,
        price = price,
        stock = stock,
        status = status,
        updatedAt = updatedAt,
    )

    private fun ShopOrderLocal.toRemoteDto() = ShopOrderRemoteDto(
        id = id.takeIf { it.isNotBlank() && SERVER_UUID.matches(it) },
        shopId = shopId,
        customerName = customerName,
        itemCount = itemCount,
        total = total,
        status = status,
        createdAt = createdAt,
    )

    companion object {
        private val SERVER_UUID =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    }
}
