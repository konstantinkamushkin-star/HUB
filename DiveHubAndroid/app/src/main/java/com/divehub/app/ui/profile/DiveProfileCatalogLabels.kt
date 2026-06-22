package com.divehub.app.ui.profile

import java.util.Locale

/**
 * Fixed English (industry-style) labels for stored diver profile catalog *codes*.
 * We intentionally do not localize these values: certification names, agencies, etc. stay
 * consistent regardless of app UI language (matches iOS + onboarding pickers).
 */
object DiveProfileCatalog {
    val certificationLevels: List<String> = listOf(
        "TRY_SCUBA",
        "OPEN_WATER",
        "ADVANCED_OPEN_WATER",
        "RESCUE",
        "DIVEMASTER",
        "INSTRUCTOR",
        "NITROX",
        "ADVANCED_NITROX",
        "DECO_PROCEDURES",
        "EXTENDED_RANGE",
        "TRIMIX",
        "ADVANCED_TRIMIX",
        "DEEP_TRIMIX",
        "FREEDIVER",
        "OTHER",
    )

    fun certificationLevelsForPicker(currentSelection: String?): List<String> {
        val current = currentSelection?.trim().orEmpty()
        if (current.isEmpty() || certificationLevels.contains(current)) return certificationLevels
        return certificationLevels + current
    }

    val certifyingAgencies: List<String> = listOf(
        "PADI", "SSI", "CMAS", "NAUI", "RAID", "GUE", "OTHER", "NONE_YET",
    )

    val diveCountRanges: List<String> = listOf(
        "0", "1_10", "11_25", "26_50", "51_100", "100_PLUS",
    )

    val diveInterests: List<String> = listOf(
        "WRECK", "DRIFT", "NIGHT", "CAVE", "PHOTOGRAPHY", "VIDEOGRAPHY",
        "MACRO", "BIG_ANIMALS", "TECHNICAL", "FREEDIVING", "COLD_WATER", "CORAL_REEFS",
    )

    val equipmentKeys: List<String> = listOf(
        "BCD", "REGULATOR", "COMPUTER", "MASK_FINS_SNORKEL", "WETSUIT_DRY_SUIT",
        "TORCH", "CAMERA",
    )
}

object DiveProfileCatalogLabels {
    fun certificationLevelLabel(code: String): String = englishCatalogValue(code)

    fun englishCatalogValue(code: String): String {
        val c = code.trim()
        if (c.isEmpty()) return ""
        return when (c) {
            "TRY_SCUBA" -> "Discover Scuba / Try Scuba"
            "OPEN_WATER" -> "Open Water Diver"
            "ADVANCED_OPEN_WATER" -> "Advanced Open Water Diver"
            "RESCUE" -> "Rescue Diver"
            "DIVEMASTER" -> "Divemaster"
            "INSTRUCTOR" -> "Instructor"
            "NITROX" -> "Nitrox Diver"
            "ADVANCED_NITROX" -> "Advanced Nitrox"
            "DECO_PROCEDURES" -> "Decompression Procedures"
            "EXTENDED_RANGE" -> "Extended Range"
            "TRIMIX" -> "Trimix Diver"
            "ADVANCED_TRIMIX" -> "Advanced Trimix"
            "DEEP_TRIMIX" -> "Deep Trimix"
            "TECHNICAL" -> "Technical Diver"
            "FREEDIVER" -> "Freediver"
            "OTHER" -> "Other"
            "PADI" -> "PADI"
            "SSI" -> "SSI"
            "CMAS" -> "CMAS"
            "NAUI" -> "NAUI"
            "RAID" -> "RAID"
            "GUE" -> "GUE"
            "NONE_YET" -> "None yet"
            "0" -> "0"
            "1_10" -> "1-10"
            "11_25" -> "11-25"
            "26_50" -> "26-50"
            "51_100" -> "51-100"
            "100_PLUS" -> "100+"
            "WRECK" -> "Wreck"
            "DRIFT" -> "Drift"
            "NIGHT" -> "Night"
            "CAVE" -> "Cave"
            "PHOTOGRAPHY" -> "Photography"
            "VIDEOGRAPHY" -> "Videography"
            "MACRO" -> "Macro"
            "BIG_ANIMALS" -> "Big animals"
            "FREEDIVING" -> "Freediving"
            "COLD_WATER" -> "Cold water"
            "CORAL_REEFS" -> "Coral reefs"
            "BCD" -> "BCD"
            "REGULATOR" -> "Regulator"
            "COMPUTER" -> "Computer"
            "MASK_FINS_SNORKEL" -> "Mask, fins, snorkel"
            "WETSUIT_DRY_SUIT" -> "Wetsuit / dry suit"
            "TORCH" -> "Torch"
            "CAMERA" -> "Camera"
            else -> titlecaseUnderscoreCode(c)
        }
    }

    private fun titlecaseUnderscoreCode(raw: String): String =
        raw.replace('_', ' ').lowercase(Locale.ROOT)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}
