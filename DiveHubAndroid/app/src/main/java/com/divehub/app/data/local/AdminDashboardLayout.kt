package com.divehub.app.data.local

import org.json.JSONArray
import org.json.JSONObject

/**
 * Dive-center admin home dashboard (stored under `diver_profile.adminDashboardLayout`).
 *
 * - Five boolean flags for Android partner home blocks.
 * - [sectionOrder] — порядок отображения блоков (`managed`, `kpi`, `bookings`, `inventory`, `trips`).
 * - `quick` / `cal` — iOS admin home (см. [KEY_QUICK], [KEY_CAL]); при merge сохраняются с сервера.
 */
data class AdminDashboardLayout(
    val showManagedCenters: Boolean = true,
    val showKpis: Boolean = true,
    val showBookingShortcuts: Boolean = true,
    val showInventoryButton: Boolean = true,
    val showTripsSection: Boolean = true,
    /** Порядок секций; пусто = порядок по умолчанию. */
    val sectionOrder: List<String> = emptyList(),
) {
    fun normalizedSectionOrder(): List<String> {
        val known = DEFAULT_SECTION_ORDER
        val base = if (sectionOrder.isEmpty()) known else sectionOrder.map { it.lowercase().trim() }
        val out = base.filter { it in known.toSet() }.distinct().toMutableList()
        for (k in known) {
            if (k !in out) out.add(k)
        }
        return out
    }

    fun toJson(): String {
        val o = JSONObject()
            .put(KEY_MANAGED, showManagedCenters)
            .put(KEY_KPI, showKpis)
            .put(KEY_BOOKINGS, showBookingShortcuts)
            .put(KEY_INVENTORY, showInventoryButton)
            .put(KEY_TRIPS, showTripsSection)
        val arr = org.json.JSONArray()
        normalizedSectionOrder().forEach { arr.put(it) }
        o.put(KEY_SECTION_ORDER, arr)
        return o.toString()
    }

    /** Merge flags + порядок; сохраняет прочие ключи из `inner` (в т.ч. iOS `quick`/`cal`). */
    fun mergeIntoExisting(inner: Map<String, Any?>?): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        inner?.forEach { (k, v) ->
            when (k) {
                KEY_MANAGED, KEY_KPI, KEY_BOOKINGS, KEY_INVENTORY, KEY_TRIPS, KEY_SECTION_ORDER -> Unit
                KEY_QUICK, KEY_CAL -> v.toLayoutBoolOrNull()?.let { out[k] = it }
                else -> if (v != null) out[k] = v
            }
        }
        out[KEY_MANAGED] = showManagedCenters
        out[KEY_KPI] = showKpis
        out[KEY_BOOKINGS] = showBookingShortcuts
        out[KEY_INVENTORY] = showInventoryButton
        out[KEY_TRIPS] = showTripsSection
        out[KEY_SECTION_ORDER] = normalizedSectionOrder()
        return out
    }

    fun showsSection(id: String): Boolean =
        when (id) {
            KEY_MANAGED -> showManagedCenters
            KEY_KPI -> showKpis
            KEY_BOOKINGS -> showBookingShortcuts
            KEY_INVENTORY -> showInventoryButton
            KEY_TRIPS -> showTripsSection
            else -> false
        }

    companion object {
        const val KEY_MANAGED = "managed"
        const val KEY_KPI = "kpi"
        const val KEY_BOOKINGS = "bookings"
        const val KEY_INVENTORY = "inventory"
        const val KEY_TRIPS = "trips"
        const val KEY_SECTION_ORDER = "sectionOrder"
        const val KEY_QUICK = "quick"
        const val KEY_CAL = "cal"

        val DEFAULT_SECTION_ORDER = listOf(KEY_MANAGED, KEY_KPI, KEY_BOOKINGS, KEY_INVENTORY, KEY_TRIPS)

        val Default = AdminDashboardLayout()

        fun fromJson(raw: String?): AdminDashboardLayout {
            if (raw.isNullOrBlank()) return Default
            return runCatching {
                val o = JSONObject(raw)
                fromJsonObject(o, parseSectionOrderFromJson(o))
            }.getOrDefault(Default)
        }

        fun fromDiverProfile(diverProfile: Map<String, Any?>?): AdminDashboardLayout {
            val nested = diverProfile?.get("adminDashboardLayout") ?: return Default
            val raw = nested as? Map<*, *> ?: return Default
            val o = JSONObject()
            var order: List<String> = emptyList()
            raw.forEach { (k, v) ->
                val key = (k as? String) ?: return@forEach
                when {
                    key == KEY_SECTION_ORDER -> {
                        order = parseSectionOrderFromAny(v)
                    }
                    v is Boolean -> o.put(key, v)
                    v is Number -> o.put(key, v.toDouble() != 0.0)
                }
            }
            return runCatching { fromJsonObject(o, order) }.getOrDefault(Default)
        }

        fun defaultServerMapAllPlatforms(): Map<String, Any> =
            mapOf(
                KEY_MANAGED to true,
                KEY_KPI to true,
                KEY_BOOKINGS to true,
                KEY_INVENTORY to true,
                KEY_TRIPS to true,
                KEY_QUICK to true,
                KEY_CAL to true,
                KEY_SECTION_ORDER to ArrayList(DEFAULT_SECTION_ORDER),
            )

        private fun fromJsonObject(o: JSONObject, order: List<String>): AdminDashboardLayout =
            AdminDashboardLayout(
                showManagedCenters = o.optBoolean(KEY_MANAGED, true),
                showKpis = o.optBoolean(KEY_KPI, true),
                showBookingShortcuts = o.optBoolean(KEY_BOOKINGS, true),
                showInventoryButton = o.optBoolean(KEY_INVENTORY, true),
                showTripsSection = o.optBoolean(KEY_TRIPS, true),
                sectionOrder = order,
            )

        private fun parseSectionOrderFromJson(o: JSONObject): List<String> =
            if (o.has(KEY_SECTION_ORDER)) {
                parseSectionOrderFromAny(o.get(KEY_SECTION_ORDER))
            } else {
                emptyList()
            }

        private fun parseSectionOrderFromAny(v: Any?): List<String> =
            when (v) {
                is List<*> -> v.mapNotNull { it?.toString()?.trim()?.lowercase() }.filter { it.isNotBlank() }
                is JSONArray ->
                    (0 until v.length()).mapNotNull { i ->
                        v.optString(i)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                    }
                else -> emptyList()
            }
    }
}

private fun Any?.toLayoutBoolOrNull(): Boolean? =
    when (this) {
        is Boolean -> this
        is Number -> this.toInt() != 0
        else -> null
    }
