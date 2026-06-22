# Матрица аудита: Android ↔ iOS (DiveHub)

**Цель:** визуальный и UX-паритет с iOS 1.1.0 (SwiftUI). Статусы: **OK** — соответствует; **gap** — есть расхождения; **WIP** — в работе (релиз 1.1.0).

**Контроль:** [PARITY_VERIFICATION.md](../../docs/PARITY_VERIFICATION.md); телефон (портрет), тёмная/светлая тема, RU + EN.

| Модуль Android | Ключевые файлы Compose | Эталон iOS (Swift) | Статус |
|----------------|------------------------|---------------------|--------|
| Тема / токены | `ui/theme/Theme.kt`, `IosDesign.kt`, `DiveHubChrome.kt` | Глобальные цвета + `OpenStreetMapView` | OK |
| Дизайн-гайд | `ui/theme/DesignSystem.kt` | — | OK |
| Shell дайвера | `main/DiverAppShell.kt`, `DiverTabIndices.kt` | `MainTabView.swift`, `DiveHubCarouselTabBar.swift` | OK |
| Shell партнёра/админа | `main/PartnerAppShell.kt`, `PartnerShellTab.kt` | `AdminTabView.swift`, `InstructorTabView.swift`, `ShopTabView.swift` | OK |
| Исследовать | `explore/ExploreScreen.kt`, `ExploreMapOsm.kt` | `ExploreView.swift`, `ExploreMapView.swift` | OK |
| Лента | `feed/FeedScreen.kt` | `Feed/FeedView.swift` | OK |
| Логбук | `logbook/LogbookScreen.kt` | `Logbook/LogbookTabView.swift` | OK |
| Соцсеть | `social/SocialScreen.kt` | `Social/SocialTabView.swift` | OK |
| Чат | `chat/ChatScreen.kt` | `Chat/ChatListView.swift`, `ChatDetailView.swift` | OK |
| Профиль | `profile/ProfileScreen.kt` | `Profile/ProfileTabView.swift` | OK |
| Поиск | `search/GlobalSearchScreen.kt` | `Search/SearchView.swift` | OK |
| Онбординг / Auth | `onboarding/`, `auth/` | `Auth/*` | OK |
| Уведомления | `notifications/NotificationsScreen.kt` | `Notifications/NotificationsView.swift` | OK |
| Бронирование | `booking/BookingWizardScreen.kt` | `Booking/BookingWizardView.swift` | OK |
| Публичный центр | `centers/DiveCenterPublicRoute.kt` | `Detail/DiveCenterPublicView.swift` | OK |
| Магазин публичный | `shops/ShopPublicRoute.kt` | Shop public views | OK |
| Поездки | `trips/TripsScreens.kt` | `Trips/TripsListView.swift` | OK |
| Ачивки | `achievements/AchievementsScreen.kt` | `Achievements/AchievementsView.swift` | OK |
| Редактор погружения | `diveeditor/DiveEditorScreen.kt`, `services/PhotoEnhancementWorker.kt` | `DiveEditor/DiveEditorTabView.swift` | OK |
| Карта (fullscreen) | `explore/MapFullscreenRoute.kt` | Explore / `MapTabView` (не в carousel) | OK |
| Админ веб-панель | `admin/AdminWebPanelScreen.kt` | `Admin/AdminWebPanelView.swift` | OK |
| Остальные admin | `admin/*.kt` | `Admin/*.swift` | OK |
| Локализация API | `data/LocalizationRepository.kt` | `LocalizationService.swift` | OK |
| Interface scale | settings + `MainActivity` | `SettingsService.interfaceScale` | OK |
| Партнёр контент | `partner/*.kt` | `Instructor/`, `Shop/` | OK |

**Исключения (релиз 1.1.0):** Sign in with Apple — не на Android; Apple Pay → `google_pay` / `online` / `on_site`.

**Эталон версии:** iOS `MARKETING_VERSION = 1.2.0` → Android `versionName = 1.2.0`.

| Модуль | Статус v1.2.0 |
|--------|----------------|
| Dive Editor UVM Bech (ai2) | OK — `PhotoEnhancementProcessor.kt` |
| Logbook search/sort/delete/share | OK — `LogbookScreen.kt`, `DiveLogDetailScreen.kt` |
| Feed hashtags | OK — `HashtagFeedScreen.kt`, `FeedHashtagParser.kt` |
| Unified profile edit | OK — `EditProfileScreen.kt` + `UnifiedEditProfileSections.kt` |
| Subscription trial UI | OK — `SubscriptionScreen.kt` |
| Settings 7 languages | OK — `HubScreens.kt` |
