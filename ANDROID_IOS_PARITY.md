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
| `Splash/SplashView.swift` | `splash/SplashScreen.kt` | 🟡 | Verify timing & session restore vs iOS |
| `Auth/OnboardingView.swift` | `onboarding/OnboardingScreen.kt` | 🟡 | |
| `Auth/LoginView.swift` | `auth/LoginScreen.kt` | 🟡 | |
| `Auth/ForgotPasswordView.swift` | `auth/ForgotPasswordScreen.kt` | 🟡 | |
| `Auth/DiveCenterRegistrationView.swift` | `auth/DiveCenterRegistrationRoute` + `auth/PartnerRegistrationScreen.kt` (fixed `PartnerRegKind.DIVE_CENTER`) | 🟡 | Dedicated route/screen entry now exists; still shares most form UI with partner flow (further iOS layout/details parity pending) |
| `Auth/ForcePasswordChangeView.swift` | `auth/ChangePasswordScreen.kt` | 🟡 | Forced gate + policy validation/parity UX improved; still validate final visual polish/details vs iOS |
| `Auth/GoogleSignInBrandButtonLabel.swift` | Embedded in login UI | 🟡 | Branding parity |
| `Testing/APITestView.swift` | — | — | Dev-only |

---

## Diver — main tabs

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Explore/ExploreTabView.swift`, `ExploreView.swift`, `ExploreMapView.swift` | `explore/ExploreScreen.kt`, `explore/ExploreMapOsm.kt`, `explore/ExploreViewModel.kt` | 🟡 | List/map; list pull-to-refresh; content kept during reload when data exists; align filters & cards with `ListCard.swift` |
| `Map/MapTabView.swift`, `MapKitView.swift`, `OpenStreetMapView.swift`, `MapChromeControls.swift`, `Map/FilterView.swift` | `ui/map/MapTabRoute.kt` + `ExploreMapOsm.kt` | 🟡 | Dedicated diver map tab + filters added; iOS add-log button and site detail sheet parity still pending |
| `Feed/FeedView.swift`, `CreatePostView.swift` | `feed/FeedScreen.kt`, `feed/FeedViewModel.kt` | 🟡 | Pull-to-refresh; posts kept on reload; error + retry when empty |
| `Logbook/LogbookTabView.swift`, `AddDiveLogView.swift`, `DiveLogDetailView.swift` | `logbook/LogbookScreen.kt`, `logbook/LogbookViewModel.kt` | 🟡 | Pull-to-refresh; list kept on reload; error + retry when empty; detail/edit parity |
| `Logbook/FishSpeciesPickerView.swift` | — | ⬜ | |
| `Social/SocialTabView.swift` | `social/SocialScreen.kt`, `social/SocialViewModel.kt` | 🟡 | |
| `Chat/ChatListView.swift`, `ChatDetailView.swift` | `chat/ChatScreen.kt`, `chat/ChatViewModel.kt` | 🟡 | |
| `Chat/NewChatWithFriendView.swift` | `chat/ChatScreen.kt` (`NewChatWithFriendsSheet`) | 🟡 | Sheet from chat tab; handle `openOrCreate` failures in UI if needed |
| `Chat/BusinessChatLaunchView.swift` | `chat/BusinessChatOpenRoute.kt` + `InnerRoutes.BusinessChatOpen` | 🟡 | Opens API then hands off to Messages tab; errors stay on launcher screen |
| `DiveEditor/DiveEditorTabView.swift` + editor views | `diveeditor/DiveEditorScreen.kt` | 🟡 | Video/advanced tools vs iOS |
| `PhotoProcessing/PhotoProcessingView.swift` | Partner `InstructorPhotoTab.kt` | 🟡 | |
| `Profile/ProfileTabView.swift` | `profile/ProfileScreen.kt`, `profile/HubScreens.kt` | 🟡 | |
| `Profile/EditProfileView.swift` | `profile/EditProfileScreen.kt` | 🟡 | |
| `Profile/SettingsViews.swift` (subscription, certs, gear, privacy, notifications, units, …) | `profile/UserPreferenceScreens.kt`, `SubscriptionScreen.kt`, … | 🟡 | Keep in lockstep with iOS `SettingsViews` |
| `Profile/UserProfileView.swift` | `profile/UserProfileScreen.kt` | 🟡 | |
| `Profile/DiveCenterAdminView.swift` | — / partner shell | ⬜ | Align entry points with iOS |
| `Search/SearchView.swift` | `search/GlobalSearchScreen.kt`, `GlobalSearchViewModel.kt` | 🟡 | Places: sites + centers + shops; center + shop → public profile; `divehub://search?q=`; pull + toolbar refresh; results kept on reload |
| `Notifications/NotificationsView.swift` | `notifications/NotificationsScreen.kt` | 🟡 | Pull + toolbar refresh; list kept on reload |
| `Statistics/StatisticsView.swift` | `statistics/StatisticsScreen.kt` | 🟡 | |
| `Achievements/AchievementsView.swift` | `achievements/AchievementsScreen.kt` | 🟡 | Pull + toolbar refresh; grid kept on reload |
| `Help/HelpSupportView.swift` | `profile/HubScreens.kt` / Help route | 🟡 | |

