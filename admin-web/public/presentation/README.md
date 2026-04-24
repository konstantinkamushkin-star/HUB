# Presentation Scenario Capture

Single entrypoint script for deterministic Android/iOS scenario runs:

`python3 AppStore/capture_presentation_scenarios.py`

The script exports web-ready assets into this directory with exact names:

- `scenario-search.png` or `scenario-search.webp`
- `scenario-booking.png` or `scenario-booking.webp`
- `scenario-logbook.png` or `scenario-logbook.webp`
- `scenario-support.png` or `scenario-support.webp`

All outputs are normalized to `1170x2532`.

## Required env

Common:

- `PLATFORM=ios|android` (default: `ios`)
- `OUTPUT_FORMAT=png|webp` (default: `png`)

iOS:

- `IOS_DEVICE` (default: `iPhone 17 Pro Max`)
- `IOS_SCHEME` (default: `DiveHub`)
- `IOS_BUNDLE_ID` (default: `Dive-Hub.ru`)
- `IOS_DERIVED_DATA` (default: `/tmp/DiveHubDerivedDataPresentation`)

Android:

- `ANDROID_SERIAL` (optional, if multiple devices)
- `ANDROID_PACKAGE` (default: `com.divehub.app`)
- `ANDROID_ACTIVITY` (default: `com.divehub.app.MainActivity`)
- `TEST_CENTER_ID` (required for booking scenario)

Optional timing/tap tuning (for deterministic UI state capture):

- iOS: `IOS_BOOKING_TAP_X`, `IOS_BOOKING_TAP_Y`, `IOS_SUPPORT_TAP_X`, `IOS_SUPPORT_TAP_Y`
- Android: `ANDROID_BOOKING_TAP_X`, `ANDROID_BOOKING_TAP_Y`, `ANDROID_SUPPORT_TAP_X`, `ANDROID_SUPPORT_TAP_Y`
- waits: `IOS_WAIT_*`, `ANDROID_WAIT_*`, `SCENARIO_SEARCH_QUERY`

## Test accounts

Use dedicated deterministic accounts (non-personal data), keep credentials in CI secrets / local `.env` only:

- Diver account for scenario capture (must be pre-authorized in app session)
- Admin/support-visible account if support view requires role-gated data

Recommended secret names:

- `DIVEHUB_E2E_EMAIL`
- `DIVEHUB_E2E_PASSWORD`
- `DIVEHUB_E2E_SUPPORT_EMAIL`
- `DIVEHUB_E2E_SUPPORT_PASSWORD`

## Local commands

iOS PNG:

`PLATFORM=ios OUTPUT_FORMAT=png python3 AppStore/capture_presentation_scenarios.py`

Android WebP:

`PLATFORM=android OUTPUT_FORMAT=webp TEST_CENTER_ID=<center-id> python3 AppStore/capture_presentation_scenarios.py`

Install Pillow once if needed:

`python3 -m pip install Pillow`

## CI commands

Example iOS job step:

`PLATFORM=ios OUTPUT_FORMAT=png python3 AppStore/capture_presentation_scenarios.py`

Example Android job step:

`PLATFORM=android OUTPUT_FORMAT=png TEST_CENTER_ID=$TEST_CENTER_ID ANDROID_SERIAL=$ANDROID_SERIAL python3 AppStore/capture_presentation_scenarios.py`

## Output -> web assets mapping

Direct mapping used by admin-web presentation pages:

- `search` scenario -> `admin-web/public/presentation/scenario-search.<ext>`
- `booking` scenario -> `admin-web/public/presentation/scenario-booking.<ext>`
- `logbook` scenario -> `admin-web/public/presentation/scenario-logbook.<ext>`
- `support` scenario -> `admin-web/public/presentation/scenario-support.<ext>`

Where `<ext>` is `png` or `webp` from `OUTPUT_FORMAT`.
