# Screenshot QA matrix (Android ↔ iOS 1.1.0)

Side-by-side checklist before Play Store release. Capture **iOS simulator** and **Android emulator** at **1280×2778** (or device-native) in **RU + EN**, **light + dark**.

## Diver shell

| # | Screen | iOS reference | Android route | RU | EN | Light | Dark |
|---|--------|---------------|---------------|----|----|-------|------|
| 1 | Explore list | `ExploreView` | Tab 0 | ☐ | ☐ | ☐ | ☐ |
| 2 | Explore map | `ExploreMapView` | Map fullscreen | ☐ | ☐ | ☐ | ☐ |
| 3 | Feed | `FeedView` | Tab 1 | ☐ | ☐ | ☐ | ☐ |
| 4 | Logbook | `LogbookTabView` | Tab 2 | ☐ | ☐ | ☐ | ☐ |
| 5 | Social | `SocialTabView` | Tab 4 | ☐ | ☐ | ☐ | ☐ |
| 6 | Trips hub | `UserTripsHubView` | Tab 3 | ☐ | ☐ | ☐ | ☐ |
| 7 | Chat hub | `ChatHubView` | Tab 5 | ☐ | ☐ | ☐ | ☐ |
| 8 | Dive Editor | `DiveEditorTabView` | Tab 6 (flag) | ☐ | ☐ | ☐ | ☐ |
| 9 | Profile | `ProfileTabView` | Tab 7/8 | ☐ | ☐ | ☐ | ☐ |
| 9 | Settings | `SettingsViews` | Inner → Settings | ☐ | ☐ | ☐ | ☐ |

## Auth & onboarding

| # | Screen | Android | RU | EN |
|---|--------|---------|----|----|
| 10 | Login + Google consent | `LoginScreen` | ☐ | ☐ |
| 11 | Register | `RegisterScreen` | ☐ | ☐ |
| 12 | Profile onboarding | `ProfileOnboardingScreen` | ☐ | ☐ |

## Booking & public

| # | Screen | Android | RU | EN |
|---|--------|---------|----|----|
| 13 | Booking wizard (5 steps) | `BookingWizardScreen` | ☐ | ☐ |
| 14 | Public dive center | `DiveCenterPublicRoute` | ☐ | ☐ |
| 15 | Public shop | `ShopPublicRoute` | ☐ | ☐ |
| 16 | Trips list + import URL | `TripsScreens` | ☐ | ☐ |

## Partner shells

| Role | Tabs to verify | ☐ |
|------|----------------|---|
| Dive-center admin | Configurable carousel (dashboard, explore, feed, courses, trips, photo, services, chats, profile) | ☐ |
| Instructor | Dashboard, Schedule, Photo, Profile | ☐ |
| Shop | Dashboard, My shop, Products, Orders, Analytics, Profile | ☐ |
| Super admin | Web panel, Profile | ☐ |

## Deep links (both platforms)

| URI | Expected |
|-----|----------|
| `divehub://explore` | Explore tab |
| `divehub://map` | Explore + fullscreen map |
| `divehub://feed` | Feed tab |
| `divehub://chat?peerType=dive_center&peerId=…` | Chat tab + business chat |
| `divehub://trip/{id}` | Trip detail |
| `divehub://dive_center/{id}` | Public center |

## CI gates

```bash
python3 DiveHubAndroid/scripts/verify-locale-string-keys.py   # exit 0
cd DiveHubAndroid && ./gradlew :app:compileDebugKotlin       # BUILD SUCCESSFUL
```

## Play Store assets

See [PlayStore/README.md](../PlayStore/README.md). Reuse creative from `AppStore/` where dimensions match.
