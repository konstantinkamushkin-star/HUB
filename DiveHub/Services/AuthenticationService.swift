//
//  AuthenticationService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine
import AuthenticationServices

enum AuthError: LocalizedError {
    case invalidCredentials
    case networkError
    /// Нет сети / не удаётся достучаться до сервера (URLError и т.п.).
    case noInternetConnection
    case userNotFound
    case emailAlreadyExists
    case weakPassword
    case invalidEmail
    case emptyFields
    case serverError
    case backendUnavailable
    case invalidVerificationCode
    case verificationCodeExpired
    case verificationCodeNotFound
    case unknown(Error)
    
    var errorDescription: String? {
        let localizationService = LocalizationService.shared
        switch self {
        case .invalidCredentials:
            return localizationService.localizedString("invalidCredentials", table: "errors")
        case .networkError:
            return localizationService.localizedString("networkError", table: "errors")
        case .noInternetConnection:
            return localizationService.localizedString("noInternetConnection", table: "errors")
        case .userNotFound:
            return localizationService.localizedString("userNotFound", table: "errors")
        case .emailAlreadyExists:
            return localizationService.localizedString("emailAlreadyExists", table: "errors")
        case .weakPassword:
            return localizationService.localizedString("weakPassword", table: "errors")
        case .invalidEmail:
            return localizationService.localizedString("invalidEmail", table: "errors")
        case .emptyFields:
            return localizationService.localizedString("emptyFields", table: "errors")
        case .serverError:
            return localizationService.localizedString("serverError", table: "errors")
        case .backendUnavailable:
            return localizationService.localizedString("backendUnavailable", table: "errors")
        case .invalidVerificationCode:
            return localizationService.localizedString("invalidVerificationCode", table: "errors")
        case .verificationCodeExpired:
            return localizationService.localizedString("verificationCodeExpired", table: "errors")
        case .verificationCodeNotFound:
            return localizationService.localizedString("verificationCodeNotFound", table: "errors")
        case .unknown(let error):
            // Try to localize if it's a NetworkError
            if let networkError = error as? NetworkError {
                switch networkError {
                case .serverError(let code):
                    if code == 400 {
                        return localizationService.localizedString("serverError", table: "errors")
                    } else if code == 401 {
                        return localizationService.localizedString("pleaseSignIn", table: "errors")
                    } else if code == 403 {
                        return localizationService.localizedString("editProfileNoPermission", table: "errors")
                    } else if code == 404 {
                        return localizationService.localizedString("backendUnavailable", table: "errors")
                    } else if code == 409 {
                        return localizationService.localizedString("emailAlreadyExists", table: "errors")
                    } else if code == 429 {
                        return localizationService.localizedString("tooManyRequests", table: "errors")
                    }
                    return localizationService.localizedString("serverError", table: "errors")
                case .serverErrorWithDetail(let code, _):
                    if code == 401 {
                        return localizationService.localizedString("pleaseSignIn", table: "errors")
                    }
                    if code == 403 {
                        return localizationService.localizedString("editProfileNoPermission", table: "errors")
                    }
                    if code == 409 {
                        return networkError.errorDescription
                            ?? localizationService.localizedString("emailAlreadyExists", table: "errors")
                    }
                    return networkError.errorDescription ?? localizationService.localizedString("serverError", table: "errors")
                case .networkUnavailable:
                    return localizationService.localizedString("noInternetConnection", table: "errors")
                default:
                    return networkError.errorDescription ?? localizationService.localizedString("serverError", table: "errors")
                }
            }
            if NetworkError.isTransportLikelyNoInternet(error) {
                return localizationService.localizedString("noInternetConnection", table: "errors")
            }
            return error.localizedDescription
        }
    }
}

/// Body for `PATCH /api/auth/me` (only non-`nil` fields are encoded).
struct AuthMePatchBody: Encodable {
    var firstName: String?
    var lastName: String?
    var phone: String?
    var bio: String?
    var language: String?
    var avatarUrl: String?
    var countryCode: String?
    var diverProfile: DiverProfilePayload?
    /// Смена email (если поддерживается бэкендом); `nil` — не отправлять поле.
    var email: String?
}

class AuthenticationService: ObservableObject {
    static let shared = AuthenticationService()

