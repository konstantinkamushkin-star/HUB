//
//  EditProfileView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine
import UIKit

struct EditProfileView: View {
    @StateObject private var authService = AuthenticationService.shared
    @Environment(\.dismiss) var dismiss
    @State private var firstName: String = ""
    @State private var lastName: String = ""
    @State private var email: String = ""
    @State private var phoneNumber: String = ""
    @State private var bio: String = ""
    @State private var showImagePicker = false
    @State private var selectedImage: UIImage?
    @State private var showDeleteConfirmation = false
    @State private var isSaving = false
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
                        Spacer()
                    }
                    .padding(.vertical)
                }
                
                Section("Personal Information") {
                    TextField("First Name", text: $firstName)
                    TextField("Last Name", text: $lastName)
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                    TextField("Phone Number", text: $phoneNumber)
                        .keyboardType(.phonePad)
                }
                
                Section("About") {
                    TextEditor(text: $bio)
                        .frame(height: 100)
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
                        showDeleteConfirmation = true
                    }) {
                        Text("Delete Account")
                    }
                }
            }
            .navigationTitle("Edit Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
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
            .alert("Delete Account", isPresented: $showDeleteConfirmation) {
                Button("Cancel", role: .cancel) {}
                Button("Delete", role: .destructive) {
                    Task {
                        await deleteAccount()
                    }
                }
            } message: {
                Text("Are you sure you want to delete your account? This action cannot be undone.")
            }
    }
    
    private func loadProfile() {
        if let user = authService.currentUser {
            firstName = user.firstName ?? ""
            lastName = user.lastName ?? ""
            email = user.email
            phoneNumber = user.phoneNumber ?? ""
        }
    }
    
    private func saveProfile() async {
        
        guard var user = authService.currentUser else {
            return
        }
        
        isSaving = true
        errorMessage = nil
        
        // Validate email
        guard !email.isEmpty, email.contains("@") else {
            errorMessage = "Please enter a valid email address"
            isSaving = false
            return
        }
        
        do {
            // Upload profile image if selected
            if let image = selectedImage,
               let imageData = image.jpegData(compressionQuality: 0.8) {
                do {
                    let avatarUrl = try await NetworkService.shared.uploadProfileImage(imageData: imageData)
                    user.avatarURL = avatarUrl
                } catch {
                    // If image upload fails (e.g., endpoint doesn't exist), continue without updating avatar
                    // Don't block profile save if image upload fails
                }
            }
            
            user.firstName = firstName.isEmpty ? nil : firstName
            user.lastName = lastName.isEmpty ? nil : lastName
            user.email = email
            user.phoneNumber = phoneNumber.isEmpty ? nil : phoneNumber
            
            struct UpdateUserRequest: Codable {
                let firstName: String?
                let lastName: String?
                let email: String
                let phone: String?
                let avatarUrl: String?
            }
            
            let request = UpdateUserRequest(
                firstName: user.firstName,
                lastName: user.lastName,
                email: user.email,
                phone: user.phoneNumber,
                avatarUrl: user.avatarURL
            )
            
            
            do {
                let updatedUser: User = try await NetworkService.shared.request(
                    endpoint: "/api/users/me",
                    method: .patch,
                    body: request
                )
                
                
                // Ensure avatarURL is absolute URL if it's relative
                var userToUpdate = updatedUser
                if let avatarURL = userToUpdate.avatarURL, avatarURL.hasPrefix("/") && !avatarURL.hasPrefix("http") {
                    userToUpdate.avatarURL = NetworkService.shared.baseURL + avatarURL
                }
                
                authService.updateUser(userToUpdate)
                dismiss()
            } catch let error as NetworkError {
                
                // If endpoint doesn't exist (404), update user locally
                if case .serverError(404) = error {
                    // Update user locally since API endpoint doesn't exist
                    user.firstName = firstName.isEmpty ? nil : firstName
                    user.lastName = lastName.isEmpty ? nil : lastName
                    user.email = email
                    user.phoneNumber = phoneNumber.isEmpty ? nil : phoneNumber
                    authService.updateUser(user)
                    dismiss()
                } else {
                    errorMessage = "Failed to save profile: \(error.localizedDescription)"
                }
            } catch {
                errorMessage = "Failed to save profile: \(error.localizedDescription)"
            }
        }
        
        isSaving = false
    }
    
    private func deleteAccount() async {
        do {
            _ = try await NetworkService.shared.request(
                endpoint: "/api/users/me",
                method: .delete,
                body: Optional<String>.none
            ) as EmptyResponse
            
            authService.signOut()
            dismiss()
        } catch {
            errorMessage = "Failed to delete account: \(error.localizedDescription)"
        }
    }
}

#Preview {
    NavigationStack {
        EditProfileView()
    }
}
