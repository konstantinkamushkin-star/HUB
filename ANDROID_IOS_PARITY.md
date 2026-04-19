# Android ↔ iOS parity checklist

Use this file to track **feature and UI parity** between `DiveHub/` (iOS) and `DiveHubAndroid/`. Update statuses as work lands.

## Legend

| Status | Meaning |
|--------|---------|
| ✅ | Matches iOS behavior closely enough for release |
| 🟡 | Present but incomplete (subset of UX, mock data, or missing edge cases) |
| ⬜ | Not implemented or placeholder only |
| — | Optional / dev-only on iOS; parity not required for prod |

## Phase 1 progress log

| Date | Change |
|------|--------|
| 2026-04-18 | **Infra (documented)**: production **FCM** requires replacing `google-services.json` with the real Firebase project; **Google OAuth** release builds need `google_oauth_web_client_id` (see strings/Gradle); **Stripe** env: `STRIPE_SECRET_KEY` / `STRIPE_PUBLISHABLE_KEY` in `backend/.env.local.example`. |
| 2026-04-18 | **Full-stack parity batch (plan)**: `backend/docs/API_CONTRACT_MATRIX.md` — матрица контрактов; Nest — `POST /api/bookings/payment-intent` (Stripe), `PaymentDto` в create booking, миграция `031` shop commerce + center gear/inventory; Android — `BookingWizard` способы оплаты `online` / `on_site` / `google_pay` + опциональный PaymentIntent; `ShopSellRepository` и `AdminGearRepository` / `InventoryRepository` — **remote-first** с кэшем в `TokenStore`; `ShopSellTab` / gear / inventory синхронизируют с API при наличии `shopId` / `diveCenterId`. |
| 2026-04-18 | **Settings language → backend** + **Explore sort/filter i18n**: `SettingsRoute` — при выборе EN/RU вызывается `AuthRepository.updateProfile(language)` и обновляется сессия; системный язык — только локально. Bottom sheet сортировки и фильтров Explore переведены на строки (раньше был hardcoded EN). |
| 2026-04-18 | **Google OAuth (Android)**: `POST auth/google` — `GoogleAuthRequest`, `AuthRepository.loginWithGoogle`, `AuthViewModel.signInWithGoogle`, `rememberGoogleSignInStarter` + `play-services-auth`; Login/Register wired; consent text `ConsentTexts.googleOAuthConsentText()`; Web client ID via `google_oauth_web_client_id` (empty until Firebase). **My dive site contributions**: `GET v1/dive-sites/contributions/mine`, `MyDiveSiteContributionsRoute`, profile entry for divers. |
| 2026-04-18 | **Apple auth API parity (Android)**: `AppleAuthRequest`, `POST auth/apple`, `AuthRepository.loginWithApple`, `AuthViewModel.signInWithApple`, `ConsentTexts.appleOAuthConsentText()` — готово к подключению UI, когда появится способ получить Apple identity token на Android. Кнопка Apple по-прежнему показывает пояснение. **Версии**: iOS `MARKETING_VERSION` выровнен к **1.0.0** как Android `versionName`; в настройках Android показывается сборка **%1$s (%2$d)**. |
| 2026-04-18 | **Google Sign-In implementation**: replaced deprecated `GoogleSignIn` / `play-services-auth` with **Credential Manager** (`androidx.credentials` + `credentials-play-services-auth` + `googleid` `GetGoogleIdOption` / `GoogleIdTokenCredential`). Cancellation (`GetCredentialCancellationException`) does not show an error snackbar. |
| 2026-04-17 | **Instructor public profile**: `InstructorPublicRoute` (`InstructorPublicScreen.kt`) — аватар, рейтинг по отзывам, bio, сертификаты/языки из `UserDto`/`diverProfile`, отзывы `instructor` + добавление отзыва, кнопки **Book** (wizard с `instructorId`, из центра — с `centerId`). Навигация с карточки инструктора в `DiveCenterPublicRoute`, из глобального поиска для `INSTRUCTOR`, deep link `divehub://instructor/{id}`. |
| 2026-04-17 | **Migration backlog reconciliation**: `ANDROID_IOS_MIGRATION_BACKLOG.md` полностью синхронизирован с текущим деревом `DiveHubAndroid/` (карта, инвентарь, админ-экраны, shop sell, super-admin web panel, бронирование и т.д.); ⬜ сведены к реальным пробелам (OAuth Google, `InstructorDetailView`). |
| 2026-04-17 | **Statistics + splash parity step**: `StatisticsScreen`/`StatisticsViewModel` — **toolbar refresh**, **pull-to-refresh**, **keep prior stats while reloading** with optional **inline refresh error** banner; **empty logbook hint** when totals are zero. `SplashScreen` — **solid primary** background (iOS-style), **launcher logo** + **localized title**, **1.2s ease-in** scale/opacity + **2s** delay before navigation (matches `SplashView` timing intent); session routing unchanged. |
| 2026-04-17 | **Help & support parity step**: `HelpRoute` (`HubScreens.kt`) adds **expandable FAQ** (four items), **contact** actions (mailto / tel intents), **live-chat hint** (in-app Chat), and **web links** (FAQ / terms / privacy URLs) before the existing feature overview sections + partner application button — closer to iOS `HelpSupportView` structure. |
| 2026-04-17 | **Instructor photo tab parity step**: `InstructorPhotoTab.kt` is now a scrollable screen with **typical flow** card (pick media → adjust/process → save/share), primary **Open Dive Editor**, secondary **Notifications** — closer to iOS `PhotoProcessingView` intent without duplicating the full picker UI (that lives in `DiveEditorScreen`). |
| 2026-04-17 | **Partner analytics booking trend step**: `PartnerAnalyticsTab.kt` adds a 7-day **new bookings per day** mini-chart from `AdminBookingsRepository` cache (`createdAt` → UTC day buckets + `LinearProgressIndicator` rows). |
| 2026-04-17 | **Booking confirmation summary step**: After successful `BookingRepository.create`, wizard stores `BookingConfirmationSummary` and success `AlertDialog` shows booking id, center, service, date/time, payment mode, participant count, gear line, and notes (EN/RU strings). |
| 2026-04-17 | **Inventory list reset filters step**: Inventory tab shows **Reset filters** when search/sort/filters deviate from defaults, restoring list parity convenience vs dense iOS filter UX. |
| 2026-04-17 | **Inventory ticket priority labels step**: Maintenance list, item-detail ticket rows, ticket detail header, and Reports “by priority” rows use localized priority labels (`ticketPriorityLabel`); inspection sheet normalizes initial priority key for chip selection. |
| 2026-04-17 | **Inventory add/edit fields step**: Shared item editor sheet now edits **condition** (good / needs service chips) and **notes** (multiline); `addItem` / `updateItem` persist into `InventoryItemLocal`; list row subtitle includes localized condition. |
| 2026-04-17 | **Inventory item detail parity step**: `InventoryItemDetailRoute` shows condition + item notes, richer ticket cards (created date + timeline preview), top-bar **Check in** (issued) + **Delete** with confirmation; `InventoryRepository` gains `checkInItem` / `deleteItemAndRelatedTickets` (shared with `InventoryViewModel.checkIn`). |
| 2026-04-17 | **Inventory checkout sign-off step**: Checkout sheet requires **Handed off by (staff)** with persisted `checkoutHandedOffBy` + `checkoutHandedOffAt`; list/detail show handoff lines + notes line; check-in clears sign-off fields — closer to iOS checkout acknowledgment without bitmap signature. |
| 2026-04-17 | **Inventory dashboard trend step**: Dashboard tab now includes the same 7-day daily trend card (items + tickets, shared scale) as Reports, so trend visibility is not reports-only. |
| 2026-04-17 | **Inventory reports trend step**: Reports tab now includes a visual 7-day trend block (daily items + daily tickets) and exports these trend rows in text/CSV report payloads, reducing the "richer trend timeline" gap. |
| 2026-04-17 | **Inventory inspection checklist step**: `InventoryScreen.kt` Inspection flow now includes structured checklist toggles (visual/pressure/sanitization) + "checked by" signer capture; persisted in `MaintenanceTicketLocal` and rendered in `InventoryTicketDetailRoute` for richer audit context closer to iOS `InspectionView`. |
| 2026-04-15 | **Inventory dashboard mini-charts step**: `InventoryScreen.kt` Dashboard tab now renders visual progress-chart blocks (item status distribution + ticket priority distribution) via `LinearProgressIndicator`, moving beyond plain KPI cards toward iOS-style visual density. |
| 2026-04-15 | **Inventory CSV export step**: `InventoryScreen.kt` Reports tab now supports sharing both human-readable summary and CSV snapshot (`text/csv`) for quick handoff/reporting workflows. |
| 2026-04-15 | **Inventory advanced filters step**: `InventoryScreen.kt` Inventory tab adds advanced controls beyond base search/status/category: condition filter (`good`/`needs_service`), due-date filter (`with/no/overdue`), and sort chips (newest/oldest/name A-Z/Z-A). |
| 2026-04-15 | **Inventory report export step**: Reports tab now supports native text-share export (`ACTION_SEND`) with summary sections (totals, category/status/priority breakdowns), providing a lightweight export workflow while chart/PDF backend exports are still pending. |
| 2026-04-15 | **Ticket audit timeline step**: inventory maintenance tickets now persist event timeline (`opened/started/completed`) with timestamps (`events`, `startedAt`, `completedAt`) in `InventoryLocalDtos`. `InventoryTicketDetailRoute` renders timeline/history and workflow transitions append events. |
| 2026-04-15 | **Maintenance ticket detail route step**: added dedicated route `InnerRoutes.InventoryTicketDetail` + `InventoryTicketDetailRoute` with ticket metadata, linked item section, and workflow actions (start/complete) from detail. Maintenance and item-detail ticket cards now open ticket detail, reducing `MaintenanceTicketsView` gap. |
| 2026-04-15 | **Inventory reports timeline step**: `InventoryScreen.kt` Reports tab now includes recent-activity metrics (items/tickets in last 7 and 30 days) and overdue-issued count based on due date parsing, in addition to status/priority breakdowns. |
| 2026-04-15 | **Inventory reports enrichment step**: `InventoryScreen.kt` Reports tab now includes additional breakdown sections for item statuses, ticket statuses, and ticket priorities (in addition to totals + categories), improving parity with iOS reporting density. |
| 2026-04-15 | **Inventory dashboard alerts step**: `InventoryScreen.kt` Dashboard tab now adds status/ticket breakdown blocks and alert section (high-priority open tickets, issued items without due date), reducing gap vs iOS dashboard warning density beyond basic KPI cards. |
| 2026-04-15 | **Maintenance tickets UX step**: `InventoryScreen.kt` Maintenance tab now has status filter chips (all/open/in-progress/completed), KPI summary for filtered tickets, localized ticket status labels, created-at line, and **Open item** action to jump into `InventoryItemDetailRoute`. |
| 2026-04-15 | **Inventory item detail drill-down step**: added dedicated route `InnerRoutes.InventoryItemDetail` + `InventoryItemDetailRoute` from list cards. Item detail screen now shows inventory fields (category/status/size/location/issued-to/due/created) and related maintenance tickets, reducing gap vs iOS `ItemDetailView`. |
| 2026-04-15 | **Inventory list filtering UX step**: `InventoryScreen.kt` Inventory tab now includes search (name/category/status/size/location/issued-to), horizontal status + category filter chips, filtered KPI line (shown/issued/maintenance), localized status labels, and dedicated empty state for active filters. |
| 2026-04-15 | **Inventory checkout + maintenance workflow step**: `InventoryScreen.kt` list actions now open structured checkout/inspection sheets (issued-to, due text, notes, ticket title/description/priority). Items persist checkout metadata (`issuedToName`, `dueAt`, `checkoutNotes`) and support **check-in**. Maintenance tab now supports status transitions (**Start work**, **Complete**), and completing a ticket restores item status to available. |
| 2026-04-15 | **Inventory edit-form step**: `InventoryScreen.kt` inventory list now supports local **edit item** flow (edit action per row + shared add/edit bottom sheet). Added `InventoryViewModel.updateItem(...)` persistence path and item detail lines (size/location) in list cards, reducing gap vs iOS `AddEditItemView`. |
| 2026-04-15 | **Instructor management assignment step**: `CenterInstructorsScreen.kt` now supports local **assign/unassign** workflows (search users in sheet, assign action, row unassign action) on top of backend instructor list. Added persisted local overlay `AdminCenterInstructorsRepository` (`TokenStore` `admin_center_instructors_json`) so removed/added instructors remain reflected until backend assignment APIs are available. |
| 2026-04-15 | **Super-admin shell + web panel step**: Android admin shell now mirrors iOS super-admin split: in `PartnerAppShell.kt`, `SUPER_ADMIN` gets a 2-tab shell (**Web panel** + **Profile**) instead of dive-center admin tabs. Added embedded `AdminWebPanelScreen.kt` (`InnerRoutes.AdminWebPanel`) that bridges session into `localStorage` (`divehub_admin_token`, `divehub_admin_refresh`, `divehub_admin_user`) and redirects to `/dashboard`, with in-screen refresh and error overlay. |
| 2026-04-15 | **Admin shops + affiliated sites batch**: `AdminShopsManagementScreen.kt` adds KPI line, toolbar **add draft** + `common_refresh`, persisted **local shop drafts** (`AdminShopsDraftsRepository`, `TokenStore` `admin_shop_drafts_json`) merged above catalog rows, bottom sheet create/edit, delete on card, catalog rows still open public shop. `AdminAffiliatedSitesScreen.kt` adds **site search**, KPI (linked vs listed), **Select all listed** / **Clear listed** for the current filter, and toolbar refresh uses `common_refresh`. |
| 2026-04-14 | **Center instructors + gear management batch**: `CenterInstructorsScreen.kt` adds search, KPI line, pull-to-refresh + toolbar refresh, and clearer profile affordance. `AdminGearManagementScreen.kt` adds gear search, KPI line for current filters, and horizontal-scroll status chips; toolbar refresh uses shared `common_refresh`. |
| 2026-04-14 | **Booking calendar + instructor dashboard batch**: `AdminBookingCalendarScreen.kt` gains month navigation + **heatmap grid** (booking count intensity + dominant-status dot per day, tap day to select). `InstructorHomeTab` now shows **local booking KPIs** (same cache as schedule) plus quick actions (schedule tab, photo tab, Dive Editor route, notifications) and manual refresh. |
| 2026-04-14 | **Admin dashboard + bookings management batch**: `AdminHomeTab` now surfaces local KPI widgets (bookings pending/revenue, inventory maintenance) with direct actions to bookings/calendar/inventory. `AdminBookingManagementScreen` now includes search, horizontal status chips, KPI summary line, improved status coloring, extra status transitions (pending/confirm/complete/cancel), and quick jump to center trips from booking details. |
| 2026-04-14 | **Shop + trips parity batch**: `ShopSellTab.kt` now has local management flows for products/orders (search, status filters, create/edit sheets, archive/unarchive/status updates) backed by new `ShopSellRepository` + `TokenStore` JSON (`shop_products_json`, `shop_orders_json`). `TripsListTabContent` now adds search, filter chips (upcoming/full/type), and KPI line (total/open/full) to better match iOS management UX density. |
| 2026-04-14 | **Partner courses management parity step**: `PartnerCoursesTab.kt` upgraded from read-only list to local management flow (search + status filters, create/edit sheet, archive/unarchive actions) with persistence via new `PartnerCoursesRepository` + `TokenStore` JSON (`partner_courses_json`). Remote `GET /courses` remains source-of-truth baseline; local draft/status overlays bridge missing backend CRUD parity. |
| 2026-04-14 | **Bigger parity batch (single iteration)**: `PartnerAnalyticsTab.kt` now includes **booking + inventory KPI cards** from local caches (`AdminBookingsRepository`, `InventoryRepository`) in addition to backend overview/error stats. `DiveCenterAdminProfileScreen.kt` now loads extra admin data (**affiliated sites + trips counts**), surfaces richer center fields (nitrox, services, photos, cert agency, price-from), and adds direct actions to booking calendar/management, inventory, gear, and public profile. |
| 2026-04-12 | Diver shell: **horizontal scroll tab bar** (all roots visible like iOS `DiverTabView`), removed “More” bottom sheet; **tab index fix** when `diveEditorEnabled` toggles (mirror iOS `onChange`); **Explore** gets same bottom inset as other tabs so content clears the bar. |
| 2026-04-12 | **Explore** list cards: **distance** from user to site (m / km) when location permission + last known fix; list bottom padding aligned with tab bar. **Chat**: **New message** sheet — pick a friend (same list as Social), `openOrCreateConversation` (iOS `NewChatWithFriendView`). |
| 2026-04-12 | **Explore** detail sheet: **Show on map** (switch to map + zoom like iOS), **Message center/shop** → `BusinessChatOpenRoute`. **Chat**: **Snackbar** on failed `openOrCreateConversation`; **pending conversation** from business flow via `AppGraph` + `consume` on chat tab. **ChatRepository** unified `openConversation(peerId, peerType)`. |
| 2026-04-12 | **Explore** dive sites API uses **app language** from `TokenStore` (at `ExploreViewModel` create). **Booking wizard**: **confirmation dialog** after submit + `acknowledgeSubmitSuccess`. **Logbook** detail sheet: **current** + **dive type**. **Deep link** `divehub://chat?peerType=…&peerId=…` → `BusinessChatOpenRoute` via `DiveHubApp` + `DiverAppShell`. |
| 2026-04-12 | **Trips**: after **join** succeeds, **open organizer chat + intro message** (iOS `TripBookingView.createChatForBooking`); full multi-participant booking form still depends on backend. |
| 2026-04-12 | **Trips list**: **refresh** in app bar (diver list, center-managed list, partner tab); **`TripsListViewModel.refresh`** keeps existing trips while loading (no blank list on pull). |
| 2026-04-12 | **Trips list**: **Pull-to-refresh** (`PullToRefreshBox`) on diver trips, center-managed trips, and partner tab list when the list is non-empty (indicator while `loading` + existing rows). |
| 2026-04-12 | **Explore**: **Pull-to-refresh** on **list** mode; initial load / empty data still full-screen spinner; **reload no longer blanks** list/map — spinner only when `loading && allSites.isEmpty()`; full-screen error only when `error && allSites.isEmpty()`. |
| 2026-04-12 | **Global search**: **Pull-to-refresh** + app bar **refresh**; full-screen spinner/error only when **no** prior results; repeat search keeps list and shows indicator; inline **error banner** when refresh fails but old rows remain; success clears `error`. |
| 2026-04-12 | **Feed**: **`refresh()`** no longer resets to empty `FeedUiState` — keeps posts while loading; **pull-to-refresh**; first-load / empty error UI with **Retry**; inline error when reload fails with existing posts. |
| 2026-04-12 | **Logbook**: **Pull-to-refresh**; spinner only when `loading && logs.isEmpty()`; **error + Retry** when load failed with no dives; inline error on failed refresh when logs remain. |
| 2026-04-12 | **Notifications** + **Achievements**: **`refresh()`** keeps existing rows while loading; **pull-to-refresh**; app bar **refresh** (notifications + achievements); full-screen error only when empty + not loading; inline error banner when reload fails with data. |
| 2026-04-12 | **Dedicated Map tab**: new diver **`Map`** tab (`ui/map/MapTabRoute.kt`) backed by OSM (`ExploreMapOsm`), with **map filters sheet** (dive type + difficulty), map controls (zoom / center), and dive-site tap → booking; deep link tab indices updated for new tab order. |
| 2026-04-12 | **Admin gear management**: new route **`InnerRoutes.AdminGearManagement`** + screen `ui/admin/AdminGearManagementScreen.kt` (status filter, add item sheet, status update menu, pull-to-refresh, error states), wired from **Admin More** tab. Data currently persisted locally (`TokenStore` JSON via `AdminGearRepository`). |
| 2026-04-12 | **Admin shops management**: new route **`InnerRoutes.AdminShopsManagement`** + screen `ui/admin/AdminShopsManagementScreen.kt` (shops fetch, search, pull-to-refresh, open `ShopPublicRoute`), wired from **Admin More** tab. |
| 2026-04-12 | **Admin booking management**: new route **`InnerRoutes.AdminBookingManagement`** + screen `ui/admin/AdminBookingManagementScreen.kt` (status filters, detail sheet, confirm/cancel local status updates, pull-to-refresh, error states). `BookingRepository.create` now mirrors created booking into local admin list cache (`AdminBookingsRepository`). |
| 2026-04-12 | **Admin booking calendar**: new route **`InnerRoutes.AdminBookingCalendar`** + screen `ui/admin/AdminBookingCalendarScreen.kt` (calendar/list mode toggle, date picker, bookings for selected day, pull-to-refresh), wired from **Admin More** tab. |
| 2026-04-12 | **Admin affiliated sites**: new route **`InnerRoutes.AdminAffiliatedSites`** + screen `ui/admin/AdminAffiliatedSitesScreen.kt` (managed center picker + dive-site checkboxes), local persistence via `AdminAffiliatedSitesRepository` (`TokenStore` JSON). |
| 2026-04-12 | **Inventory module (initial Android port)**: new route **`InnerRoutes.Inventory`** + `ui/inventory/InventoryScreen.kt` (dashboard/list/maintenance/reports tabs), local persistence via `InventoryRepository` (`TokenStore` JSON for items + tickets), add item / checkout / inspection ticket actions, pull-to-refresh + empty/error states. |
| 2026-04-12 | **DiveEditor media parity step**: `DiveEditorScreen.kt` gets photo **split compare** mode and **app gallery sheet** (loads logbook photo URLs like iOS `DiveEditorAppGallerySheet`), plus URL-source support for export path (`saveEditedImage` accepts `http/https` input streams). |
| 2026-04-12 | **DiveEditor video parity step**: added shared-style `VideoUnderwaterProgressBanner` (`ui/components/VideoUnderwaterProgressBanner.kt`) and wired it into `DiveEditorScreen.kt` during video export with live progress/ETA based on processed timeline. |
| 2026-04-12 | **DiveEditor video UX follow-up**: `DiveEditorScreen.kt` now exposes **Share** action for processed videos (matching iOS), and updates preview URI only after successful export/save to avoid false "processed" state on failures. |
| 2026-04-12 | **Auth parity step**: added dedicated Android route `Routes.DiveCenterRegistration` + `DiveCenterRegistrationRoute` (fixed `PartnerRegKind.DIVE_CENTER`) with specific title/subtitle and login entry action, so dive-center onboarding no longer depends only on generic partner form. |
| 2026-04-12 | **Force-password parity step**: `ChangePasswordScreen.kt` now mirrors iOS forced-change UX/text better (lock-reset visual cue, password policy hint, letter+digit validation, explicit mismatch message, localized strings EN/RU) while keeping sign-out fallback and splash/login gate by `mustChangePassword`. |
| 2026-04-12 | **Auth localization parity cleanup**: `LoginScreen.kt` moved remaining hardcoded RU texts (title/subtitle/forgot/sign-in labels) into shared string resources (`values` + `values-ru`) to align with iOS localization-driven auth UI. |
| 2026-04-12 | **Map tab parity step**: `MapTabRoute.kt` now opens a **site detail bottom sheet** on marker tap (instead of instant booking), with dive-site summary + Book action, and adds floating **Add dive log** button that switches to Logbook tab (closer to iOS `MapTabView` add-log flow). |
| 2026-04-12 | **Logbook parity step**: added Android **fish species picker** in `AddDiveLogSheet` (`LogbookScreen.kt`) with searchable multi-select list (40 common species); selected species are persisted into dive notes payload for backend compatibility until dedicated field support exists. |
| 2026-04-12 | **Map/detail flow follow-up**: map site detail sheet now handles entity kind actions (center/shop profile open, business chat launch, and correct booking route args for center/site/shop), reducing mismatch with iOS `DiveCenterDetailView` action set. |
| 2026-04-12 | **Profile parity step**: `ProfileScreen.kt` now exposes explicit **Open partner portal** entry for partner roles (`DIVE_CENTER_ADMIN` / `SHOP_ADMIN` / `SUPER_ADMIN` / `INSTRUCTOR`), aligning with iOS `DiveCenterAdminView`-style admin entry point expectations from profile context. |
| 2026-04-12 | **Dive center admin screen step**: added dedicated route `InnerRoutes.DiveCenterAdminProfile` + screen `profile/DiveCenterAdminProfileScreen.kt` (load linked center, show stats, quick actions: manage instructors/sites, open admin dashboard), wired from profile for center-admin roles. |
| 2026-04-12 | **Auth branding step**: added reusable Android component `GoogleSignInBrandButtonLabel.kt` and integrated it into login/registration OAuth actions to better match iOS `GoogleSignInBrandButtonLabel` visual intent. |
| 2026-04-12 | **DiveEditor AI video step**: `DiveEditorScreen.kt` now supports cloud UVM video processing (`/v1/process/video/ai2` or `/api/v1/process/video/ai2` auto-detect), with progress banner reuse and save-to-gallery on success; local export path remains available. |
| 2026-04-12 | **Instructor schedule step**: `InstructorScheduleTab.kt` now loads real booking rows from `AdminBookingsRepository`, supports calendar/list modes with day filtering, pull-to-refresh, and status/amount cards (replaces static placeholder text). |
| 2026-04-12 | **Course → booking**: `InnerRoutes.bookingWizard` gains optional **`courseId`**; from **dive center public** course sheet, wizard loads course via `GET courses`, adds it as selectable **service** (price on request), pre-selects; step hint strings EN/RU. |
| 2026-04-12 | **Booking wizard participants**: **name-only** extra participants (email optional), matching iOS `CourseBookingView`; add button enabled when name non-empty; list hides blank emails. |
| 2026-04-12 | **Booking wizard**: **Notes** multiline field on payment step; trimmed text sent as `BookingCreateDto.notes` (parity with iOS booking notes). |
| 2026-04-12 | **Booking wizard payment step**: **Summary** block like iOS `PaymentStep` — Service (name-priced or price on request), gear subtotal, **Total** in USD; then notes + confirmation hint. |
| 2026-04-12 | **Public dive center**: backend `GET /v1/dive-centers/:id`; Android **Center profile** from Explore (dive center sheet) → courses, instructors, trips, book, business chat. |
| 2026-04-12 | **Global search** places: merge dive sites + centers + shops (app language for sites), tappable rows; **dive center** → `DiveCenterPublicRoute`, **fullscreen map** pin on center → profile (was booking-only). |
| 2026-04-12 | **Dive center public**: **reviews** list + add review (`dive_center`), **course** tap → bottom sheet + book; **`AddReviewableDialog`** extracted for Explore + center. |
| 2026-04-12 | **Shop public** `ShopPublicRoute` (`GET v1/shops/:id`): reviews, chat, book; **Explore** sheet / **search** / **fullscreen map** → shop profile; **`ReviewListRow`** shared; **trip detail** → dive center profile when organizer is `dive_center`. |
| 2026-04-12 | **Trip detail**: link to **`UserProfile`** when organizer is `user` (same pattern as dive center profile). |
| 2026-04-12 | **Deep links** `divehub://trip/…`, `trips?id=`, `divehub://center/…`, `shop/…`, `user/…` → **`innerNavDeepLinkRequests`** (diver + partner shells), `launchSingleTop`. |
| 2026-04-12 | **Deep link** `divehub://search?q=…` → **Global search** with query via **`AppGraph` pending search** (consume on route open). |

