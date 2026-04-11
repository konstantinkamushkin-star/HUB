package com.divehub.app.data.remote.dto

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/** Subset of GET /trips response (Gson ignores extra fields). */
data class TripListItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("organizerId") val organizerId: String? = null,
    @SerializedName("organizerType") val organizerType: String? = null,
    @SerializedName("tripType") val tripType: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("startDate") val startDate: String? = null,
    @SerializedName("endDate") val endDate: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("totalSpots") val totalSpots: Int? = 0,
    @SerializedName("bookedSpots") val bookedSpots: Int? = 0,
    @SerializedName("nitroxAvailable") val nitroxAvailable: Boolean? = false,
    @SerializedName("equipmentRentalAvailable") val equipmentRentalAvailable: Boolean? = false,
    @SerializedName("minimumCertificationLevel") val minimumCertificationLevel: String? = null,
    @SerializedName("minimumDives") val minimumDives: Int? = null,
    @SerializedName("hotelId") val hotelId: String? = null,
    @SerializedName("yachtId") val yachtId: String? = null,
    @SerializedName("hotelLabel") val hotelLabel: String? = null,
    @SerializedName("yachtLabel") val yachtLabel: String? = null,
    @SerializedName("groupLeaderId") val groupLeaderId: String? = null,
    @SerializedName("programDays") val programDays: JsonArray? = null,
    @SerializedName("additionalExpenses") val additionalExpenses: JsonArray? = null,
    @SerializedName("priceDetails") val priceDetails: JsonObject? = null,
    @SerializedName("availableCourses") val availableCourses: List<String>? = null,
    @SerializedName("photos") val photos: List<String>? = emptyList(),
    /** JSON array of `{ userId, joinedAt }` or legacy string user ids. */
    @SerializedName("participants") val participants: JsonArray? = null,
)

/**
 * Pairs: userId to optional joinedAt (ISO).
 * Tolerates legacy entries stored as plain user id strings in the array.
 */
fun TripListItemDto.participantUserRows(): List<Pair<String, String?>> {
    val raw = participants ?: return emptyList()
    val out = ArrayList<Pair<String, String?>>(raw.size())
    for (i in 0 until raw.size()) {
        val el = raw.get(i)
        when {
            el.isJsonPrimitive && el.asJsonPrimitive.isString -> {
                val s = el.asString.trim()
                if (s.isNotEmpty()) out.add(s to null)
            }
            el.isJsonObject -> {
                val o = el.asJsonObject
                val id = o.get("userId")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                if (id.isNotEmpty()) {
                    val joined = o.get("joinedAt")?.takeIf { !it.isJsonNull }?.asString
                    out.add(id to joined)
                }
            }
        }
    }
    return out
}

data class TripJoinResponseDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("bookedSpots") val bookedSpots: Int = 0,
    @SerializedName("totalSpots") val totalSpots: Int = 0,
)
