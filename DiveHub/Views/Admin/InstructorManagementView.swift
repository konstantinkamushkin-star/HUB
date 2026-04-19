//
//  InstructorManagementView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import UIKit

struct InstructorManagementView: View {
    @ObservedObject private var authService = AuthenticationService.shared
    @StateObject private var viewModel = AdminViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showAddInstructor = false
    
    var body: some View {
        VStack(spacing: 0) {
            List {
                Section {
                    ForEach(viewModel.instructors) { instructor in
                        if let centerId = authService.currentUser?.diveCenterId {
                            NavigationLink {
                                AdminInstructorDetailView(diveCenterId: centerId, instructor: instructor)
                            } label: {
                                InstructorManagementRow(instructor: instructor)
                            }
                        } else {
                            InstructorManagementRow(instructor: instructor)
                        }
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("instructors", table: "admin"))
        .diveHubNavigationChrome()
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showAddInstructor = true }) {
                    Image(systemName: "plus.circle.fill")
                        .font(.title3)
                }
            }
        }
        .sheet(isPresented: $showAddInstructor) {
            if let centerId = authService.currentUser?.diveCenterId {
                AddInstructorView(
                    centerId: centerId,
                    existingInstructorUserIds: Set(viewModel.instructors.map(\.id)),
                    onInstructorAdded: { _ in
                        showAddInstructor = false
                        Task { await viewModel.loadInstructors() }
                    }
                )
            } else {
                Text(localizationService.localizedString("noDiveCenterLinked", table: "admin"))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding()
            }
        }
        .task {
            if authService.currentUser?.diveCenterId == nil,
               authService.currentUser?.role == .diveCenterAdmin {
                await authService.refreshSessionUserFromServer()
            }
            await viewModel.loadInstructors()
        }
    }
}

struct InstructorManagementRow: View {
    let instructor: User
    
    var body: some View {
        HStack {
            AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: instructor.avatarURL) ?? "")) { image in
                image.resizable().aspectRatio(contentMode: .fill)
            } placeholder: {
                Image(systemName: "person.circle.fill")
                    .foregroundColor(.secondary)
            }
            .frame(width: 50, height: 50)
            .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 4) {
                Text(instructor.displayName)
                    .font(.headline)
                Text(instructor.email)
                    .font(.caption)
                    .foregroundColor(.secondary)
                if let certification = instructor.certificationLevel {
                    Text(certification)
                        .font(.caption)
                        .foregroundColor(.divePrimary)
                }
            }
            
            Spacer()
        }
    }
}

// MARK: - Instructor profile (admin)

struct AdminInstructorDetailView: View {
    let diveCenterId: String
    @State private var user: User
    @State private var bioDraft: String
    @State private var isSaving = false
    @State private var errorMessage: String?
    @StateObject private var localizationService = LocalizationService.shared

    init(diveCenterId: String, instructor: User) {
        self.diveCenterId = diveCenterId
        _user = State(initialValue: instructor)
        _bioDraft = State(initialValue: instructor.bio ?? "")
    }

    var body: some View {
        Form {
            Section {
                HStack(spacing: 16) {
                    AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: user.avatarURL) ?? "")) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Image(systemName: "person.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                    .frame(width: 72, height: 72)
                    .clipShape(Circle())

                    VStack(alignment: .leading, spacing: 4) {
                        Text(user.displayName)
                            .font(.title3.weight(.semibold))
                        Text(user.email)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(user.role.displayName)
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }
                .padding(.vertical, 4)
            }

            if let cert = user.certificationLevel, !cert.isEmpty {
                Section(header: Text(localizationService.localizedString("adminInstructorCert", table: "admin"))) {
                    Text(cert)
                }
            }

            Section(
                header: Text(localizationService.localizedString("adminInstructorAbout", table: "admin")),
                footer: Text(localizationService.localizedString("adminInstructorBioHint", table: "admin"))
                    .font(.caption2)
            ) {
                TextEditor(text: $bioDraft)
                    .frame(minHeight: 120)
            }

            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                        .font(.caption)
                }
            }
        }
        .navigationTitle(localizationService.localizedString("instructors", table: "admin"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    Task { await saveAsync() }
                } label: {
                    if isSaving {
                        ProgressView()
                    } else {
                        Text(localizationService.localizedString("save", table: "common"))
                    }
                }
                .disabled(isSaving)
            }
        }
        .task {
            await refreshUser()
        }
    }

    private func refreshUser() async {
        do {
            let fresh: User = try await NetworkService.shared.getUser(userId: user.id)
            await MainActor.run {
                user = fresh
                if bioDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    bioDraft = fresh.bio ?? ""
                }
            }
        } catch {
            // оставляем данные из списка
        }
    }

    @MainActor
    private func saveAsync() async {
        let trimmed = bioDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let payload: String? = trimmed.isEmpty ? nil : trimmed
        isSaving = true
        errorMessage = nil
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
        do {
            let updated = try await NetworkService.shared.patchInstructorProfile(
                diveCenterId: diveCenterId,
                userId: user.id,
                bio: payload
            )
            user = updated
            bioDraft = updated.bio ?? ""
        } catch {
            if let ne = error as? NetworkError {
                errorMessage = ne.errorDescription
            } else {
                errorMessage = error.localizedDescription
            }
        }
        isSaving = false
    }
}

#Preview {
    InstructorManagementView()
}
