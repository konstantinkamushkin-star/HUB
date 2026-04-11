package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Maps to backend `ReviewableType` when creating/listing reviews. */
enum class ExploreItemKind {
    DIVE_SITE,
    DIVE_CENTER,
    SHOP,
    ;

    fun toApiReviewType(): String = when (this) {
        DIVE_SITE -> "dive_site"
        DIVE_CENTER -> "dive_center"
        SHOP -> "shop"
    }
}

data class DiveSiteDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("country") val country: String? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("diveTypes") val diveTypes: List<String>? = null,
    @SerializedName("difficultyLevel") val difficultyLevel: Int? = null,
    @SerializedName("depthMax") val depthMax: Double? = null,
    @SerializedName("averageRating") val averageRating: Double? = null,
    @SerializedName("reviewCount") val reviewCount: Int? = null,
)

data class DiveCenterSearchResultDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<DiveCenterItemDto> = emptyList(),
)

data class DiveCenterItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("country") val country: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("average_rating") val averageRating: Double? = null,
    @SerializedName("review_count") val reviewCount: Int? = null,
    @SerializedName("nitrox_available") val nitroxAvailable: Boolean? = null,
)

data class ShopSearchResultDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<ShopItemDto> = emptyList(),
)

data class ShopItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("country") val country: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("averageRating") val averageRating: Double? = null,
    @SerializedName("reviewCount") val reviewCount: Int? = null,
    @SerializedName("serviceAvailable") val serviceAvailable: Boolean? = null,
)

data class ExploreDiveSite(
    val id: String,
    val name: String,
    val description: String,
    val country: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val diveType: String,
    val difficulty: String,
    val depthMax: Double,
    val rating: Double,
    val reviewCount: Int,
    val kind: ExploreItemKind = ExploreItemKind.DIVE_SITE,
)

fun DiveSiteDto.toExploreDiveSite(): ExploreDiveSite {
    val difficultyText = when (difficultyLevel ?: 1) {
        1 -> "Beginner"
        2 -> "Intermediate"
        3 -> "Advanced"
        else -> "Expert"
    }
    return ExploreDiveSite(
        id = id,
        name = name,
        description = description.orEmpty(),
        country = country.orEmpty(),
        region = region.orEmpty(),
        latitude = latitude,
        longitude = longitude,
        diveType = diveTypes?.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Other",
        difficulty = difficultyText,
        depthMax = depthMax ?: 0.0,
        rating = averageRating ?: 0.0,
        reviewCount = reviewCount ?: 0,
        kind = ExploreItemKind.DIVE_SITE,
    )
}

fun DiveCenterItemDto.toExploreDiveSite(): ExploreDiveSite = ExploreDiveSite(
    id = id,
    name = name,
    description = "",
    country = country.orEmpty(),
    region = city.orEmpty(),
    latitude = latitude,
    longitude = longitude,
    diveType = "Dive Center",
    difficulty = if (nitroxAvailable == true) "Nitrox" else "Standard",
    depthMax = 0.0,
    rating = averageRating ?: 0.0,
    reviewCount = reviewCount ?: 0,
    kind = ExploreItemKind.DIVE_CENTER,
)

fun ShopItemDto.toExploreDiveSite(): ExploreDiveSite = ExploreDiveSite(
    id = id,
    name = name,
    description = description.orEmpty(),
    country = country.orEmpty(),
    region = city.orEmpty(),
    latitude = latitude,
    longitude = longitude,
    diveType = (type ?: "Shop").replaceFirstChar { it.uppercase() },
    difficulty = if (serviceAvailable == true) "Service" else "Store",
    depthMax = 0.0,
    rating = averageRating ?: 0.0,
    reviewCount = reviewCount ?: 0,
    kind = ExploreItemKind.SHOP,
)
