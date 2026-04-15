package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class NotificationSettingsPatch(
    @SerializedName("pushNotifications") val pushNotifications: Boolean,
    @SerializedName("bookingReminders") val bookingReminders: Boolean,
    @SerializedName("friendActivity") val friendActivity: Boolean,
    @SerializedName("newMessages") val newMessages: Boolean,
)

data class PrivacySettingsPatch(
    @SerializedName("shareLocation") val shareLocation: Boolean,
    @SerializedName("publicProfile") val publicProfile: Boolean,
    @SerializedName("showInFriendSearch") val showInFriendSearch: Boolean,
    @SerializedName("shareLogbook") val shareLogbook: Boolean,
)

data class NotificationPrefs(
    val pushNotifications: Boolean = true,
    val bookingReminders: Boolean = true,
    val friendActivity: Boolean = true,
    val newMessages: Boolean = true,
)

data class PrivacyPrefs(
    val shareLocation: Boolean = false,
    val publicProfile: Boolean = false,
    val showInFriendSearch: Boolean = true,
    val shareLogbook: Boolean = false,
)

/** Metric: meters + °C; imperial: feet + °F (matches iOS `MeasurementUnits`). */
data class MeasurementPrefs(
    val metric: Boolean = true,
)
