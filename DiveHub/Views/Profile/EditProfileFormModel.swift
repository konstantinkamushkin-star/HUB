//
//  EditProfileFormModel.swift
//  DiveHub
//

import Combine
import SwiftUI
import UIKit

@MainActor
final class EditProfileFormModel: ObservableObject {
    private struct DeleteAccountRequest: Codable {
        let confirmation: String
        let currentPassword: String?
    }

    @Published var firstName: String = ""
    @Published var lastName: String = ""
    @Published var email: String = ""
    @Published var phoneNumber: String = ""
    @Published var bio: String = ""
    @Published var showImagePicker = false
    @Published var selectedImage: UIImage?
    @Published var showDeleteReauthSheet = false
    @Published var deleteConfirmationInput = ""
    @Published var deleteCurrentPassword = ""
    @Published var isSaving = false
    @Published var isDeleting = false
    @Published var errorMessage: String?

    private let authService = AuthenticationService.shared
    private let localizationService = LocalizationService.shared

    func loadProfile() {
        guard let user = authService.currentUser else { return }
        firstName = user.firstName ?? ""
        lastName = user.lastName ?? ""
        email = user.email
        phoneNumber = user.phoneNumber ?? ""
        bio = user.bio ?? ""
    }

    func saveProfile(onSuccess: (() -> Void)? = nil) async {
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
                // Do not block saving other fields if photo upload fails.
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
            onSuccess?()
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

    func deleteAccount(currentPassword: String?, onSuccess: (() -> Void)? = nil) async {
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
            onSuccess?()
        } catch {
            errorMessage = localizedProfileError(error, isDelete: true)
        }
    }

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
}

struct EditProfileFormSections: View {
    @ObservedObject var model: EditProfileFormModel
    /// Called after successful account deletion (e.g. dismiss the editor).
    var onAccountDeleted: (() -> Void)? = nil
    private let localizationService = LocalizationService.shared

    var body: some View {
        Group {
            Section {
                HStack {
                    Spacer()
                    Button(action: { model.showImagePicker = true }) {
                        if let image = model.selectedImage {
                            Image(uiImage: image)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } else if let user = AuthenticationService.shared.currentUser, let avatarURL = user.avatarURL {
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
                TextField(localizationService.localizedString("editProfileFirstName", table: "common"), text: $model.firstName)
                TextField(localizationService.localizedString("editProfileLastName", table: "common"), text: $model.lastName)
                TextField(localizationService.localizedString("email", table: "common"), text: $model.email)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField(localizationService.localizedString("editProfilePhoneNumber", table: "common"), text: $model.phoneNumber)
                    .keyboardType(.phonePad)
            } header: {
                Text(localizationService.localizedString("editProfileSectionPersonal", table: "common"))
            }

            Section {
                TextEditor(text: $model.bio)
                    .frame(height: 100)
            } header: {
                Text(localizationService.localizedString("editProfileSectionAbout", table: "common"))
            }

            if let error = model.errorMessage {
                Section {
                    Text(error)
                        .foregroundColor(.red)
                        .font(.caption)
                }
            }

            Section {
                Button(role: .destructive, action: {
                    model.deleteConfirmationInput = ""
                    model.deleteCurrentPassword = ""
                    model.showDeleteReauthSheet = true
                }) {
                    Text(localizationService.localizedString("editProfileDeleteAccount", table: "common"))
                }
            }
        }
        .sheet(isPresented: $model.showImagePicker) {
            ImagePicker(selectedImage: $model.selectedImage)
        }
        .sheet(isPresented: $model.showDeleteReauthSheet) {
            NavigationStack {
                Form {
                    Section {
                        Text(localizationService.localizedString("editProfileDeleteConfirmMessage", table: "common"))
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }

                    Section {
                        TextField("ui_profile_type_delete".localized, text: $model.deleteConfirmationInput)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                    } header: {
                        Text("ui_booking_confirmation".localized)
                    }

                    Section {
                        SecureField("ui_profile_current_password_optional_if_recently_signed_in".localized, text: $model.deleteCurrentPassword)
                    } header: {
                        Text("ui_profile_re_authentication".localized)
                    } footer: {
                        Text("ui_profile_for_security_account_deletion_requires_recent_sign_in_or".localized)
                    }

                    Section {
                        Button(role: .destructive) {
                            Task {
                                await model.deleteAccount(currentPassword: model.deleteCurrentPassword, onSuccess: onAccountDeleted)
                            }
                        } label: {
                            if model.isDeleting {
                                ProgressView()
                                    .frame(maxWidth: .infinity, alignment: .center)
                            } else {
                                Text(localizationService.localizedString("delete", table: "common"))
                                    .frame(maxWidth: .infinity, alignment: .center)
                            }
                        }
                        .disabled(model.isDeleting || model.deleteConfirmationInput.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() != "DELETE")
                    }
                }
                .navigationTitle(localizationService.localizedString("editProfileDeleteConfirmTitle", table: "common"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button(localizationService.localizedString("cancel", table: "common")) {
                            model.showDeleteReauthSheet = false
                        }
                    }
                }
            }
        }
    }
}
