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

