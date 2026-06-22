#!/usr/bin/env bash
# Pre-release checks for DiveHub Android 1.2.0 Play Store upload.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ANDROID="$ROOT/DiveHubAndroid"

echo "== Locale keys =="
python3 "$ANDROID/scripts/verify-locale-string-keys.py"

echo "== OAuth Web client ID =="
if grep -q 'google_oauth_web_client_id" translatable="false" />$' "$ANDROID/app/src/main/res/values/strings.xml" 2>/dev/null; then
  echo "ERROR: google_oauth_web_client_id is empty in values/strings.xml"
  exit 1
fi
echo "OK"

echo "== google-services.json =="
GS="$ANDROID/app/google-services.json"
if grep -q '"project_number": "000000000000"' "$GS"; then
  echo "ERROR: Replace placeholder project_number in google-services.json (see docs/FIREBASE_SETUP.md)"
  exit 1
fi
if grep -q 'AIzaSyPlaceholder' "$GS"; then
  echo "WARN: API key still placeholder — FCM/push will not work until you download google-services.json from Firebase"
fi
echo "project_number OK"

echo "== Play Store assets =="
python3 "$ANDROID/scripts/sync-play-store-screenshots.py"
SHOT_DIR="$ANDROID/PlayStore/phone-screenshots-ru"
COUNT=$(find "$SHOT_DIR" -name '*.png' 2>/dev/null | wc -l | tr -d ' ')
if [[ "$COUNT" -lt 2 ]]; then
  echo "ERROR: Need at least 2 phone screenshots in PlayStore/phone-screenshots-ru (found $COUNT)"
  exit 1
fi
echo "OK ($COUNT screenshots)"

echo "== Gradle compile =="
(cd "$ANDROID" && ./gradlew :app:compileDebugKotlin -q)

echo ""
echo "Release prep passed. Before Play upload:"
echo "  1. Replace app/google-services.json from Firebase (FCM + Android OAuth SHA-1)"
echo "  2. Upload PlayStore/phone-screenshots-ru/*.png and listing text from PlayStore/listing/"
echo "  3. ./gradlew :app:bundleRelease"
