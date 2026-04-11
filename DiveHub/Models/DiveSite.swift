//
//  DiveSite.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import CoreLocation

enum DiveSiteType: String, Codable, CaseIterable {
    case wreck = "wreck"
    case reef = "reef"
    case wall = "wall"
    case cave = "cave"
    case drift = "drift"
    case shore = "shore"
    case boat = "boat"
    case other = "other"
    
    var displayName: String {
        switch self {
        case .wreck: return "Wreck"
        case .reef: return "Reef"
        case .wall: return "Wall"
        case .cave: return "Cave"
        case .drift: return "Drift"
        case .shore: return "Shore"
        case .boat: return "Boat"
        case .other: return "Other"
        }
    }
}

enum DifficultyLevel: String, Codable, CaseIterable {
    case beginner = "beginner"
    case intermediate = "intermediate"
    case advanced = "advanced"
    case expert = "expert"
    
    var displayName: String {
        let localizationService = LocalizationService.shared
        let key = "difficulty.\(self.rawValue)"
        let localized = localizationService.localizedString(key, table: "diveSite")
        // If localization not found, return English default
        if localized == key {
            switch self {
            case .beginner: return "Beginner"
            case .intermediate: return "Intermediate"
            case .advanced: return "Advanced"
            case .expert: return "Expert"
            }
        }
        return localized
    }
}

