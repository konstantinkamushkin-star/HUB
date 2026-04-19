//
//  DiveCenterProfileHubView.swift
//  DiveHub
//

import SwiftUI

/// One screen: dive center overview / tools and personal profile editing (for center-linked staff).
struct DiveCenterProfileHubView: View {
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var editModel = EditProfileFormModel()

    @State private var diveCenter: DiveCenter?
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        Form {
            DiveCenterAdminListBody(isLoading: isLoading, errorMessage: errorMessage, diveCenter: diveCenter)
            EditProfileFormSections(model: editModel)
        }
        .navigationTitle("ui_profile_dive_center_profile".localized)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(localizationService.localizedString("save", table: "common")) {
                    Task {
                        await editModel.saveProfile()
                    }
                }
                .disabled(editModel.isSaving)
            }
        }
        .task {
            await loadDiveCenter()
        }
        .onAppear {
            editModel.loadProfile()
        }
    }

    private func loadDiveCenter() async {
        guard let user = authService.currentUser,
              let centerId = user.diveCenterId else {
            errorMessage = "No dive center associated with your account"
            return
        }

        isLoading = true
        errorMessage = nil

        do {
            let centers = try await NetworkService.shared.getDiveCenters()
            diveCenter = centers.first { $0.id == centerId }
            if diveCenter == nil {
                errorMessage = "Dive center not found"
            }
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}
