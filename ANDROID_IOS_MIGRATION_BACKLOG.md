# Полный перенос DiveHub: iOS → Android — бэклог и статусы

Документ составлен по дереву `DiveHub/` (SwiftUI) и `DiveHubAndroid/` (Compose). Используйте как единый чеклист; статусы обновляйте по мере работы.

**Сверка с репозиторием (2026-04-17):** таблицы ниже приведены в соответствие с фактическими пакетами `DiveHubAndroid/` (инвентарь, админ-брони/календарь/gear/shops/affiliated sites, веб-панель super-admin, отдельный таб **Карта**, мастер бронирования и т.д.). Статус ⬜ оставлен только там, где на Android нет отдельного экрана/флоу или нет реального OAuth.

## Условные обозначения

| Статус | Значение |
|--------|----------|
| ✅ | Есть рабочий аналог на Android (может отличаться UI) |
| 🟡 | Частично (MVP, другой UX, нет части полей/API) |
| ⬜ | Нет / только заглушка |
| 🔌 | Зависит от API бэкенда (проверить `backend/`) |

---

## 1. Оболочка приложения и роли

| iOS | Android | Статус | Примечания |
|-----|---------|--------|------------|
| `MainTabView` — выбор Shop / Admin / Instructor / Diver | `AppHome` + `AppShellKind` | 🟡 | `resolveShellKind`: SHOP по `shopId` или роли `SHOP_ADMIN`; ADMIN по `DIVE_CENTER_ADMIN` / `SUPER_ADMIN`; оставшиеся UX-отличия — см. `ANDROID_IOS_PARITY.md` |
| `DiverTabView` (7 табов) | `DiverAppShell` — горизонтальный таббар: **Explore, Map, Feed, Logbook, Social, Chat**, опционально **Dive Editor**, **Profile** (`DiverIosScrollTabBar`) | 🟡 | Deep link `divehub://` → `innerNavDeepLinkRequests` / смена таба; паритет порядка вкладок с iOS — по `ANDROID_IOS_PARITY.md` |
| `AdminTabView` | `PartnerAppShell` (Home, Trips, Alerts, More) + вложенные маршруты `MainShell` (`Admin*`, инвентарь, курсы магазина и т.д.) | 🟡 | `SUPER_ADMIN`: 2 вкладки **Web panel** + **Profile** (`AdminWebPanelScreen`); остальное — см. §8 |
| `ShopTabView` | `PartnerAppShell` | 🟡 | `ShopHomeTab` + `ShopSellTab`; каталог/заказы: `GET/POST /api/v1/shops/:shopId/products|orders` + кэш в `TokenStore` |
| `InstructorTabView` | `PartnerAppShell` | 🟡 | Для роли INSTRUCTOR: домашняя `InstructorHomeTab`, вторая вкладка «Расписание»; переключение на diver shell в настройках |
| `PushNotificationBootstrap` | FCM + `DiveHubFirebaseMessagingService` + `POST users/me/push-token` | 🟡 | Замените `app/google-services.json` на файл из Firebase Console; плейсхолдер даёт сборку, реальные пуши — после привязки проекта |

---

## 2. Авторизация и онбординг

| iOS | Android | Статус |
|-----|---------|--------|
| `SplashView` | `SplashScreen` | ✅ |
| `OnboardingView` | `OnboardingScreen` | ✅ |
| `LoginView` | `LoginScreen` | 🟡 |
| `ForgotPasswordView` | `ForgotPasswordScreen` | ✅ |
| `ForcePasswordChangeView` | `ChangePasswordRoute` | 🟡 |
| `DiveCenterRegistrationView` (партнёр) | `PartnerRegistrationRoute` + `POST v1/partner-registrations`; вход, Help, «Ещё» (дайвер), More (партнёр) | 🟡 |
| `OAuthService` / Google | `POST auth/google` + Credential Manager + `AuthRepository.loginWithGoogle`; Web client ID в `google_oauth_web_client_id` (Firebase). | 🟡 🔌 |
| `OAuthService` / Apple | `POST auth/apple` + `AuthRepository` (готово к токену); **в UI кнопки Apple нет** — нет фиктивного входа | ✅ 🔌 |

---

## 3. Дайвер: основные табы