    private static let pendingPostRegistrationProWelcomeKey = "pending_post_registration_pro_welcome"
    private static let dismissedPostRegistrationProWelcomeIdsKey = "dismissed_post_registration_pro_welcome_user_ids"
    
    @Published var currentUser: User?
    @Published var isAuthenticated: Bool = false
    @Published var isLoading: Bool = false
    @Published var error: AuthError?
    /// После входа с временным паролем партнёра — показать экран смены пароля.
    @Published var requiresPasswordReset: Bool = false
    /// Полноэкранный баннер «спасибо за регистрацию + 4 месяца PRO».
    @Published var showPostRegistrationProWelcome: Bool = false
    
    /// Вызывать после успешной **регистрации** (email или Apple/Google из формы регистрации).
    func requestPostRegistrationProWelcomeForNewAccount() {
        UserDefaults.standard.set(true, forKey: Self.pendingPostRegistrationProWelcomeKey)
    }
    
    /// Показать баннер, если регистрация только что прошла и пользователь ещё не закрывал его.
    func presentPostRegistrationProWelcomeIfPending() {
        guard !showPostRegistrationProWelcome else { return }
        guard let u = currentUser else { return }
        let dismissed = Set(UserDefaults.standard.stringArray(forKey: Self.dismissedPostRegistrationProWelcomeIdsKey) ?? [])
        guard !dismissed.contains(u.id) else {
            UserDefaults.standard.removeObject(forKey: Self.pendingPostRegistrationProWelcomeKey)
            return
        }
        guard u.role == .diverPro else { return }
        if let exp = u.subscriptionExpiresAt {
            guard exp > Date() else { return }
        } else if u.subscriptionStatus != .active {
            return
        }
        UserDefaults.standard.removeObject(forKey: Self.pendingPostRegistrationProWelcomeKey)
        showPostRegistrationProWelcome = true
    }
    
    func dismissPostRegistrationProWelcome() {
        if let id = currentUser?.id {
            var ids = UserDefaults.standard.stringArray(forKey: Self.dismissedPostRegistrationProWelcomeIdsKey) ?? []
            if !ids.contains(id) {
                ids.append(id)
                UserDefaults.standard.set(ids, forKey: Self.dismissedPostRegistrationProWelcomeIdsKey)
            }
        }
        UserDefaults.standard.removeObject(forKey: Self.pendingPostRegistrationProWelcomeKey)
        showPostRegistrationProWelcome = false
    }
    
    // Helper method to validate authentication state
    func validateAuthentication() {
        if isAuthenticated {
            let hasAccessToken = KeychainService.shared.getAccessToken() != nil
            let hasRefreshToken = KeychainService.shared.getRefreshToken() != nil
            if !hasAccessToken && !hasRefreshToken {
                // Tokens are missing, clear authentication
                isAuthenticated = false
                currentUser = nil
                clearSession()
            }
        }
    }
    
    private var cancellables = Set<AnyCancellable>()

    private init() {
        // Check for existing session
        loadSavedSession()
    }
    
    // MARK: - Authentication Methods
    
    func signIn(email: String, password: String) async throws {
        isLoading = true
        error = nil
        
        struct LoginRequest: Codable {
            let email: String
            let password: String
        }
        
        struct LoginResponse: Codable {
            let accessToken: String
            let refreshToken: String
            let user: User
            let mustChangePassword: Bool?
        }
        
        do {
            let loginRequest = LoginRequest(email: email, password: password)
            
            let response: LoginResponse = try await NetworkService.shared.request(
                endpoint: "/api/auth/login",
                method: .post,
                body: loginRequest
            )
            
            // Сохраняем токены
            NetworkService.shared.saveAuthTokens(
                accessToken: response.accessToken,
                refreshToken: response.refreshToken
            )
            
            // Обновляем пользователя
            currentUser = response.user
            isAuthenticated = true
            requiresPasswordReset =
                response.mustChangePassword == true
                || response.user.mustChangePassword == true
            saveSession()
            isLoading = false
        } catch {
            isLoading = false
            if let networkError = error as? NetworkError {
                switch networkError {
                case .serverError(401), .serverErrorWithDetail(401, _):
                    throw AuthError.invalidCredentials
                case .serverError(404), .serverErrorWithDetail(404, _):
                    throw AuthError.backendUnavailable
                case .networkUnavailable:
                    throw AuthError.noInternetConnection
                default:
                    throw AuthError.unknown(error)
                }
            } else {
                throw AuthError.unknown(error)
            }
        }
    }
    
