# API contract matrix: iOS `NetworkService` ↔ Nest backend ↔ Android

Цель: единый источник правды для full-stack паритета. Обновляйте строки при изменении контрактов.

Легенда: **OK** — реализовано и используется на Android; **PART** — частично / локальный кэш / другой путь; **GAP** — нет сервера или не подключено на Android.

| Область | iOS (`DiveHub/Services/NetworkService.swift`) | Backend | Android |
|--------|-----------------------------------------------|---------|---------|
| Auth / refresh | `/api/auth/*` | `auth/auth.controller.ts` | `AuthApi`, `AuthRepository` |
| Explore dive sites | `/api/v1/dive-sites/explore`, `/api/dive-sites/:id` | `dive-sites/*` | `ExploreApi`, `ExploreRepository` |
| Dive site contributions | `/api/v1/dive-sites/contributions` | `dive-site-contributions.controller.ts` | `ExploreApi` |
| Dive centers | `/api/dive-centers/:id`, search/map/popular v1 | `dive-centers/*` | `ExploreApi` / `TripsApi` |
| Bookings create/list | `POST/GET /api/bookings` | `bookings/bookings.controller.ts` | `BookingApi`, `BookingRepository` |
| Booking payment (JSON) | `payment` в теле create | `CreateBookingDto.payment` → JSONB | `BookingCreateDto.payment` |
| PaymentIntent (online) | *при необходимости клиентского Stripe* | `POST /api/bookings/payment-intent` | `BookingApi.createPaymentIntent` |
| Admin bookings | `GET/PATCH /api/admin/bookings` | `bookings-admin.controller.ts` | `BookingApi`, `AdminBookingsRepository` |
| Instructor bookings | `GET/POST .../instructor/bookings` | `bookings-instructor.controller.ts` | `BookingApi` |
| Center managed | `GET /api/admin/centers/managed` | `dive-center-admin.controller.ts` | `TripsApi` / repos |
| Affiliated sites | `GET/PATCH .../affiliated-sites` | `dive-center-admin.controller.ts` | `PartnerAdminApi` |
| Center instructors | `GET .../centers/:id/instructors` | `dive-center-admin.controller.ts` | `PartnerAdminApi` |
| **Center gear** | `GET .../centers/:id/gear`, `PATCH .../gear/:id/status` | `dive-centers/admin-mobile.controller.ts` | `PartnerAdminApi`, `AdminGearRepository` |
| Center services | center-services API | `center-services/*` | `CenterServicesApi` |
| Courses | `GET/POST/PATCH/DELETE /courses` | `courses/courses.controller.ts` | `CoursesApi`, `PartnerCoursesRepository` |
| Trips | `/api/trips` | `trips/trips.controller.ts` | `TripsApi` |
| Shops public | `GET /v1/shops/:id` | `shops/shops.controller.ts` | `ShopsApi`, `ShopRepository` |
| **Shop products/orders** | *iOS Shop sell tab* | `v1/shops/:shopId/products`, `.../orders` | `ShopsApi`, `ShopSellRepository` (remote + cache) |
| Feed | `/api/feed/posts` | `feed/*` | `FeedApi` |
| Chat | `/api/chat/*` | `chat/chat.controller.ts` | `ChatApi` |
| Social / friends | `/api/friends/*`, `/api/users/search` | `friends/*`, `users/*` | `SocialApi`, `UsersApi` |
| Reviews | `/api/reviews` | `reviews/*` | `ReviewsApi` |
| Dive logs | `/api/dive-logs` | `dive-logs/*` | `DiveLogsApi` |
| Notifications | `/api/notifications` | `notifications/*` | `NotificationsApi` |
| Push token | `/api/users/me/push-token` | `users/*` | `UsersApi` / FCM service |
| Media upload | `/api/media/upload` | `media/*` | via OkHttp / Coil |
| Localization | `/api/localization/:lang` | `localization/*` | resources + backend language |
| Translate | `/api/translate` | *если включено* | GAP / не в MVP Android |
| **Center inventory** | *iOS модуль Inventory* | `dive-centers/admin-mobile.controller.ts` (`.../inventory/items`, `.../tickets`) | `PartnerAdminApi`, `InventoryRepository` |
| Stripe webhook | *сервер* | `webhooks/stripe-webhook.controller.ts` | N/A |
| Admin error stats | `/api/admin/error-stats` | `admin.controller.ts` / stats | `AdminDashboardApi` |

## Известные GAP (закрываются в рамках full-stack паритета)

1. **Переводчик / OCR** — в продуктовом бэклоге отдельно; не блокирует основной паритет.
2. **Stripe**: требуется `STRIPE_SECRET_KEY` и ключи в клиенте для live; без них `payment-intent` возвращает объясняющую ошибку.

## Как обновлять

- После добавления эндпоинта: строка в таблице + ссылка на DTO в `backend/src/**/dto`.
- Android: новый метод в `*Api.kt` + репозиторий; избегать дублирования путей — использовать единый префикс `/api/`.
