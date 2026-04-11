//
//  DiveCenterDetailView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct DiveCenterDetailView: View {
    let center: DiveCenter
    var onShowOnMap: (() -> Void)? = nil
    @State private var showBooking = false
    @State private var showMessage = false
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Photo Gallery
                if !center.photos.isEmpty {
                    TabView {
                        ForEach(center.photos, id: \.self) { photoURL in
                            AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: photoURL) ?? "")) { image in
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Rectangle()
                                    .fill(Color.gray.opacity(0.3))
                            }
                        }
                    }
                    .frame(height: 250)
                    .tabViewStyle(.page)
                }
                
                VStack(alignment: .leading, spacing: 12) {
                    // Title and Rating
                    HStack {
                        VStack(alignment: .leading) {
                            Text(center.name)
                                .font(.title)
                                .fontWeight(.bold)
                            Text(center.location.city + ", " + center.location.country)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        VStack(alignment: .trailing) {
                            HStack {
                                Image(systemName: "star.fill")
                                    .foregroundColor(.yellow)
                                Text(String(format: "%.1f", center.averageRating))
                                    .fontWeight(.semibold)
                            }
                            Text("(\(center.reviewCount) \(LocalizationService.shared.localizedString("reviews", table: "common")))")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    Divider()
                    
                    // Contact Info
                    VStack(alignment: .leading, spacing: 8) {
                        InfoRow(icon: "phone", text: "Phone: \(center.contactInfo.phone)")
                        InfoRow(icon: "envelope", text: "Email: \(center.contactInfo.email)")
                        if let website = center.contactInfo.website {
                            InfoRow(icon: "globe", text: "Website: \(website)")
                        }
                    }
                    
                    // Description
                    if !center.displayDescription.isEmpty {
                        Text(LocalizationService.shared.localizedString("description"))
                            .font(.headline)
                        Text(center.displayDescription)
                            .font(.body)
                    }
                    
                    // AI Summary
                    if let aiSummary = center.aiSummary {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Image(systemName: "sparkles")
                                    .foregroundColor(.divePrimary)
                                Text(LocalizationService.shared.localizedString("aiSummary", table: "diveSite"))
                                    .font(.headline)
                            }
                            Text(aiSummary)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        .padding()
                        .background(Color.diveBackground)
                        .cornerRadius(12)
                    }
                    
                    // Services
                    if !center.services.isEmpty {
                        Text(LocalizationService.shared.localizedString("services"))
                            .font(.headline)
                        ForEach(center.services) { service in
                            ServiceRow(service: service)
                        }
                    }
                    
                    // Reviews Section
                    ReviewsSection(reviewableType: .diveCenter, reviewableId: center.id)
                }
                .padding()
            }
        }
        .navigationTitle(center.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                if let onShowOnMap = onShowOnMap {
                    Button(action: onShowOnMap) {
                        Image(systemName: "map")
                    }
                }
                Button(action: { showMessage = true }) {
                    Image(systemName: "message.fill")
                }
                Button(action: { showBooking = true }) {
                    Text(LocalizationService.shared.localizedString("book"))
                        .fontWeight(.semibold)
                }
            }
        }
        .sheet(isPresented: $showBooking) {
            BookingWizardView(diveCenterId: center.id)
        }
        .sheet(isPresented: $showMessage) {
            NavigationStack {
                BusinessChatLaunchView(
                    peerType: "dive_center",
                    peerId: center.id,
                    title: center.name
                )
            }
        }
    }
}

struct ServiceRow: View {
    let service: Service
    
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(service.name)
                    .fontWeight(.semibold)
                Text(service.displayDescription)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            Text(String(format: "%.2f %@", service.price.amount, service.price.currency))
                .fontWeight(.semibold)
        }
        .padding()
        .background(Color.diveBackground)
        .cornerRadius(12)
    }
}

#Preview {
    NavigationView {
        DiveCenterDetailView(center: DiveCenter(
            id: "1",
            name: "Blue Water Diving",
            description: "A great dive center",
            location: DiveCenter.Location(
                latitude: 20.0,
                longitude: -80.0,
                address: "123 Beach Road",
                city: "Cozumel",
                country: "Mexico"
            ),
            contactInfo: DiveCenter.ContactInfo(
                phone: "+52 987 123 4567",
                email: "info@bluewater.com",
                website: nil,
                socialMedia: nil
            ),
            photos: [],
            videos: [],
            averageRating: 4.8,
            reviewCount: 156,
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
