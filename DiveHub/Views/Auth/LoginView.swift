//
//  LoginView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import AuthenticationServices
import Foundation

struct LoginView: View {
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @State private var email = ""
    @State private var password = ""
    @State private var showSignUp = false
    @State private var showForgotPassword = false
    @State private var showDiveCenterRegistration = false
    @State private var errorMessage: String?
    
    var body: some View {
        VStack(spacing: 24) {
            // Logo/Header
            VStack(spacing: 8) {
                DiveHubLogoMark(color: .divePrimary)
                    .frame(width: 92, height: 74)
                Text(localizationService.localizedString("appName"))
                    .font(.largeTitle)
                    .fontWeight(.bold)
            }
            .padding(.top, 60)
            
            // Form
            VStack(spacing: 16) {
                TextField(localizationService.localizedString("email", table: "common"), text: $email)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                
                SecureField(localizationService.localizedString("password", table: "auth"), text: $password)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                
                // Forgot Password Link
                HStack {
                    Spacer()
                    Button(localizationService.localizedString("forgotPasswordQuestion", table: "auth")) {
                        showForgotPassword = true
                    }
                    .font(.caption)
                    .foregroundColor(.divePrimary)
                }
                
                if let error = errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                }
                
                Button(action: signIn) {
                    if authService.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Text(localizationService.localizedString("signIn", table: "auth"))
                            .font(.headline)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.divePrimary)
                .foregroundColor(.white)
                .cornerRadius(12)
                .disabled(authService.isLoading || email.isEmpty || password.isEmpty)
                
                // Divider
                HStack {
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(height: 1)
                    Text(localizationService.localizedString("or", table: "auth"))
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 8)
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(height: 1)
                }
                .padding(.vertical, 8)
                
                // OAuth Buttons
                VStack(spacing: 12) {
                    // Apple Sign In Button
                    SignInWithAppleButton(
                        onRequest: { request in
                            request.requestedScopes = [.fullName, .email]
                        },
                        onCompletion: { result in
                            handleAppleSignIn(result: result)
                        }
                    )
                    .signInWithAppleButtonStyle(.black)
                    .frame(height: 50)
                    .cornerRadius(12)
                    .disabled(authService.isLoading)
                    
                    // Google Sign In Button
                    Button(action: signInWithGoogle) {
                        GoogleSignInBrandButtonLabel(
                            title: localizationService.localizedString("continueWithGoogle", table: "auth")
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(authService.isLoading)
                }
            }
            .padding(.horizontal)

            Button {
                showDiveCenterRegistration = true
            } label: {
                Text(localizationService.localizedString("diveCenterPartnerRegistration", table: "auth"))
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.divePrimary.opacity(0.12))
                    .foregroundColor(.divePrimary)
                    .cornerRadius(12)
            }
            .padding(.horizontal)
            
            // Sign Up Link
            HStack {
                Text(localizationService.localizedString("dontHaveAccount", table: "auth"))
                Button(localizationService.localizedString("signUp", table: "auth")) {
                    showSignUp = true
                }
                .foregroundColor(.divePrimary)
            }
            
            Spacer()
        }
        .sheet(isPresented: $showSignUp) {
            SignUpView()
        }
        .sheet(isPresented: $showForgotPassword) {
            ForgotPasswordView()
        }
        .sheet(isPresented: $showDiveCenterRegistration) {
            DiveCenterRegistrationView()
        }
    }
    
    private func signIn() {
        // Validate input
        guard !email.isEmpty, !password.isEmpty else {
            errorMessage = localizationService.localizedString("pleaseEnterEmailAndPassword", table: "auth")
            return
        }
        
        // Enhanced email validation
        let emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        let emailPredicate = NSPredicate(format:"SELF MATCHES %@", emailRegex)
        guard email.contains("@") && emailPredicate.evaluate(with: email) else {
            errorMessage = localizationService.localizedString("pleaseEnterValidEmail", table: "auth")
            return
        }
        
        errorMessage = nil
        Task {
            do {
                try await authService.signIn(email: email, password: password)
            } catch {
                if let authError = error as? AuthError {
                    errorMessage = authError.errorDescription
                } else {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    private func handleAppleSignIn(result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            Task {
                do {
                    try await authService.signInWithApple(authorization: authorization)
                } catch {
                    await MainActor.run {
                        if let authError = error as? AuthError {
                            errorMessage = authError.errorDescription
                        } else {
                            errorMessage = error.localizedDescription
                        }
                    }
                }
            }
        case .failure(let error):
            // User cancelled or other error
            if let authError = error as? ASAuthorizationError {
                if authError.code != .canceled {
                    errorMessage = userFriendlyAppleAuthError(authError)
                }
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }
    
    private func signInWithGoogle() {
        errorMessage = nil
        Task { @MainActor in
            do {
                try await GoogleSignInCoordinator.signInAndAuthenticate(authService: authService)
            } catch {
                if GoogleSignInCoordinator.wasUserCanceled(error) {
                    return
                }
                if let authError = error as? AuthError {
                    errorMessage = authError.errorDescription
                } else {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func userFriendlyAppleAuthError(_ error: ASAuthorizationError) -> String {
        switch error.code {
        case .unknown:
            return "Не удалось выполнить вход через Apple (AuthorizationError 1000). Проверьте: 1) в таргете включен Sign in with Apple, 2) app Bundle ID зарегистрирован в Apple Developer, 3) для этого App ID включен Sign in with Apple, 4) устройство/симулятор авторизован в Apple ID."
        case .invalidResponse:
            return "Apple вернул некорректный ответ. Попробуйте снова."
        case .notHandled:
            return "Запрос Apple Sign In не был обработан. Проверьте конфигурацию Signing & Capabilities."
        case .failed:
            return "Вход через Apple не выполнен. Проверьте интернет и настройки Apple ID."
        default:
            return error.localizedDescription
        }
    }
}

struct SignUpView: View {
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @Environment(\.dismiss) var dismiss
    @State private var email = ""
    @State private var password = ""
    @State private var displayName = ""
    @State private var personalDataConsentAccepted = false
    @State private var errorMessage: String?
    
    private var isCreateButtonDisabled: Bool {
        let emailTrimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let passwordTrimmed = password.trimmingCharacters(in: .whitespacesAndNewlines)
        let displayNameTrimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        return authService.isLoading
            || emailTrimmed.isEmpty
            || passwordTrimmed.isEmpty
            || displayNameTrimmed.isEmpty
            || !personalDataConsentAccepted
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                Form {
                    Section {
                        TextField(localizationService.localizedString("displayName", table: "auth"), text: $displayName)
                        TextField(localizationService.localizedString("email", table: "common"), text: $email)
                            .keyboardType(.emailAddress)
                            .autocapitalization(.none)
                        SecureField(localizationService.localizedString("password", table: "auth"), text: $password)
                    }

                    Section("Согласие") {
                        Toggle(isOn: $personalDataConsentAccepted) {
                            Text("Подтверждаю ознакомление с Политикой конфиденциальности и Пользовательским соглашением DiveHub и принимаю их условия, включая обработку персональных данных.")
                        }
                        HStack(spacing: 16) {
                            Link("Политика конфиденциальности", destination: ConsentTexts.privacyPolicyURL)
                            Link("Пользовательское соглашение", destination: ConsentTexts.userAgreementURL)
                        }
                        .font(.caption)
                        Text("Документы открываются в браузере.")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    
                    if let error = errorMessage {
                        Section {
                            Text(error)
                                .foregroundColor(.red)
                        }
                    }
                    
                    Section {
                        Button(action: signUp) {
                            if authService.isLoading {
                                ProgressView()
                            } else {
                                Text(localizationService.localizedString("createAccount", table: "auth"))
                            }
                        }
                        .disabled(isCreateButtonDisabled)
                    }
                }
                
                // OAuth Sign Up Section
                VStack(spacing: 12) {
                    // Divider
                    HStack {
                        Rectangle()
                            .fill(Color.gray.opacity(0.3))
                            .frame(height: 1)
                        Text(localizationService.localizedString("or", table: "auth"))
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 8)
                        Rectangle()
                            .fill(Color.gray.opacity(0.3))
                            .frame(height: 1)
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                    
                    // OAuth Buttons
                    VStack(spacing: 12) {
                        // Apple Sign In Button
                        SignInWithAppleButton(
                            onRequest: { request in
                                request.requestedScopes = [.fullName, .email]
                            },
                            onCompletion: { result in
                                handleAppleSignUp(result: result)
                            }
                        )
                        .signInWithAppleButtonStyle(.black)
                        .frame(height: 50)
                        .cornerRadius(12)
                        .disabled(authService.isLoading)
                        .padding(.horizontal)
                        
                        // Google Sign In Button
                        Button(action: signUpWithGoogle) {
                            GoogleSignInBrandButtonLabel(
                                title: localizationService.localizedString("signUpWithGoogle", table: "auth")
                            )
                        }
                        .buttonStyle(.plain)
                        .disabled(authService.isLoading)
                        .padding(.horizontal)
                    }
                    .padding(.bottom, 16)
                }
                .background(Color(.systemGroupedBackground))
            }
            .navigationTitle(localizationService.localizedString("signUp", table: "auth"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        dismiss()
                    }
                }
            }
        }
    }
    
    private func signUp() {
        // Валидация - используем trimmed значения
        let emailTrimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let passwordTrimmed = password.trimmingCharacters(in: .whitespacesAndNewlines)
        let displayNameTrimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        
        guard !emailTrimmed.isEmpty, !passwordTrimmed.isEmpty, !displayNameTrimmed.isEmpty else {
            errorMessage = AuthError.emptyFields.errorDescription
            return
        }
        
        // Enhanced email validation
        let emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        let emailPredicate = NSPredicate(format:"SELF MATCHES %@", emailRegex)
        guard emailTrimmed.contains("@") && emailPredicate.evaluate(with: emailTrimmed) else {
            errorMessage = AuthError.invalidEmail.errorDescription
            return
        }
        
        // Enhanced password validation
        guard passwordTrimmed.count >= 8 else {
            errorMessage = AuthError.weakPassword.errorDescription
            return
        }
        
        // Check for at least one letter and one number
        let hasLetter = passwordTrimmed.rangeOfCharacter(from: .letters) != nil
        let hasNumber = passwordTrimmed.rangeOfCharacter(from: .decimalDigits) != nil
        guard hasLetter && hasNumber else {
            errorMessage = AuthError.weakPassword.errorDescription
            return
        }
        
        // Validate display name (at least 2 characters)
        guard displayNameTrimmed.count >= 2 else {
            errorMessage = localizationService.localizedString("invalidEmail", table: "errors") // TODO: Add specific error for display name
            return
        }

        guard personalDataConsentAccepted else {
            errorMessage = "Для регистрации нужно согласие на обработку персональных данных."
            return
        }
        
        errorMessage = nil
        Task {
            do {
                try await authService.signUp(
                    email: emailTrimmed,
                    password: passwordTrimmed,
                    displayName: displayNameTrimmed,
                    personalDataConsent: true,
                    personalDataConsentText: ConsentTexts.registrationConsentText()
                )
                dismiss()
            } catch {
                if let authError = error as? AuthError {
                    errorMessage = authError.errorDescription
                } else {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    private func handleAppleSignUp(result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            Task {
                do {
                    try await authService.signInWithApple(authorization: authorization)
                    await MainActor.run {
                        dismiss()
                    }
                } catch {
                    await MainActor.run {
                        if let authError = error as? AuthError {
                            errorMessage = authError.errorDescription
                        } else {
                            errorMessage = error.localizedDescription
                        }
                    }
                }
            }
        case .failure(let error):
            // User cancelled or other error
            if let authError = error as? ASAuthorizationError {
                if authError.code != .canceled {
                    errorMessage = userFriendlyAppleAuthError(authError)
                }
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }
    
    private func signUpWithGoogle() {
        errorMessage = nil
        Task { @MainActor in
            do {
                try await GoogleSignInCoordinator.signInAndAuthenticate(authService: authService)
                dismiss()
            } catch {
                if GoogleSignInCoordinator.wasUserCanceled(error) {
                    return
                }
                if let authError = error as? AuthError {
                    errorMessage = authError.errorDescription
                } else {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func userFriendlyAppleAuthError(_ error: ASAuthorizationError) -> String {
        switch error.code {
        case .unknown:
            return "Не удалось выполнить вход через Apple (AuthorizationError 1000). Проверьте: 1) в таргете включен Sign in with Apple, 2) app Bundle ID зарегистрирован в Apple Developer, 3) для этого App ID включен Sign in with Apple, 4) устройство/симулятор авторизован в Apple ID."
        case .invalidResponse:
            return "Apple вернул некорректный ответ. Попробуйте снова."
        case .notHandled:
            return "Запрос Apple Sign In не был обработан. Проверьте конфигурацию Signing & Capabilities."
        case .failed:
            return "Вход через Apple не выполнен. Проверьте интернет и настройки Apple ID."
        default:
            return error.localizedDescription
        }
    }
}

#Preview {
    LoginView()
}
