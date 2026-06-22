package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FriendLocationDto(
    @SerializedName("userId") val userId: String,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("source") val source: String? = null,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("distanceKm") val distanceKm: Double? = null,
) {
    fun displayName(): String {
        val fn = firstName?.trim().orEmpty()
        val ln = lastName?.trim().orEmpty()
        val full = listOf(fn, ln).filter { it.isNotEmpty() }.joinToString(" ")
        return full.ifEmpty { "Diver" }
    }
}

data class DiscoverNearbyDto(
    @SerializedName("userId") val userId: String,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("distanceKm") val distanceKm: Double,
    @SerializedName("updatedAt") val updatedAt: String,
) {
    fun displayName(): String {
        val fn = firstName?.trim().orEmpty()
        val ln = lastName?.trim().orEmpty()
        val full = listOf(fn, ln).filter { it.isNotEmpty() }.joinToString(" ")
        return full.ifEmpty { "Diver" }
    }
}

data class ReportLocationBody(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracyMeters") val accuracyMeters: Double? = null,
    @SerializedName("source") val source: String = "last_known",
)

data class UserProfileSummaryDto(
    @SerializedName("userId") val userId: String,
    @SerializedName("isFriend") val isFriend: Boolean = false,
    @SerializedName("username") val username: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("totalDives") val totalDives: Int? = null,
    @SerializedName("certificationLevel") val certificationLevel: String? = null,
    @SerializedName("certifyingAgencies") val certifyingAgencies: List<String>? = null,
    @SerializedName("countriesDived") val countriesDived: List<String>? = null,
    @SerializedName("uniqueDiveSitesCount") val uniqueDiveSitesCount: Int? = null,
    @SerializedName("deepestDiveMeters") val deepestDiveMeters: Double? = null,
)

data class GroupTripDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("destination") val destination: String? = null,
    @SerializedName("organizerId") val organizerId: String,
    @SerializedName("chatId") val chatId: String,
    @SerializedName("participants") val participants: List<String>,
    @SerializedName("startDate") val startDate: String? = null,
    @SerializedName("endDate") val endDate: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
)

data class CreateGroupTripBody(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("destination") val destination: String? = null,
    @SerializedName("startDate") val startDate: String? = null,
    @SerializedName("endDate") val endDate: String? = null,
    @SerializedName("memberUserIds") val memberUserIds: List<String> = emptyList(),
)

data class AddTripMemberBody(
    @SerializedName("userId") val userId: String,
)
