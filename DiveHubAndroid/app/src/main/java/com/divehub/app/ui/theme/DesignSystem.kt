package com.divehub.app.ui.theme

/**
 * Единая логика токенов DiveHub Android в паритете с iOS (см. `docs/IOS_PARITY_AUDIT_MATRIX.md`).
 *
 * **Фон страницы / canvas:** [iosChromePageBackground] или [iosGroupedFormPageBackground] для экранов с карточками на «grouped» фоне.
 *
 * **Поверхности карточек / строк списка:** [iosGroupedCardColor] или [MaterialTheme.colorScheme.surface] в контексте M3-списков.
 *
 * **Top app bar:** [diveHubTopAppBarColors] — фон как у страницы, без белой полосы.
 *
 * **Акценты ссылок / фокус полей:** [iosAccentLinkColor]; вторичный текст — [iosSecondaryMutedTextColor].
 *
 * **Исследовать / сегменты:** [exploreChromeColors] + [iosSegmentedButtonColors] для чипов/сегментов.
 *
 * **Material vs IosDesign:** предпочитать [MaterialTheme.colorScheme] для типографики и семантики (error, primary);
 * фиксированные iOS-цвета — через [IosDesign] только где важен визуальный матч с iOS (Explore, Chat bubble).
 */
object DesignSystem
