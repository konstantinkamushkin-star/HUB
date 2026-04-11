//
//  ForcePasswordChangeView.swift
//  DiveHub
//

import SwiftUI

/// Первый вход партнёра после письма с временным паролем — смена пароля до доступа к приложению.
struct ForcePasswordChangeView: View {
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared

    @State private var currentPassword = ""
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Image(systemName: "lock.rotation")
                    .font(.system(size: 48))
                    .foregroundColor(.divePrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 24)

                Text(localizationService.localizedString("forcePasswordChangeTitle", table: "auth"))
                    .font(.title2.bold())
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)

                Text(localizationService.localizedString("forcePasswordChangeSubtitle", table: "auth"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)

                VStack(alignment: .leading, spacing: 12) {
                    SecureField(
                        localizationService.localizedString("currentPasswordLabel", table: "auth"),
                        text: $currentPassword
                    )
                    .textFieldStyle(RoundedBorderTextFieldStyle())

                    SecureField(
                        localizationService.localizedString("newPassword", table: "auth"),
                        text: $newPassword
                    )
                    .textFieldStyle(RoundedBorderTextFieldStyle())

                    SecureField(
                        localizationService.localizedString("confirmPassword", table: "auth"),
                        text: $confirmPassword
                    )
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                }
                .padding(.top, 8)

                if let errorMessage {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundColor(.red)
                }

                Text(localizationService.localizedString("passwordPolicyLetterAndDigit", table: "auth"))
                    .font(.caption)
                    .foregroundColor(.secondary)

                Button(action: submit) {
                    if authService.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Text(localizationService.localizedString("saveNewPassword", table: "auth"))
                            .font(.headline)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.divePrimary)
                .foregroundColor(.white)
                .cornerRadius(12)
                .disabled(authService.isLoading || !canSubmit)

                Button(localizationService.localizedString("signOut", table: "common")) {
                    authService.signOut()
                }
                .frame(maxWidth: .infinity)
                .foregroundColor(.secondary)
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 24)
        }
    }

    private var canSubmit: Bool {
        !currentPassword.isEmpty && !newPassword.isEmpty && !confirmPassword.isEmpty
    }

    private func submit() {
        errorMessage = nil
        guard newPassword.count >= 8 else {
            errorMessage = localizationService.localizedString("passwordMinLength", table: "auth")
            return
        }
        let hasLetter = newPassword.range(of: "[a-zA-Z]", options: .regularExpression) != nil
        let hasDigit = newPassword.range(of: "\\d", options: .regularExpression) != nil
        guard hasLetter && hasDigit else {
            errorMessage = localizationService.localizedString("passwordPolicyLetterAndDigit", table: "auth")
            return
        }
        guard newPassword == confirmPassword else {
            errorMessage = localizationService.localizedString("passwordsDoNotMatch", table: "auth")
            return
        }

        Task {
            do {
                try await authService.changePasswordAndCompleteSetup(
                    currentPassword: currentPassword,
                    newPassword: newPassword
                )
            } catch let e as NetworkError {
                await MainActor.run {
                    errorMessage = e.errorDescription
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

#Preview {
    ForcePasswordChangeView()
}
