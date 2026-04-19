//
//  DiveCenterAdminView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct DiveCenterAdminView: View {
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @State private var diveCenter: DiveCenter?
    @State private var isLoading = false
    @State private var errorMessage: String?
    
    var body: some View {
        List {
            DiveCenterAdminListBody(isLoading: isLoading, errorMessage: errorMessage, diveCenter: diveCenter)
        }
        .navigationTitle("ui_profile_dive_center_profile".localized)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadDiveCenter()
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

/// Shared list body for `DiveCenterAdminView` and `DiveCenterProfileHubView` (center + account on one screen).
struct DiveCenterAdminListBody: View {
    var isLoading: Bool
    var errorMessage: String?
    var diveCenter: DiveCenter?

    var body: some View {
        Group {
            if isLoading {
                Section {
                    ProgressView()
                }
            } else if let errorMessage = errorMessage {
                Section {
                    Text("\("ui_label_error".localized): \(errorMessage)")
                        .foregroundColor(.red)
                }
            } else if let center = diveCenter {
                Section {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(center.name)
                            .font(.title2)
                            .fontWeight(.bold)
                        Text("\(center.location.city), \(center.location.country)")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                }

                Section("ui_contact_information".localized) {
                    InfoRow(icon: "phone", text: "\("ui_label_phone".localized): \(center.contactInfo.phone)")
                    InfoRow(icon: "envelope", text: "\("ui_label_email".localized): \(center.contactInfo.email)")
                    if let website = center.contactInfo.website {
                        InfoRow(icon: "globe", text: "\("ui_label_website".localized): \(website)")
                    }
                }

                Section("ui_statistics_statistics".localized) {
                    HStack {
                        Text("ui_profile_average_rating".localized)
                        Spacer()
                        HStack {
                            Image(systemName: "star.fill")
                                .foregroundColor(.yellow)
                            Text(String(format: "%.1f", center.averageRating))
                        }
                    }
                    HStack {
                        Text("ui_profile_total_reviews".localized)
                        Spacer()
                        Text("\(center.reviewCount)")
                    }
                    HStack {
                        Text("ui_profile_instructors".localized)
                        Spacer()
                        Text("\(center.instructors.count)")
                    }
                    HStack {
                        Text("ui_profile_affiliated_sites".localized)
                        Spacer()
                        Text("\(center.affiliatedSites.count)")
                    }
                }

                Section("ui_profile_instructors".localized) {
                    NavigationLink(destination: ManageInstructorsView(center: center)) {
                        Label("ui_profile_manage_instructors".localized, systemImage: "person.2")
                    }
                    if center.instructors.isEmpty {
                        Text("ui_profile_no_instructors_added_yet".localized)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        Text("\(center.instructors.count) \(LocalizationService.shared.localizedString("ui_profile_instructors", table: "ui").lowercased())")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Section("ui_affiliated_dive_sites".localized) {
                    NavigationLink(destination: ManageAffiliatedSitesView(center: center)) {
                        Label("ui_profile_manage_dive_sites".localized, systemImage: "map")
                    }
                    if center.affiliatedSites.isEmpty {
                        Text("ui_profile_no_dive_sites_added_yet".localized)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        Text("\(center.affiliatedSites.count) \(LocalizationService.shared.localizedString("ui_profile_affiliated_sites", table: "ui").lowercased())")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Section("ui_management".localized) {
                    NavigationLink(destination: AdminDashboardView(embedNavigationChrome: false)) {
                        Label("ui_profile_admin_dashboard".localized, systemImage: "chart.bar")
                    }
                }
            } else {
                Section {
                    Text("ui_profile_no_dive_center_associated_with_your_account".localized)
                        .foregroundColor(.secondary)
                }
            }
        }
    }
}

#Preview {
    NavigationView {
        DiveCenterAdminView()
    }
}
