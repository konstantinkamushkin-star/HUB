package com.divehub.app.services

import com.divehub.app.data.remote.dto.DiveLogDto
import com.divehub.app.data.remote.dto.UserDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class AchievementContext(
    val logs: List<DiveLogDto>,
    val user: UserDto?,
    val friendsCount: Int,
    val languageTag: String,
)

data class AchievementComputed(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val unlockedAtEpochSec: Long?,
    val progressFraction: Double?,
    val progressText: String?,
)

object AchievementEngine {
    fun build(definitions: List<AchievementDefinition>, context: AchievementContext): List<AchievementComputed> {
        val ru = context.languageTag.lowercase().startsWith("ru")
        return definitions.map { def ->
            val unlocked = unlockAt(def.rule, context)
            val (pFrac, pText) = AchievementProgress.progressFor(def.rule, context)
            AchievementComputed(
                id = def.id,
                title = if (ru) def.titleRu else def.titleEn,
                description = if (ru) def.descriptionRu else def.descriptionEn,
                iconName = def.iconName,
                unlockedAtEpochSec = unlocked,
                progressFraction = if (unlocked != null) 1.0 else pFrac,
                progressText = pText,
            )
        }
    }

    private fun unlockAt(rule: AchievementRule, context: AchievementContext): Long? {
        val logs = context.logs.sortedBy { parseEpoch(it.date) ?: Long.MAX_VALUE }
        fun nthEpoch(filtered: List<DiveLogDto>, n: Int): Long? {
            if (n <= 0) return null
            val s = filtered.sortedBy { parseEpoch(it.date) ?: Long.MAX_VALUE }
            return if (s.size >= n) parseEpoch(s[n - 1].date) else null
        }
        return when (rule.kind) {
            AchievementRuleKind.MIN_TOTAL_DIVES -> nthEpoch(logs, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.MIN_MAX_DEPTH -> {
                val d = rule.valueDouble ?: return null
                logs
                    .sortedBy { parseEpoch(it.date) ?: Long.MAX_VALUE }
                    .firstOrNull { it.maxDepth >= d }
                    ?.let { parseEpoch(it.date) }
            }
            AchievementRuleKind.MIN_SINGLE_BOTTOM_MIN -> {
                val m = rule.valueInt ?: Int.MAX_VALUE
                logs
                    .sortedBy { parseEpoch(it.date) ?: Long.MAX_VALUE }
                    .firstOrNull { it.duration >= m }
                    ?.let { parseEpoch(it.date) }
            }
            AchievementRuleKind.MIN_TOTAL_BOTTOM_MIN -> cumulativeTimeEpoch(logs, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.MIN_UNIQUE_DIVE_SITES -> uniqueThresholdEpoch(logs, rule.valueInt ?: Int.MAX_VALUE) { it.diveSiteId }
            AchievementRuleKind.MIN_UNIQUE_DIVE_CENTERS -> uniqueThresholdEpoch(logs, rule.valueInt ?: Int.MAX_VALUE) { it.diveCenterId }
            AchievementRuleKind.MIN_DIVES_WITH_PHOTO -> nthEpoch(logs.filter { !it.photoUrls.isNullOrEmpty() }, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.MIN_PUBLISHED_DIVES -> nthEpoch(logs.filter { it.isPublished == true }, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.MIN_DIVES_WITH_VIDEO -> nthEpoch(logs.filter { !it.videoUrls.isNullOrEmpty() }, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.MIN_DISTINCT_FISH -> distinctFishEpoch(logs, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.MIN_FISH_IN_SINGLE_DIVE -> logs.firstOrNull { (it.fishSpecies?.size ?: 0) >= (rule.valueInt ?: Int.MAX_VALUE) }?.let { parseEpoch(it.date) }
            AchievementRuleKind.ANY_VISIBILITY_AT_LEAST -> logs.firstOrNull { (it.visibility ?: 0.0) >= (rule.valueDouble ?: Double.MAX_VALUE) }?.let { parseEpoch(it.date) }
            AchievementRuleKind.ANY_WATER_TEMP_AT_MOST -> logs.firstOrNull { (it.waterTemperature ?: Double.MAX_VALUE) <= (rule.valueDouble ?: Double.MIN_VALUE) }?.let { parseEpoch(it.date) }
            AchievementRuleKind.ANY_WATER_TEMP_AT_LEAST -> logs.firstOrNull { (it.waterTemperature ?: Double.MIN_VALUE) >= (rule.valueDouble ?: Double.MAX_VALUE) }?.let { parseEpoch(it.date) }
            AchievementRuleKind.PROFILE_USERNAME_SET -> {
                val u = context.user
                if (u == null) {
                    null
                } else {
                    val handle = u.username?.trim()?.takeIf { it.isNotEmpty() }
                        ?: (u.diverProfile?.get("username") as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    handle?.let { parseEpoch(u.updatedAt) ?: nowEpoch() }
                }
            }
            AchievementRuleKind.PROFILE_ONBOARDING_COMPLETE -> (context.user?.diverProfile?.get("onboardingCompleted") as? Boolean)?.takeIf { it }?.let { parseEpoch(context.user.updatedAt) ?: nowEpoch() }
            AchievementRuleKind.PROFILE_BIO_MIN_CHARS -> context.user?.bio?.trim()?.takeIf { it.length >= (rule.valueInt ?: Int.MAX_VALUE) }?.let { parseEpoch(context.user.updatedAt) ?: nowEpoch() }
            AchievementRuleKind.MAX_DIVES_IN_SINGLE_MONTH -> perMonthThresholdEpoch(logs, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.DEEP_DIVE_COUNT -> {
                val minD = rule.valueDouble ?: return null
                val need = rule.valueInt ?: return null
                val deep = logs.filter { it.maxDepth >= minD }.sortedBy { parseEpoch(it.date) ?: Long.MAX_VALUE }
                nthEpoch(deep, need)
            }
            AchievementRuleKind.AVERAGE_MAX_DEPTH_WITH_MIN_DIVES -> {
                val minCount = rule.valueInt ?: Int.MAX_VALUE
                val minAvg = rule.valueDouble ?: Double.MAX_VALUE
                if (logs.size < minCount) null else {
                    val avg = logs.map { it.maxDepth }.average()
                    if (avg >= minAvg) parseEpoch(logs[minCount - 1].date) else null
                }
            }
            AchievementRuleKind.NIGHT_DIVE_HEURISTIC_COUNT -> nthEpoch(logs.filter(::isNightDive), rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.NOTES_CONTAINS_WRECK -> logs.firstOrNull { containsWreck(it) }?.let { parseEpoch(it.date) }
            AchievementRuleKind.MIN_DIVES_WITH_CURRENT -> nthEpoch(logs.filter { !it.current.isNullOrBlank() }, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.MIN_DIVES_WITH_BUDDY -> nthEpoch(logs.filter { !it.buddy.isNullOrBlank() }, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.MIN_SAME_DIVE_SITE_VISITS -> repeatedSiteEpoch(logs, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.SHORT_DIVE_MAX_MIN -> logs.firstOrNull { it.duration in 1..(rule.valueInt ?: 0) }?.let { parseEpoch(it.date) }
            AchievementRuleKind.WINTER_HEMISPHERE_DIVE -> logs.firstOrNull { monthOf(it.date) in setOf(12, 1, 2) }?.let { parseEpoch(it.date) }
            AchievementRuleKind.MIN_PHOTOS_IN_SINGLE_DIVE -> logs.firstOrNull { (it.photoUrls?.size ?: 0) >= (rule.valueInt ?: Int.MAX_VALUE) }?.let { parseEpoch(it.date) }
            AchievementRuleKind.NOTES_MAX_LEN_IN_ONE_DIVE -> logs.firstOrNull { (it.notes?.length ?: 0) >= (rule.valueInt ?: Int.MAX_VALUE) }?.let { parseEpoch(it.date) }
            AchievementRuleKind.YEAR_WITH_MIN_DIVE_COUNT -> perYearThresholdEpoch(logs, rule.valueInt ?: Int.MAX_VALUE)
            AchievementRuleKind.MIN_FRIENDS -> if (context.friendsCount >= (rule.valueInt ?: Int.MAX_VALUE)) nowEpoch() else null
            AchievementRuleKind.HAS_ANY_DIVE_WITH_NOTES -> logs.firstOrNull { !it.notes.isNullOrBlank() }?.let { parseEpoch(it.date) }
            AchievementRuleKind.ALL_DIVES_SHALLOW_MAX_DEPTH -> {
                val maxD = rule.valueDouble ?: Double.MIN_VALUE
                if (logs.size >= 10 && logs.all { it.maxDepth <= maxD }) parseEpoch(logs[9].date) else null
            }
        }
    }

    private fun parseEpoch(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw).epochSecond }.getOrElse {
            runCatching { LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() }.getOrNull()
        }
    }

    private fun monthOf(raw: String?): Int? =
        runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).monthValue }.getOrElse {
            runCatching { LocalDate.parse(raw).monthValue }.getOrNull()
        }

    private fun yearOf(raw: String?): Int? =
        runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).year }.getOrElse {
            runCatching { LocalDate.parse(raw).year }.getOrNull()
        }

    private fun nowEpoch(): Long = Instant.now().epochSecond

    private fun cumulativeTimeEpoch(logs: List<DiveLogDto>, target: Int): Long? {
        var sum = 0
        for (log in logs) {
            sum += log.duration
            if (sum >= target) return parseEpoch(log.date)
        }
        return null
    }

    private fun uniqueThresholdEpoch(logs: List<DiveLogDto>, target: Int, key: (DiveLogDto) -> String?): Long? {
        val seen = linkedSetOf<String>()
        for (log in logs) {
            val id = key(log)?.trim().orEmpty()
            if (id.isEmpty()) continue
            seen += id
            if (seen.size >= target) return parseEpoch(log.date)
        }
        return null
    }

    private fun distinctFishEpoch(logs: List<DiveLogDto>, target: Int): Long? {
        val seen = linkedSetOf<String>()
        for (log in logs) {
            (log.fishSpecies ?: emptyList()).forEach { s ->
                val v = s.trim().lowercase()
                if (v.isNotEmpty()) seen += v
            }
            if (seen.size >= target) return parseEpoch(log.date)
        }
        return null
    }

    private fun perMonthThresholdEpoch(logs: List<DiveLogDto>, target: Int): Long? {
        val groups = linkedMapOf<String, MutableList<DiveLogDto>>()
        logs.forEach { log ->
            val key = runCatching { Instant.parse(log.date).atZone(ZoneId.systemDefault()).toLocalDate().let { "${it.year}-${it.monthValue}" } }
                .getOrElse {
                    runCatching { LocalDate.parse(log.date).let { "${it.year}-${it.monthValue}" } }.getOrNull()
                } ?: return@forEach
            groups.getOrPut(key) { mutableListOf() }.add(log)
        }
        return groups.values
            .filter { it.size >= target }
            .mapNotNull { arr ->
                val sorted = arr.sortedBy { parseEpoch(it.date) ?: Long.MAX_VALUE }
                parseEpoch(sorted[target - 1].date)
            }
            .minOrNull()
    }

    private fun perYearThresholdEpoch(logs: List<DiveLogDto>, target: Int): Long? {
        val byYear = linkedMapOf<Int, MutableList<DiveLogDto>>()
        logs.forEach { log -> yearOf(log.date)?.let { byYear.getOrPut(it) { mutableListOf() }.add(log) } }
        return byYear.values
            .filter { it.size >= target }
            .mapNotNull { arr ->
                val sorted = arr.sortedBy { parseEpoch(it.date) ?: Long.MAX_VALUE }
                parseEpoch(sorted[target - 1].date)
            }
            .minOrNull()
    }

    /** Matches iOS `sameSiteMaxDate`: among sites with ≥`target` visits, use the most-visited; unlock date = `target`‑th dive there. */
    private fun repeatedSiteEpoch(logs: List<DiveLogDto>, target: Int): Long? {
        val bySite = logs
            .mapNotNull { log -> log.diveSiteId?.trim()?.takeIf { it.isNotEmpty() }?.let { id -> id to log } }
            .groupBy({ it.first }, { it.second })
        val candidates = bySite.values.filter { it.size >= target }
        if (candidates.isEmpty()) return null
        val best = candidates.maxBy { it.size }
        val sorted = best.sortedBy { parseEpoch(it.date) ?: Long.MAX_VALUE }
        return if (sorted.size >= target) parseEpoch(sorted[target - 1].date) else null
    }

    private fun isNightDive(log: DiveLogDto): Boolean {
        val text = "${log.diveType.orEmpty()} ${log.notes.orEmpty()}".lowercase()
        if ("night" in text || "ноч" in text) return true
        val timeRaw = log.startTime ?: return false
        val hour = runCatching { LocalDateTime.parse(timeRaw).hour }.getOrElse {
            runCatching { Instant.parse(timeRaw).atZone(ZoneId.systemDefault()).hour }.getOrNull()
        } ?: return false
        return hour >= 21 || hour <= 5
    }

    private fun containsWreck(log: DiveLogDto): Boolean {
        val blob = "${log.notes.orEmpty()} ${log.conditions.orEmpty()} ${log.diveType.orEmpty()} ${log.locationName.orEmpty()}".lowercase()
        return "wreck" in blob || "врейк" in blob || "пароход" in blob
    }
}