    func signUp(
        email: String,
        password: String,
        personalDataConsent: Bool,
        personalDataConsentText: String
    ) async throws {
        isLoading = true
        error = nil
        
        // Валидация
        guard !email.isEmpty, !password.isEmpty else {
            isLoading = false
            throw AuthError.emptyFields
        }
        
        guard email.contains("@") else {
            isLoading = false
            throw AuthError.invalidEmail
        }
        
        guard password.count >= 8 else {
            isLoading = false
            throw AuthError.weakPassword
        }
        
        // Плейсхолдер имени до онбординга: локальная часть email или «Diver».
        let normalizedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let localPart = normalizedEmail.split(separator: "@").first.map(String.init) ?? "diver"
        let sanitizedLocal = localPart
            .replacingOccurrences(of: "[^a-zA-Z0-9._-]", with: "", options: .regularExpression)
        let firstName = String(sanitizedLocal.prefix(80)).isEmpty ? "Diver" : String(sanitizedLocal.prefix(80))
        let lastName = firstName
        
        struct RegisterRequest: Codable {
            let email: String
            let password: String
            let firstName: String
            let lastName: String
            let phone: String?
            let personalDataConsent: Bool
            let personalDataConsentText: String
        }

        // Backward-compatible payload for servers where consent fields are not yet in DTO.
        struct LegacyRegisterRequest: Codable {
            let email: String
            let password: String
            let firstName: String
            let lastName: String
            let phone: String?
        }
        
        struct RegisterResponse: Codable {
            let accessToken: String
            let refreshToken: String
            let user: User
        }
        
        do {
            let registerRequest = RegisterRequest(
                email: normalizedEmail,
                password: password,
                firstName: firstName,
                lastName: lastName,
                phone: nil,
                personalDataConsent: personalDataConsent,
                personalDataConsentText: personalDataConsentText
            )

            let response: RegisterResponse
            do {
                response = try await NetworkService.shared.request(
                    endpoint: "/api/auth/register",
                    method: .post,
                    body: registerRequest
                )
            } catch let networkError as NetworkError {
                // Legacy fallback: older backend may reject new consent fields as unknown.
                if case .serverErrorWithDetail(let code, let message) = networkError, code == 400 {
                    let lower = message.lowercased()
                    let shouldRetryWithLegacy =
                        lower.contains("personaldataconsent")
                        || lower.contains("personaldataconsenttext")
                        || lower.contains("should not exist")
                        || lower.contains("property")
                    if shouldRetryWithLegacy {
                        let legacyRequest = LegacyRegisterRequest(
                            email: normalizedEmail,
                            password: password,
                            firstName: firstName,
                            lastName: lastName,
                            phone: nil
                        )
                        response = try await NetworkService.shared.request(
                            endpoint: "/api/auth/register",
                            method: .post,
                            body: legacyRequest
                        )
                    } else {
                        throw networkError
                    }
                } else {
                    throw networkError
                }
            }
            
            // Сохраняем токены
            NetworkService.shared.saveAuthTokens(
                accessToken: response.accessToken,
                refreshToken: response.refreshToken
            )
            
            currentUser = response.user
            isAuthenticated = true
            requiresPasswordReset = false
            saveSession()
            requestPostRegistrationProWelcomeForNewAccount()
            isLoading = false
        } catch {
            isLoading = false
            if let networkError = error as? NetworkError {
                switch networkError {
                case .serverError(400):
                    throw AuthError.unknown(networkError)
                case .serverError(409):
                    throw AuthError.emailAlreadyExists
                case .serverErrorWithDetail(let code, let message):
                    let lower = message.lowercased()
                    if code == 409
                        || (code == 400 && (lower.contains("already exists") || lower.contains("user with this email")))
                    {
                        throw AuthError.emailAlreadyExists
                    } else if code == 404 {
                        throw AuthError.backendUnavailable
                    } else {
                        // Preserve backend validation/diagnostic text in UI.
                        throw AuthError.unknown(networkError)
                    }
                case .serverError(404):
                    throw AuthError.backendUnavailable
                case .networkUnavailable:
                    throw AuthError.noInternetConnection
                default:
                    throw AuthError.unknown(networkError)
                }
            } else {
                throw AuthError.unknown(error)
            }
        }
    }
    
