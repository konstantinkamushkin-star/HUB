package com.divehub.app.ui.profile.gear

import androidx.annotation.StringRes
import com.divehub.app.R

/**
 * iOS [GearItem.GearCategory] — same raw string in JSON.
 * `COMPASS` is stored as `"compensator"` in Swift.
 */
enum class GearProfileCategory(val raw: String, @StringRes val labelRes: Int) {
    WETSUIT("wetsuit", R.string.ui_gear_category_wetsuit),
    BCD("bcd", R.string.ui_gear_category_bcd),
    REGULATOR("regulator", R.string.ui_gear_category_regulator),
    FINS("fins", R.string.ui_gear_category_fins),
    MASK("mask", R.string.ui_gear_category_mask),
    SNORKEL("snorkel", R.string.ui_gear_category_snorkel),
    BOOT("boot", R.string.ui_gear_category_boot),
    GLOVE("glove", R.string.ui_gear_category_glove),
    WEIGHT("weight", R.string.ui_gear_category_weight),
    TANK("tank", R.string.ui_gear_category_tank),
    COMPUTER("computer", R.string.ui_gear_category_computer),
    CAMERA("camera", R.string.ui_gear_category_camera),
    FLASHLIGHT("flashlight", R.string.ui_gear_category_flashlight),
    COMPASS("compensator", R.string.ui_gear_category_compensator),
    OTHER("other", R.string.ui_gear_category_other),
    ;

    companion object {
        val allEntries: List<GearProfileCategory> = entries

        fun fromRaw(raw: String): GearProfileCategory {
            val t = raw.trim().lowercase()
            if (t == "compass") return COMPASS
            return entries.firstOrNull { it.raw == t } ?: OTHER
        }

        /**
         * Normalize any legacy free-text or odd casing from older Android builds.
         */
        fun normalizeStoredCategory(raw: String): String = fromRaw(raw).raw
    }
}
