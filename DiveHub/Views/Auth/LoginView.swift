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

                // OAuth: галка согласия только на экране регистрации; для API при входе уходит тот же юридический текст.
                VStack(spacing: 12) {
                    Button(action: { Task { await signInWithAppleTapped() } }) {
                        HStack(spacing: 10) {
                            Image(systemName: "applelogo")
                                .font(.title3)
                            Text(localizationService.localizedString("signInWithApple", table: "auth"))
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .foregroundColor(.white)
                        .background(Color.black)
                        .cornerRadius(12)
                    }
                    .buttonStyle(.plain)
                    .disabled(authService.isLoading)

                    Button(action: signInWithGoogle) {
                        GoogleSignInBrandButtonLabel(
                            title: localizationService.localizedString("signInWithGoogle", table: "auth")
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
    
    @MainActor
    private func signInWithAppleTapped() async {
        errorMessage = nil
        do {
            let authorization = try await OAuthService.shared.signInWithApple()
            try await authService.signInWithApple(
                authorization: authorization,
                personalDataConsent: true,
                personalDataConsentText: ConsentTexts.registrationConsentText()
            )
        } catch {
            if let appleErr = error as? ASAuthorizationError {
                if appleErr.code == .canceled { return }
                errorMessage = userFriendlyAppleAuthError(appleErr)
            } else if let authErr = error as? AuthError {
                errorMessage = authErr.errorDescription
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func signInWithGoogle() {
        errorMessage = nil
        Task { @MainActor in
            do {
                try await GoogleSignInCoordinator.signInAndAuthenticate(
                    authService: authService,
                    personalDataConsent: true,
                    personalDataConsentText: ConsentTexts.registrationConsentText()
                )
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
    @State private var showPassword = false
    @State private var personalDataConsentAccepted = false
    @State private var errorMessage: String?
    @State private var emailFieldError: String?
    @State private var passwordFieldError: String?

    private var emailTrimmed: String { email.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var passwordTrimmed: String { password.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var passwordStrengthLabel: String {
        let p = passwordTrimmed
        if p.count < 8 { return localizationService.localizedString("passwordStrengthWeak", table: "onboarding") }
        let hasLetter = p.rangeOfCharacter(from: .letters) != nil
        let hasNumber = p.rangeOfCharacter(from: .decimalDigits) != nil
        if !hasLetter || !hasNumber { return localizationService.localizedString("passwordStrengthWeak", table: "onboarding") }
        if p.count >= 12 { return localizationService.localizedString("passwordStrengthStrong", table: "onboarding") }
        return localizationService.localizedString("passwordStrengthOk", table: "onboarding")
    }

    private var passwordStrengthColor: Color {
        let p = passwordTrimmed
        if p.count < 8 { return .red.opacity(0.8) }
        let hasLetter = p.rangeOfCharacter(from: .letters) != nil
        let hasNumber = p.rangeOfCharacter(from: .decimalDigits) != nil
        if !hasLetter || !hasNumber { return .orange }
        if p.count >= 12 { return .green }
        return .yellow.opacity(0.9)
    }

    private var isCreateButtonDisabled: Bool {
        if authService.isLoading || !personalDataConsentAccepted { return true }
        if emailTrimmed.isEmpty || passwordTrimmed.isEmpty { return true }
        let emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        let emailPredicate = NSPredicate(format: "SELF MATCHES %@", emailRegex)
        guard emailPredicate.evaluate(with: emailTrimmed) else { return true }
        guard passwordTrimmed.count >= 8 else { return true }
        let hasLetter = passwordTrimmed.rangeOfCharacter(from: .letters) != nil
        let hasNumber = passwordTrimmed.rangeOfCharacter(from: .decimalDigits) != nil
        return !(hasLetter && hasNumber)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text(localizationService.localizedString("registrationTitle", table: "onboarding"))
                        .font(.largeTitle.bold())
                    Text(localizationService.localizedString("registrationSubtitle", table: "onboarding"))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    VStack(spacing: 12) {
                        Button(action: { Task { await signUpWithAppleTapped() } }) {
                            HStack(spacing: 10) {
                                Image(systemName: "applelogo")
                                    .font(.title3)
                                Text(localizationService.localizedString("continueWithApple", table: "auth"))
                                    .fontWeight(.semibold)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: 52)
                            .foregroundStyle(.white)
                            .background(Color.black)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                        .disabled(authService.isLoading || !personalDataConsentAccepted)

                        Button(action: signUpWithGoogle) {
                            GoogleSignInBrandButtonLabel(
                                title: localizationService.localizedString("continueWithGoogle", table: "auth")
                            )
                        }
                        .buttonStyle(.plain)
                        .disabled(authService.isLoading || !personalDataConsentAccepted)
                    }

                    HStack {
                        Rectangle().fill(Color.gray.opacity(0.3)).frame(height: 1)
                        Text(localizationService.localizedString("or", table: "auth"))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Rectangle().fill(Color.gray.opacity(0.3)).frame(height: 1)
                    }
                    .padding(.vertical, 4)

                    VStack(alignment: .leading, spacing: 6) {
                        Text(localizationService.localizedString("emailLabel", table: "onboarding"))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        TextField("ui_auth_name_example_com".localized, text: $email)
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .padding(12)
                            .background(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.35)))
                        if let e = emailFieldError {
                            Text(e).font(.caption).foregroundStyle(.red)
                        }
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text(localizationService.localizedString("password", table: "auth"))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        HStack {
                            Group {
                                if showPassword {
                                    TextField("", text: $password)
                                        .textContentType(.newPassword)
                                } else {
                                    SecureField("", text: $password)
                                        .textContentType(.newPassword)
                                }
                            }
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            Button {
                                showPassword.toggle()
                            } label: {
                                Image(systemName: showPassword ? "eye.slash" : "eye")
                                    .foregroundStyle(.secondary)
                            }
                            .accessibilityLabel(showPassword ? localizationService.localizedString("passwordHide", table: "onboarding") : localizationService.localizedString("passwordShow", table: "onboarding"))
                        }
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.35)))
                        HStack {
                            Text(localizationService.localizedString("passwordStrength", table: "onboarding"))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            Text(passwordStrengthLabel)
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(passwordStrengthColor)
                        }
                        if let e = passwordFieldError {
                            Text(e).font(.caption).foregroundStyle(.red)
                        }
                    }

                    consentRow

                    if let error = errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }

                    Button(action: signUp) {
                        Group {
                            if authService.isLoading {
                                ProgressView().tint(.white)
                            } else {
                                Text(localizationService.localizedString("createAccount", table: "auth"))
                                    .font(.headline)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 52)
                        .background(isCreateButtonDisabled ? Color.gray.opacity(0.45) : Color.divePrimary)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .disabled(isCreateButtonDisabled)

                    HStack(spacing: 4) {
                        Text(localizationService.localizedString("alreadyHaveAccount", table: "onboarding"))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        Button(localizationService.localizedString("signIn", table: "auth")) { dismiss() }
                            .font(.footnote.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, 8)
                }
                .padding()
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(localizationService.localizedString("signUp", table: "auth"))
                        .font(.headline)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizationService.localizedString("cancel", table: "common")) { dismiss() }
                }
            }
        }
    }

    private var consentRow: some View {
        HStack(alignment: .top, spacing: 10) {
            Button {
                personalDataConsentAccepted.toggle()
            } label: {
                Image(systemName: personalDataConsentAccepted ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(personalDataConsentAccepted ? Color.divePrimary : .secondary)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 4) {
                if localizationService.currentLanguage == .english {
                    Text(englishConsentAttributed)
                        .font(.footnote)
                } else {
                    Text(localizationService.localizedString("consentCheckboxShort", table: "onboarding"))
                        .font(.footnote)
                    HStack(spacing: 10) {
                        Link(
                            localizationService.localizedString("privacyPolicyLinkTitle", table: "auth"),
                            destination: ConsentTexts.privacyPolicyURL
                        )
                        Link(
                            localizationService.localizedString("userAgreementLinkTitle", table: "auth"),
                            destination: ConsentTexts.userAgreementURL
                        )
                    }
                    .font(.caption)
                }
            }
        }
    }

    private var englishConsentAttributed: AttributedString {
        var a = AttributedString("I agree to the ")
        var p = AttributedString("Privacy Policy")
        p.link = ConsentTexts.privacyPolicyURL
        let mid = AttributedString(" and ")
        var t = AttributedString("Terms of Use")
        t.link = ConsentTexts.userAgreementURL
        a.append(p)
        a.append(mid)
        a.append(t)
        return a
    }

    private func signUp() {
        emailFieldError = nil
        passwordFieldError = nil
        let emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        let emailPredicate = NSPredicate(format: "SELF MATCHES %@", emailRegex)
        guard emailPredicate.evaluate(with: emailTrimmed) else {
            emailFieldError = localizationService.localizedString("pleaseEnterValidEmail", table: "auth")
            errorMessage = nil
            return
        }
        guard passwordTrimmed.count >= 8 else {
            passwordFieldError = localizationService.localizedString("passwordMinLength", table: "auth")
            return
        }
        let hasLetter = passwordTrimmed.rangeOfCharacter(from: .letters) != nil
        let hasNumber = passwordTrimmed.rangeOfCharacter(from: .decimalDigits) != nil
        guard hasLetter && hasNumber else {
            passwordFieldError = localizationService.localizedString("passwordLetterNumberRequired", table: "onboarding")
            return
        }
        guard personalDataConsentAccepted else {
            errorMessage = localizationService.localizedString("authConsentRequired", table: "auth")
            return
        }
        errorMessage = nil
        Task {
            do {
                try await authService.signUp(
                    email: emailTrimmed,
                    password: passwordTrimmed,
                    personalDataConsent: true,
                    personalDataConsentText: ConsentTexts.registrationConsentText()
                )
                dismiss()
            } catch {
                if let authError = error as? AuthError {
                    if case .emailAlreadyExists = authError {
                        emailFieldError = localizationService.localizedString("emailAlreadyExists", table: "errors")
                    }
                    errorMessage = authError.errorDescription
                } else {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    @MainActor
    private func signUpWithAppleTapped() async {
        guard personalDataConsentAccepted else {
            errorMessage = localizationService.localizedString("authConsentRequired", table: "auth")
            return
        }
        errorMessage = nil
        do {
            let authorization = try await OAuthService.shared.signInWithApple()
            try await authService.signInWithApple(
                authorization: authorization,
                personalDataConsent: true,
                personalDataConsentText: ConsentTexts.registrationConsentText()
            )
            dismiss()
        } catch {
            if let appleErr = error as? ASAuthorizationError {
                if appleErr.code == .canceled { return }
                errorMessage = userFriendlyAppleAuthError(appleErr)
            } else if let authErr = error as? AuthError {
                errorMessage = authErr.errorDescription
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func signUpWithGoogle() {
        guard personalDataConsentAccepted else {
            errorMessage = localizationService.localizedString("authConsentRequired", table: "auth")
            return
        }
        errorMessage = nil
        Task { @MainActor in
            do {
                try await GoogleSignInCoordinator.signInAndAuthenticate(
                    authService: authService,
                    personalDataConsent: true,
                    personalDataConsentText: ConsentTexts.registrationConsentText()
                )
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
