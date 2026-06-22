# Play Store release assets (Android 1.1.0)

## Status

| Asset | Location | Status |
|-------|----------|--------|
| App icon 512/1024 | `icon-1024.png` | ✅ from App Store icon |
| Phone screenshots RU | `phone-screenshots-ru/` | ✅ 16 PNG (1284×2778) |
| Listing RU/EN | `listing/*.txt` | ✅ |
| OAuth Web client ID | `values/strings.xml` | ✅ configured |
| `google-services.json` | `app/google-services.json` | ⚠️ project # set; download full file from Firebase for FCM |

## Refresh screenshots

```bash
# From repo root (after iOS marketing run):
python3 AppStore/sync_play_upload_from_iphone.py
python3 DiveHubAndroid/scripts/sync-play-store-screenshots.py
```

## Pre-upload checklist

```bash
chmod +x DiveHubAndroid/scripts/prepare-play-store-release.sh
./DiveHubAndroid/scripts/prepare-play-store-release.sh
```

## Upload AAB

```bash
cd DiveHubAndroid && ./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

## Firebase (required for push)

Replace `app/google-services.json` — see [docs/FIREBASE_SETUP.md](../docs/FIREBASE_SETUP.md).

Add **SHA-1** of release keystore in Firebase → Android app → OAuth client.
