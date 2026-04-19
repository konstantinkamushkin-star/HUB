//
//  ListCard.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct ListCard: View {
    let item: any ExploreItem
    let category: ExploreCategory
    let onTap: () -> Void
    let onAddToTrip: (() -> Void)?
    let friendsVisited: Int?
    let isRecommended: Bool
    let distanceInMeters: Double?
    
    @StateObject private var localizationService = LocalizationService.shared
    @Environment(\.colorScheme) private var colorScheme
    
    init(
        item: any ExploreItem,
        category: ExploreCategory,
        onTap: @escaping () -> Void,
        onAddToTrip: (() -> Void)? = nil,
        friendsVisited: Int? = nil,
        isRecommended: Bool = false,
        distanceInMeters: Double? = nil
    ) {
        self.item = item
        self.category = category
        self.onTap = onTap
        self.onAddToTrip = onAddToTrip
        self.friendsVisited = friendsVisited
        self.isRecommended = isRecommended
        self.distanceInMeters = distanceInMeters
    }
    
    private var distanceText: String? {
        guard let distance = distanceInMeters else { return nil }
        if distance < 1000 {
            return String(format: "%.0f m", distance)
        } else {
            return String(format: "%.1f km", distance / 1000)
        }
    }
    
    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 12) {
                // Header with badges
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 8) {
                            Text(item.exploreName)
                                .font(.headline)
                                .foregroundColor(.primary)
                            
                            if isRecommended {
                                RecommendationBadge()
                            }
                        }
                        
                        categorySpecificSubtitle
                    }
                    
                    Spacer()
                    
                    VStack(alignment: .trailing, spacing: 4) {
                        if let distanceText = distanceText {
                            HStack(spacing: 4) {
                                Image(systemName: "location.fill")
                                    .font(.caption2)
                                Text(distanceText)
                                    .font(.caption)
                                    .fontWeight(.medium)
                            }
                            .foregroundColor(.blue)
                        }
                        
                        RatingView(rating: item.rating, reviewCount: item.reviewCount)
                        
                        if let friendsVisited = friendsVisited, friendsVisited > 0 {
                            FriendsVisitedBadge(count: friendsVisited)
                        }
                    }
                }
                
                // Category-specific details
                categorySpecificDetails
                
                // Action buttons
                if onAddToTrip != nil {
                    HStack {
                        Spacer()
                        Button(action: {
                            onAddToTrip?()
                        }) {
                            Label("ui_explore_add_to_trip".localized, systemImage: "plus.circle.fill")
                                .font(.caption)
                                .foregroundColor(.blue)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(Color.primary.opacity(colorScheme == .dark ? 0.12 : 0.06), lineWidth: 1)
            )
            .shadow(
                color: Color.black.opacity(colorScheme == .dark ? 0.45 : 0.08),
                radius: colorScheme == .dark ? 10 : 4,
                x: 0,
                y: colorScheme == .dark ? 4 : 2
            )
        }
        .buttonStyle(.plain)
    }
    
    @ViewBuilder
    private var categorySpecificSubtitle: some View {
        switch category {
        case .diveSites:
            if let site = item as? DiveSite {
                HStack(spacing: 8) {
                    HStack(spacing: 4) {
                        DiveHubLogoMark(color: .secondary)
                            .aspectRatio(1, contentMode: .fit)
                            .frame(width: 16, height: 16)
                        Text(site.siteType.displayName)
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                    Text("ui_explore_a".localized)
                        .foregroundColor(.secondary)
                    Label("\(Int(site.maxDepth))m", systemImage: "arrow.down")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        case .diveCenters:
            if let center = item as? DiveCenter {
                HStack(spacing: 8) {
                    Label(center.location.city, systemImage: "location")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    if let agency = center.certificationAgency {
                        Text("ui_explore_a".localized)
                            .foregroundColor(.secondary)
                        Text(agency)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
        case .shops:
            if let shop = item as? Shop {
                HStack(spacing: 8) {
                    Label(shop.type.displayName, systemImage: shop.type == .online ? "globe" : "storefront")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    if let city = shop.location.city {
                        Text("ui_explore_a".localized)
                            .foregroundColor(.secondary)
                        Label(city, systemImage: "location")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
        }
    }
    
    @ViewBuilder
    private var categorySpecificDetails: some View {
        switch category {
        case .diveSites:
            if let site = item as? DiveSite {
                HStack(spacing: 16) {
                    if let waterTemp = site.waterTemp {
                        DetailChip(icon: "thermometer", text: "\(Int(waterTemp))°C")
                    }
                    if let visibility = site.visibility {
                        DetailChip(icon: "eye", text: visibility)
                    }
                    DetailChip(icon: "chart.bar", text: site.difficulty.displayName)
                }
            }
        case .diveCenters:
            if let center = item as? DiveCenter {
                HStack(spacing: 16) {
                    if center.nitroxAvailable {
                        DetailChip(icon: "airpods", text: "Nitrox")
                    }
                    if !center.languages.isEmpty {
                        DetailChip(icon: "globe", text: "\(center.languages.count) languages")
                    }
                    if let priceFrom = center.priceFrom {
                        DetailChip(icon: "dollarsign.circle", text: "From $\(Int(priceFrom))")
                    }
                }
            }
        case .shops:
            if let shop = item as? Shop {
                HStack(spacing: 16) {
                    if shop.serviceAvailable {
                        DetailChip(icon: "wrench.and.screwdriver", text: "Service")
                    }
                    if !shop.brands.isEmpty {
                        DetailChip(icon: "tag", text: "\(shop.brands.count) brands")
                    }
                }
            }
        }
    }
}

struct RatingView: View {
    let rating: Double
    let reviewCount: Int
    
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "star.fill")
                .foregroundColor(.yellow)
                .font(.caption)
            Text(String(format: "%.1f", rating))
                .font(.subheadline)
                .fontWeight(.semibold)
            Text("(\(reviewCount))")
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

struct RecommendationBadge: View {
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "sparkles")
                .font(.caption2)
            Text("ui_explore_recommended".localized)
                .font(.caption2)
                .fontWeight(.medium)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.blue.opacity(0.1))
        .foregroundColor(.blue)
        .cornerRadius(8)
    }
}

struct FriendsVisitedBadge: View {
    let count: Int
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "person.2.fill")
                .font(.caption2)
            Text("\(count) \(localizationService.localizedString("friends", table: "social"))")
                .font(.caption2)
                .fontWeight(.medium)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.green.opacity(0.1))
        .foregroundColor(.green)
        .cornerRadius(8)
    }
}

struct DetailChip: View {
    let icon: String
    let text: String
    
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.caption2)
            Text(text)
                .font(.caption)
        }
        .foregroundColor(.secondary)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color(.systemGray6))
        .cornerRadius(6)
    }
}

#Preview {
    VStack(spacing: 16) {
        ListCard(
            item: MockExploreData.diveSites[0],
            category: .diveSites,
            onTap: {},
            onAddToTrip: {},
            friendsVisited: 3,
            isRecommended: true,
            distanceInMeters: 1250.5
        )
        
        ListCard(
            item: MockExploreData.diveCenters[0],
            category: .diveCenters,
            onTap: {},
            friendsVisited: 1,
            distanceInMeters: 500.0
        )
        
        ListCard(
            item: MockExploreData.shops[0],
            category: .shops,
            onTap: {},
            distanceInMeters: 2500.0
        )
    }
    .padding()
}