## How to work

1. Pick a **phase** (below).
2. For each row: implement Android target, align strings (EN/RU), wire APIs used by iOS.
3. Change status in this doc in the same PR when possible.

---

## Phases (suggested order)

| # | Phase | Goal |
|---|--------|------|
| 1 | **Diver core** | Tabs, explore, feed, logbook, social, chat, profile/settings — full UX vs iOS |
| 2 | **Navigation & details** | Dedicated map tab, dive center/site/instructor detail flows like iOS |
| 3 | **Booking** | Wizard branches, confirmation screen, trip/course booking, backend `bookings` if missing |
| 4 | **Partner — admin** | Dashboard, bookings/calendar, courses, trips mgmt, shops/sites/instructors/gear |
| 5 | **Partner — instructor** | Schedule, dashboard, photo pipeline vs `PhotoProcessingView` / iOS instructor tabs |
| 6 | **Partner — shop** | Full `ShopTabView` commerce and management |
| 7 | **Inventory** | Entire `Views/Inventory/*` module |
| 8 | **Media editors** | Underwater / image / video editor parity with iOS |
| 9 | **Polish** | Force-password flow, dive center registration, deep links, analytics parity |

---

## Shell & auth

| iOS (`DiveHub/Views/…`) | Android target | Status | Notes |
|-------------------------|------------------|--------|-------|
| `MainTabView.swift` (diver/admin/instructor/shop routing) | `AppHome.kt`, `DiverAppShell.kt`, `PartnerAppShell.kt`, `AppShellKind.kt` | 🟡 | Diver tab strip now includes dedicated **Map** tab + deep-link index remap; remaining admin/shop/instructor depth still partial |
| `Splash/SplashView.swift` | `splash/SplashScreen.kt` | 🟡 | **2s** gate + **1.2s** entrance animation aligned with iOS; primary canvas + logo + localized title; uses **mipmap launcher** (not iOS `BrandLogoSplash` raster) |
| `Auth/OnboardingView.swift` | `onboarding/OnboardingScreen.kt` | 🟡 | |
| `Auth/LoginView.swift` | `auth/LoginScreen.kt` | 🟡 | |
| `Auth/ForgotPasswordView.swift` | `auth/ForgotPasswordScreen.kt` | 🟡 | |
| `Auth/DiveCenterRegistrationView.swift` | `auth/DiveCenterRegistrationRoute` + `auth/PartnerRegistrationScreen.kt` (fixed `PartnerRegKind.DIVE_CENTER`) | 🟡 | Dedicated route/screen entry now exists; still shares most form UI with partner flow (further iOS layout/details parity pending) |
| `Auth/ForcePasswordChangeView.swift` | `auth/ChangePasswordScreen.kt` | 🟡 | Forced gate + policy validation/parity UX improved; still validate final visual polish/details vs iOS |
| `Auth/GoogleSignInBrandButtonLabel.swift` | `ui/components/GoogleSignInBrandButtonLabel.kt` (+ login/register usage) | 🟡 | Reusable branded label added; final logo asset parity (exact Google glyph asset) can still be refined |
| `Testing/APITestView.swift` | — | — | Dev-only |

