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
            if isLoading {
                ProgressView()
            } else if errorMessage != nil {
                Text("ui_profile_error_value".localized)
                    .foregroundColor(.red)
            } else if let center = diveCenter {
                Section {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(center.name)
                            .font(.title2)
                            .fontWeight(.bold)
                        Text("ui_logbook_value_value".localized)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                }
                
                Section("ui_contact_information".localized) {
                    InfoRow(icon: "phone", text: "Phone: \(center.contactInfo.phone)")
                    InfoRow(icon: "envelope", text: "Email: \(center.contactInfo.email)")
                    if let website = center.contactInfo.website {
                        InfoRow(icon: "globe", text: "Website: \(website)")
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
                        Text("ui_profile_value_6".localized)
                    }
                    HStack {
                        Text("ui_profile_instructors".localized)
                        Spacer()
                        Text("ui_profile_value_5".localized)
                    }
                    HStack {
                        Text("ui_profile_affiliated_sites".localized)
                        Spacer()
                        Text("ui_profile_value".localized)
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
                        Text("ui_profile_value_instructor_s_configured".localized)
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
                        Text("ui_profile_value_dive_site_s_configured".localized)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                
                Section("ui_management".localized) {
                    NavigationLink(destination: AdminTabView()) {
                        Label("ui_profile_admin_dashboard".localized, systemImage: "chart.bar")
                    }
                }
            } else {
                Text("ui_profile_no_dive_center_associated_with_your_account".localized)
                    .foregroundColor(.secondary)
            }
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


#Preview {
    NavigationView {
        DiveCenterAdminView()
    }
}
