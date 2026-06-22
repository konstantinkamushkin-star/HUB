package com.divehub.app.data

import com.divehub.app.AppGraph
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Merges embedded `strings.xml` with optional server overrides from `GET /api/localization/{lang}`.
 * Mirrors iOS `LocalizationService.loadTranslationsFromBackend`.
 */
class LocalizationRepository(private val graph: AppGraph) {
    private val cache = ConcurrentHashMap<String, Map<String, Map<String, String>>>()
    private val mapType = object : TypeToken<Map<String, Map<String, String>>>() {}.type
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    suspend fun sync(language: String) {
        val lang = language.trim().ifBlank { "ru" }
        runCatching {
            val remote = graph.localizationApi().getTranslations(lang)
            cache[lang] = remote
            graph.tokenStore.setServerLocalizationJson(graph.gson.toJson(remote))
            _revision.value += 1
        }
        if (!cache.containsKey(lang)) {
            loadStored(lang)
        }
    }

    suspend fun loadStored(language: String) {
        val lang = language.trim().ifBlank { "ru" }
        if (cache.containsKey(lang)) return
        val json = graph.tokenStore.getServerLocalizationJson() ?: return
        runCatching {
            val parsed: Map<String, Map<String, String>> = graph.gson.fromJson(json, mapType)
            cache[lang] = parsed
            _revision.value += 1
        }
    }

    /** Server override with EN fallback — iOS `localizedString` semantics. */
    fun resolve(language: String, table: String, key: String): String? {
        val lang = language.trim().ifBlank { "ru" }
        lookup(lang, table, key)?.let { return it }
        if (lang != "en") lookup("en", table, key)?.let { return it }
        return lookupAnyTable(lang, key) ?: if (lang != "en") lookupAnyTable("en", key) else null
    }

    fun lookup(language: String, table: String, key: String): String? {
        val lang = language.trim().ifBlank { "ru" }
        return cache[lang]?.get(table)?.get(key)
    }

    private fun lookupAnyTable(language: String, key: String): String? {
        val tables = cache[language.trim().ifBlank { "ru" }] ?: return null
        for (table in tables.values) {
            table[key]?.let { return it }
        }
        return null
    }
}