---

## Diver — main tabs

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Explore/ExploreTabView.swift`, `ExploreView.swift`, `ExploreMapView.swift` | `explore/ExploreScreen.kt`, `explore/ExploreMapOsm.kt`, `explore/ExploreViewModel.kt` | 🟡 | List/map; list pull-to-refresh; content kept during reload when data exists; align filters & cards with `ListCard.swift` |
| `Map/MapTabView.swift`, `MapKitView.swift`, `OpenStreetMapView.swift`, `MapChromeControls.swift`, `Map/FilterView.swift` | `ui/map/MapTabRoute.kt` + `ExploreMapOsm.kt` | 🟡 | Dedicated map tab + filters + site detail sheet + add-log CTA + center/shop profile/chat actions; remaining visual polish/details vs iOS |
| `Feed/FeedView.swift`, `CreatePostView.swift` | `feed/FeedScreen.kt`, `feed/FeedViewModel.kt` | 🟡 | Pull-to-refresh; posts kept on reload; error + retry when empty |
| `Logbook/LogbookTabView.swift`, `AddDiveLogView.swift`, `DiveLogDetailView.swift` | `logbook/LogbookScreen.kt`, `logbook/LogbookViewModel.kt` | 🟡 | Pull-to-refresh; list kept on reload; error + retry when empty; detail/edit parity |
| `Logbook/FishSpeciesPickerView.swift` | `logbook/LogbookScreen.kt` (`FishSpeciesPickerSheet`) | 🟡 | Search + multi-select implemented; currently stored in notes (no dedicated API field yet) |
| `Social/SocialTabView.swift` | `social/SocialScreen.kt`, `social/SocialViewModel.kt` | 🟡 | |
| `Chat/ChatListView.swift`, `ChatDetailView.swift` | `chat/ChatScreen.kt`, `chat/ChatViewModel.kt` | 🟡 | |
| `Chat/NewChatWithFriendView.swift` | `chat/ChatScreen.kt` (`NewChatWithFriendsSheet`) | 🟡 | Sheet from chat tab; `openOrCreate` failures surface via **Snackbar** (`openConversationError`) |
| `Chat/BusinessChatLaunchView.swift` | `chat/BusinessChatOpenRoute.kt` + `InnerRoutes.BusinessChatOpen` | 🟡 | Opens API then hands off to Messages tab; errors stay on launcher screen |
| `DiveEditor/DiveEditorTabView.swift` + editor views | `diveeditor/DiveEditorScreen.kt` | 🟡 | Video/advanced tools vs iOS |
| `PhotoProcessing/PhotoProcessingView.swift` | Partner `InstructorPhotoTab.kt` | 🟡 | Entry hub: flow copy + Dive Editor + Notifications; full in-app picker/slider stack remains in `DiveEditorScreen.kt` |
| `Profile/ProfileTabView.swift` | `profile/ProfileScreen.kt`, `profile/HubScreens.kt` | 🟡 | |
| `Profile/EditProfileView.swift` | `profile/EditProfileScreen.kt` | 🟡 | |
| `Profile/SettingsViews.swift` (subscription, certs, gear, privacy, notifications, units, …) | `profile/UserPreferenceScreens.kt`, `SubscriptionScreen.kt`, … | 🟡 | Keep in lockstep with iOS `SettingsViews` |
| `Profile/UserProfileView.swift` | `profile/UserProfileScreen.kt` | 🟡 | |
| `Profile/DiveCenterAdminView.swift` | `profile/DiveCenterAdminProfileScreen.kt` + `PartnerAppShell.kt` | 🟡 | Dedicated route + profile entry + expanded stats/actions (sites/trips/nitrox/services/photos, direct links to calendar/bookings/inventory/gear/public profile); still lacks full iOS depth and backend-complete data contract |
| `Search/SearchView.swift` | `search/GlobalSearchScreen.kt`, `GlobalSearchViewModel.kt` | 🟡 | Places: sites + centers + shops; center + shop → public profile; `divehub://search?q=`; pull + toolbar refresh; results kept on reload |
| `Notifications/NotificationsView.swift` | `notifications/NotificationsScreen.kt` | 🟡 | Pull + toolbar refresh; list kept on reload |
| `Statistics/StatisticsView.swift` | `statistics/StatisticsScreen.kt` | 🟡 | Pull + app-bar refresh; stats **persist on reload**; inline error on soft failure; empty-state hint when no dives; iOS card icons / headline polish optional |
| `Achievements/AchievementsView.swift` | `achievements/AchievementsScreen.kt` | 🟡 | Pull + toolbar refresh; grid kept on reload |
| `Help/HelpSupportView.swift` | `profile/HubScreens.kt` / Help route | 🟡 | FAQ accordion + contact intents + legal/web links + in-app chat hint; iOS searchable list / dedicated live-chat screen still optional |

