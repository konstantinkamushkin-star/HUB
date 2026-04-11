//
//  ExploreCategory.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

enum ExploreCategory: String, CaseIterable, Identifiable {
    case diveSites = "dive_sites"
    case diveCenters = "dive_centers"
    case shops = "shops"
    
    var id: String { rawValue }
    
    var displayName: String {
        switch self {
        case .diveSites: return "Dive Sites"
        case .diveCenters: return "Dive Centers"
        case .shops: return "Shops"
        }
    }
    
    var searchPlaceholder: String {
        switch self {
        case .diveSites: return "Search dive sites..."
        case .diveCenters: return "Search dive centers..."
        case .shops: return "Search shops..."
        }
    }
    
    var iconName: String {
        switch self {
        case .diveSites: return "divehub.logo"
        case .diveCenters: return "building.2"
        case .shops: return "bag"
        }
    }
}

// Protocol for items that can be displayed in Explore
protocol ExploreItem: Identifiable, Equatable {
    var id: String { get }
    var exploreName: String { get }
    var rating: Double { get }
    var reviewCount: Int { get }
    var exploreLocation: ExploreLocation { get }
}

protocol ExploreLocation {
    var latitude: Double { get }
    var longitude: Double { get }
}

// Extensions to make models conform to ExploreItem
extension DiveSite: ExploreItem {
    var exploreName: String { displayName }
    var rating: Double { averageRating }
    var exploreLocation: ExploreLocation { LocationWrapper(location: self.location) }
}

extension DiveCenter: ExploreItem {
    var exploreName: String { name }
    var rating: Double { averageRating }
    var exploreLocation: ExploreLocation { LocationWrapper(location: location) }
}

extension Shop: ExploreItem {
    var exploreName: String { displayName }
    var rating: Double { averageRating }
    var exploreLocation: ExploreLocation { LocationWrapper(location: self.location) }
}

// Wrapper to make Location conform to ExploreLocation
private struct LocationWrapper: ExploreLocation {
    let latitude: Double
    let longitude: Double
    
    init(location: DiveSite.Location) {
        self.latitude = location.latitude
        self.longitude = location.longitude
    }
    
    init(location: DiveCenter.Location) {
        self.latitude = location.latitude
        self.longitude = location.longitude
    }
    
    init(location: Shop.Location) {
        self.latitude = location.latitude
        self.longitude = location.longitude
    }
}
