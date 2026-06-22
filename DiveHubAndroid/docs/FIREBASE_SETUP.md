# Firebase setup (Android)

The repo ships a **placeholder** `app/google-services.json` so the project compiles without secrets.

## Replace before production release

1. Open [Firebase Console](https://console.firebase.google.com/) → project **226473319509** (Google Cloud project used by DiveHub OAuth).
2. Add Android app with package name `com.divehub.app` if missing.
3. Add **SHA-1** fingerprints (debug + release):

   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

4. Download `google-services.json` and replace:

   `DiveHubAndroid/app/google-services.json`

5. **Web client ID** (Google Sign-In via Credential Manager) is already in `values/strings.xml`:

   `226473319509-vrdqhhd9ne60oqub0m8468ad9eca72e8.apps.googleusercontent.com`

6. Rebuild:

   ```bash
   cd DiveHubAndroid && ./gradlew :app:assembleDebug
   ```

## Partial config in repo

The committed file includes the correct **project_number** and **Web OAuth client** for builds. **FCM/push** requires the real `mobilesdk_app_id` and `api_key` from Firebase download.

## Do not commit real keys to public repos

Use CI secrets or local override ignored by git if the repository is public.

## Placeholder detection

If `project_id` is `divehub-placeholder`, FCM and Google Sign-In will not work on device — expected for local dev only.