| iOS | Android | Статус | Примечания |
|-----|---------|--------|------------|
| `ExploreTabView` / `ExploreView` | `ExploreScreen` + OSM | 🟡 | Кнопка глобального поиска в шапке → `GlobalSearchRoute`; детали/бронь см. §6 |
| `FeedView` / `CreatePostView` | `FeedScreen` | 🟡 |
| `LogbookTabView` / `AddDiveLogView` / `DiveLogDetailView` / `FishSpeciesPickerView` | `LogbookScreen` | 🟡 | Пикер видов рыб и детальный экран как в iOS — сверить |
| `SocialTabView` | `SocialScreen` | 🟡 | Тап по другу / результату поиска → `UserProfileRoute` |
| `ChatListView` / `ChatDetailView` / `NewChatWithFriendView` / `BusinessChatLaunchView` | `ChatScreen` | 🟡 | Бизнес-чаты и новый чат — сверить паритет |
| `DiveEditorTabView` + редакторы | `DiveEditorScreen` | 🟡 |
| `ProfileTabView` + секции | `ProfileScreen` + inner routes | 🟡 | Много подпунктов профиля — см. §4 |

---

## 4. Профиль, настройки, вспомогательные экраны

| iOS | Android | Статус | Примечания |
|-----|---------|--------|------------|
| `EditProfileView` | `EditProfileScreen` + `PATCH auth/me` | ✅ 🔌 | |
| `MyBookingsView` | `MyBookingsRoute` + `MyBookingDetailBottomSheet` (`GET /api/bookings`, фильтр, детали как `BookingConfirmationView`, календарь, **шаринг полного текста сводки**) | 🟡 | Мелкие UX-отличия от iOS при желании |
| `UserProfileView` (чужой профиль) | `UserProfileRoute` (`GET users/{id}`) | 🟡 | Базовый экран; расширять по паритету с iOS |
| `DiveCenterAdminView` | `DiveCenterAdminProfileScreen.kt` + `InnerRoutes.DiveCenterAdminProfile` | 🟡 | Загрузка центра, быстрые действия, счётчики; полный контракт данных — 🔌 |
| `SubscriptionView` | `SubscriptionRoute` — статус из **`GET auth/me`** (синхронизация), без локальной «демо-активации» PRO | ✅ 🔌 | Оплата в приложении — только когда появится публичный billing API |
| `CertificationsView` | `CertificationsRoute` / `CertificationsScreen.kt` | 🟡 | Экран + репозиторий; сверить поля с iOS |
| `GearProfilesView` | `GearProfilesRoute` / `GearProfilesScreen.kt` | 🟡 | Экран + локаль/ API; сверить с iOS |
| `StatisticsView` | `StatisticsScreen` (из логбука) | 🟡 | iOS частично без API; Android считает из `dive-logs` |
| `AchievementsView` | `AchievementsScreen` | 🟡 | Логика своя; не общий API с iOS |
| `NotificationsView` | `NotificationsScreen` + `GET notifications` | 🟡 | Тап по карточке: `actionURL` → `divehub://` (табы) или http(s) в браузере |
| `SettingsViews` (язык, пуш, приватность, единицы) | `SettingsRoute` + `UserPreferenceScreens.kt` (`PrivacySettingsRoute`, `NotificationSettingsRoute`, единицы измерения и др.) | 🟡 | Тема светлая/тёмная/системная (`TokenStore.app_theme` + `DiveHubTheme`); масштаб интерфейса iOS — пока нет; **`FeaturePlaceholderRoute` удалён** |
| `HelpSupportView` | `HelpRoute` + переход на `PartnerRegistrationRoute` (корневой навигатор) | 🟡 | |
| `DeveloperBackendSettingsView` / `APITestView` (DEBUG) | Debug URL в профиле | 🟡 | |
| Переключатель Dive Editor (`FeatureFlags`) | `TokenStore` dive editor | 🟡 | |

---

## 5. Карта (отдельный модуль iOS)

| iOS | Android | Статус |
|-----|---------|--------|
| `MapTabView` / `MapKitView` / `OpenStreetMapView` / `FilterView` | Отдельный таб **`MapTabRoute`** (OSM `ExploreMapOsm`) + фильтры + sheet деталей; `MapFullscreenRoute` из Explore | 🟡 | Сверка визуала/жестов с iOS `MapTabView` |

---

## 6. Поиск, детали, бронирование

