package com.divehub.app.util

import java.util.Locale

/**
 * Maps backend English country names to localized display labels (same idea as iOS `CountryLocalizationHelper`).
 */
object CountryDisplayNames {
    private val englishNameToIso: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for (iso in Locale.getISOCountries()) {
            val english = Locale("", iso).getDisplayCountry(Locale.ENGLISH)
            if (english.isNotBlank()) {
                map[normalize(english)] = iso
            }
        }
        overrides.forEach { (name, iso) -> map[normalize(name)] = iso }
        map
    }

    private val overrides = mapOf(
        "russian federation" to "RU",
        "russia" to "RU",
        "россия" to "RU",
        "united states" to "US",
        "usa" to "US",
        "united kingdom" to "GB",
        "uk" to "GB",
        "uae" to "AE",
        "united arab emirates" to "AE",
        "czech republic" to "CZ",
        "czechia" to "CZ",
        "south korea" to "KR",
        "north korea" to "KP",
        "vietnam" to "VN",
        "viet nam" to "VN",
        "turkey" to "TR",
        "türkiye" to "TR",
        "turkiye" to "TR",
        "españa" to "ES",
        "espana" to "ES",
        "éire" to "IE",
        "eire" to "IE",
        "éire / ireland" to "IE",
    )

    private fun normalize(name: String): String =
        name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun localized(storedName: String, displayLocale: Locale = Locale.getDefault()): String {
        val trimmed = storedName.trim()
        if (trimmed.isEmpty()) return trimmed
        val key = normalize(trimmed)

        if (key.length == 2) {
            val fromIso = Locale("", key.uppercase(Locale.ROOT)).getDisplayCountry(displayLocale)
            if (fromIso.isNotBlank()) return fromIso
        }

        englishNameToIso[key]?.let { iso ->
            Locale("", iso).getDisplayCountry(displayLocale).takeIf { it.isNotBlank() }?.let { return it }
        }

        for (iso in Locale.getISOCountries()) {
            val localized = Locale("", iso).getDisplayCountry(displayLocale)
            if (normalize(localized) == key) return localized
        }

        return trimmed
    }
}
