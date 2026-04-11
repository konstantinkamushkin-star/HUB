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
            } else if let error = errorMessage {
                Text("Error: \(error)")
                    .foregroundColor(.red)
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
                
                Section("Contact Information") {
                    InfoRow(icon: "phone", text: "Phone: \(center.contactInfo.phone)")
                    InfoRow(icon: "envelope", text: "Email: \(center.contactInfo.email)")
                    if let website = center.contactInfo.website {
                        InfoRow(icon: "globe", text: "Website: \(website)")
                    }
                }
                
                Section("Statistics") {
                    HStack {
                        Text("Average Rating")
                        Spacer()
                        HStack {
                            Image(systemName: "star.fill")
                                .foregroundColor(.yellow)
                            Text(String(format: "%.1f", center.averageRating))
                        }
                    }
                    HStack {
                        Text("Total Reviews")
                        Spacer()
                        Text("\(center.reviewCount)")
                    }
                    HStack {
                        Text("Instructors")
                        Spacer()
                        Text("\(center.instructors.count)")
                    }
                    HStack {
                        Text("Affiliated Sites")
                        Spacer()
                        Text("\(center.affiliatedSites.count)")
                    }
                }
                
                Section("Instructors") {
                    NavigationLink(destination: ManageInstructorsView(center: center)) {
                        Label("Manage Instructors", systemImage: "person.2")
                    }
                    if center.instructors.isEmpty {
                        Text("No instructors added yet")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        Text("\(center.instructors.count) instructor(s) configured")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                
                Section("Affiliated Dive Sites") {
                    NavigationLink(destination: ManageAffiliatedSitesView(center: center)) {
                        Label("Manage Dive Sites", systemImage: "map")
                    }
                    if center.affiliatedSites.isEmpty {
                        Text("No dive sites added yet")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        Text("\(center.affiliatedSites.count) dive site(s) configured")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                
                Section("Management") {
                    NavigationLink(destination: AdminTabView()) {
                        Label("Admin Dashboard", systemImage: "chart.bar")
                    }
                }
            } else {
                Text("No dive center associated with your account")
                    .foregroundColor(.secondary)
            }
        }
        .navigationTitle("Dive Center Profile")
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
