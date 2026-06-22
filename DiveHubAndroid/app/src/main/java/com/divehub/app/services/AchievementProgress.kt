package com.divehub.app.services

import com.divehub.app.data.remote.dto.DiveLogDto
import com.divehub.app.data.remote.dto.UserDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * Parity with iOS [AchievementEngine.progressInfo]: progress bar + caption for locked achievements.
 */
object AchievementProgress {
    fun progressFor(rule: AchievementRule, context: AchievementContext): Pair<Double?, String?> {
        val ru = context.languageTag.lowercase().startsWith("ru")
        fun t(en: String, rus: String) = if (ru) rus else en
        val logs = context.logs.sortedBy { logSortKey(it.date) }
        val u = context.user
        val clamp: (Double) -> Double = { v -> min(1.0, max(0.0, v)) }
        val den: (Int) -> Double = { n -> n.toDouble().coerceAtLeast(1.0) }

        when (rule.kind) {
            AchievementRuleKind.MIN_TOTAL_DIVES -> {
                val n = rule.valueInt ?: 1
                val c = logs.size
                return Pair(clamp(c.toDouble() / den(n)), t("$c/$n dives", "$c/$n погр."))
            }
            AchievementRuleKind.MIN_MAX_DEPTH -> {
                val d = rule.valueDouble ?: 0.0
                val best = logs.maxOfOrNull { it.maxDepth } ?: 0.0
                if (logs.isEmpty()) {
                    return Pair(0.0, t("0 m, need max ≥${d.toInt()} m", "0 м, макс. глубина ≥${d.toInt()} м"))
                }
                return Pair(clamp(best / d), t("max ${best.toInt()}m / need ${d.toInt()}m", "макс. ${best.toInt()}м / ≥${d.toInt()}м"))
            }
            AchievementRuleKind.MIN_SINGLE_BOTTOM_MIN -> {
                val m = rule.valueInt ?: 1
                val best = logs.maxOfOrNull { it.duration } ?: 0
                return Pair(clamp(best / den(m)), t("$best/$m min (one dive)", "$best/$m мин (лог)"))
            }
            AchievementRuleKind.MIN_TOTAL_BOTTOM_MIN -> {
                val target = rule.valueInt ?: 1
                val sum = logs.sumOf { it.duration }
                return Pair(clamp(sum / den(target)), t("$sum/$target min on bottom", "$sum/$target мин на дне"))
            }
            AchievementRuleKind.MIN_UNIQUE_DIVE_SITES -> {
                val n = rule.valueInt ?: 1
                val ids = logs.mapNotNull { it.diveSiteId?.trim() }.filter { it.isNotEmpty() }.toSet()
                val c = ids.size
                if (c == 0 && logs.isEmpty()) {
                    return Pair(0.0, t("0/$n different sites (link a site to logs)", "0/$n — привяжите сайт к погр."))
                }
                if (c == 0) {
                    return Pair(0.0, t("0/$n — set dive site on your logs", "0/$n — укажите дайв-сайт в логах"))
                }
                return Pair(clamp(c / den(n)), t("$c/$n different site IDs", "$c/$n id сайта"))
            }
            AchievementRuleKind.MIN_UNIQUE_DIVE_CENTERS -> {
                val n = rule.valueInt ?: 1
                val ids = logs.mapNotNull { it.diveCenterId?.trim() }.filter { it.isNotEmpty() }.toSet()
                val c = ids.size
                if (c == 0 && logs.isNotEmpty()) {
                    return Pair(0.0, t("0/$n different centers (link a center)", "0/$n центр. (id центра)"))
                }
                return Pair(clamp(c / den(n)), t("$c/$n different center IDs", "$c/$n id дайв-центра"))
            }
            AchievementRuleKind.MIN_DIVES_WITH_PHOTO -> {
                val n = rule.valueInt ?: 1
                val c = logs.count { !it.photoUrls.isNullOrEmpty() }
                return Pair(clamp(c / den(n)), t("$c/$n with photos", "$c/$n с фото"))
            }
            AchievementRuleKind.MIN_PUBLISHED_DIVES -> {
                val n = rule.valueInt ?: 1
                val c = logs.count { it.isPublished == true }
                return Pair(clamp(c / den(n)), t("$c/$n published to feed", "$c/$n публ. в ленту"))
            }
            AchievementRuleKind.MIN_DIVES_WITH_VIDEO -> {
                val n = rule.valueInt ?: 1
                val c = logs.count { !it.videoUrls.isNullOrEmpty() }
                return Pair(clamp(c / den(n)), t("$c/$n with video", "$c/$n с видео"))
            }
            AchievementRuleKind.MIN_DISTINCT_FISH -> {
                val n = rule.valueInt ?: 1
                val set = linkedSetOf<String>()
                logs.forEach { log ->
                    (log.fishSpecies ?: emptyList()).forEach { s ->
                        val x = s.trim().lowercase()
                        if (x.isNotEmpty()) set.add(x)
                    }
                }
                val c = set.size
                return Pair(clamp(c / den(n)), t("$c/$n different species (all logs)", "$c/$n уник. вид(ов)"))
            }
            AchievementRuleKind.MIN_FISH_IN_SINGLE_DIVE -> {
                val n = rule.valueInt ?: 1
                val best = logs.maxOfOrNull { it.fishSpecies?.size ?: 0 } ?: 0
                return Pair(clamp(best / den(n)), t("max $best in one / need $n", "до $best в одной / нужно $n"))
            }
            AchievementRuleKind.ANY_VISIBILITY_AT_LEAST -> {
                val v = rule.valueDouble ?: 0.0
                val withV = logs.mapNotNull { it.visibility }
                if (withV.isEmpty()) {
                    return Pair(null, t("Log visibility (m) on a dive", "Добавьте видимость, м, в погр."))
                }
                val best = withV.maxOrNull() ?: 0.0
                if (best >= v) {
                    return Pair(1.0, t("max ${best.toInt()}m vis.", "видим. до ${best.toInt()}м"))
                }
                return Pair(clamp(best / v), t("max ${best.toInt()}m / need ${v.toInt()}m", "макс. ${best.toInt()}м / ≥${v.toInt()}м"))
            }
            AchievementRuleKind.ANY_WATER_TEMP_AT_MOST -> {
                val tcel = rule.valueDouble ?: 0.0
                val temps = logs.mapNotNull { it.waterTemperature }
                if (temps.isEmpty()) {
                    return Pair(null, t("Log water temperature (°C)", "Укажите темп. воды °C в логе"))
                }
                val coldest = temps.minOrNull()!!
                if (coldest <= tcel) {
                    return Pair(1.0, t("≤ ${coldest.toInt()}°C", "≤ ${coldest.toInt()}°C"))
                }
                return Pair(null, t("coldest ${coldest.toInt()}°C, need ≤${tcel.toInt()}°C", "сам. хол. ${coldest.toInt()}°C, нужно ≤${tcel.toInt()}°C"))
            }
            AchievementRuleKind.ANY_WATER_TEMP_AT_LEAST -> {
                val tcel = rule.valueDouble ?: 0.0
                val temps = logs.mapNotNull { it.waterTemperature }
                if (temps.isEmpty()) {
                    return Pair(null, t("Log water temperature (°C)", "Укажите темп. воды °C"))
                }
                val w = temps.maxOrNull() ?: 0.0
                if (w >= tcel) {
                    return Pair(1.0, t("≥ ${w.toInt()}°C", "≥ ${w.toInt()}°C"))
                }
                return Pair(clamp(w / tcel), t("warmest ${w.toInt()}°C / need ≥${tcel.toInt()}°C", "тёпл. ${w.toInt()}°C / ≥${tcel.toInt()}°C"))
            }
            AchievementRuleKind.PROFILE_USERNAME_SET -> {
                val ok = usernameSet(u)
                return Pair(if (ok) 1.0 else 0.0, t(if (ok) "Username set" else "Set a unique @ handle in profile", if (ok) "Ник задан" else "Укажите @ в профиле"))
            }
            AchievementRuleKind.PROFILE_ONBOARDING_COMPLETE -> {
                val done = (u?.diverProfile?.get("onboardingCompleted") as? Boolean) == true
                return Pair(if (done) 1.0 else 0.0, t(if (done) "Onboarding done" else "Complete diver onboarding", if (done) "Онбординг" else "Заверши настройку профиля"))
            }
            AchievementRuleKind.PROFILE_BIO_MIN_CHARS -> {
                val c = rule.valueInt ?: 1
                val len = u?.bio?.trim().orEmpty().length
                return Pair(clamp(len / den(c)), t("${min(len, c)}/$c characters in bio", "${min(len, c)}/$c симв. в «о себе»"))
            }
            AchievementRuleKind.MAX_DIVES_IN_SINGLE_MONTH -> {
                val need = rule.valueInt ?: 1
                val groups = linkedMapOf<String, MutableList<DiveLogDto>>()
                logs.forEach { log ->
                    val k = yearMonthKey(log.date) ?: return@forEach
                    groups.getOrPut(k) { mutableListOf() }.add(log)
                }
                val m = groups.values.maxOfOrNull { it.size } ?: 0
                return Pair(clamp(m.toDouble() / den(need)), t("best month: $m/$need dives", "лучш. месяц: $m/$need погр."))
            }
            AchievementRuleKind.DEEP_DIVE_COUNT -> {
                val minD = rule.valueDouble ?: 0.0
                val count = rule.valueInt ?: 1
                val deep = logs.filter { it.maxDepth >= minD }
                return Pair(
                    clamp(deep.size / den(count)),
                    t("${deep.size}/$count dives at ≥${minD.toInt()}m", "${deep.size}/$count погр. ≥${minD.toInt()}м"),
                )
            }
            AchievementRuleKind.AVERAGE_MAX_DEPTH_WITH_MIN_DIVES -> {
                val minAvg = rule.valueDouble ?: 0.0
                val minLogCount = rule.valueInt ?: 1
                val n = logs.size
                if (n == 0) {
                    return Pair(0.0, t("0/$minLogCount logs for average", "0/$minLogCount погр. для средн."))
                }
                if (n < minLogCount) {
                    return Pair(
                        clamp(n / den(minLogCount)),
                        t("$n/$minLogCount logs (then avg ≥ ${minAvg.toInt()}m)", "$n/$minLogCount погр. (потом ср. ≥ ${minAvg.toInt()}м)"),
                    )
                }
                val sum = logs.sumOf { it.maxDepth }
                val avg = sum / n
                if (avg >= minAvg) {
                    return Pair(1.0, t("avg ${"%.1f".format(avg)}m (≥ ${minAvg.toInt()}m)", "средн. ${"%.1f".format(avg)}м"))
                }
                return Pair(clamp(avg / minAvg), t("avg ${"%.1f".format(avg)}m / need ≥ ${minAvg.toInt()}m", "ср. ${"%.1f".format(avg)}м / ≥ ${minAvg.toInt()}м"))
            }
            AchievementRuleKind.NIGHT_DIVE_HEURISTIC_COUNT -> {
                val need = rule.valueInt ?: 1
                val n = logs.count { isNightDiveHeuristic(it) }
                return Pair(
                    clamp(n.toDouble() / den(need)),
                    t("$n/$need “night” dives (time/notes)", "$n/$need «ночн.» (время/текст)"),
                )
            }
            AchievementRuleKind.NOTES_CONTAINS_WRECK -> {
                val hit = logs.any { containsWreckHeuristic(it) }
                if (hit) {
                    return Pair(1.0, t("Keyword found in log", "Ключев. слов. в логе"))
                }
                return Pair(0.0, t("Add “wreck” / «врейк» in notes or title", "Добав. wreck/врейк в лог, услов. или имя м."))
            }
            AchievementRuleKind.MIN_DIVES_WITH_CURRENT -> {
                val n = rule.valueInt ?: 1
                val c = logs.count { !it.current.isNullOrBlank() }
                return Pair(clamp(c / den(n)), t("$c/$n with current", "$c/$n с теч."))
            }
            AchievementRuleKind.MIN_DIVES_WITH_BUDDY -> {
                val n = rule.valueInt ?: 1
                val c = logs.count { !it.buddy.isNullOrBlank() }
                return Pair(clamp(c / den(n)), t("$c/$n with buddy", "$c/$n с напарник."))
            }
            AchievementRuleKind.MIN_SAME_DIVE_SITE_VISITS -> {
                val need = rule.valueInt ?: 1
                val by = hashMapOf<String, Int>()
                var m = 0
                for (log in logs) {
                    val id = log.diveSiteId?.trim().orEmpty()
                    if (id.isEmpty()) continue
                    val c = (by[id] ?: 0) + 1
                    by[id] = c
                    if (c > m) m = c
                }
                if (m == 0 && logs.isNotEmpty()) {
                    return Pair(0.0, t("0/… need same site id repeated", "0/… в одного сайта (id)"))
                }
                return Pair(clamp(m / den(need)), t("$m/$need at the same site id", "$m/$need к тому же сайту"))
            }
            AchievementRuleKind.SHORT_DIVE_MAX_MIN -> {
                val maxMin = rule.valueInt ?: 0
                val ok = logs.any { it.duration in 1..maxMin }
                return Pair(
                    if (ok) 1.0 else 0.0,
                    t(
                        if (ok) "You have a ≤ $maxMin min log" else "Add a log with bottom ≤ $maxMin min",
                        if (ok) "Есть лог ≤ $maxMin мин" else "Созд. погр. с временем на дне ≤ $maxMin мин",
                    ),
                )
            }
            AchievementRuleKind.WINTER_HEMISPHERE_DIVE -> {
                val ok = logs.any { log ->
                    val mo = monthOfLog(log.date) ?: return@any false
                    mo == 12 || mo == 1 || mo == 2
                }
                return Pair(
                    if (ok) 1.0 else 0.0,
                    t(
                        if (ok) "Dive in Dec / Jan / Feb" else "Log a dive in Dec, Jan, or Feb",
                        if (ok) "Дек / янв / фев" else "Сдел. погр. в дек, янв или февр.",
                    ),
                )
            }
            AchievementRuleKind.MIN_PHOTOS_IN_SINGLE_DIVE -> {
                val n = rule.valueInt ?: 1
                val best = logs.maxOfOrNull { it.photoUrls?.size ?: 0 } ?: 0
                return Pair(
                    clamp(best / den(n)),
                    t("max $best in one / need $n photos", "макс. $best в одн. / нужно $n фото"),
                )
            }
            AchievementRuleKind.NOTES_MAX_LEN_IN_ONE_DIVE -> {
                val n = rule.valueInt ?: 1
                val best = logs.maxOfOrNull { (it.notes?.length) ?: 0 } ?: 0
                return Pair(
                    clamp(best / den(n)),
                    t("longest notes: ${min(best, n)}/$n chars", "сам. дл. notes: ${min(best, n)}/$n с."),
                )
            }
            AchievementRuleKind.YEAR_WITH_MIN_DIVE_COUNT -> {
                val need = rule.valueInt ?: 1
                val by = hashMapOf<Int, MutableList<DiveLogDto>>()
                logs.forEach { log ->
                    val y = yearOfLog(log.date) ?: return@forEach
                    by.getOrPut(y) { mutableListOf() }.add(log)
                }
                val m = by.values.maxOfOrNull { it.size } ?: 0
                return Pair(
                    clamp(m.toDouble() / need.toDouble().coerceAtLeast(1.0)),
                    t("best year: $m/$need logs", "лучш. год: $m/$need погр."),
                )
            }
            AchievementRuleKind.MIN_FRIENDS -> {
                val n = rule.valueInt ?: 1
                val c = context.friendsCount
                return Pair(
                    clamp(c / den(n)),
                    t("$c/$n in-app friends", "$c/$n друга(ей) в приложен."),
                )
            }
            AchievementRuleKind.HAS_ANY_DIVE_WITH_NOTES -> {
                val hit = logs.any { !it.notes.isNullOrBlank() }
                return Pair(
                    if (hit) 1.0 else 0.0,
                    t(if (hit) "You have notes in a log" else "Write a note in any dive", if (hit) "Заметка в погр. есть" else "Впиш. заметку в люб. погр."),
                )
            }
            AchievementRuleKind.ALL_DIVES_SHALLOW_MAX_DEPTH -> {
                val maxD = rule.valueDouble ?: 12.0
                val deep = logs.filter { it.maxDepth > maxD }
                if (deep.isNotEmpty()) {
                    return Pair(0.0, t("Some logs exceed ${maxD.toInt()}m (need all ≤ ${maxD.toInt()}m for 10+)", "Есть погр. > ${maxD.toInt()}м (нужны все ≤ ${maxD.toInt()}м)"))
                }
                if (logs.isEmpty()) {
                    return Pair(0.0, t("0/10… need 10+ shallow logs (≤ ${maxD.toInt()}m each)", "0/10, нуж. 10+ погр. (каж. ≤ ${maxD.toInt()}м)"))
                }
                if (logs.size < 10) {
                    return Pair(
                        clamp(logs.size / 10.0),
                        t("${logs.size}/10 shallow (≤ ${maxD.toInt()}m) logs", "${logs.size}/10 мелк. (каж. ≤ ${maxD.toInt()}м)"),
                    )
                }
                return Pair(1.0, t("10+ shallow, all ≤ ${maxD.toInt()}m", "10+ погр., все ≤ ${maxD.toInt()}м"))
            }
        }
    }

