//
//  Shop.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import CoreLocation

enum ShopType: String, Codable, CaseIterable {
    case offline = "offline"
    case online = "online"
    
    var displayName: String {
        switch self {
        case .offline: return "Offline"
        case .online: return "Online"
        }
    }
}

struct Shop: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var description: String
    var localizedName: [String: String]?
    var localizedDescription: [String: String]?
    var type: ShopType
    var brands: [String] // e.g., ["Scubapro", "Aqualung", "Mares"]
    var serviceAvailable: Bool
    var averageRating: Double
    var reviewCount: Int
    var location: Location
    var photos: [String] // URLs
    var contactInfo: ContactInfo?
    var ownerId: String? // Owner user ID
    var createdAt: Date
    var updatedAt: Date
    
    // Initializer for creating new shops
    init(
        id: String,
        name: String,
        description: String,
        localizedName: [String: String]? = nil,
        localizedDescription: [String: String]? = nil,
        type: ShopType,
        brands: [String],
        serviceAvailable: Bool,
        averageRating: Double = 0,
        reviewCount: Int = 0,
        location: Location,
        photos: [String],
        contactInfo: ContactInfo? = nil,
        ownerId: String? = nil,
        createdAt: Date = Date(),
        updatedAt: Date = Date()
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.localizedName = localizedName
        self.localizedDescription = localizedDescription
        self.type = type
        self.brands = brands
        self.serviceAvailable = serviceAvailable
        self.averageRating = averageRating
        self.reviewCount = reviewCount
        self.location = location
        self.photos = photos
        self.contactInfo = contactInfo
        self.ownerId = ownerId
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
    
    struct Location: Codable {
        var latitude: Double
        var longitude: Double
        var address: String?
        var city: String?
        var country: String?
        
        var coordinate: CLLocationCoordinate2D {
            CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        }
    }
    
    struct ContactInfo: Codable {
        var phone: String?
        var email: String?
        var website: String?
    }
    
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
    
    // Custom decoding to map flat API structure to nested model
    enum CodingKeys: String, CodingKey {
        case id, name, description
        case localizedName, localizedDescription
        case type, brands, serviceAvailable
        case latitude, longitude, country, city, address
        case email, phone, website
        case photoUrls, photos
        case averageRating, reviewCount
        case ownerId
        case createdAt, updatedAt
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        description = try container.decodeIfPresent(String.self, forKey: .description) ?? ""
        localizedName = try container.decodeIfPresent([String: String].self, forKey: .localizedName)
        localizedDescription = try container.decodeIfPresent([String: String].self, forKey: .localizedDescription)
        type = try container.decode(ShopType.self, forKey: .type)
        brands = try container.decodeIfPresent([String].self, forKey: .brands) ?? []
        serviceAvailable = try container.decodeIfPresent(Bool.self, forKey: .serviceAvailable) ?? false
        
        // Map flat location fields to nested Location
        let latitude = try container.decodeIfPresent(Double.self, forKey: .latitude) ?? 0
        let longitude = try container.decodeIfPresent(Double.self, forKey: .longitude) ?? 0
        let country = try container.decodeIfPresent(String.self, forKey: .country)
        let city = try container.decodeIfPresent(String.self, forKey: .city)
        let address = try container.decodeIfPresent(String.self, forKey: .address)
        
        location = Location(
            latitude: latitude,
            longitude: longitude,
            address: address,
            city: city,
            country: country
        )
        
        // Map flat contact fields to nested ContactInfo
        let email = try container.decodeIfPresent(String.self, forKey: .email)
        let phone = try container.decodeIfPresent(String.self, forKey: .phone)
        let website = try container.decodeIfPresent(String.self, forKey: .website)
        
        if email != nil || phone != nil || website != nil {
            contactInfo = ContactInfo(
                phone: phone,
                email: email,
                website: website
            )
        } else {
            contactInfo = nil
        }
        
        // Handle both photoUrls and photos
        if let photoUrls = try? container.decodeIfPresent([String].self, forKey: .photoUrls) {
            photos = photoUrls
        } else {
            photos = try container.decodeIfPresent([String].self, forKey: .photos) ?? []
        }
        
        averageRating = try container.decodeIfPresent(Double.self, forKey: .averageRating) ?? 0
        reviewCount = try container.decodeIfPresent(Int.self, forKey: .reviewCount) ?? 0
        ownerId = try container.decodeIfPresent(String.self, forKey: .ownerId)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
        updatedAt = try container.decodeIfPresent(Date.self, forKey: .updatedAt) ?? createdAt
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        
        try container.encode(id, forKey: .id)
        try container.encode(name, forKey: .name)
        try container.encode(description, forKey: .description)
        try container.encodeIfPresent(localizedName, forKey: .localizedName)
        try container.encodeIfPresent(localizedDescription, forKey: .localizedDescription)
        try container.encode(type, forKey: .type)
        try container.encode(brands, forKey: .brands)
        try container.encode(serviceAvailable, forKey: .serviceAvailable)
        
        // Flatten location
        try container.encode(location.latitude, forKey: .latitude)
        try container.encode(location.longitude, forKey: .longitude)
        try container.encodeIfPresent(location.country, forKey: .country)
        try container.encodeIfPresent(location.city, forKey: .city)
        try container.encodeIfPresent(location.address, forKey: .address)
        
        // Flatten contact info
        try container.encodeIfPresent(contactInfo?.email, forKey: .email)
        try container.encodeIfPresent(contactInfo?.phone, forKey: .phone)
        try container.encodeIfPresent(contactInfo?.website, forKey: .website)
        
        try container.encode(photos, forKey: .photoUrls)
        try container.encode(averageRating, forKey: .averageRating)
        try container.encode(reviewCount, forKey: .reviewCount)
        try container.encodeIfPresent(ownerId, forKey: .ownerId)
        try container.encode(createdAt, forKey: .createdAt)
        try container.encode(updatedAt, forKey: .updatedAt)
    }
    
    // Equatable conformance
    static func == (lhs: Shop, rhs: Shop) -> Bool {
        return lhs.id == rhs.id
    }
}