    func signOut() {
        currentUser = nil
        isAuthenticated = false
        NetworkService.shared.clearAuthTokens()
        clearSession()
    }
    
    func updateUser(_ user: User) {
        let apply = {
            self.currentUser = user
            self.saveSession()
        }
        if Thread.isMainThread {
            apply()
        } else {
            DispatchQueue.main.async(execute: apply)
        }
    }

    /// PATCH `/api/auth/me` — partial update; optional fields omitted when `nil`.
    func patchAuthenticatedProfile(_ patch: AuthMePatchBody) async throws -> User {
        isLoading = true
        error = nil
        defer { isLoading = false }
        do {
            let user: User = try await NetworkService.shared.request(
                endpoint: "/api/auth/me",
                method: .patch,
                body: patch
            )
            await MainActor.run {
                self.currentUser = user
                self.saveSession()
            }
            return user
        } catch {
            if let networkError = error as? NetworkError {
                switch networkError {
                case .networkUnavailable:
                    throw AuthError.noInternetConnection
                default:
                    throw AuthError.unknown(error)
                }
            }
            throw AuthError.unknown(error)
        }
    }

    /// Подтягивает профиль с сервера (в т.ч. `diveCenterId` после правок бэкенда / привязки owner).
    func refreshSessionUserFromServer() async {
        guard isAuthenticated else { return }
        do {
            let user: User = try await NetworkService.shared.request(
                endpoint: "/api/auth/me",
                method: .get
            )
            await MainActor.run {
                self.updateUser(user)
            }
        } catch {
            // оставляем локальный снимок пользователя
        }
    }
    
    // MARK: - Session Management
    
    private func saveSession() {
        if let user = currentUser,
           let encoded = try? JSONEncoder().encode(user) {
            UserDefaults.standard.set(encoded, forKey: "current_user")
            UserDefaults.standard.set(true, forKey: "is_authenticated")
        }
    }
    
    private func loadSavedSession() {
        if let data = UserDefaults.standard.data(forKey: "current_user"),
           let user = try? JSONDecoder().decode(User.self, from: data) {
            currentUser = user
            // Check if tokens exist in Keychain - if not, user is not authenticated
            let hasAccessToken = KeychainService.shared.getAccessToken() != nil
            let hasRefreshToken = KeychainService.shared.getRefreshToken() != nil
            let savedAuthState = UserDefaults.standard.bool(forKey: "is_authenticated")
            isAuthenticated = savedAuthState && (hasAccessToken || hasRefreshToken)

            // If tokens are missing but session says authenticated, clear the session
            if !isAuthenticated && savedAuthState {
                clearSession()
                currentUser = nil
            } else if isAuthenticated {
                requiresPasswordReset = currentUser?.mustChangePassword == true
            }
        }
    }
    
    private func clearSession() {
        UserDefaults.standard.removeObject(forKey: "current_user")
        UserDefaults.standard.removeObject(forKey: "is_authenticated")
        requiresPasswordReset = false
    }

    func changePasswordAndCompleteSetup(currentPassword: String, newPassword: String) async throws {
        isLoading = true
        error = nil
        defer { isLoading = false }

        struct Body: Codable {
            let currentPassword: String
            let newPassword: String
        }
        struct Res: Codable {
            let ok: Bool
            let user: User
        }

        let body = Body(currentPassword: currentPassword, newPassword: newPassword)
        let res: Res = try await NetworkService.shared.request(
            endpoint: "/api/auth/password",
            method: .patch,
            body: body
        )
        var u = res.user
        u.mustChangePassword = false
        currentUser = u
        requiresPasswordReset = false
        saveSession()
    }
    
    // MARK: - Password Recovery
    