    private fun usernameSet(u: UserDto?): Boolean {
        if (u == null) return false
        if (!u.username.isNullOrBlank()) return true
        val m = u.diverProfile?.get("username") as? String
        return !m.isNullOrBlank()
    }

    private fun logSortKey(raw: String?): Long = when {
        raw.isNullOrBlank() -> Long.MAX_VALUE
        else -> runCatching { Instant.parse(raw).toEpochMilli() }
            .getOrElse {
                runCatching { LocalDate.parse(raw).toEpochDay() * 86400000 }.getOrNull() ?: Long.MAX_VALUE
            }
    }

    private fun yearMonthKey(raw: String?): String? = when {
        raw.isNullOrBlank() -> null
        else -> runCatching {
            val i = Instant.parse(raw)
            val z = i.atZone(ZoneId.systemDefault())
            "${z.year}-${z.monthValue}"
        }.getOrElse {
            runCatching { LocalDate.parse(raw) }.getOrNull()?.let { "${it.year}-${it.monthValue}" }
        }
    }

    private fun yearOfLog(raw: String?): Int? = when {
        raw.isNullOrBlank() -> null
        else -> runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).year }
            .getOrElse { runCatching { LocalDate.parse(raw).year }.getOrNull() }
    }

    private fun monthOfLog(raw: String?): Int? = when {
        raw.isNullOrBlank() -> null
        else -> runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).monthValue }
            .getOrElse { runCatching { LocalDate.parse(raw).monthValue }.getOrNull() }
    }

    private fun isNightDiveHeuristic(log: DiveLogDto): Boolean {
        val text = "${log.diveType.orEmpty()} ${log.notes.orEmpty()}".lowercase()
        if ("night" in text || "ноч" in text) return true
        val timeRaw = log.startTime ?: return false
        val hour = runCatching { LocalDateTime.parse(timeRaw).hour }.getOrElse {
            runCatching { Instant.parse(timeRaw).atZone(ZoneId.systemDefault()).hour }.getOrNull()
        } ?: return false
        return hour >= 21 || hour <= 5
    }

    private fun containsWreckHeuristic(log: DiveLogDto): Boolean {
        val blob = "${log.notes.orEmpty()} ${log.conditions.orEmpty()} ${log.diveType.orEmpty()} ${log.locationName.orEmpty()}".lowercase()
        return "wreck" in blob || "врейк" in blob || "пароход" in blob
    }
}