| iOS | Android | Статус | Примечания |
|-----|---------|--------|------------|
| `SearchView` + `SearchViewModel` | `GlobalSearchScreen` + `GlobalSearchViewModel` | 🟡 | Пользователи + фильтр по dive sites (локально по списку); без отдельного API поиска мест |
| `DiveSiteDetailView` | Sheet/деталь в Explore | 🟡 | |
| `DiveCenterDetailView` / `DiveCenterPublicView` | Sheet в Explore + переход в Trips | 🟡 | Отдельный экран центра как в iOS — позже |
| `InstructorDetailView` | `InstructorPublicRoute` (`GET users/{id}` + `GET reviews` + бронь в wizard) + `InnerRoutes.instructorPublic`; из центра передаётся `centerId` для контекста брони; `divehub://instructor/{id}` | 🟡 🔌 |
| `BookingWizardView` / `BookingConfirmationView` | `BookingWizardScreen.kt` + `InnerRoutes.BookingWizard` + summary после `BookingRepository.create` | 🟡 | Оплата Stripe / полный паритет шагов iOS — 🔌 |
| `CourseBookingView` | Курс подставляется в мастер (`courseId` в пути) + выбор как сервис | 🟡 🔌 | Отдельный экран как на iOS не обязателен при наличии wizard |
| `TripBookingView` | `POST trips/:id/join` + UI на `TripDetailRoute` (участники + имена через `GET users/{id}`) | 🟡 | Без оплаты; один слот на пользователя |

---

## 7. Поездки (дайвер и партнёры)

| iOS | Android | Статус | Примечания |
|-----|---------|--------|------------|
| `TripsListView` | `TripsRoute` | 🟡 | |
| Создание/редактирование (`CreateTripView`) | `CreateTripRoute` + `trip_edit/{id}` + `POST/PATCH /api/trips` | 🟡 | Полный паритет полей iOS (отель/яхта, программа…) — далее |
| Админ: `TripsManagementView` | `CreateTripRoute` + редактирование из деталей + удаление в списке центра + `CenterManagedTripsRoute` | 🟡 | Календарь — далее; удаление только без броней (как защита данных) |

---

## 8. Админ (дайв-центр)

| iOS | Android | Статус |
|-----|---------|--------|
| `DashboardView` | `AdminHomeTab` (центры + KPI + переходы) | 🟡 |
| `CoursesManagementView` | `PartnerCoursesTab.kt` + `PartnerCoursesRepository` (локальные черновики поверх `GET /courses`) | 🟡 🔌 |
| `TripsManagementView` | `CreateTripScreen` / `trip_edit`, `CenterTrips`, удаление без броней и т.д. | 🟡 |
| `BookingManagementView` / `BookingCalendarView` / `CalendarView` | `AdminBookingManagementScreen`, `AdminBookingCalendarScreen` + кэш `AdminBookingsRepository` | 🟡 🔌 |
| `AnalyticsView` | `PartnerAnalyticsTab.kt` (обзор + KPI из кэша броней/инвентаря) | 🟡 🔌 |
| `GearManagementView` | `AdminGearManagementScreen` + `AdminGearRepository` | 🟡 |
| `InstructorManagementView` / `ManageInstructorsView` | `CenterInstructorsScreen` + бэк + локальный оверлей `AdminCenterInstructorsRepository` | 🟡 |
| `ManageAffiliatedSitesView` | `AdminAffiliatedSitesScreen` + `AdminAffiliatedSitesRepository` | 🟡 |
| `ShopsManagementView` (в админ-контексте) | `AdminShopsManagementScreen` + черновики `AdminShopsDraftsRepository` | 🟡 |
| `PhotoProcessingView` | Партнёр: `InstructorPhotoTab.kt` (хаб → Dive Editor / уведомления) | 🟡 |
| `SuperAdminControlCenterView` + секции | `AdminWebPanelScreen` (веб `/dashboard` с токеном в `localStorage`) + профиль | 🟡 |

---

## 9. Магазин (Shop)

| iOS | Android | Статус |
|-----|---------|--------|
| `ShopTabView` + dashboard / products / orders / analytics | `ShopHomeTab` + `ShopSellTab.kt` (`ShopSellRepository`: продукты/заказы локально) + `GET v1/shops/:id` | 🟡 🔌 |
| `ShopService` | `ShopsApi` + `ShopRepository` (профиль магазина) | 🟡 |

---

## 10. Инструктор