    /// Same rules as API + Android: trim and lowercase (email DB matching).
    private func normalizedPasswordResetEmail(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
    
    func requestPasswordReset(email: String) async throws {
        isLoading = true
        error = nil
        
        guard !email.isEmpty else {
            isLoading = false
            throw AuthError.emptyFields
        }
        
        guard email.contains("@") else {
            isLoading = false
            throw AuthError.invalidEmail
        }
        
        struct PasswordResetRequest: Codable {
            let email: String
        }
        
        struct PasswordResetResponse: Codable {
            let message: String
            let resetCode: String?
            let note: String?
        }

        do {
            let request = PasswordResetRequest(email: normalizedPasswordResetEmail(email))
            let response: PasswordResetResponse = try await NetworkService.shared.request(
                endpoint: "/api/auth/forgot-password",
                method: .post,
                body: request
            )
            #if DEBUG
            if let resetCode = response.resetCode {
                print("Password reset code (debug API response): \(resetCode)")
            }
            #endif
            isLoading = false
        } catch {
            isLoading = false
            if let networkError = error as? NetworkError {
                switch networkError {
                case .networkUnavailable:
                    throw AuthError.noInternetConnection
                default:
                    throw AuthError.unknown(error)
                }
            } else {
                throw AuthError.unknown(error)
            }
        }
    }
    
    func verifyResetCode(email: String, code: String) async throws {
        isLoading = true
        error = nil
        
        guard !email.isEmpty, !code.isEmpty else {
            isLoading = false
            throw AuthError.emptyFields
        }
        
        struct VerifyCodeRequest: Codable {
            let email: String
            let code: String
        }
        
        struct VerifyCodeResponse: Codable {
            let message: String
            let token: String? // Reset token for password reset
        }
        
        do {
            let request = VerifyCodeRequest(email: normalizedPasswordResetEmail(email), code: code)
            let _: VerifyCodeResponse = try await NetworkService.shared.request(
                endpoint: "/api/auth/verify-reset-code",
                method: .post,
                body: request
            )
            isLoading = false
        } catch {
            isLoading = false
            if let networkError = error as? NetworkError {
                switch networkError {
                case .serverError(400):
                    throw AuthError.invalidVerificationCode
                case .serverError(404):
                    throw AuthError.verificationCodeNotFound
                case .serverError(410):
                    throw AuthError.verificationCodeExpired
                case .networkUnavailable:
                    throw AuthError.noInternetConnection
                default:
                    throw AuthError.unknown(error)
                }
            } else if error is AuthError {
                throw error
            } else {
                throw AuthError.unknown(error)
            }
        }
    }
    
    func resetPassword(email: String, code: String, newPassword: String) async throws {
        isLoading = true
        error = nil
        
        guard !email.isEmpty, !code.isEmpty, !newPassword.isEmpty else {
            isLoading = false
            throw AuthError.emptyFields
        }
        
        guard newPassword.count >= 8 else {
            isLoading = false
            throw AuthError.weakPassword
        }
        
        struct ResetPasswordRequest: Codable {
            let email: String
            let code: String
            let newPassword: String
        }
        
        struct ResetPasswordResponse: Codable {
            let message: String
        }
        
        do {
            let request = ResetPasswordRequest(
                email: normalizedPasswordResetEmail(email),
                code: code,
                newPassword: newPassword
            )
            let _: ResetPasswordResponse = try await NetworkService.shared.request(
                endpoint: "/api/auth/reset-password",
                method: .post,
                body: request
            )
            isLoading = false
        } catch {
            isLoading = false
            if let networkError = error as? NetworkError {
                switch networkError {
                case .serverError(400):
                    throw AuthError.invalidVerificationCode
                case .serverError(404):
                    throw AuthError.verificationCodeNotFound
                case .serverError(410):
                    throw AuthError.verificationCodeExpired
                case .networkUnavailable:
                    throw AuthError.noInternetConnection
                default:
                    throw AuthError.unknown(error)
                }
            } else if error is AuthError {
                throw error
            } else {
                throw AuthError.unknown(error)
            }
        }
    }
    
    // MARK: - OAuth Authentication
    
    func signInWithApple(
        authorization: ASAuthorization,
        personalDataConsent: Bool,
        personalDataConsentText: String
    ) async throws {
        isLoading = true
        error = nil
        
        guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            isLoading = false
            throw AuthError.invalidCredentials
        }
        
        guard let identityToken = appleIDCredential.identityToken else {
            isLoading = false
            throw AuthError.invalidCredentials
        }
        
        guard let idTokenString = String(data: identityToken, encoding: .utf8) else {
            isLoading = false
            throw AuthError.invalidCredentials
        }
        
        // Extract user info
        let email = appleIDCredential.email
        let fullName = appleIDCredential.fullName
        let firstName = fullName?.givenName ?? ""
        let lastName = fullName?.familyName ?? ""
        
        // Send to backend
        struct AppleSignInRequest: Codable {
            let idToken: String
            let email: String?
            let firstName: String?
            let lastName: String?
            let personalDataConsent: Bool
            let personalDataConsentText: String
        }
        
        struct AppleSignInResponse: Codable {
            let accessToken: String
            let refreshToken: String
            let user: User
            let mustChangePassword: Bool?
        }
        
        do {
            let request = AppleSignInRequest(
                idToken: idTokenString,
                email: email,
                firstName: firstName.isEmpty ? nil : firstName,
                lastName: lastName.isEmpty ? nil : lastName,
                personalDataConsent: personalDataConsent,
                personalDataConsentText: personalDataConsentText
            )
            
            let response: AppleSignInResponse = try await NetworkService.shared.request(
                endpoint: "/api/auth/apple",
                method: .post,
                body: request
            )
            
            // Save tokens
            NetworkService.shared.saveAuthTokens(
                accessToken: response.accessToken,
                refreshToken: response.refreshToken
            )
            
            currentUser = response.user
            isAuthenticated = true
            requiresPasswordReset =
                response.mustChangePassword == true
                || response.user.mustChangePassword == true
            saveSession()
            isLoading = false
        } catch {
            isLoading = false
            if let networkError = error as? NetworkError {
                switch networkError {
                case .serverError(401), .serverErrorWithDetail(401, _):
                    throw AuthError.invalidCredentials
                case .serverError(404), .serverErrorWithDetail(404, _):
                    throw AuthError.backendUnavailable
                case .networkUnavailable:
                    throw AuthError.noInternetConnection
                default:
                    throw AuthError.unknown(error)
                }
            } else {
                throw AuthError.unknown(error)
            }
        }
    }
    
