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
import java.util.Locale

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
        /** "light", "dark", or unset = follow system (iOS Appearance). */
        val APP_THEME = stringPreferencesKey("app_theme")
        /** Last FCM token successfully sent to `POST users/me/push-token` for current session */
        val LAST_REGISTERED_FCM_TOKEN = stringPreferencesKey("last_registered_fcm_token")
        /** INSTRUCTOR: use diver shell (map, logs, social) instead of partner portal */
        val PREFER_DIVER_SHELL = booleanPreferencesKey("prefer_diver_shell")
        val NOTIFICATION_PREFS_JSON = stringPreferencesKey("notification_prefs_json")
        val PRIVACY_PREFS_JSON = stringPreferencesKey("privacy_prefs_json")
        val MEASUREMENT_PREFS_JSON = stringPreferencesKey("measurement_prefs_json")
        val GEAR_PROFILES_JSON = stringPreferencesKey("gear_profiles_json")
        val ADMIN_GEAR_ITEMS_JSON = stringPreferencesKey("admin_gear_items_json")
        val ADMIN_BOOKINGS_JSON = stringPreferencesKey("admin_bookings_json")
        val ADMIN_AFFILIATED_SITES_JSON = stringPreferencesKey("admin_affiliated_sites_json")
        val INVENTORY_ITEMS_JSON = stringPreferencesKey("inventory_items_json")
        val INVENTORY_TICKETS_JSON = stringPreferencesKey("inventory_tickets_json")
        val PARTNER_COURSES_JSON = stringPreferencesKey("partner_courses_json")
        val SHOP_PRODUCTS_JSON = stringPreferencesKey("shop_products_json")
        val SHOP_ORDERS_JSON = stringPreferencesKey("shop_orders_json")
        val ADMIN_SHOP_DRAFTS_JSON = stringPreferencesKey("admin_shop_drafts_json")
        val ADMIN_CENTER_INSTRUCTORS_JSON = stringPreferencesKey("admin_center_instructors_json")
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

    /** Emits BCP-47 tag or empty when user clears app language (follow system). */
    val appLanguageTagFlow: Flow<String> = store.data.map { prefs ->
        prefs[Keys.APP_LANGUAGE]?.trim().orEmpty()
    }

    suspend fun setAppLanguageTag(tag: String?) {
        store.edit { prefs ->
            if (tag.isNullOrBlank()) {
                prefs.remove(Keys.APP_LANGUAGE)
            } else {
                prefs[Keys.APP_LANGUAGE] = tag.trim().lowercase()
            }
        }
    }

    /** Emits "", "light", or "dark" (empty = match system). */
    val appThemeFlow: Flow<String> = store.data.map { prefs ->
        (prefs[Keys.APP_THEME]?.trim()?.lowercase(Locale.ROOT)).orEmpty()
    }

    suspend fun setAppTheme(mode: String?) {
        store.edit { prefs ->
            when {
                mode.isNullOrBlank() -> prefs.remove(Keys.APP_THEME)
                mode.equals("system", ignoreCase = true) -> prefs.remove(Keys.APP_THEME)
                mode.equals("light", ignoreCase = true) -> prefs[Keys.APP_THEME] = "light"
                mode.equals("dark", ignoreCase = true) -> prefs[Keys.APP_THEME] = "dark"
                else -> prefs.remove(Keys.APP_THEME)
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

    suspend fun getNotificationPrefsJson(): String? =
        store.data.map { it[Keys.NOTIFICATION_PREFS_JSON] }.first()

    suspend fun setNotificationPrefsJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.NOTIFICATION_PREFS_JSON)
            else prefs[Keys.NOTIFICATION_PREFS_JSON] = json
        }
    }

    suspend fun getPrivacyPrefsJson(): String? =
        store.data.map { it[Keys.PRIVACY_PREFS_JSON] }.first()

    suspend fun setPrivacyPrefsJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.PRIVACY_PREFS_JSON)
            else prefs[Keys.PRIVACY_PREFS_JSON] = json
        }
    }

    suspend fun getMeasurementPrefsJson(): String? =
        store.data.map { it[Keys.MEASUREMENT_PREFS_JSON] }.first()

    suspend fun setMeasurementPrefsJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.MEASUREMENT_PREFS_JSON)
            else prefs[Keys.MEASUREMENT_PREFS_JSON] = json
        }
    }

    suspend fun getGearProfilesJson(): String? =
        store.data.map { it[Keys.GEAR_PROFILES_JSON] }.first()

    suspend fun setGearProfilesJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.GEAR_PROFILES_JSON)
            else prefs[Keys.GEAR_PROFILES_JSON] = json
        }
    }

    suspend fun getAdminGearItemsJson(): String? =
        store.data.map { it[Keys.ADMIN_GEAR_ITEMS_JSON] }.first()

    suspend fun setAdminGearItemsJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.ADMIN_GEAR_ITEMS_JSON)
            else prefs[Keys.ADMIN_GEAR_ITEMS_JSON] = json
        }
    }

    suspend fun getAdminBookingsJson(): String? =
        store.data.map { it[Keys.ADMIN_BOOKINGS_JSON] }.first()

    suspend fun setAdminBookingsJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.ADMIN_BOOKINGS_JSON)
            else prefs[Keys.ADMIN_BOOKINGS_JSON] = json
        }
    }

    suspend fun getAdminAffiliatedSitesJson(): String? =
        store.data.map { it[Keys.ADMIN_AFFILIATED_SITES_JSON] }.first()

    suspend fun setAdminAffiliatedSitesJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.ADMIN_AFFILIATED_SITES_JSON)
            else prefs[Keys.ADMIN_AFFILIATED_SITES_JSON] = json
        }
    }

    suspend fun getInventoryItemsJson(): String? =
        store.data.map { it[Keys.INVENTORY_ITEMS_JSON] }.first()

    suspend fun setInventoryItemsJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.INVENTORY_ITEMS_JSON)
            else prefs[Keys.INVENTORY_ITEMS_JSON] = json
        }
    }

    suspend fun getInventoryTicketsJson(): String? =
        store.data.map { it[Keys.INVENTORY_TICKETS_JSON] }.first()

    suspend fun setInventoryTicketsJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.INVENTORY_TICKETS_JSON)
            else prefs[Keys.INVENTORY_TICKETS_JSON] = json
        }
    }

    suspend fun getPartnerCoursesJson(): String? =
        store.data.map { it[Keys.PARTNER_COURSES_JSON] }.first()

    suspend fun setPartnerCoursesJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.PARTNER_COURSES_JSON)
            else prefs[Keys.PARTNER_COURSES_JSON] = json
        }
    }

    suspend fun getShopProductsJson(): String? =
        store.data.map { it[Keys.SHOP_PRODUCTS_JSON] }.first()

    suspend fun setShopProductsJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.SHOP_PRODUCTS_JSON)
            else prefs[Keys.SHOP_PRODUCTS_JSON] = json
        }
    }

    suspend fun getShopOrdersJson(): String? =
        store.data.map { it[Keys.SHOP_ORDERS_JSON] }.first()

    suspend fun setShopOrdersJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.SHOP_ORDERS_JSON)
            else prefs[Keys.SHOP_ORDERS_JSON] = json
        }
    }

    suspend fun getAdminShopDraftsJson(): String? =
        store.data.map { it[Keys.ADMIN_SHOP_DRAFTS_JSON] }.first()

    suspend fun setAdminShopDraftsJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.ADMIN_SHOP_DRAFTS_JSON)
            else prefs[Keys.ADMIN_SHOP_DRAFTS_JSON] = json
        }
    }

    suspend fun getAdminCenterInstructorsJson(): String? =
        store.data.map { it[Keys.ADMIN_CENTER_INSTRUCTORS_JSON] }.first()

    suspend fun setAdminCenterInstructorsJson(json: String?) {
        store.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.ADMIN_CENTER_INSTRUCTORS_JSON)
            else prefs[Keys.ADMIN_CENTER_INSTRUCTORS_JSON] = json
        }
    }
}
