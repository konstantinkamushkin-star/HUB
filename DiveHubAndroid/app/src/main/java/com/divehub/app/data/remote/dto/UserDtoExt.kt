package com.divehub.app.data.remote.dto

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
