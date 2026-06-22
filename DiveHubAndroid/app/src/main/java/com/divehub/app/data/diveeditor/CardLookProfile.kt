package com.divehub.app.data.diveeditor

/**
 * Production default — Bech engine alias on server (`ai2`). Mirrors iOS [NetworkService.cardLookProfile].
 */
data class UnderwaterPhotoProfile(
    val engine: String,
    val strength: Double?,
    val mode: String?,
)

object CardLookProfile {
    val default = UnderwaterPhotoProfile(
        engine = "ai2",
        strength = null,
        mode = null,
    )
}