| iOS | Android | Статус |
|-----|---------|--------|
| `InstructorDashboardView` | `InstructorHomeTab` в `PartnerAppShell` (MVP) | 🟡 |
| `ScheduleView` | `InstructorScheduleTab.kt` (брони из `AdminBookingsRepository`, календарь/список) | 🟡 🔌 |
| `PhotoProcessingView` (таб) | `InstructorPhotoTab.kt` | 🟡 |
| `InstructorModeToggle` (профиль) | Настройки: «Домашний экран дайвера» + `prefer_diver_shell` в DataStore, `resolveShellKind` | 🟡 |

---

## 11. Инвентарь

| iOS | Android | Статус |
|-----|---------|--------|
| `InventoryTabView` + dashboard, list, item, checkout, reports, maintenance, inspection | `InventoryScreen.kt` + `InnerRoutes.Inventory*`, `InventoryRepository` / `TokenStore` (локально); полный бэкенд-контракт как у iOS — при появлении API 🔌 | 🟡 |

---

## 12. Редакторы медиа (расширенные)

| iOS | Android | Статус |
|-----|---------|--------|
| `ImageEditingView` / `UnderwaterPhotoEditorView` | Частично в Dive Editor | 🟡 |
| `DiveEditorVideoEditorView` и пр. | Сверить с `DiveEditorScreen` | 🟡 |

---

## 13. Сервисы iOS → что учесть на Android

| Сервис iOS | Android / действие |
|------------|-------------------|
| `NetworkService` | Retrofit + `AppGraph` — расширять по мере экранов |
| `AuthenticationService` / Keychain | `TokenStore` + `AuthRepository` |
| `LocalizationService` | Ресурсы + экран выбора языка + возможно backend `language` |
| `ExploreDataService` / `ExploreCacheService` / `GeoCacheService` | Кэш и офлайн — при необходимости |
| `TranslationService` | ⬜ |
| `OCRService` | ⬜ |
| `UnderwaterImageProcessor` | Сверка с UVM/бэкенд процессингом |
| `ShopService` | `ShopsApi` + `ShopRepository` (частично) |
| `SettingsService` | UserDefaults-аналоги — DataStore |

---

## 14. Рекомендуемые фазы работ (перенести «всё»)

Порядок снижает риск: сначала дайвер + API, потом партнёры и тяжёлые модули.

### Фаза A — паритет дайвера (UX + недостающие экраны)

1. ~~Навигация: таб «Чат» на панели, `divehub://` + `MainActivity` intent~~ — сделано (осталось полировать UX).  
2. ~~`SearchView` — общий поиск~~ — сделано (`GlobalSearchRoute`, вход с Explore и из настроек/профиля).  
3. ~~Детали (центр/магазин в Explore)~~: кнопка «Бронь» ведёт в Trips; подпись локации для не-сайтов; ~~deep link~~ — готово. Экран инструктора как сущность — позже.  
4. ~~Бронь поездки (MVP)~~: `POST /api/trips/:id/join` (бэкенд) + подтверждение и кнопка на `TripDetailRoute` (Android); полноценный wizard/оплата — позже.  
5. ~~`UserProfileView` (публичный профиль)~~ — базовая версия есть.  
6. Профиль: ~~заглушки подписка/сертификаты/снаряжение/приватность/единицы~~; язык приложения — сделано; полный 🔌-паритет с iOS — позже.  
7. ~~FCM + `POST users/me/push-token`~~ — клиент: регистрация токена при входе и в `onNewToken`; сервер уже принимает `platform: android`.

### Фаза B — поездки и курсы (дайвер + админ)

1. ~~Бронирование поездок (MVP)~~ — `POST trips/:id/join` + Android; курсы и полный wizard — далее.  
2. ~~`CreateTripView` (создание)~~ — `POST /api/trips` + `GET /api/admin/centers/managed` + экран `CreateTripRoute` (партнёры). Редактирование / полный `TripsManagementView` — далее.

### Фаза C — Instructor

1. ~~Таббар + дашборд + расписание~~ — `InstructorHomeTab`, **`InstructorScheduleTab`** (брони/календарь из кэша). Далее: полный паритет метрик/календаря с iOS.  
2. ~~Переключатель instructor/diver mode~~ — `TokenStore` + переключатель в настройках, `UserDto.resolveShellKind(preferDiverShell)`.  
3. ~~Фото-вкладка~~ — `InstructorPhotoTab` (хаб). Далее: полный паритет с iOS `PhotoProcessingView` (встроенный пайплайн без Dive Editor).

