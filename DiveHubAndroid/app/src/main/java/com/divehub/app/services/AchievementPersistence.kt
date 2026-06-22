package com.divehub.app.services

import android.content.Context

object AchievementPersistence {
    private const val PREF = "divehub_achievements_v1"

    fun merge(context: Context, keyScope: String, computed: List<AchievementComputed>): List<AchievementComputed> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val out = computed.map { a ->
            val key = "$keyScope:${a.id}"
            val saved = prefs.getLong(key, 0L).takeIf { it > 0L }
            if (a.unlockedAtEpochSec == null && saved != null) {
                a.copy(unlockedAtEpochSec = saved, progressFraction = 1.0, progressText = a.progressText)
            } else {
                a
            }
        }
        prefs.edit().apply {
            out.forEach { a ->
                val key = "$keyScope:${a.id}"
                val ts = a.unlockedAtEpochSec
                if (ts != null) putLong(key, ts) else remove(key)
            }
        }.apply()
        return out
    }
}
