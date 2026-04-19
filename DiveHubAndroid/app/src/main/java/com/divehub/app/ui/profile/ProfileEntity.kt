package com.divehub.app.ui.profile

import com.divehub.app.data.remote.dto.UserDto
import java.util.Locale

sealed interface ProfileEntity {
    val user: UserDto?

    data class Diver(
        override val user: UserDto?,
    ) : ProfileEntity

    data class DiveCenter(
        override val user: UserDto?,
        val diveCenterId: String?,
    ) : ProfileEntity
}

fun UserDto?.toProfileEntity(): ProfileEntity {
    if (this == null) return ProfileEntity.Diver(null)
    val role = role?.trim()?.uppercase(Locale.ROOT).orEmpty()
    val isDiveCenterEntity = role == "DIVE_CENTER_ADMIN" || role == "SUPER_ADMIN"
    return if (isDiveCenterEntity) {
        ProfileEntity.DiveCenter(
            user = this,
            diveCenterId = diveCenterId?.trim()?.takeIf { it.isNotEmpty() },
        )
    } else {
        ProfileEntity.Diver(this)
    }
}
