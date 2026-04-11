//
//  ForgotPasswordView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Foundation

enum PasswordResetStep {
    case email
    case verificationCode
    case newPassword
}

struct ForgotPasswordView: View {
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @Environment(\.dismiss) var dismiss
    
    @State private var currentStep: PasswordResetStep = .email
    @State private var email = ""
    @State private var verificationCode = ""
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var errorMessage: String?
    @State private var successMessage: String?
    @State private var countdown: Int = 0
    @State private var timer: Timer?
    
    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                // Progress indicator
                HStack(spacing: 8) {
                    ProgressCircle(isActive: currentStep == .email, isCompleted: currentStep != .email)
                    ProgressLine(isActive: currentStep != .email)
                    ProgressCircle(isActive: currentStep == .verificationCode, isCompleted: currentStep == .newPassword)
                    ProgressLine(isActive: currentStep == .newPassword)
                    ProgressCircle(isActive: currentStep == .newPassword, isCompleted: false)
                }
                .padding(.horizontal)
                .padding(.top, 20)
                
                // Step content
                VStack(spacing: 20) {
                    switch currentStep {
                    case .email:
                        emailStepView
                    case .verificationCode:
                        verificationCodeStepView
                    case .newPassword:
                        newPasswordStepView
                    }
                }
                .padding(.horizontal)
                
