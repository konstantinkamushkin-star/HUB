package com.divehub.app.ui.navigation

import com.divehub.app.data.remote.dto.UserDto

enum class AppShellKind {
    DIVER,
    ADMIN,
    SHOP,
    INSTRUCTOR,
}

/** Mirrors iOS `MainTabView` partner routing: shop shell when the user owns a shop; center admin vs super-admin share `ADMIN`. */
fun UserDto?.resolveShellKind(preferDiverShell: Boolean = false): AppShellKind {
    if (this == null) return AppShellKind.DIVER
    val r = role?.trim()?.uppercase().orEmpty()
    if (r == "SHOP_ADMIN" || !shopId.isNullOrBlank()) return AppShellKind.SHOP
    if (r == "SUPER_ADMIN" || r == "DIVE_CENTER_ADMIN") return AppShellKind.ADMIN
    if (r == "INSTRUCTOR") {
        if (preferDiverShell) return AppShellKind.DIVER
        return AppShellKind.INSTRUCTOR
    }
    return AppShellKind.DIVER
}
