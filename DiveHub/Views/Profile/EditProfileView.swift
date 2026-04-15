//
//  EditProfileView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import UIKit

struct EditProfileView: View {
    private struct DeleteAccountRequest: Codable {
        let confirmation: String
        let currentPassword: String?
    }

    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @Environment(\.dismiss) var dismiss
    @State private var firstName: String = ""
    @State private var lastName: String = ""
    @State private var email: String = ""
    @State private var phoneNumber: String = ""
    @State private var bio: String = ""
    @State private var showImagePicker = false
    @State private var selectedImage: UIImage?
    @State private var showDeleteReauthSheet = false
    @State private var deleteConfirmationInput = ""
    @State private var deleteCurrentPassword = ""
    @State private var isSaving = false
    @State private var isDeleting = false
    @State private var errorMessage: String?

    var body: some View {
        Form {
            Section {
                HStack {
                    Spacer()
                    Button(action: { showImagePicker = true }) {
                        if let image = selectedImage {
                            Image(uiImage: image)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } else if let user = authService.currentUser, let avatarURL = user.avatarURL {
                            AsyncImage(url: URL(string: avatarURL.hasPrefix("/") && !avatarURL.hasPrefix("http") ? NetworkService.shared.baseURL + avatarURL : avatarURL)) { image in
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Image(systemName: "person.circle.fill")
                                    .font(.system(size: 80))
                                    .foregroundColor(.secondary)
                            }
                        } else {
                            Image(systemName: "person.circle.fill")
                                .font(.system(size: 80))
                                .foregroundColor(.secondary)
                        }
                    }
                    .frame(width: 100, height: 100)
                    .clipShape(Circle())
                    .overlay(
                        Circle()
                            .stroke(Color.divePrimary, lineWidth: 2)
                    )
                    .accessibilityLabel(localizationService.localizedString("editProfileChangePhotoHint", table: "common"))
                    Spacer()
                }
                .padding(.vertical)
            } footer: {
                Text(localizationService.localizedString("editProfileChangePhotoHint", table: "common"))
                    .font(.caption)
            }

            Section {
                TextField(localizationService.localizedString("editProfileFirstName", table: "common"), text: $firstName)
                TextField(localizationService.localizedString("editProfileLastName", table: "common"), text: $lastName)
                TextField(localizationService.localizedString("email", table: "common"), text: $email)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField(localizationService.localizedString("editProfilePhoneNumber", table: "common"), text: $phoneNumber)
                    .keyboardType(.phonePad)
            } header: {
                Text(localizationService.localizedString("editProfileSectionPersonal", table: "common"))
            }

            Section {
                TextEditor(text: $bio)
                    .frame(height: 100)
            } header: {
                Text(localizationService.localizedString("editProfileSectionAbout", table: "common"))
            }

            if let error = errorMessage {
                Section {
                    Text(error)
                        .foregroundColor(.red)
                        .font(.caption)
                }
            }

            Section {
                Button(role: .destructive, action: {
                    deleteConfirmationInput = ""
                    deleteCurrentPassword = ""
                    showDeleteReauthSheet = true
                }) {
                    Text(localizationService.localizedString("editProfileDeleteAccount", table: "common"))
                }
            }
        }
        .navigationTitle(localizationService.localizedString("editProfile", table: "common"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(localizationService.localizedString("cancel", table: "common")) {
                    dismiss()
                }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(localizationService.localizedString("save", table: "common")) {
                    Task {
                        await saveProfile()
                    }
                }
                .disabled(isSaving)
            }
        }
        .sheet(isPresented: $showImagePicker) {
            ImagePicker(selectedImage: $selectedImage)
        }
        .onAppear {
            loadProfile()
        }
        .sheet(isPresented: $showDeleteReauthSheet) {
            NavigationStack {
                Form {
                    Section {
                        Text(localizationService.localizedString("editProfileDeleteConfirmMessage", table: "common"))
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }

                    Section {
                        TextField("ui_profile_type_delete".localized, text: $deleteConfirmationInput)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                    } header: {
                        Text("ui_booking_confirmation".localized)
                    }

                    Section {
                        SecureField("ui_profile_current_password_optional_if_recently_signed_in".localized, text: $deleteCurrentPassword)
                    } header: {
                        Text("ui_profile_re_authentication".localized)
                    } footer: {
                        Text("ui_profile_for_security_account_deletion_requires_recent_sign_in_or".localized)
                    }

                    Section {
                        Button(role: .destructive) {
                            Task {
                                await deleteAccount(currentPassword: deleteCurrentPassword)
                            }
                        } label: {
                            if isDeleting {
                                ProgressView()
                                    .frame(maxWidth: .infinity, alignment: .center)
                            } else {
                                Text(localizationService.localizedString("delete", table: "common"))
                                    .frame(maxWidth: .infinity, alignment: .center)
                            }
                        }
                        .disabled(isDeleting || deleteConfirmationInput.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() != "DELETE")
                    }
                }
                .navigationTitle(localizationService.localizedString("editProfileDeleteConfirmTitle", table: "common"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button(localizationService.localizedString("cancel", table: "common")) {
                            showDeleteReauthSheet = false
                        }
                    }
                }
            }
        }
    }

    private func loadProfile() {
        if let user = authService.currentUser {
            firstName = user.firstName ?? ""
            lastName = user.lastName ?? ""
            email = user.email
            phoneNumber = user.phoneNumber ?? ""
            bio = user.bio ?? ""
        }
    }