                Spacer()
            }
            .navigationTitle(localizationService.localizedString("forgotPassword", table: "auth"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        dismiss()
                    }
                }
            }
        }
        .onDisappear {
            timer?.invalidate()
        }
    }
    
    // MARK: - Email Step
    
    private var emailStepView: some View {
        VStack(spacing: 16) {
            Image(systemName: "envelope")
                .font(.system(size: 50))
                .foregroundColor(.divePrimary)
            
            Text(localizationService.localizedString("enterYourEmail", table: "auth"))
                .font(.title2)
                .fontWeight(.semibold)
                .multilineTextAlignment(.center)
            
            Text(localizationService.localizedString("weWillSendCode", table: "auth"))
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            TextField(localizationService.localizedString("email", table: "common"), text: $email)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .keyboardType(.emailAddress)
                .autocapitalization(.none)
                .disabled(authService.isLoading)
            
            if let error = errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
            }
            
            if let success = successMessage {
                Text(success)
                    .font(.caption)
                    .foregroundColor(.green)
            }
            
            Button(action: sendVerificationCode) {
                if authService.isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                } else {
                    Text(localizationService.localizedString("sendCode", table: "auth"))
                        .font(.headline)
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.divePrimary)
            .foregroundColor(.white)
            .cornerRadius(12)
            .disabled(authService.isLoading || email.isEmpty)
        }
    }
    
    // MARK: - Verification Code Step
    
    private var verificationCodeStepView: some View {
        VStack(spacing: 16) {
            Image(systemName: "key")
                .font(.system(size: 50))
                .foregroundColor(.divePrimary)
            
            Text(localizationService.localizedString("enterVerificationCode", table: "auth"))
                .font(.title2)
                .fontWeight(.semibold)
                .multilineTextAlignment(.center)
            
            Text("\(localizationService.localizedString("weSentCodeTo", table: "auth")) \(email)")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            TextField(localizationService.localizedString("verificationCode", table: "auth"), text: $verificationCode)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .keyboardType(.numberPad)
                .disabled(authService.isLoading)
            
            if countdown > 0 {
                Text("\(localizationService.localizedString("resendCodeIn", table: "auth")) \(countdown) \(localizationService.localizedString("seconds", table: "auth"))")
                    .font(.caption)
                    .foregroundColor(.secondary)
            } else {
                Button(localizationService.localizedString("resendCode", table: "auth")) {
                    sendVerificationCode()
                }
                .font(.caption)
                .foregroundColor(.divePrimary)
            }
            
            if let error = errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
            }
            
            Button(action: verifyCode) {
                if authService.isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                } else {
                    Text(localizationService.localizedString("verifyCode", table: "auth"))
                        .font(.headline)
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.divePrimary)
            .foregroundColor(.white)
            .cornerRadius(12)
            .disabled(authService.isLoading || verificationCode.isEmpty)
            
            Button(localizationService.localizedString("back", table: "common")) {
                currentStep = .email
                errorMessage = nil
                verificationCode = ""
            }
            .foregroundColor(.divePrimary)
        }
    }
    
    // MARK: - New Password Step
    
    private var newPasswordStepView: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock.rotation")
                .font(.system(size: 50))
                .foregroundColor(.divePrimary)
            
            Text(localizationService.localizedString("createNewPassword", table: "auth"))
                .font(.title2)
                .fontWeight(.semibold)
                .multilineTextAlignment(.center)
            
            SecureField(localizationService.localizedString("newPassword", table: "auth"), text: $newPassword)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .disabled(authService.isLoading)
            
            SecureField(localizationService.localizedString("confirmPassword", table: "auth"), text: $confirmPassword)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .disabled(authService.isLoading)
            
            if let error = errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
            }
            
            if let success = successMessage {
                Text(success)
                    .font(.caption)
                    .foregroundColor(.green)
            }
            
            Button(action: resetPassword) {
                if authService.isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                } else {
                    Text(localizationService.localizedString("resetPassword", table: "auth"))
                        .font(.headline)
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.divePrimary)
            .foregroundColor(.white)
            .cornerRadius(12)
            .disabled(authService.isLoading || newPassword.isEmpty || confirmPassword.isEmpty)
            
            Button(localizationService.localizedString("back", table: "common")) {
                currentStep = .verificationCode
                errorMessage = nil
                newPassword = ""
                confirmPassword = ""
            }
            .foregroundColor(.divePrimary)
        }
    }
    
    // MARK: - Actions
    
    private func sendVerificationCode() {
        guard !email.isEmpty else {
            errorMessage = localizationService.localizedString("pleaseEnterEmail", table: "auth")
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
        successMessage = nil
        
        Task {
            do {
                try await authService.requestPasswordReset(email: email)
                await MainActor.run {
                    successMessage = localizationService.localizedString("verificationCodeSent", table: "auth")
                    currentStep = .verificationCode
                    startCountdown()
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
    }
    
    private func verifyCode() {
        guard !verificationCode.isEmpty else {
            errorMessage = localizationService.localizedString("pleaseEnterVerificationCode", table: "auth")
            return
        }
        
        // Validate code format (should be 6 digits)
        guard verificationCode.count == 6 && verificationCode.allSatisfy({ $0.isNumber }) else {
            errorMessage = localizationService.localizedString("invalidVerificationCode", table: "errors")
            return
        }
        
        errorMessage = nil
        
        Task {
            do {
                try await authService.verifyResetCode(email: email, code: verificationCode)
                await MainActor.run {
                    currentStep = .newPassword
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
    }
    
    private func resetPassword() {
        guard !newPassword.isEmpty, !confirmPassword.isEmpty else {
            errorMessage = localizationService.localizedString("pleaseFillAllFields", table: "auth")
            return
        }
        
        // Enhanced password validation
        guard newPassword.count >= 8 else {
            errorMessage = localizationService.localizedString("passwordMinLength", table: "auth")
            return
        }
        
        // Check for at least one letter and one number
        let hasLetter = newPassword.rangeOfCharacter(from: .letters) != nil
        let hasNumber = newPassword.rangeOfCharacter(from: .decimalDigits) != nil
        guard hasLetter && hasNumber else {
            errorMessage = localizationService.localizedString("weakPassword", table: "errors")
            return
        }
        
        guard newPassword == confirmPassword else {
            errorMessage = localizationService.localizedString("passwordsDoNotMatch", table: "auth")
            return
        }
        
        errorMessage = nil
        successMessage = nil
        
        Task {
            do {
                try await authService.resetPassword(email: email, code: verificationCode, newPassword: newPassword)
                await MainActor.run {
                    successMessage = localizationService.localizedString("passwordResetSuccess", table: "auth")
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        dismiss()
                    }
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
    }
    
    private func startCountdown() {
        countdown = 60
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if countdown > 0 {
                countdown -= 1
            } else {
                timer?.invalidate()
            }
        }
    }
}

// MARK: - Progress Components

struct ProgressCircle: View {
    let isActive: Bool
    let isCompleted: Bool
    
    var body: some View {
        Circle()
            .fill(isActive || isCompleted ? Color.divePrimary : Color.gray.opacity(0.3))
            .frame(width: 12, height: 12)
            .overlay(
                Circle()
                    .stroke(Color.divePrimary, lineWidth: 2)
                    .opacity(isActive ? 1 : 0)
            )
    }
}

struct ProgressLine: View {
    let isActive: Bool
    
    var body: some View {
        Rectangle()
            .fill(isActive ? Color.divePrimary : Color.gray.opacity(0.3))
            .frame(height: 2)
    }
}

#Preview {
    ForgotPasswordView()
}