### Фаза D — Admin (дайв-центр)

1. ~~Таббар как `AdminTabView`~~ — `PartnerAppShell`: Home, Trips, Alerts, More.  
2. ~~MVP админки и вложенные экраны~~ — `AdminHomeTab`, **брони/календарь** (`AdminBookingManagementScreen`, `AdminBookingCalendarScreen`), **gear / shops / affiliated sites**, **PartnerCoursesTab**, **PartnerAnalyticsTab**, **инвентарь** (`InventoryScreen`), **веб-панель** super-admin (`AdminWebPanelScreen`). Далее: полный контракт данных с бэкендом (убрать локальные оверлеи там, где появятся API).

### Фаза E — Shop

1. ~~Таббар как `ShopTabView`~~ — общий `PartnerAppShell`; `ShopHomeTab` + **`ShopSellTab`** (локальное управление продуктами/заказами).  
2. ~~MVP `v1/shops/:id`~~ — `ShopsApi` / `ShopPublicRoute`. Далее: серверный каталог/заказы вместо локальных JSON, паритет аналитики с iOS.

### Фаза F — Inventory

1. ~~Клиентский модуль инвентаря на Android~~ — `InventoryScreen` (дашборд, список, обслуживание, отчёты, детали, чек-аут/инспекции) с локальным persistence. Далее: 🔌 выравнивание с сервером, если появится полный API.

### Фаза G — Полировка

1. Локализация как `LocalizationService` (динамический язык приложения).  
2. OAuth Google.  
3. Сверка аналитики/редакторов с бэкендом UVM/image jobs.

---

## 15. Быстрая матрица «файл iOS → куда класть Android»

| Папка iOS `Views/` | Пакет Android (предложение) |
|--------------------|-------------------------------|
| `Auth/` | `ui/auth/` |
| `Splash/` / `Onboarding/` | `ui/splash/`, `ui/onboarding/` |
| `Explore/` / `Search/` | `ui/explore/`, `ui/search/` |
| `Map/` | `ui/map/` (`MapTabRoute`, `MapFullscreenRoute`) + OSM в explore |
| `Feed/` | `ui/feed/` |
| `Logbook/` | `ui/logbook/` |
| `Social/` | `ui/social/` |
| `Chat/` | `ui/chat/` |
| `Trips/` | `ui/trips/` |
| `Booking/` | `ui/booking/` |
| `Profile/` + `Settings` в `Profile/SettingsViews` | `ui/profile/`, `ui/settings/` |
| `Notifications/` | `ui/notifications/` |
| `Statistics/` / `Achievements/` | `ui/statistics/`, `ui/achievements/` |
| `Help/` | `ui/help/` |
| `DiveEditor/` / `ImageEditing/` | `ui/diveeditor/`, `ui/imageediting/` |
| `PhotoProcessing/` | `ui/photoprocessing/` |
| `Admin/` | `ui/admin/` |
| `Shop/` | `ui/shop/` |
| `Instructor/` | `ui/instructor/` |
| `Inventory/` | `ui/inventory/` |
| `Detail/` | `ui/detail/` или по сущности |
| `Testing/` | `ui/debug/` (debug-only) |

---

## 16. Контрольный список «готово перенесено»

Используйте как финальный аудит:

- [ ] Все роли видят свою оболочку как в iOS (diver/admin/shop/instructor).  
- [ ] Все пункты `ProfileTabView` имеют экран или осознанный scope cut.  
- [ ] Поиск + детали + бронь + поездки согласованы с API.  
- [x] Уведомления: список + действия по `actionURL` (Android: тап по карточке).  
- [ ] Push на iOS и Android с одним бэкендом (Android: токен на `users/me/push-token`; доставка FCM на стороне бэкенда — по готовности).  
- [ ] Локализация и OAuth по необходимости продукта.  
- [x] Admin / Shop / Inventory: **основные REST-контракты** подключены (gear/inventory/mobile admin, shop products/orders); Instructor — см. §8–11; UX-детали vs iOS — по желанию.  
- [ ] OAuth Google (Credential Manager; нужен Web client ID в сборке).

---

*Обновляйте статусы (✅/🟡/⬜) по мере мерджей. Исходная точка: анализ репозитория DivePROD.*
