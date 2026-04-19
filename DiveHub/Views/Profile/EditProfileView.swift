//
//  EditProfileView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import UIKit

struct EditProfileView: View {
    @StateObject private var model = EditProfileFormModel()
    @StateObject private var localizationService = LocalizationService.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Form {
            EditProfileFormSections(model: model, onAccountDeleted: { dismiss() })
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
                        await model.saveProfile {
                            dismiss()
                        }
                    }
                }
                .disabled(model.isSaving)
            }
        }
        .onAppear {
            model.loadProfile()
        }
    }
}

#Preview {
    NavigationStack {
        EditProfileView()
    }
}