---

## Detail screens (diver / public)

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Detail/DiveSiteDetailView.swift` | Explore sheet + booking | 🟡 | Consider full-screen detail route like iOS |
| `Detail/DiveCenterDetailView.swift` | `centers/DiveCenterPublicRoute.kt` + map detail sheet actions (`ui/map/MapTabRoute.kt`) | 🟡 | Separate iOS screen behavior mostly covered by public center route and map detail actions; dedicated Android "detail-only" route still optional |
| `Detail/InstructorDetailView.swift` | `profile/InstructorPublicScreen.kt` + `InnerRoutes.instructorPublic` (центр, поиск, deep link `divehub://instructor/…`) | 🟡 | Отзывы `reviewableType=instructor`; AI summary / iOS-only поля — опционально |
| `Detail/DiveCenterPublicView.swift` | `centers/DiveCenterPublicRoute.kt`, `GET v1/dive-centers/:id` | 🟡 | Reviews + course bottom sheet + shared `AddReviewableDialog`; iOS course detail sheet still richer |

---

## Trips

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Trips/TripsListView.swift` | `trips/TripsScreens.kt`, `TripsViewModel.kt` | 🟡 | Toolbar refresh; pull-to-refresh; list preserved on reload |
| `Trips/CreateTripView.swift` | `trips/CreateTripScreen.kt` | 🟡 | `CreateTripRequestDto` matches Nest `CreateTripDto` (program days, expenses, price JSON, courses); optional `hotelId`/`yachtId` UUIDs not wired on Android (labels only) — same as relaxed iOS path when pickers are absent |
| `Trips/TripBookingView.swift` | `TripDetailViewModel` post-join organizer chat; full form N/A until API | 🟡 | Join flow + intro DM like iOS; rich participant payload not in `POST /trips/:id/join` |

---

## Booking

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Booking/BookingWizardView.swift` | `booking/BookingWizardScreen.kt`, `BookingWizardViewModel.kt` | 🟡 | Payment methods `online` / `on_site` / `google_pay` + optional `POST /api/bookings/payment-intent` (Stripe на сервере); summary dialog; services/instructors/sites data still verify vs iOS |
| `Booking/BookingConfirmationView.swift` | `booking/BookingWizardScreen.kt` (`AlertDialog` after submit) | 🟡 | Post-submit dialog now includes **structured booking summary** (id, center, service, schedule, payment, participants, gear, notes); dedicated full-screen confirmation route still optional |
| `Booking/CourseBookingView.swift` | `booking_wizard` + `courseId` from `DiveCenterPublicRoute` course sheet | 🟡 | Dedicated form + confirmation parity still partial; API still uses generic `BookingCreateDto.serviceId` |

