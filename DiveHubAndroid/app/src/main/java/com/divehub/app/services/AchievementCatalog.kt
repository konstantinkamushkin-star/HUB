package com.divehub.app.services

enum class AchievementRuleKind {
    MIN_TOTAL_DIVES,
    MIN_MAX_DEPTH,
    MIN_SINGLE_BOTTOM_MIN,
    MIN_TOTAL_BOTTOM_MIN,
    MIN_UNIQUE_DIVE_SITES,
    MIN_UNIQUE_DIVE_CENTERS,
    MIN_DIVES_WITH_PHOTO,
    MIN_PUBLISHED_DIVES,
    MIN_DIVES_WITH_VIDEO,
    MIN_DISTINCT_FISH,
    MIN_FISH_IN_SINGLE_DIVE,
    ANY_VISIBILITY_AT_LEAST,
    ANY_WATER_TEMP_AT_MOST,
    ANY_WATER_TEMP_AT_LEAST,
    PROFILE_USERNAME_SET,
    PROFILE_ONBOARDING_COMPLETE,
    PROFILE_BIO_MIN_CHARS,
    MAX_DIVES_IN_SINGLE_MONTH,
    DEEP_DIVE_COUNT,
    AVERAGE_MAX_DEPTH_WITH_MIN_DIVES,
    NIGHT_DIVE_HEURISTIC_COUNT,
    NOTES_CONTAINS_WRECK,
    MIN_DIVES_WITH_CURRENT,
    MIN_DIVES_WITH_BUDDY,
    MIN_SAME_DIVE_SITE_VISITS,
    SHORT_DIVE_MAX_MIN,
    WINTER_HEMISPHERE_DIVE,
    MIN_PHOTOS_IN_SINGLE_DIVE,
    NOTES_MAX_LEN_IN_ONE_DIVE,
    YEAR_WITH_MIN_DIVE_COUNT,
    MIN_FRIENDS,
    HAS_ANY_DIVE_WITH_NOTES,
    ALL_DIVES_SHALLOW_MAX_DEPTH,
}

data class AchievementRule(
    val kind: AchievementRuleKind,
    val valueInt: Int? = null,
    val valueDouble: Double? = null,
    val secondInt: Int? = null,
)

data class AchievementDefinition(
    val id: String,
    val titleEn: String,
    val titleRu: String,
    val descriptionEn: String,
    val descriptionRu: String,
    val iconName: String,
    val rule: AchievementRule,
)

/**
 * Kept in lockstep with iOS [AchievementCatalog] (59 definitions).
 */