    func signInWithGoogle(
        idToken: String,
        accessToken: String?,
        email: String?,
        fullName: String?,
        personalDataConsent: Bool,
        personalDataConsentText: String
    ) async throws {
        isLoading = true
        error = nil
        
        guard !idToken.isEmpty else {
            isLoading = false
            throw AuthError.invalidCredentials
        }
        
        // Extract name components
        let nameComponents = (fullName ?? "").components(separatedBy: " ")
        let firstName = nameComponents.first ?? ""
        let lastName = nameComponents.count > 1 ? nameComponents.dropFirst().joined(separator: " ") : ""
        
        struct GoogleSignInRequest: Codable {
            let idToken: String
            let accessToken: String?
            let email: String?
            let firstName: String?
            let lastName: String?
            let personalDataConsent: Bool
            let personalDataConsentText: String
        }
        
        struct GoogleSignInResponse: Codable {
            let accessToken: String
            let refreshToken: String
            let user: User
            let mustChangePassword: Bool?
        }
        
        do {
            let request = GoogleSignInRequest(
                idToken: idToken,
                accessToken: accessToken,
                email: email,
                firstName: firstName.isEmpty ? nil : firstName,
                lastName: lastName.isEmpty ? nil : lastName,
                personalDataConsent: personalDataConsent,
                personalDataConsentText: personalDataConsentText
            )
            
            let response: GoogleSignInResponse = try await NetworkService.shared.request(
                endpoint: "/api/auth/google",
                method: .post,
                body: request
            )
            
            // Save tokens
            NetworkService.shared.saveAuthTokens(
                accessToken: response.accessToken,
                refreshToken: response.refreshToken
            )
            
            currentUser = response.user
            isAuthenticated = true
            requiresPasswordReset =
                response.mustChangePassword == true
                || response.user.mustChangePassword == true
            saveSession()
            isLoading = false
        } catch {
            isLoading = false
            if let networkError = error as? NetworkError {
                switch networkError {
                case .serverError(401), .serverErrorWithDetail(401, _):
                    throw AuthError.invalidCredentials
                case .serverError(404), .serverErrorWithDetail(404, _):
                    throw AuthError.backendUnavailable
                case .networkUnavailable:
                    throw AuthError.noInternetConnection
                default:
                    throw AuthError.unknown(error)
                }
            } else {
                throw AuthError.unknown(error)
            }
        }
    }
    
}