---

## Partner — admin (`AdminTabView` subtree)

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Admin/AdminTabView.swift` | `PartnerAppShell.kt` (ADMIN) | 🟡 | `SUPER_ADMIN` now uses 2-tab shell (**Web panel** + **Profile**) like iOS; dive-center admin tab set remains partial vs iOS depth |
| `Admin/DashboardView.swift` | `AdminHomeTab` in `PartnerAppShell.kt` | 🟡 | Added local KPI widgets + direct actions (bookings/calendar/inventory); full iOS dashboard depth/charts/backend aggregates still pending |
| `Admin/BookingManagementView.swift` | `ui/admin/AdminBookingManagementScreen.kt` + `AdminBookingsRepository` | 🟡 | Local cache now has search, status chips, KPI line, richer detail actions; backend booking admin list/update APIs still pending |
| `Admin/BookingCalendarView.swift`, `CalendarView.swift`, `CustomCalendarView.swift` | `ui/admin/AdminBookingCalendarScreen.kt` | 🟡 | Calendar/list + month heatmap (count + status dot) + month nav + day tap; not a full iOS `CustomCalendarView` clone yet |
| `Admin/CoursesManagementView.swift` | `partner/PartnerCoursesTab.kt` + `data/PartnerCoursesRepository.kt` | 🟡 | Search/filter + local create/edit/archive flows implemented with persisted overlays; full backend CRUD + iOS detail/module editing stack still pending |
| `Admin/TripsManagementView.swift` | `TripsListTabContent` + center trips routes | 🟡 | Added search/filter chips + KPI summary in tab list; center-managed routes still lighter than iOS full management stack |
| `Admin/AnalyticsView.swift` | `partner/PartnerAnalyticsTab.kt` | 🟡 | Backend overview/error stats + local booking/inventory KPI cards + **7-day local booking-creation trend**; richer charts/time-series vs iOS still pending |
| `Admin/InstructorManagementView.swift`, `ManageInstructorsView.swift` | `admin/CenterInstructorsScreen.kt` | 🟡 | Search + KPI + pull refresh + local assign/unassign overlay; backend instructor assignment APIs still pending |
| `Admin/GearManagementView.swift` | `ui/admin/AdminGearManagementScreen.kt` + `AdminGearRepository` | 🟡 | Remote-first (`GET/POST/PATCH` under `/api/admin/.../gear`) with `TokenStore` cache; UX polish vs iOS optional |
| `Admin/ShopsManagementView.swift` | `ui/admin/AdminShopsManagementScreen.kt` | 🟡 | Browse/search/open public shop; **local drafts** (create/edit/delete, persisted) bridge missing backend shop admin CRUD |
| `Admin/ManageAffiliatedSitesView.swift` | `ui/admin/AdminAffiliatedSitesScreen.kt` + `AdminAffiliatedSitesRepository` | 🟡 | Local center→site mapping + search, KPI, bulk select/clear for listed sites; backend center affiliation API still pending |

---

## Partner — instructor

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Instructor/InstructorTabView.swift` | `PartnerAppShell.kt` (INSTRUCTOR) | 🟡 | |
| `Instructor/InstructorDashboardView.swift` | `InstructorHomeTab` in `PartnerAppShell.kt` | 🟡 | Local booking KPI strip + refresh + shortcuts (schedule/photo/editor/notifications); still not full iOS instructor dashboard depth |
| `Instructor/ScheduleView.swift` | `partner/InstructorScheduleTab.kt` | 🟡 | Calendar/list now show booking rows from local booking cache; backend instructor-specific schedule API still pending |

