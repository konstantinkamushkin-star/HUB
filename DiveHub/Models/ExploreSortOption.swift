//
//  ExploreSortOption.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

enum ExploreSortOption: String, CaseIterable, Identifiable {
    case distance = "distance"
    case rating = "rating"
    case name = "name"
    case reviewCount = "reviewCount"
    
    var id: String { rawValue }
    
    var displayName: String {
        switch self {
        case .distance: return "Distance"
        case .rating: return "Rating"
        case .name: return "Name"
        case .reviewCount: return "Reviews"
        }
    }
    
    var iconName: String {
        switch self {
        case .distance: return "location"
        case .rating: return "star.fill"
        case .name: return "textformat.abc"
        case .reviewCount: return "text.bubble"
        }
    }
}
