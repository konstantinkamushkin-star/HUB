//
//  ManageAffiliatedSitesView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct ManageAffiliatedSitesView: View {
    let center: DiveCenter
    @StateObject private var exploreViewModel = ExploreViewModel()
    @State private var affiliatedSiteIds: Set<String>
    @State private var isLoading = false
    @State private var errorMessage: String?
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    
    init(center: DiveCenter) {
        self.center = center
        _affiliatedSiteIds = State(initialValue: Set(center.affiliatedSites))
    }
    
    var body: some View {
        VStack(spacing: 0) {
            List {
                if isLoading {
                    ProgressView()
                } else if let error = errorMessage {
                    Text("\(localizationService.localizedString("error", table: "common")): \(error)")
                        .foregroundColor(.red)
                } else {
                    Section {
                        Text(localizationService.localizedString("selectDiveSitesForCenter", table: "admin"))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    Section(localizationService.localizedString("availableDiveSites", table: "admin")) {
                        ForEach(exploreViewModel.diveSites) { site in
                            Toggle(isOn: Binding(
                                get: { affiliatedSiteIds.contains(site.id) },
                                set: { isSelected in
                                    if isSelected {
                                        affiliatedSiteIds.insert(site.id)
                                    } else {
                                        affiliatedSiteIds.remove(site.id)
                                    }
                                    saveAffiliatedSites()
                                }
                            )) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(site.displayName)
                                        .font(.headline)
                                    Text(site.siteType.displayName)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("manageDiveSites", table: "admin"))
        .diveHubNavigationChrome()
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(localizationService.localizedString("done", table: "common")) {
                    dismiss()
                }
            }
        }
        .task {
            await exploreViewModel.loadData()
        }
    }
    
    private func saveAffiliatedSites() {
        isLoading = true
        errorMessage = nil
        
        Task {
            // Update dive center with new affiliated sites
            _ = Array(affiliatedSiteIds)
            // TODO: Call API to update dive center's affiliated sites
            // try await NetworkService.shared.updateDiveCenterAffiliatedSites(centerId: center.id, siteIds: updatedSiteIds)
            
            // For now, just save locally
            isLoading = false
        }
    }
}

#Preview {
    NavigationView {
        ManageAffiliatedSitesView(center: DiveCenter(
            id: "1",
            name: "Test Center",
            description: "Test",
            location: DiveCenter.Location(
                latitude: 20.0,
                longitude: -80.0,
                address: "123 Test",
                city: "Test City",
                country: "Test Country"
            ),
            contactInfo: DiveCenter.ContactInfo(
                phone: "+1234567890",
                email: "test@test.com",
                website: nil,
                socialMedia: nil
            ),
            photos: [],
            videos: [],
            averageRating: 4.5,
            reviewCount: 10,
            aiSummary: nil,
            instructors: [],
            affiliatedSites: [],
            services: [],
            operatingHours: DiveCenter.OperatingHours(),
            createdAt: Date(),
            updatedAt: Date()
        ))
    }
}