---

## Detail screens (diver / public)

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Detail/DiveSiteDetailView.swift` | Explore sheet + booking | 🟡 | Consider full-screen detail route like iOS |
| `Detail/DiveCenterDetailView.swift` | — | ⬜ | |
| `Detail/InstructorDetailView.swift` | `admin/CenterInstructorsScreen.kt` (partial) | 🟡 | Diver-facing instructor profile |
| `Detail/DiveCenterPublicView.swift` | `centers/DiveCenterPublicRoute.kt`, `GET v1/dive-centers/:id` | 🟡 | Reviews + course bottom sheet + shared `AddReviewableDialog`; iOS course detail sheet still richer |

---

## Trips

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Trips/TripsListView.swift` | `trips/TripsScreens.kt`, `TripsViewModel.kt` | 🟡 | Toolbar refresh; pull-to-refresh; list preserved on reload |
| `Trips/CreateTripView.swift` | `trips/CreateTripScreen.kt` | 🟡 | |
| `Trips/TripBookingView.swift` | `TripDetailViewModel` post-join organizer chat; full form N/A until API | 🟡 | Join flow + intro DM like iOS; rich participant payload not in `POST /trips/:id/join` |

---

## Booking

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Booking/BookingWizardView.swift` | `booking/BookingWizardScreen.kt`, `BookingWizardViewModel.kt` | 🟡 | Services/instructors/sites: align data with iOS; extra participants: name-only add (email optional); notes → API; payment-step **Summary** rows (service / gear / total) |
| `Booking/BookingConfirmationView.swift` | `booking/BookingWizardScreen.kt` (`AlertDialog` after submit) | 🟡 | Inline confirmation; full stack parity optional |
| `Booking/CourseBookingView.swift` | `booking_wizard` + `courseId` from `DiveCenterPublicRoute` course sheet | 🟡 | Dedicated form + confirmation parity still partial; API still uses generic `BookingCreateDto.serviceId` |

---

## Partner — admin (`AdminTabView` subtree)

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Admin/AdminTabView.swift` | `PartnerAppShell.kt` (ADMIN) | 🟡 | Tab set similar; many screens missing |
| `Admin/DashboardView.swift` | `AdminHomeTab` in `PartnerAppShell.kt` | 🟡 | KPIs/widgets vs iOS |
| `Admin/BookingManagementView.swift` | `ui/admin/AdminBookingManagementScreen.kt` + `AdminBookingsRepository` | 🟡 | Local cache for list/status; backend booking admin list/update APIs still pending |
| `Admin/BookingCalendarView.swift`, `CalendarView.swift`, `CustomCalendarView.swift` | `ui/admin/AdminBookingCalendarScreen.kt` | 🟡 | Calendar/list mode + selected-day list implemented; no month heatmap/status dots yet |
| `Admin/CoursesManagementView.swift` | `partner/PartnerCoursesTab.kt` | 🟡 | |
| `Admin/TripsManagementView.swift` | `TripsListTabContent` + center trips routes | 🟡 | |
| `Admin/AnalyticsView.swift` | `partner/PartnerAnalyticsTab.kt` | 🟡 | |
| `Admin/InstructorManagementView.swift`, `ManageInstructorsView.swift` | `admin/CenterInstructorsScreen.kt` | 🟡 | |
| `Admin/GearManagementView.swift` | `ui/admin/AdminGearManagementScreen.kt` + `AdminGearRepository` | 🟡 | Local persistence only; backend inventory/gear APIs still pending |
| `Admin/ShopsManagementView.swift` | `ui/admin/AdminShopsManagementScreen.kt` | 🟡 | Browse/search/open public shop; no create/edit workflows yet |
| `Admin/ManageAffiliatedSitesView.swift` | `ui/admin/AdminAffiliatedSitesScreen.kt` + `AdminAffiliatedSitesRepository` | 🟡 | Local center→site mapping; backend center affiliation API still pending |