struct DiveSite: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var description: String
    var localizedName: [String: String]? // Localized names: {"en": "Name", "ru": "Название"}
    var localizedDescription: [String: String]? // Localized descriptions
    var location: Location
    var siteType: DiveSiteType
    var diveTypes: [String] // Store full array of dive types from API
    var difficulty: DifficultyLevel
    var maxDepth: Double // in meters
    var averageDepth: Double // in meters
    var visibility: String? // e.g., "10-20m"
    var waterTemp: Double? // Water temperature in Celsius
    var current: String? // e.g., "Moderate"
    var marineLife: [String] // e.g., ["Sharks", "Turtles", "Rays"]
    var photos: [String] // URLs
    var videos: [String] // URLs
    var averageRating: Double
    var reviewCount: Int
    var aiSummary: String? // AI-generated summary
    var affiliatedCenters: [String] // Dive Center IDs
    var country: String // Country name, e.g. "Egypt"
    var createdAt: Date
    var updatedAt: Date
    
    // Computed properties for localized content
    var displayName: String {
        let language = LocalizationService.shared.currentLanguage.rawValue
        if let localized = localizedName?[language], !localized.isEmpty {
            return localized
        }
        return name
    }
    
    var displayDescription: String {
        let language = LocalizationService.shared.currentLanguage.rawValue
        if let localized = localizedDescription?[language], !localized.isEmpty {
            return localized
        }
        return description
    }
    
    struct Location: Codable {
        var latitude: Double
        var longitude: Double
        var address: String?
        
        var coordinate: CLLocationCoordinate2D {
            // CLLocationCoordinate2D uses (latitude, longitude) order
            // API returns coordinates correctly, so use them directly
            // Validate that coordinates are in valid range
            guard abs(latitude) <= 90 && abs(longitude) <= 180 else {
                #if DEBUG
                print("❌ [DiveSite.Location] INVALID COORDINATES: lat=\(latitude), lng=\(longitude)")
                #endif
                // Return a default coordinate (0,0) if invalid - this will show an error on map
                return CLLocationCoordinate2D(latitude: 0, longitude: 0)
            }
            
            // Create coordinate directly from latitude and longitude
            // IMPORTANT: CLLocationCoordinate2D(latitude: X, longitude: Y) - order is correct
            return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        }
    }
    
    // Custom decoding to map flat API structure to nested model
    enum CodingKeys: String, CodingKey {
        case id, name, description
        case localizedName, localizedDescription
        case latitude, longitude, country, region
        case diveTypes, difficultyLevel
        case depthMin, depthMax
        case marineLife
        case averageRating, reviewCount
        case createdAt, updatedAt
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        // Geo search / list endpoints may omit description; default to empty instead of failing decode
        description = try container.decodeIfPresent(String.self, forKey: .description) ?? ""
        localizedName = try container.decodeIfPresent([String: String].self, forKey: .localizedName)
        localizedDescription = try container.decodeIfPresent([String: String].self, forKey: .localizedDescription)
        
        // Map flat location fields to nested Location
        // IMPORTANT: API returns {"latitude": X, "longitude": Y}
        // We decode them directly - latitude goes to latitude, longitude goes to longitude
        var latitude = try container.decode(Double.self, forKey: .latitude)
        var longitude = try container.decode(Double.self, forKey: .longitude)
        let country = try container.decodeIfPresent(String.self, forKey: .country) ?? ""
        let region = try container.decodeIfPresent(String.self, forKey: .region) ?? ""
        
        // Debug: Log decoded coordinates for verification
        #if DEBUG
        print("📍 [DiveSite] Decoded: \(name) - lat=\(latitude), lng=\(longitude), country=\(country)")
        #endif
        
        // If latitude/longitude are out of valid ranges, treat them as swapped and fix.
        if abs(latitude) > 90 || abs(longitude) > 180 {
            let temp = latitude
            latitude = longitude
            longitude = temp
        }

        let addressString = "\(region), \(country)".trimmingCharacters(in: CharacterSet(charactersIn: ", "))
        let finalAddress: String? = addressString.isEmpty ? nil : addressString
        
        // Create Location with decoded coordinates
        // IMPORTANT: Location(latitude: X, longitude: Y) - order is correct
        location = Location(latitude: latitude, longitude: longitude, address: finalAddress)
        
        // Debug: Verify Location was created correctly
        #if DEBUG
        let coord = location.coordinate
        print("📍 [DiveSite] Location created: \(name) - coordinate lat=\(coord.latitude), lng=\(coord.longitude)")
        if abs(coord.latitude - latitude) > 0.0001 || abs(coord.longitude - longitude) > 0.0001 {
            print("⚠️ [DiveSite] COORDINATE MISMATCH for \(name)!")
            print("   Decoded: lat=\(latitude), lng=\(longitude)")
            print("   Coordinate: lat=\(coord.latitude), lng=\(coord.longitude)")
        }
        #endif
        
        // Map diveTypes array to siteType enum (use first type or default to "other")
        let diveTypes = try container.decodeIfPresent([String].self, forKey: .diveTypes) ?? []
        // Store full array for filtering
        self.diveTypes = diveTypes
        if let firstType = diveTypes.first, let mappedType = DiveSiteType(rawValue: firstType) {
            siteType = mappedType
        } else {
            siteType = .other
        }
        
        // Map difficultyLevel (Int) to DifficultyLevel enum
        let difficultyLevelInt = try container.decodeIfPresent(Int.self, forKey: .difficultyLevel) ?? 1
        switch difficultyLevelInt {
        case 1: difficulty = DifficultyLevel.beginner
        case 2: difficulty = DifficultyLevel.intermediate
        case 3: difficulty = DifficultyLevel.advanced
        default: difficulty = DifficultyLevel.expert
        }
        
        // Map depthMin/depthMax to maxDepth/averageDepth
        let depthMin = try container.decodeIfPresent(Double.self, forKey: .depthMin) ?? 0
        let depthMax = try container.decodeIfPresent(Double.self, forKey: .depthMax) ?? 0
        maxDepth = depthMax
        averageDepth = (depthMin + depthMax) / 2
        
        marineLife = try container.decodeIfPresent([String].self, forKey: .marineLife) ?? []
        photos = []
        videos = []
        averageRating = try container.decodeIfPresent(Double.self, forKey: .averageRating) ?? 0
        reviewCount = try container.decodeIfPresent(Int.self, forKey: .reviewCount) ?? 0
        aiSummary = nil
        affiliatedCenters = []
        self.country = country
        visibility = nil
        waterTemp = nil
        current = nil
        
        createdAt = try container.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
        updatedAt = try container.decodeIfPresent(Date.self, forKey: .updatedAt) ?? Date()
    }
    
    // Custom encoding (if needed)
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(name, forKey: .name)
        try container.encode(description, forKey: .description)
        try container.encode(location.latitude, forKey: .latitude)
        try container.encode(location.longitude, forKey: .longitude)
        try container.encode(country, forKey: .country)
        try container.encode(diveTypes.isEmpty ? [siteType.rawValue] : diveTypes, forKey: .diveTypes)
        try container.encode(difficultyLevelInt, forKey: .difficultyLevel)
        try container.encode(averageDepth * 2 - maxDepth, forKey: .depthMin)
        try container.encode(maxDepth, forKey: .depthMax)
        try container.encode(marineLife, forKey: .marineLife)
        try container.encode(averageRating, forKey: .averageRating)
        try container.encode(reviewCount, forKey: .reviewCount)
        try container.encode(createdAt, forKey: .createdAt)
        try container.encode(updatedAt, forKey: .updatedAt)
    }
    
    private var difficultyLevelInt: Int {
        switch difficulty {
        case .beginner: return 1
        case .intermediate: return 2
        case .advanced: return 3
        case .expert: return 4
        }
    }
    
    // Memberwise initializer for manual creation (e.g., in previews)
    init(id: String, name: String, description: String, location: Location, siteType: DiveSiteType, difficulty: DifficultyLevel, maxDepth: Double, averageDepth: Double, visibility: String? = nil, waterTemp: Double? = nil, current: String? = nil, marineLife: [String] = [], photos: [String] = [], videos: [String] = [], averageRating: Double = 0, reviewCount: Int = 0, aiSummary: String? = nil, affiliatedCenters: [String] = [], country: String = "", createdAt: Date = Date(), updatedAt: Date = Date(), diveTypes: [String]? = nil) {
        self.id = id
        self.name = name
        self.description = description
        self.location = location
        self.siteType = siteType
        self.diveTypes = diveTypes ?? [siteType.rawValue] // Default to single type if not provided
        self.difficulty = difficulty
        self.maxDepth = maxDepth
        self.averageDepth = averageDepth
        self.visibility = visibility
        self.waterTemp = waterTemp
        self.current = current
        self.marineLife = marineLife
        self.photos = photos
        self.videos = videos
        self.averageRating = averageRating
        self.reviewCount = reviewCount
        self.aiSummary = aiSummary
        self.affiliatedCenters = affiliatedCenters
        self.country = country
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
    
    // Equatable conformance - compare by id
    static func == (lhs: DiveSite, rhs: DiveSite) -> Bool {
        return lhs.id == rhs.id
    }
}
