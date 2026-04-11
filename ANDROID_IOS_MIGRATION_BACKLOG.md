# Полный перенос DiveHub: iOS → Android — бэклог и статусы

Документ составлен по дереву `DiveHub/` (SwiftUI) и `DiveHubAndroid/` (Compose). Используйте как единый чеклист; статусы обновляйте по мере работы.

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
| `MainTabView` — выбор Shop / Admin / Instructor / Diver | `AppHome` + `AppShellKind` | 🟡 | Логика ролей упрощена; shop: iOS ещё и по `diveCenterId` |
| `DiverTabView` (7 табов) | `DiverAppShell` (6 на панели + More) | 🟡 | Панель: Explore, Feed, Logbook, Social, **Chat**, More; Dive Editor/Profile из More. Deep link `divehub://` → смена таба (`DiveHubApp.diverTabEvents`) |
| `AdminTabView` | `PartnerAppShell` (4 таба: Home, Trips, Alerts, More) | 🟡 | Home: `AdminHomeTab` + `GET admin/centers/managed`; полноценные экраны админки — веб |
| `ShopTabView` | `PartnerAppShell` | 🟡 | Home: `ShopHomeTab`; магазинные экраны — далее / веб |
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
| `OAuthService` / Google | — | ⬜ 🔌 |

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
| `UserProfileView` (чужой профиль) | `UserProfileRoute` (`GET users/{id}`) | 🟡 | Базовый экран; расширять по паритету с iOS |
| `DiveCenterAdminView` | — | ⬜ | |
| `SubscriptionView` | `FeaturePlaceholderRoute` | 🟡 | Заглушка до API |
| `CertificationsView` | `FeaturePlaceholderRoute` | 🟡 | Заглушка до API |
| `GearProfilesView` | `FeaturePlaceholderRoute` | 🟡 | Заглушка до API |
| `StatisticsView` | `StatisticsScreen` (из логбука) | 🟡 | iOS частично без API; Android считает из `dive-logs` |
| `AchievementsView` | `AchievementsScreen` | 🟡 | Логика своя; не общий API с iOS |
| `NotificationsView` | `NotificationsScreen` + `GET notifications` | 🟡 | Тап по карточке: `actionURL` → `divehub://` (табы) или http(s) в браузере |
| `SettingsViews` (язык, пуш-настройки, приватность, единицы) | `SettingsRoute` | 🟡 | Язык: `AppCompatDelegate` + `TokenStore`; приватность/пуш/единицы — заглушки (`FeaturePlaceholderRoute`) |
| `HelpSupportView` | `HelpRoute` + переход на `PartnerRegistrationRoute` (корневой навигатор) | 🟡 | |
| `DeveloperBackendSettingsView` / `APITestView` (DEBUG) | Debug URL в профиле | 🟡 | |
| Переключатель Dive Editor (`FeatureFlags`) | `TokenStore` dive editor | 🟡 | |

---

## 5. Карта (отдельный модуль iOS)

| iOS | Android | Статус |
|-----|---------|--------|
| `MapTabView` / `MapKitView` / `GoogleMapView` / `OpenStreetMapView` / `FilterView` | Карта внутри Explore (OSM) | 🟡 | Нет отдельного таба «Карта» как файл iOS |

---

## 6. Поиск, детали, бронирование

