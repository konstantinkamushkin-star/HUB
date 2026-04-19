package com.divehub.app.data.remote.dto

data class AdminBookingLocal(
    val id: String,
    val diveCenterId: String,
    val serviceId: String,
    val date: String,
    val startTime: String,
    val participantsCount: Int,
    val amount: Double,
    val status: String,
    val createdAt: String,
)

data class AdminAffiliatedSitesLocal(
    val centerId: String,
    val siteIds: List<String>,
)

/** Local-only admin shop rows until backend shop CRUD exists. */
data class AdminShopDraftLocal(
    val id: String,
    val name: String,
    val country: String = "",
    val region: String = "",
)

data class AdminInstructorLocal(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
)

data class AdminCenterInstructorsLocal(
    val centerId: String,
    val added: List<AdminInstructorLocal> = emptyList(),
    val removedIds: List<String> = emptyList(),
)

