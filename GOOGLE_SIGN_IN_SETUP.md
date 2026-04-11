# Настройка OAuth входа (Google и Apple)

## Apple Sign In

Apple Sign In уже настроен и готов к использованию! Он использует встроенный `AuthenticationServices` framework от Apple.

### Требования:
- iOS 13.0+
- Настроенный Apple Developer Account
- Capability "Sign in with Apple" включена в Xcode

### Настройка в Xcode:
1. Откройте проект в Xcode
2. Выберите таргет приложения
3. Перейдите в Signing & Capabilities
4. Нажмите "+ Capability"
5. Добавьте "Sign in with Apple"

## Google Sign In

Для полной поддержки Google Sign In необходимо:

## 1. Добавить GoogleSignIn SDK через Swift Package Manager

1. Откройте проект в Xcode
2. File → Add Package Dependencies...
3. Введите URL: `https://github.com/google/GoogleSignIn-iOS`
4. Выберите версию (рекомендуется последняя стабильная)
5. Добавьте пакет к таргету DiveHub

## 2. Настроить Google Cloud Console

1. Перейдите в [Google Cloud Console](https://console.cloud.google.com/)
2. Создайте новый проект или выберите существующий
3. Включите Google Sign-In API
4. Создайте OAuth 2.0 Client ID для iOS:
   - Credentials → Create Credentials → OAuth client ID
   - Application type: iOS
   - Bundle ID: ваш Bundle ID приложения
   - Сохраните Client ID

## 3. Настроить Info.plist

Добавьте в Info.plist:

```xml
<key>GIDClientID</key>
<string>YOUR_CLIENT_ID.apps.googleusercontent.com</string>
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>YOUR_REVERSED_CLIENT_ID</string>
        </array>
    </dict>
</array>
```

## 4. Обновить OAuthService.swift

После добавления SDK, обновите метод `signInWithGoogle()` в `OAuthService.swift`:

Сначала добавьте импорт в начало файла:
```swift
import GoogleSignIn
```

Затем обновите метод:

```swift
import GoogleSignIn

func signInWithGoogle() async throws -> GoogleSignInResult {
    guard let presentingViewController = await UIApplication.shared.windows.first?.rootViewController else {
        throw OAuthError.googleSignInNotConfigured
    }
    
    guard let clientID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String else {
        throw OAuthError.googleSignInNotConfigured
    }
    
    let config = GIDConfiguration(clientID: clientID)
    GIDSignIn.sharedInstance.configuration = config
    
    let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presentingViewController)
    
    guard let idToken = result.user.idToken?.tokenString else {
        throw OAuthError.invalidCredentials
    }
    
    return GoogleSignInResult(
        idToken: idToken,
        accessToken: result.user.accessToken.tokenString,
        email: result.user.profile?.email,
        fullName: result.user.profile?.name,
        profileImageURL: result.user.profile?.imageURL(withDimension: 200)?.absoluteString
    )
}
```

## 5. Обновить LoginView и SignUpView

Обновите методы `signInWithGoogle()` и `signUpWithGoogle()` в `LoginView.swift`:

```swift
private func signInWithGoogle() {
    errorMessage = nil
    Task {
        do {
            let result = try await OAuthService.shared.signInWithGoogle()
            try await authService.signInWithGoogle(
                idToken: result.idToken,
                accessToken: result.accessToken,
                email: result.email,
                fullName: result.fullName
            )
        } catch {
            if let authError = error as? AuthError {
                errorMessage = authError.errorDescription
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }
}
```

## 6. Настроить бэкенд

Убедитесь, что бэкенд поддерживает следующие endpoints:

- `POST /api/auth/google` - для входа/регистрации через Google
- `POST /api/auth/apple` - для входа/регистрации через Apple

Оба endpoints должны принимать:
- `idToken` - токен от провайдера
- `email` (опционально)
- `firstName` (опционально)
- `lastName` (опционально)

И возвращать:
- `accessToken` - JWT токен приложения
- `refreshToken` - refresh токен
- `user` - объект пользователя
