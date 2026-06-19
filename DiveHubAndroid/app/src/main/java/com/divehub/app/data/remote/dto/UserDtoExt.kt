package com.divehub.app.data.remote.dto

import java.time.Instant
import java.util.Locale

fun UserDto.needsProfileOnboarding(): Boolean {
    val r = role?.uppercase(Locale.ROOT) ?: return false
    if (r == "DIVE_CENTER_ADMIN" || r == "INSTRUCTOR" || r == "SHOP_ADMIN" || r == "SUPER_ADMIN") {
        return false
    }
    val done = diverProfile?.get("onboardingCompleted")
    val completed = when (done) {
        is Boolean -> done
        is Number -> done.toInt() != 0
        else -> false
    }
    return !completed
}

fun UserDto.hasActiveProSubscription(): Boolean {
    val r = role?.uppercase(Locale.ROOT).orEmpty()
    if (r != "DIVER_PRO") return false
    val tier = subscriptionTier?.lowercase(Locale.ROOT)
        ?: subscriptionStatus?.lowercase(Locale.ROOT)
        ?: return false
    if (tier != "active") return false
    val raw = subscriptionExpiresAt?.trim().orEmpty()
    if (raw.isEmpty()) return true
    val expires = runCatching { Instant.parse(raw) }.getOrNull() ?: return true
    return expires.isAfter(Instant.now())
}

fun UserDto.canCreateCatalogTrip(): Boolean {
    val r = role?.uppercase(Locale.ROOT).orEmpty()
    if (r in setOf("DIVE_CENTER_ADMIN", "INSTRUCTOR", "SUPER_ADMIN")) return true
    return hasActiveProSubscription()
}
