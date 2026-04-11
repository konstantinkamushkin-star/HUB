package com.divehub.app.ui.trips

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

/** Mirrors iOS `Trip.TripProgramDay` / stored `programDays` JSON. */
data class TripProgramDayModel(
    val id: String,
    val dateYmd: String,
    val description: String,
    val activities: List<TripProgramActivityModel>,
)

data class TripProgramActivityModel(
    val id: String,
    val time: String,
    val activity: String,
    val diveSiteId: String?,
    val diveCenterId: String?,
    val notes: String?,
)

fun parseProgramDaysFromJson(arr: JsonArray?): List<TripProgramDayModel> {
    if (arr == null || arr.size() == 0) return emptyList()
    val out = ArrayList<TripProgramDayModel>(arr.size())
    for (i in 0 until arr.size()) {
        val el = arr.get(i)
        if (!el.isJsonObject) continue
        val o = el.asJsonObject
        val id = o.get("id")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
            .ifEmpty { UUID.randomUUID().toString() }
        val dateYmd = o.get("date")?.takeUnless { it.isJsonNull }?.let { d ->
            when {
                d.isJsonPrimitive && d.asJsonPrimitive.isString -> d.asString.trim().take(10)
                else -> d.toString().trim('"').take(10)
            }
        }.orEmpty()
        if (dateYmd.length < 10) continue
        val desc = o.get("description")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
        val actArr = o.getAsJsonArray("activities") ?: JsonArray()
        val activities = ArrayList<TripProgramActivityModel>(actArr.size())
        for (j in 0 until actArr.size()) {
            val ael = actArr.get(j)
            if (!ael.isJsonObject) continue
            val a = ael.asJsonObject
            val aid = a.get("id")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
                .ifEmpty { UUID.randomUUID().toString() }
            val time = a.get("time")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
            val activity = a.get("activity")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
            val notes = a.get("notes")?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
            val diveSiteId = a.get("diveSiteId")?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
            val diveCenterId = a.get("diveCenterId")?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
            activities.add(
                TripProgramActivityModel(
                    id = aid,
                    time = time,
                    activity = activity,
                    diveSiteId = diveSiteId,
                    diveCenterId = diveCenterId,
                    notes = notes,
                ),
            )
        }
        out.add(TripProgramDayModel(id = id, dateYmd = dateYmd, description = desc, activities = activities))
    }
    return out
}

fun List<TripProgramDayModel>.toProgramDaysJsonArray(): JsonArray {
    val arr = JsonArray()
    for (day in this) {
        val o = JsonObject()
        o.addProperty("id", day.id)
        o.addProperty("date", day.dateYmd)
        if (day.description.isNotBlank()) o.addProperty("description", day.description)
        val acts = JsonArray()
        for (a in day.activities) {
            acts.add(
                JsonObject().apply {
                    addProperty("id", a.id)
                    addProperty("time", a.time)
                    addProperty("activity", a.activity)
                    a.diveSiteId?.let { addProperty("diveSiteId", it) }
                    a.diveCenterId?.let { addProperty("diveCenterId", it) }
                    a.notes?.let { addProperty("notes", it) }
                },
            )
        }
        o.add("activities", acts)
        arr.add(o)
    }
    return arr
}

/** Mirrors iOS `Trip.AdditionalExpense` (includes `id` in DB JSON). */
data class TripExpenseEditRow(
    val id: String,
    val expenseType: String,
    val description: String,
    val cost: Double,
    val currency: String,
)

fun parseTripExpensesFromJson(arr: JsonArray?): List<TripExpenseEditRow> {
    if (arr == null || arr.size() == 0) return emptyList()
    val out = ArrayList<TripExpenseEditRow>(arr.size())
    for (i in 0 until arr.size()) {
        val el = arr.get(i)
        if (!el.isJsonObject) continue
        val o = el.asJsonObject
        val id = o.get("id")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
            .ifEmpty { UUID.randomUUID().toString() }
        val type = o.get("expenseType")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
            .ifEmpty { "other" }
        val description = o.get("description")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
        val costPrim = o.get("cost")
        val cost = when {
            costPrim == null || costPrim.isJsonNull -> 0.0
            costPrim.isJsonPrimitive && costPrim.asJsonPrimitive.isNumber -> costPrim.asDouble
            costPrim.isJsonPrimitive && costPrim.asJsonPrimitive.isString ->
                costPrim.asString.trim().toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        val currency = o.get("currency")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
            .ifEmpty { "USD" }
        out.add(TripExpenseEditRow(id, type, description, cost, currency))
    }
    return out
}

fun List<TripExpenseEditRow>.toAdditionalExpensesJsonArray(): JsonArray {
    val arr = JsonArray()
    for (e in this) {
        arr.add(
            JsonObject().apply {
                addProperty("id", e.id)
                addProperty("expenseType", e.expenseType)
                addProperty("description", e.description)
                addProperty("cost", e.cost)
                addProperty("currency", e.currency)
            },
        )
    }
    return arr
}

val TRIP_EXPENSE_TYPES = listOf("flight", "transfer", "nutrition", "reserve", "other")

data class TripProgramActivityDraft(
    val id: String,
    val time: String,
    val activity: String,
    val notes: String,
    val diveSiteId: String,
    val diveCenterId: String,
)

data class TripProgramDayDraft(
    val editingId: String?,
    val dateYmd: String,
    val description: String,
    val activities: List<TripProgramActivityDraft>,
)

data class TripExpenseFormDraft(
    val editingId: String?,
    val expenseType: String,
    val description: String,
    val cost: String,
    val currency: String,
)