---

## Partner — shop

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Shop/ShopTabView.swift` | `PartnerAppShell.kt` (SHOP), `ShopSellTab.kt`, `data/ShopSellRepository.kt` | 🟡 | Products/orders sync with `GET/POST v1/shops/:shopId/products|orders` + local cache; full iOS-only workflows optional |

---

## Inventory (iOS-only module today)

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Inventory/InventoryTabView.swift` | `ui/inventory/InventoryScreen.kt` | 🟡 | Internal tabs + route from Admin More; items/tickets **remote-first** via `/api/admin/.../inventory/...` when `diveCenterId` present |
| `Inventory/InventoryDashboardView.swift` | `ui/inventory/InventoryScreen.kt` (Dashboard tab) | 🟡 | KPI cards + status/ticket breakdown + basic alerts + mini chart blocks + 7-day daily trend (items/tickets); deeper visual polish still pending |
| `Inventory/InventoryListView.swift` | `ui/inventory/InventoryScreen.kt` (Inventory tab) | 🟡 | List + add/status actions + search + status/category/condition/due filters, sorting chips, KPI/empty state, item drill-down, **reset filters**; richer iOS filter UX polish still pending |
| `Inventory/ItemDetailView.swift` | `ui/inventory/InventoryScreen.kt` + `InnerRoutes.InventoryItemDetail` | 🟡 | Detail shows condition + notes + checkout audit + ticket created-at + timeline preview; check-in + delete (cascade tickets) from toolbar; iOS multi-tab/photos/documents depth still pending |
| `Inventory/AddEditItemView.swift` | `ui/inventory/InventoryScreen.kt` (shared add/edit sheet) | 🟡 | Local add + edit with name/category/size/location + **condition** + **notes**; backend-validated fields/workflows still pending |
| `Inventory/CheckoutView.swift` | `ui/inventory/InventoryScreen.kt` (`Checkout` action) | 🟡 | Structured checkout (assignee/due/notes) + **staff handoff sign-off** + timestamp + check-in clears audit fields; drawn signature / full multi-step wizard still pending |
| `Inventory/InspectionView.swift` | `ui/inventory/InventoryScreen.kt` (`Inspect` action) | 🟡 | Ticket creation + workflow + checklist + signer + **priority chips**; advanced signature media/checklist templates still pending |
| `Inventory/ReportsView.swift` | `ui/inventory/InventoryScreen.kt` (Reports tab) | 🟡 | Totals + status/priority breakdowns + recent-activity metrics + visual daily trend (7d) + overdue-issued metric + text/CSV export share; richer charts and file-based exports still pending |
| `Inventory/MaintenanceTicketsView.swift` | `ui/inventory/InventoryScreen.kt` (Maintenance tab + `InventoryTicketDetailRoute`) | 🟡 | Status + **priority** filter chips + KPI + transitions + open-item + ticket detail + local audit timeline; server-backed audit and full workflow fields still pending |