object AchievementCatalog {
    val all: List<AchievementDefinition> = listOf(
        // Dive count
        AchievementDefinition("ach.dives.1", "First Splash", "Первый всплеск", "Log your first dive.", "Добавьте первую запись в логбук.", "drop.fill", AchievementRule(AchievementRuleKind.MIN_TOTAL_DIVES, valueInt = 1)),
        AchievementDefinition("ach.dives.5", "Getting Hooked", "Зацепило", "Log 5 dives.", "5 записей в логбуке.", "5.circle.fill", AchievementRule(AchievementRuleKind.MIN_TOTAL_DIVES, valueInt = 5)),
        AchievementDefinition("ach.dives.10", "Double Digits", "Двузначные", "Log 10 dives.", "10 погружений в логе.", "10.circle.fill", AchievementRule(AchievementRuleKind.MIN_TOTAL_DIVES, valueInt = 10)),
        AchievementDefinition("ach.dives.25", "Quarter Century", "Четверть века", "Log 25 dives.", "25 погружений.", "25.square.fill", AchievementRule(AchievementRuleKind.MIN_TOTAL_DIVES, valueInt = 25)),
        AchievementDefinition("ach.dives.50", "Half Hundred", "Полсотни", "Log 50 dives.", "50 погружений в логбуке.", "50.circle.fill", AchievementRule(AchievementRuleKind.MIN_TOTAL_DIVES, valueInt = 50)),
        AchievementDefinition("ach.dives.100", "Century Club", "Сотня", "Log 100 dives.", "100 погружений.", "100.circle.fill", AchievementRule(AchievementRuleKind.MIN_TOTAL_DIVES, valueInt = 100)),
        AchievementDefinition("ach.dives.200", "Deep Catalog", "Каталог", "Log 200 dives.", "200 записей в логе.", "book.closed.fill", AchievementRule(AchievementRuleKind.MIN_TOTAL_DIVES, valueInt = 200)),
        // Depth
        AchievementDefinition("ach.depth.12", "Deeper than Snorkel", "Ниже сноркла", "Log a dive to at least 12m.", "Погружение глубиной ≥ 12м.", "arrow.down.to.line", AchievementRule(AchievementRuleKind.MIN_MAX_DEPTH, valueDouble = 12.0)),
        AchievementDefinition("ach.depth.18", "Open Water Depth", "OW глубина", "Log a dive to at least 18m.", "≥ 18м в логе.", "arrow.down", AchievementRule(AchievementRuleKind.MIN_MAX_DEPTH, valueDouble = 18.0)),
        AchievementDefinition("ach.depth.30", "Deep 30", "Глубина 30", "Log a dive to at least 30m.", "Погружение глубиной ≥ 30м.", "arrow.down.circle", AchievementRule(AchievementRuleKind.MIN_MAX_DEPTH, valueDouble = 30.0)),
        AchievementDefinition("ach.depth.40", "40 Meter Club", "40 метров", "Log a dive to at least 40m.", "≥ 40м.", "arrow.down.circle.fill", AchievementRule(AchievementRuleKind.MIN_MAX_DEPTH, valueDouble = 40.0)),
        AchievementDefinition("ach.depth.50", "Fifty Meters", "Пятьдесят метров", "Log a dive to at least 50m.", "≥ 50м (тех/OC).", "diamond.fill", AchievementRule(AchievementRuleKind.MIN_MAX_DEPTH, valueDouble = 50.0)),
        AchievementDefinition("ach.depth.60", "Sixty Below", "60 метров", "Log a dive to at least 60m.", "≥ 60м.", "triangle.fill", AchievementRule(AchievementRuleKind.MIN_MAX_DEPTH, valueDouble = 60.0)),
        // Time
        AchievementDefinition("ach.btm.45", "Long Run", "Долгое погружение", "One dive with bottom time ≥ 45 minutes.", "Время погружения ≥ 45 мин (один лог).", "timer", AchievementRule(AchievementRuleKind.MIN_SINGLE_BOTTOM_MIN, valueInt = 45)),
        AchievementDefinition("ach.btm.60", "Hour Power", "Сила часа", "One dive with bottom time ≥ 60 minutes.", "Время погружения ≥ 60 мин (один лог).", "hourglass", AchievementRule(AchievementRuleKind.MIN_SINGLE_BOTTOM_MIN, valueInt = 60)),
        AchievementDefinition("ach.btm.90", "Iron Lungs", "Дыхание железа", "One dive with bottom time ≥ 90 minutes.", "Погружение ≥ 90 мин (один лог).", "lungs.fill", AchievementRule(AchievementRuleKind.MIN_SINGLE_BOTTOM_MIN, valueInt = 90)),
        AchievementDefinition("ach.tbt.500", "500 Bottom Minutes", "500 минут", "Total bottom time of at least 500 min.", "Суммарно ≥ 500 минут на дне.", "clock.badge", AchievementRule(AchievementRuleKind.MIN_TOTAL_BOTTOM_MIN, valueInt = 500)),
        AchievementDefinition("ach.tbt.3000", "3k Minutes", "3000 минут", "Total bottom time of at least 3,000 min.", "Суммарно ≥ 3000 мин на дне.", "clock.arrow.circlepath", AchievementRule(AchievementRuleKind.MIN_TOTAL_BOTTOM_MIN, valueInt = 3000)),
        AchievementDefinition("ach.tbt.6000", "Hundred Hour Bottom", "100 часов на дне", "Total bottom time of at least 6,000 min (100h).", "Суммарно ≥ 6000 мин (100 ч).", "clock.badge.checkmark", AchievementRule(AchievementRuleKind.MIN_TOTAL_BOTTOM_MIN, valueInt = 6000)),
        // Sites & centers
        AchievementDefinition("ach.sites.3", "Site Collector", "Сбор сайтов", "Dive at 3 different sites (by site id).", "3 разных дайв-сайта (по id).", "mappin.and.ellipse", AchievementRule(AchievementRuleKind.MIN_UNIQUE_DIVE_SITES, valueInt = 3)),
        AchievementDefinition("ach.sites.10", "Wanderer", "Путник", "Dive at 10 different sites.", "10 разных дайв-сайтов.", "map", AchievementRule(AchievementRuleKind.MIN_UNIQUE_DIVE_SITES, valueInt = 10)),
        AchievementDefinition("ach.sites.25", "Globe Trotter", "Турист", "Dive at 25 different sites.", "25 уникальных сайтов.", "globe", AchievementRule(AchievementRuleKind.MIN_UNIQUE_DIVE_SITES, valueInt = 25)),
        AchievementDefinition("ach.sites.50", "Fifty Spots", "Пятьдесят точек", "Dive at 50 different sites.", "50 уникальных дайв-сайтов.", "globe.europe.africa.fill", AchievementRule(AchievementRuleKind.MIN_UNIQUE_DIVE_SITES, valueInt = 50)),
        AchievementDefinition("ach.centers.2", "Multi-Center", "Два центра", "Dives with at least 2 different center IDs.", "Погружения из ≥ 2 дайв-центров.", "building.2", AchievementRule(AchievementRuleKind.MIN_UNIQUE_DIVE_CENTERS, valueInt = 2)),
        AchievementDefinition("ach.centers.4", "Center Hopper", "Четыре центра", "Dives with at least 4 different center IDs.", "≥ 4 дайв-центра в логе.", "building.2.crop.circle", AchievementRule(AchievementRuleKind.MIN_UNIQUE_DIVE_CENTERS, valueInt = 4)),
        AchievementDefinition("ach.same_site.5", "Favourite Home Reef", "Риф дома", "Log 5+ dives at the same site.", "5+ погружений в одной точке (тот же id).", "house.and.flag", AchievementRule(AchievementRuleKind.MIN_SAME_DIVE_SITE_VISITS, valueInt = 5)),
        // Media, fish, publish
        AchievementDefinition("ach.photo.1", "Proof of Dive", "Снимок", "Attach a photo to a dive log.", "Прикрепить фото к погружению.", "photo", AchievementRule(AchievementRuleKind.MIN_DIVES_WITH_PHOTO, valueInt = 1)),
        AchievementDefinition("ach.photo.10d", "Album Builder", "Создатель альбома", "10 dives with at least one photo each.", "10 погружений, у каждого ≥ 1 фото.", "photo.on.rectangle", AchievementRule(AchievementRuleKind.MIN_DIVES_WITH_PHOTO, valueInt = 10)),
        AchievementDefinition("ach.photo.5one", "Shutter Bursts", "Серия кадров", "One dive with 5+ photos in the log.", "Одно погружение, у которого ≥5 фото.", "camera.fill", AchievementRule(AchievementRuleKind.MIN_PHOTOS_IN_SINGLE_DIVE, valueInt = 5)),
        AchievementDefinition("ach.video.1", "Moving Memories", "Кино", "Log a dive with at least one video.", "Погружение с видео (список не пустой).", "video.fill", AchievementRule(AchievementRuleKind.MIN_DIVES_WITH_VIDEO, valueInt = 1)),
        AchievementDefinition("ach.published.1", "First Share", "Первый пост", "Publish a dive to the feed.", "Публикация погружения/пост (isPublished).", "square.and.arrow.up", AchievementRule(AchievementRuleKind.MIN_PUBLISHED_DIVES, valueInt = 1)),
        AchievementDefinition("ach.published.10", "Social Butterfly", "Соцсети", "10 published dives or posts (flag).", "10 публикаций в фид (флаг isPublished).", "antenna.radiowaves.left.and.right", AchievementRule(AchievementRuleKind.MIN_PUBLISHED_DIVES, valueInt = 10)),
        AchievementDefinition("ach.fish.1", "Checklist", "Список", "List at least one fish species in a log.", "В логе указан вид рыб.", "fish", AchievementRule(AchievementRuleKind.MIN_DISTINCT_FISH, valueInt = 1)),
        AchievementDefinition("ach.fish.5", "Ichthyology Kickoff", "Погружение в ихтио", "5+ different species across your logs.", "≥5 разных видов по всем логам.", "leaf", AchievementRule(AchievementRuleKind.MIN_DISTINCT_FISH, valueInt = 5)),
        AchievementDefinition("ach.fish.15", "Field Guide", "Определитель", "15+ different fish species in logs.", "15+ уникальных видов.", "book", AchievementRule(AchievementRuleKind.MIN_DISTINCT_FISH, valueInt = 15)),
        AchievementDefinition("ach.fish.3in1", "Biodiversity in One", "3 в одном", "One log lists 3+ species at once.", "В одной записи 3+ вида.", "aqi.medium", AchievementRule(AchievementRuleKind.MIN_FISH_IN_SINGLE_DIVE, valueInt = 3)),
        // Environment
        AchievementDefinition("ach.vis.30", "Glass Water", "Прозрачно", "A dive with visibility at least 30m (if recorded).", "Видимость в логе ≥ 30м (если указана).", "eye", AchievementRule(AchievementRuleKind.ANY_VISIBILITY_AT_LEAST, valueDouble = 30.0)),
        AchievementDefinition("ach.cold.12", "Chilly Dip", "Прохладно", "A dive in water 12°C or colder (if temp logged).", "Темп. воды в логе ≤ 12°C (если указана).", "snowflake", AchievementRule(AchievementRuleKind.ANY_WATER_TEMP_AT_MOST, valueDouble = 12.0)),
        AchievementDefinition("ach.cold.6", "Icebox", "Морозилка", "Water 6°C or below (if logged).", "Темп. ≤ 6°C (если указана).", "thermometer.snowflake", AchievementRule(AchievementRuleKind.ANY_WATER_TEMP_AT_MOST, valueDouble = 6.0)),
        AchievementDefinition("ach.warm.28", "Tropical Vibes", "Тёплое", "A dive in water 28°C or warmer (if logged).", "Вода в логе ≥ 28°C (если указана).", "sun.max.fill", AchievementRule(AchievementRuleKind.ANY_WATER_TEMP_AT_LEAST, valueDouble = 28.0)),
        AchievementDefinition("ach.winter", "Winter Line", "Зимняя", "A log dated in Dec, Jan, or Feb.", "Погружение (дата) в дек, янв, фев.", "thermometer.low", AchievementRule(AchievementRuleKind.WINTER_HEMISPHERE_DIVE)),
        // Profile
        AchievementDefinition("ach.prof.username", "Handle", "Ник", "Set a public username in your profile.", "Укажите уникальный @ник в профиле.", "at", AchievementRule(AchievementRuleKind.PROFILE_USERNAME_SET)),
        AchievementDefinition("ach.prof.onboarding", "Onboarded", "Профиль заполнен", "Complete the diver profile onboarding once.", "Пройдите настройку профиля (onboarding).", "checkmark.seal", AchievementRule(AchievementRuleKind.PROFILE_ONBOARDING_COMPLETE)),
        AchievementDefinition("ach.prof.bio", "Storyteller", "Рассказчик", "Write a 40+ character bio in account settings.", "Био в аккаунте 40+ символов (про себя).", "text.alignleft", AchievementRule(AchievementRuleKind.PROFILE_BIO_MIN_CHARS, valueInt = 40)),
        // Intensity & technique
        AchievementDefinition("ach.month.4", "Productive Month", "Плодотворный месяц", "4+ logs in a single calendar month.", "4+ погружения за один календарный месяц.", "calendar", AchievementRule(AchievementRuleKind.MAX_DIVES_IN_SINGLE_MONTH, valueInt = 4)),
        AchievementDefinition("ach.month.8", "Dive-Heavy Month", "8 в месяц", "8+ logs in a single month.", "8+ погружений в одном календарном месяце.", "calendar.badge.clock", AchievementRule(AchievementRuleKind.MAX_DIVES_IN_SINGLE_MONTH, valueInt = 8)),
        AchievementDefinition("ach.year.12", "Year in Review", "8 за год", "How to: log at least 8 dives in one calendar year (any year counts).", "Как получить: не меньше 8 погружений за один календарный год (год не важен).", "calendar.badge.exclamationmark", AchievementRule(AchievementRuleKind.YEAR_WITH_MIN_DIVE_COUNT, valueInt = 8)),
        AchievementDefinition("ach.deep5x30", "Recreational Max", "5× 30", "5 dives logged with max depth at least 30m.", "5 погружений, у каждого maxDepth ≥ 30м в логе.", "arrow.triangle.2.circlepath", AchievementRule(AchievementRuleKind.DEEP_DIVE_COUNT, valueDouble = 30.0, valueInt = 5)),
        AchievementDefinition("ach.avgdepth", "Consistently Down", "Средняя вниз", "10+ logs with average max depth of all dives at least 20m.", "≥10 погр., средн. max глубина по логу ≥ 20м.", "water.waves", AchievementRule(AchievementRuleKind.AVERAGE_MAX_DEPTH_WITH_MIN_DIVES, valueDouble = 20.0, valueInt = 10)),
        AchievementDefinition("ach.night.3", "Night Trilogy", "Ночная тройка", "3 dives logged as “night” (time or “night” in notes).", "3 “ночных” погруж. (время/ночь в заметках).", "moon.stars.fill", AchievementRule(AchievementRuleKind.NIGHT_DIVE_HEURISTIC_COUNT, valueInt = 3)),
        AchievementDefinition("ach.wreck", "Wreck Hunter", "Охотник", "Notes/conditions/title mention a wreck (keyword).", "В логе есть “wreck/врейк” в примечаниях.", "sailboat", AchievementRule(AchievementRuleKind.NOTES_CONTAINS_WRECK)),
        AchievementDefinition("ach.current.5", "Drift Lover", "Течение", "5+ dives with a non-empty current field.", "5+ погр., в поле течения есть текст (current).", "wind", AchievementRule(AchievementRuleKind.MIN_DIVES_WITH_CURRENT, valueInt = 5)),
        AchievementDefinition("ach.buddy.10", "Buddy Pro", "С напарником", "10+ dives with buddy name filled.", "10+ погр. с непустым «напарником» (buddy).", "person.2.fill", AchievementRule(AchievementRuleKind.MIN_DIVES_WITH_BUDDY, valueInt = 10)),
        AchievementDefinition("ach.short.15", "Sprint Dive", "Спринт", "A very short log (≤ 15 min bottom).", "Погр. в логе с временем погр. ≤ 15 мин (однократно).", "hare.fill", AchievementRule(AchievementRuleKind.SHORT_DIVE_MAX_MIN, valueInt = 15)),
        AchievementDefinition("ach.notes.500", "Essay in the Log", "Эссе", "One log with notes 500+ characters long.", "В одной записи поле “заметки” 500+ симв.", "doc.richtext", AchievementRule(AchievementRuleKind.NOTES_MAX_LEN_IN_ONE_DIVE, valueInt = 500)),
        AchievementDefinition("ach.notes.any", "Journaling", "Журнал", "You noted something for at least one dive (notes non-empty).", "Краткие заметки: хотя бы погр. с непуст. notes.", "note.text", AchievementRule(AchievementRuleKind.HAS_ANY_DIVE_WITH_NOTES)),
        // Social
        AchievementDefinition("ach.friend.1", "Fins Together", "С друзьями", "Add at least 1 app friend (friends list).", "≥ 1 друга в подводном приложении.", "hand.wave", AchievementRule(AchievementRuleKind.MIN_FRIENDS, valueInt = 1)),
        AchievementDefinition("ach.friend.5", "Pod", "Стая", "5+ app friends (friends list).", "≥5 друзей (список друзей).", "person.3.fill", AchievementRule(AchievementRuleKind.MIN_FRIENDS, valueInt = 5)),
        // Style
        AchievementDefinition("ach.shallow.5", "Mermaid", "Русалка", "10+ logs all with max depth ≤ 12m (snorkel-ish).", "10+ погр., max глуб. у каждого ≤ 12м.", "figure.pool.swim", AchievementRule(AchievementRuleKind.ALL_DIVES_SHALLOW_MAX_DEPTH, valueDouble = 12.0)),
    )
}