    /// Понятные сообщения вместо сырого `localizedDescription` / HTTP.
    private func localizedProfileError(_ error: Error, isDelete: Bool) -> String {
        let L = localizationService
        if let ne = error as? NetworkError {
            switch ne {
            case .serverError(let code):
                switch code {
                case 401:
                    return L.localizedString("pleaseSignIn", table: "errors")
                case 403:
                    return L.localizedString("editProfileNoPermission", table: "errors")
                case 409:
                    return L.localizedString("emailAlreadyExists", table: "errors")
                case 429:
                    return L.localizedString("tooManyRequests", table: "errors")
                case 500...599:
                    return L.localizedString("serverError", table: "errors")
                default:
                    return isDelete
                        ? L.localizedString("editProfileDeleteFailed", table: "errors")
                        : L.localizedString("editProfileSaveFailed", table: "errors")
                }
            case .serverErrorWithDetail(let code, let detail):
                let d = detail.lowercased()
                if d.contains("email") && (d.contains("already") || d.contains("exist") || d.contains("taken")) {
                    return L.localizedString("emailAlreadyExists", table: "errors")
                }
                if d.contains("unauthorized") || code == 401 {
                    return L.localizedString("pleaseSignIn", table: "errors")
                }
                if code == 403 {
                    return L.localizedString("editProfileNoPermission", table: "errors")
                }
                if code == 409 {
                    return L.localizedString("emailAlreadyExists", table: "errors")
                }
                return ne.localizedDescription
            case .networkUnavailable, .invalidURL, .noData, .decodingError:
                return ne.localizedDescription
            case .unknown(let underlying):
                if NetworkError.isTransportLikelyNoInternet(underlying) {
                    return L.localizedString("noInternetConnection", table: "errors")
                }
                return isDelete
                    ? L.localizedString("editProfileDeleteFailed", table: "errors")
                    : L.localizedString("editProfileSaveFailed", table: "errors")
            case .visionModuleHTTPError:
                return L.localizedString("serverError", table: "errors")
            }
        }
        if NetworkError.isTransportLikelyNoInternet(error) {
            return localizationService.localizedString("noInternetConnection", table: "errors")
        }
        return isDelete
            ? localizationService.localizedString("editProfileDeleteFailed", table: "errors")
            : localizationService.localizedString("editProfileSaveFailed", table: "errors")
    }

    private func saveProfile() async {
        guard var user = authService.currentUser else {
            errorMessage = localizationService.localizedString("pleaseSignIn", table: "errors")
            return
        }

        isSaving = true
        errorMessage = nil

        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedEmail.isEmpty, trimmedEmail.contains("@") else {
            errorMessage = localizationService.localizedString("invalidEmail", table: "errors")
            isSaving = false
            return
        }

        if let image = selectedImage,
           let imageData = image.jpegData(compressionQuality: 0.8) {
            do {
                let avatarUrl = try await NetworkService.shared.uploadProfileImage(imageData: imageData)
                user.avatarURL = avatarUrl
            } catch {
                // Не блокируем сохранение остальных полей, если загрузка фото не удалась.
            }
        }

        let fn = firstName.trimmingCharacters(in: .whitespacesAndNewlines)
        let ln = lastName.trimmingCharacters(in: .whitespacesAndNewlines)
        let phone = phoneNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let bioTrimmed = bio.trimmingCharacters(in: .whitespacesAndNewlines)

        let patch = AuthMePatchBody(
            firstName: fn.isEmpty ? nil : fn,
            lastName: ln.isEmpty ? nil : ln,
            phone: phone.isEmpty ? nil : phone,
            bio: bioTrimmed.isEmpty ? nil : bioTrimmed,
            language: nil,
            avatarUrl: user.avatarURL,
            countryCode: nil,
            diverProfile: nil,
            email: trimmedEmail
        )

        do {
            var updated = try await authService.patchAuthenticatedProfile(patch)
            if let avatarURL = updated.avatarURL, avatarURL.hasPrefix("/") && !avatarURL.hasPrefix("http") {
                updated.avatarURL = NetworkService.shared.baseURL + avatarURL
            }
            authService.updateUser(updated)
            dismiss()
        } catch let ae as AuthError {
            switch ae {
            case .unknown(let wrapped):
                errorMessage = localizedProfileError(wrapped, isDelete: false)
            default:
                errorMessage = ae.errorDescription
            }
        } catch {
            errorMessage = localizedProfileError(error, isDelete: false)
        }

        isSaving = false
    }

    private func deleteAccount(currentPassword: String?) async {
        isDeleting = true
        defer { isDeleting = false }
        do {
            let trimmedPassword = currentPassword?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            _ = try await NetworkService.shared.request(
                endpoint: "/api/users/me",
                method: .delete,
                body: DeleteAccountRequest(
                    confirmation: "DELETE",
                    currentPassword: trimmedPassword.isEmpty ? nil : trimmedPassword
                ),
                headers: [
                    "X-Account-Delete-Confirm": "true"
                ]
            ) as EmptyResponse

            authService.signOut()
            showDeleteReauthSheet = false
            dismiss()
        } catch {
            errorMessage = localizedProfileError(error, isDelete: true)
        }
    }
}

#Preview {
    NavigationStack {
        EditProfileView()
    }
}