---

## Image / video editing

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `ImageEditing/ImageEditingView.swift` | Partially `diveeditor/DiveEditorScreen.kt` | 🟡 | |
| `ImageEditing/UnderwaterPhotoEditorView.swift` | `diveeditor/DiveEditorScreen.kt` (manual params + split compare + app gallery sheet) | 🟡 | Core controls and compare/app-gallery flow added; no AI-depth/water-type model parity yet |
| `DiveEditor/DiveEditorEditorView.swift`, `DiveEditorVideoEditorView.swift`, gallery sheet | `DiveEditorScreen.kt` | 🟡 | Video export + share + cloud AI (`ai2`) processing path added; deeper preset/model tuning still pending |

---

## Shared components

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Common/DiveHubLogoMark.swift` | `components/DiveHubLogoMark.kt` | 🟡 | |
| `Common/VideoUnderwaterProgressBanner.swift` | `ui/components/VideoUnderwaterProgressBanner.kt` | 🟡 | Wired in `DiveEditorScreen.kt` video export flow |

---

## Navigation entry (Android)

- **Inner stack:** `MainShell.kt` + `InnerRoutes.kt`
- **Auth / root:** `RootNav.kt` + `Routes.kt`

Add new routes here when porting iOS flows that use `NavigationStack` / deep links.

---

## Backend cross-check

When Android reaches ✅ for a row, confirm the **same** REST (or GraphQL) contracts iOS uses exist and return the same shapes. Known gap: **`POST /bookings`** (and related list/admin booking APIs) may need Nest implementation if iOS already calls them.

---

## Maintenance

- **Owner:** update this file whenever a screen reaches ✅ or scope changes.
- **Reviews:** PR template can ask “`ANDROID_IOS_PARITY.md` updated?” for UI-heavy PRs.
