package com.divehub.app.ui.trips

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Mirrors iOS `Trip.PriceDetails.RoomPrice`. */
data class TripRoomPriceRow(
    val id: String,
    val roomType: String,
    val roomCount: Int,
    val divingPrice: Double,
    val nonDivingPrice: Double,
)

/** Mirrors iOS `Trip.PriceDetails.YachtPrice`. */
data class TripCabinPriceRow(
    val id: String,
    val cabinType: String,
    val cabinCount: Int,
    val divingPrice: Double,
    val nonDivingPrice: Double,
)

fun parseRoomPrices(pd: JsonObject?): List<TripRoomPriceRow> {
    val arr = pd?.getAsJsonArray("roomPrices") ?: return emptyList()
    val out = ArrayList<TripRoomPriceRow>(arr.size())
    for (i in 0 until arr.size()) {
        val el = arr.get(i)
        if (!el.isJsonObject) continue
        val o = el.asJsonObject
        val id = o.get("id")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
        if (id.isEmpty()) continue
        out.add(
            TripRoomPriceRow(
                id = id,
                roomType = o.get("roomType")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty(),
                roomCount = o.get("roomCount")?.takeUnless { it.isJsonNull }?.asInt ?: 1,
                divingPrice = o.get("divingPrice")?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0,
                nonDivingPrice = o.get("nonDivingPrice")?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0,
            ),
        )
    }
    return out
}

fun parseCabinPrices(pd: JsonObject?): List<TripCabinPriceRow> {
    val arr = pd?.getAsJsonArray("yachtPrices") ?: return emptyList()
    val out = ArrayList<TripCabinPriceRow>(arr.size())
    for (i in 0 until arr.size()) {
        val el = arr.get(i)
        if (!el.isJsonObject) continue
        val o = el.asJsonObject
        val id = o.get("id")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
        if (id.isEmpty()) continue
        out.add(
            TripCabinPriceRow(
                id = id,
                cabinType = o.get("cabinType")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty(),
                cabinCount = o.get("cabinCount")?.takeUnless { it.isJsonNull }?.asInt ?: 1,
                divingPrice = o.get("divingPrice")?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0,
                nonDivingPrice = o.get("nonDivingPrice")?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0,
            ),
        )
    }
    return out
}

fun priceDetailsCurrency(pd: JsonObject?): String =
    pd?.get("currency")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty().ifBlank { "—" }

fun parseTripAdditionalExpenseRows(arr: JsonArray?): List<TripExpenseDisplayRow> {
    if (arr == null || arr.size() == 0) return emptyList()
    val out = ArrayList<TripExpenseDisplayRow>(arr.size())
    for (i in 0 until arr.size()) {
        val el = arr.get(i)
        if (!el.isJsonObject) continue
        val o = el.asJsonObject
        val description = o.get("description")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
        val type = o.get("expenseType")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
        val costPrim = o.get("cost")
        val cost = when {
            costPrim == null || costPrim.isJsonNull -> null
            costPrim.isJsonPrimitive && costPrim.asJsonPrimitive.isNumber -> costPrim.asDouble
            costPrim.isJsonPrimitive && costPrim.asJsonPrimitive.isString ->
                costPrim.asString.trim().toDoubleOrNull()
            else -> null
        }
        val currency = o.get("currency")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
        if (description.isEmpty() && cost == null && type.isEmpty()) continue
        out.add(TripExpenseDisplayRow(description = description, expenseType = type, cost = cost, currency = currency))
    }
    return out
}

data class TripExpenseDisplayRow(
    val description: String,
    val expenseType: String,
    val cost: Double?,
    val currency: String,
)

fun JsonObject?.rootDivingPrice(): Double? {
    val o = this ?: return null
    val p = o.get("divingPrice") ?: return null
    if (p.isJsonNull) return null
    return when {
        p.isJsonPrimitive && p.asJsonPrimitive.isNumber -> p.asDouble
        p.isJsonPrimitive && p.asJsonPrimitive.isString -> p.asString.trim().toDoubleOrNull()
        else -> null
    }
}

fun JsonObject?.rootNonDivingPrice(): Double? {
    val o = this ?: return null
    val p = o.get("nonDivingPrice") ?: return null
    if (p.isJsonNull) return null
    return when {
        p.isJsonPrimitive && p.asJsonPrimitive.isNumber -> p.asDouble
        p.isJsonPrimitive && p.asJsonPrimitive.isString -> p.asString.trim().toDoubleOrNull()
        else -> null
    }
}
