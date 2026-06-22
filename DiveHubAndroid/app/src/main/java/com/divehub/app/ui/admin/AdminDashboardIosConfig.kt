package com.divehub.app.ui.admin

import org.json.JSONArray

/**
 * iOS [AdminDashboardLayoutPayload] home (blocks `quick` + `cal`), from [diver_profile.adminDashboardLayout].
 */
data class AdminIosHomeConfig(
    val showQuick: Boolean = true,
    val showCal: Boolean = true,
    val blockOrder: List<String> = listOf("quick", "cal"),
    val quickActionTargets: List<String> = listOf("instructors", "services"),
)

fun parseAdminIosHomeConfig(diverProfile: Map<String, Any?>?): AdminIosHomeConfig {
    val raw = diverProfile?.get("adminDashboardLayout") as? Map<*, *> ?: return AdminIosHomeConfig()
    fun bool(key: String, default: Boolean = true): Boolean {
        val v = raw[key] ?: return default
        return when (v) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            else -> default
        }
    }
    val orderRaw: List<String> = when (val v = raw["sectionOrder"]) {
        is List<*> -> v.mapNotNull { it?.toString()?.trim()?.lowercase() }.filter { it.isNotEmpty() }
        is JSONArray ->
            (0 until v.length()).mapNotNull { i ->
                v.optString(i)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            }
        else -> emptyList()
    }
    val knownIos = listOf("quick", "cal")
    val iosOnly = orderRaw.filter { it in knownIos.toSet() }
    val blockOrder = if (iosOnly.isNotEmpty()) {
        iosOnly
    } else {
        knownIos
    }
    val qat: List<String> = when (val v = raw["quickActionTargets"]) {
        is List<*> -> v.mapNotNull { it?.toString()?.trim()?.lowercase() }.filter { it.isNotEmpty() }
        is JSONArray ->
            (0 until v.length()).mapNotNull { i ->
                v.optString(i)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            }
        else -> emptyList()
    }
    val quickActionTargets = if (qat.isNotEmpty()) qat else listOf("instructors", "services")
    return AdminIosHomeConfig(
        showQuick = bool("quick", true),
        showCal = bool("cal", true),
        blockOrder = blockOrder,
        quickActionTargets = quickActionTargets,
    )
}