---

## Partner — instructor

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Instructor/InstructorTabView.swift` | `PartnerAppShell.kt` (INSTRUCTOR) | 🟡 | |
| `Instructor/InstructorDashboardView.swift` | `InstructorHomeTab` in `PartnerAppShell.kt` | 🟡 | |
| `Instructor/ScheduleView.swift` | `partner/InstructorScheduleTab.kt` | 🟡 | Wire bookings API when ready |

---

## Partner — shop

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Shop/ShopTabView.swift` | `PartnerAppShell.kt` (SHOP), `ShopSellTab.kt` | 🟡 | Full store / orders / trips segments vs iOS |

---

## Inventory (iOS-only module today)

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `Inventory/InventoryTabView.swift` | `ui/inventory/InventoryScreen.kt` | 🟡 | Internal tabs (dashboard/list/maintenance/reports) + route from Admin More |
| `Inventory/InventoryDashboardView.swift` | `ui/inventory/InventoryScreen.kt` (Dashboard tab) | 🟡 | KPI cards only (no charts/warnings yet) |
| `Inventory/InventoryListView.swift` | `ui/inventory/InventoryScreen.kt` (Inventory tab) | 🟡 | List + add + status actions; advanced filters missing |
| `Inventory/ItemDetailView.swift` | `ui/inventory/InventoryScreen.kt` (row actions + inline details) | 🟡 | No dedicated detail route yet |
| `Inventory/AddEditItemView.swift` | `ui/inventory/InventoryScreen.kt` (add item panel) | 🟡 | Add only; full edit form pending |
| `Inventory/CheckoutView.swift` | `ui/inventory/InventoryScreen.kt` (`Checkout` action) | 🟡 | Basic status switch only |
| `Inventory/InspectionView.swift` | `ui/inventory/InventoryScreen.kt` (`Inspect` action) | 🟡 | Creates maintenance ticket; no full inspection checklist |
| `Inventory/ReportsView.swift` | `ui/inventory/InventoryScreen.kt` (Reports tab) | 🟡 | Basic totals + category breakdown |
| `Inventory/MaintenanceTicketsView.swift` | `ui/inventory/InventoryScreen.kt` (Maintenance tab) | 🟡 | Ticket list only; no workflow stages yet |

---

## Image / video editing

| iOS | Android target | Status | Notes |
|-----|----------------|--------|-------|
| `ImageEditing/ImageEditingView.swift` | Partially `diveeditor/DiveEditorScreen.kt` | 🟡 | |
| `ImageEditing/UnderwaterPhotoEditorView.swift` | `diveeditor/DiveEditorScreen.kt` (manual params + split compare + app gallery sheet) | 🟡 | Core controls and compare/app-gallery flow added; no AI-depth/water-type model parity yet |
| `DiveEditor/DiveEditorEditorView.swift`, `DiveEditorVideoEditorView.swift`, gallery sheet | `DiveEditorScreen.kt` | 🟡 | Video export now shows progress banner + ETA and supports share action; advanced AI video models/presets still pending |

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
