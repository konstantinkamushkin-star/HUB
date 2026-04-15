package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.MeasurementPrefs
import com.divehub.app.data.remote.dto.NotificationPrefs
import com.divehub.app.data.remote.dto.NotificationSettingsPatch
import com.divehub.app.data.remote.dto.PrivacyPrefs
import com.divehub.app.data.remote.dto.PrivacySettingsPatch
import okhttp3.ResponseBody

class ProfilePreferencesRepository(private val graph: AppGraph) {

    private val gson get() = graph.gson

    suspend fun loadNotificationPrefs(): NotificationPrefs {
        val raw = graph.tokenStore.getNotificationPrefsJson() ?: return NotificationPrefs()
        return try {
            gson.fromJson(raw, NotificationPrefs::class.java) ?: NotificationPrefs()
        } catch (_: Exception) {
            NotificationPrefs()
        }
    }

    suspend fun saveNotificationPrefs(p: NotificationPrefs) {
        graph.tokenStore.setNotificationPrefsJson(gson.toJson(p))
        runCatching {
            graph.usersApi().patchNotificationSettings(
                NotificationSettingsPatch(
                    pushNotifications = p.pushNotifications,
                    bookingReminders = p.bookingReminders,
                    friendActivity = p.friendActivity,
                    newMessages = p.newMessages,
                ),
            ).closeQuietly()
        }
    }

    suspend fun loadPrivacyPrefs(): PrivacyPrefs {
        val raw = graph.tokenStore.getPrivacyPrefsJson() ?: return PrivacyPrefs()
        return try {
            gson.fromJson(raw, PrivacyPrefs::class.java) ?: PrivacyPrefs()
        } catch (_: Exception) {
            PrivacyPrefs()
        }
    }

    suspend fun savePrivacyPrefs(p: PrivacyPrefs) {
        graph.tokenStore.setPrivacyPrefsJson(gson.toJson(p))
        runCatching {
            graph.usersApi().patchPrivacySettings(
                PrivacySettingsPatch(
                    shareLocation = p.shareLocation,
                    publicProfile = p.publicProfile,
                    showInFriendSearch = p.showInFriendSearch,
                    shareLogbook = p.shareLogbook,
                ),
            ).closeQuietly()
        }
    }

    suspend fun loadMeasurementPrefs(): MeasurementPrefs {
        val raw = graph.tokenStore.getMeasurementPrefsJson() ?: return MeasurementPrefs()
        return try {
            gson.fromJson(raw, MeasurementPrefs::class.java) ?: MeasurementPrefs()
        } catch (_: Exception) {
            MeasurementPrefs()
        }
    }

    suspend fun saveMeasurementPrefs(p: MeasurementPrefs) {
        graph.tokenStore.setMeasurementPrefsJson(gson.toJson(p))
    }
}

private fun ResponseBody.closeQuietly() {
    runCatching { close() }
}