| iOS | Android | Статус | Примечания |
|-----|---------|--------|------------|
| `SearchView` + `SearchViewModel` | `GlobalSearchScreen` + `GlobalSearchViewModel` | 🟡 | Пользователи + фильтр по dive sites (локально по списку); без отдельного API поиска мест |
| `DiveSiteDetailView` | Sheet/деталь в Explore | 🟡 | |
| `DiveCenterDetailView` / `DiveCenterPublicView` | Sheet в Explore + переход в Trips | 🟡 | Отдельный экран центра как в iOS — позже |
| `InstructorDetailView` | — | ⬜ | |
| `BookingWizardView` / `BookingConfirmationView` | Диалог подтверждения на экране поездки | 🟡 | Wizard по шагам — позже |
| `CourseBookingView` | — | ⬜ 🔌 | |
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
| `DashboardView` | `AdminHomeTab` (центры + CTA поездки/создание) | 🟡 |
| `CoursesManagementView` | — | ⬜ 🔌 |
| `TripsManagementView` | `CreateTripRoute` / `trip_edit`, инструкторы (`CenterInstructorsRoute`), поездки центра + удаление без броней (`DELETE /trips/:id`) | 🟡 |
| `BookingManagementView` / `BookingCalendarView` / `CalendarView` | — | ⬜ 🔌 |
| `AnalyticsView` | — | ⬜ 🔌 |
| `GearManagementView` | — | ⬜ |
| `InstructorManagementView` / `ManageInstructorsView` | `CenterInstructorsRoute` + `GET admin/centers/:id/instructors` (список, переход в профиль) | 🟡 |
| `ManageAffiliatedSitesView` | — | ⬜ |
| `ShopsManagementView` (в админ-контексте) | — | ⬜ |
| `PhotoProcessingView` | — | ⬜ |
| `SuperAdminControlCenterView` + секции | — | ⬜ |

---

## 9. Магазин (Shop)

| iOS | Android | Статус |
|-----|---------|--------|
| `ShopTabView` + dashboard / products / orders / analytics | `ShopHomeTab` + `GET v1/shops/:id` по `user.shopId` | 🟡 🔌 |
| `ShopService` | `ShopsApi` + `ShopRepository` (профиль магазина) | 🟡 |

---

## 10. Инструктор

| iOS | Android | Статус |
|-----|---------|--------|
| `InstructorDashboardView` | `InstructorHomeTab` в `PartnerAppShell` (MVP) | 🟡 |
| `ScheduleView` | Вкладка «Расписание» = тот же список поездок (`TripsListTabContent`); отдельный API/календарь — далее | 🟡 🔌 |
| `PhotoProcessingView` (таб) | — | ⬜ |
| `InstructorModeToggle` (профиль) | Настройки: «Домашний экран дайвера» + `prefer_diver_shell` в DataStore, `resolveShellKind` | 🟡 |

---

## 11. Инвентарь

| iOS | Android | Статус |
|-----|---------|--------|
| `InventoryTabView` + dashboard, list, item, checkout, reports, maintenance, inspection | — | ⬜ 🔌 |

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

1. ~~Таббар + дашборд + расписание~~ — MVP: `PartnerAppShell` для инструктора с вкладкой «Расписание» (список поездок) и отдельной домашней `InstructorHomeTab`; полноценный календарь/дашборд метрик — далее.  
2. ~~Переключатель instructor/diver mode~~ — `TokenStore` + переключатель в настройках, `UserDto.resolveShellKind(preferDiverShell)`.  
3. Фото-пайплайн при необходимости.

### Фаза D — Admin (дайв-центр)

1. ~~Таббар как `AdminTabView`~~ — первый шаг: общий нижний таббар для **Admin / Shop / Instructor** (`PartnerAppShell`: Home, Trips, Alerts, More).  
2. MVP домашней админки: `AdminHomeTab` — список центров (`GET admin/centers/managed`), ссылки **Инструкторы** → `CenterInstructorsRoute`, **Поездки** → `CenterManagedTripsRoute` (`GET trips?organizerId=`); общая вкладка Trips и создание поездки; `ShopHomeTab` для магазина. Далее: брони/календарь → редактирование поездок → курсы → аналитика → gear → сайты.

### Фаза E — Shop

1. ~~Таббар как `ShopTabView`~~ — общий `PartnerAppShell`; домашняя вкладка магазина расширена.  
2. MVP `v1/shops/:id` на Android (`ShopsApi`, карточка в `ShopHomeTab`). Заказы / каталог / аналитика — 🔌 далее.

### Фаза F — Inventory

1. Отдельный модуль или встроить в Shop/Admin — 🔌 полный набор API.

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
| `Map/` | `ui/map/` или внутри explore |
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
- [ ] Admin / Shop / Inventory / Instructor закрыты по фазам C–F.

---

*Обновляйте статусы (✅/🟡/⬜) по мере мерджей. Исходная точка: анализ репозитория DivePROD.*
