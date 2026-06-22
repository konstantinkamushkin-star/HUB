# Android ↔ iOS parity matrix

Эталон UI/UX: **iOS** (`DiveHub/`). Клиент **Android** (`DiveHubAndroid/`). Статусы:

| Статус | Значение |
|--------|-----------|
| OK | Соответствует по layout, строкам, основным состояниям и действиям |
| Partial | Частично (есть явные расхождения или не весь стек экранов) |
| Gap | Не реализовано или сильный разрыв с iOS |

## Навигация верхнего уровня

| Роль | iOS | Android | Статус |
|------|-----|---------|--------|
| Shell routing | `MainTabView.swift` → Shop / Admin / Instructor / Diver | `AppHome.kt` → `PartnerAppShell` / `DiverAppShell` | OK |
| Diver tabs | `DiverTabView` + `DiveHubCarouselTabBar` | `DiverAppShell.kt` (`DiverIosScrollTabBar`) | OK |
| Inner stack / deep links | SwiftUI navigation, notifications | `MainShell.kt`, `InnerRoutes.kt`, `DiveHubApp.handleDeepLink` | OK |
| Partner / admin / shop / instructor | `AdminTabView`, `InstructorTabView`, `ShopTabView` | `PartnerAppShell.kt` (конфиг по роли/раскладке) | OK |

## Diver — вкладки

| Экран | iOS | Android | Статус |
|-------|-----|---------|--------|
| Explore | `ExploreView.swift`, `GenericExploreViewModel` | `ExploreScreen.kt`, `ExploreViewModel` | OK |
| Feed | `FeedView.swift` | `FeedScreen.kt` | OK |
| Logbook | `LogbookTabView.swift` (search, sort, empty CTA) | `LogbookScreen.kt`, `LogbookViewModel` | OK |
| Social | `SocialTabView.swift` | `SocialScreen.kt` | OK |
| Messages hub | `ChatHubView` (Messages / Notifications) | `ChatRoute` + сегмент + `NotificationsTabEmbed` | OK |
| Dive editor (flag) | `DiveEditorTabView`, `DiveEditorEditorView` | `DiveEditorScreen.kt` | OK |
| Profile | `ProfileTabView.swift` | `ProfileScreen.kt` | OK |
| Settings / prefs | `SettingsViews.swift`, ссылки из профиля | `UserPreferenceScreens.kt`, `SettingsRoute`, др. | OK |

## Auth и pre-main

| Экран | iOS | Android | Статус |
|-------|-----|---------|--------|
| Splash / onboarding | `SplashView`, onboarding flows | `RootNav.kt`, onboarding routes | Partial |
| Login / Register / Forgot | Views under `Views/Auth/` | `LoginScreen`, `RegisterScreen`, `ForgotPasswordScreen` | OK |
| Profile onboarding | `ProfileOnboardingView` | `ProfileOnboardingScreen` | Partial |

Тексты и валидация должны совпадать по смыслу с iOS и с ответами API (`backend` auth). Строки Android — те же ключи, что в эталонном `values/strings.xml`; полнота переводов в `values-xx` — скрипт `verify-locale-string-keys.py`.

## Partner / instructor / shop / admin (Android `PartnerAppShell`)

| Shell | iOS | Android | Статус |
|-------|-----|---------|--------|
| Center admin configurable bar | `AdminTabView.swift`, layout keys | `PartnerAppShell.kt`, `AdminDashboardLayout` | OK |
| Instructor | `InstructorTabView.swift` | `PartnerAppShell` + `InstructorScheduleTab`, `InstructorPhotoTab` | OK |
| Shop | `ShopTabView.swift` | `ShopSellTab`, `PartnerAnalyticsTab`, … | OK |
| Super admin | `AdminTabView` web panel | `AdminWebPanelRoute` | Partial |

Табы и порядок зависят от роли и серверного/локального layout; сверять с iOS при смене `AdminTabDefaultOrder` / ключей раскладки.

## Deep links / InnerRoutes

| Назначение | iOS (концептуально) | Android |
|------------|---------------------|---------|
| Схема `divehub://` | Обработчики в Swift | `DiveHubApp.handleDeepLink` + `emitDiverTab` / `requestInnerNavRoute` |

Полный список маршрутов: [`InnerRoutes.kt`](../DiveHubAndroid/app/src/main/java/com/divehub/app/ui/navigation/InnerRoutes.kt).

## Закрытие строк матрицы (процесс)

1. Для строки со статусом **Gap** или **Partial**: открыть эталонный файл iOS, соответствующий `*Route`/`*Screen` на Android.
2. Пройти [DoD](./PARITY_VERIFICATION.md).
3. Обновить статус в этой таблице в PR.

## Связанные документы

- Критерии проверки: [PARITY_VERIFICATION.md](./PARITY_VERIFICATION.md)
- Проверка ключей: `DiveHubAndroid/scripts/verify-locale-string-keys.py` (exit 0 = все `values-*` содержат все ключи из `values/`)
- Досинхронизация недостающих ключей (копия текста из base): `DiveHubAndroid/scripts/sync_missing_locale_strings.py`
