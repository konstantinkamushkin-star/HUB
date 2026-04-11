package com.divehub.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.divehub.app.BuildConfig
import com.divehub.app.util.mediaOriginBaseUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "divehub_session")

class TokenStore(private val context: Context) {

    private val store get() = context.dataStore

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val USER_JSON = stringPreferencesKey("user_json")
        val API_BASE_OVERRIDE = stringPreferencesKey("api_base_url_override")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val DIVE_EDITOR_ENABLED = booleanPreferencesKey("dive_editor_enabled")
        /** BCP-47 tag: "en", "ru", or unset = follow system */
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        /** Last FCM token successfully sent to `POST users/me/push-token` for current session */
        val LAST_REGISTERED_FCM_TOKEN = stringPreferencesKey("last_registered_fcm_token")
        /** INSTRUCTOR: use diver shell (map, logs, social) instead of partner portal */
        val PREFER_DIVER_SHELL = booleanPreferencesKey("prefer_diver_shell")
    }

    val accessToken: Flow<String?> = store.data.map { it[Keys.ACCESS] }
    val refreshToken: Flow<String?> = store.data.map { it[Keys.REFRESH] }
    val userJson: Flow<String?> = store.data.map { it[Keys.USER_JSON] }

    suspend fun getRootBaseUrl(): String {
        val override = store.data.map { it[Keys.API_BASE_OVERRIDE] }.first()?.trim().orEmpty()
        if (override.isNotEmpty()) {
            return mediaOriginBaseUrl(override)
        }
        return mediaOriginBaseUrl(
            if (BuildConfig.DEBUG) {
                BuildConfig.API_BASE_URL_DEBUG
            } else {
                BuildConfig.API_BASE_URL_RELEASE
            },
        )
    }

    suspend fun setApiBaseOverride(url: String?) {
        store.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(Keys.API_BASE_OVERRIDE)
            } else {
                prefs[Keys.API_BASE_OVERRIDE] = mediaOriginBaseUrl(url.trim())
            }
        }
    }

    suspend fun saveSession(access: String, refresh: String, userJson: String) {
        store.edit { prefs ->
            prefs[Keys.ACCESS] = access
            prefs[Keys.REFRESH] = refresh
            prefs[Keys.USER_JSON] = userJson
        }
    }

    suspend fun updateTokens(access: String, refresh: String) {
        store.edit { prefs ->
            prefs[Keys.ACCESS] = access
            prefs[Keys.REFRESH] = refresh
        }
    }

    suspend fun clearSession() {
        store.edit { prefs ->
            prefs.remove(Keys.ACCESS)
            prefs.remove(Keys.REFRESH)
            prefs.remove(Keys.USER_JSON)
            prefs.remove(Keys.LAST_REGISTERED_FCM_TOKEN)
        }
    }

    suspend fun updateUserJson(json: String) {
        store.edit { prefs -> prefs[Keys.USER_JSON] = json }
    }

    suspend fun getAccessToken(): String? = accessToken.first()
    suspend fun getRefreshToken(): String? = refreshToken.first()
    suspend fun getUserJson(): String? = userJson.first()

    suspend fun getApiBaseOverride(): String? =
        store.data.map { it[Keys.API_BASE_OVERRIDE] }.first()?.trim()?.takeIf { it.isNotEmpty() }

    suspend fun hasCompletedOnboarding(): Boolean =
        store.data.map { it[Keys.HAS_COMPLETED_ONBOARDING] ?: false }.first()

    suspend fun setHasCompletedOnboarding(value: Boolean) {
        store.edit { prefs -> prefs[Keys.HAS_COMPLETED_ONBOARDING] = value }
    }

    suspend fun isDiveEditorEnabled(): Boolean =
        store.data.map { it[Keys.DIVE_EDITOR_ENABLED] ?: true }.first()

    suspend fun setDiveEditorEnabled(value: Boolean) {
        store.edit { prefs -> prefs[Keys.DIVE_EDITOR_ENABLED] = value }
    }

    suspend fun getAppLanguageTag(): String =
        store.data.map { it[Keys.APP_LANGUAGE]?.trim().orEmpty() }.first()

    suspend fun setAppLanguageTag(tag: String?) {
        store.edit { prefs ->
            if (tag.isNullOrBlank()) {
                prefs.remove(Keys.APP_LANGUAGE)
            } else {
                prefs[Keys.APP_LANGUAGE] = tag.trim().lowercase()
            }
        }
    }

    suspend fun getLastRegisteredFcmToken(): String? =
        store.data.map { it[Keys.LAST_REGISTERED_FCM_TOKEN] }.first()

    suspend fun setLastRegisteredFcmToken(token: String?) {
        store.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(Keys.LAST_REGISTERED_FCM_TOKEN)
            } else {
                prefs[Keys.LAST_REGISTERED_FCM_TOKEN] = token
            }
        }
    }

    suspend fun getPreferDiverShell(): Boolean =
        store.data.map { it[Keys.PREFER_DIVER_SHELL] ?: false }.first()

    suspend fun setPreferDiverShell(value: Boolean) {
        store.edit { prefs -> prefs[Keys.PREFER_DIVER_SHELL] = value }
    }
}
