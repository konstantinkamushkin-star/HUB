package com.divehub.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.diveEditorRecentDataStore by preferencesDataStore("dive_editor_recent")

/** Recent edited image URIs — iOS `DiveEditorRecentStore`. */
class DiveEditorRecentStore(
    context: Context,
    private val gson: Gson = Gson(),
) {
    private val store = context.applicationContext.diveEditorRecentDataStore
    private val listType = object : TypeToken<List<String>>() {}.type

    val recentUris: Flow<List<String>> = store.data.map { prefs ->
        val raw = prefs[Keys.RECENT_JSON] ?: return@map emptyList()
        runCatching { gson.fromJson<List<String>>(raw, listType) }.getOrElse { emptyList() }
    }

    suspend fun prepend(uri: String, max: Int = 24) {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return
        store.edit { prefs ->
            val current = runCatching {
                gson.fromJson<List<String>>(prefs[Keys.RECENT_JSON] ?: "[]", listType)
            }.getOrElse { emptyList() }
            val next = (listOf(trimmed) + current.filter { it != trimmed }).take(max)
            prefs[Keys.RECENT_JSON] = gson.toJson(next)
        }
    }

    private object Keys {
        val RECENT_JSON = stringPreferencesKey("recent_uris_json")
    }
}
