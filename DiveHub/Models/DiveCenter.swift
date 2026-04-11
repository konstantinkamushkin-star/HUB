//
//  DiveCenter.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import CoreLocation

struct DiveCenter: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var description: String
    var localizedDescription: [String: String]? // Localized descriptions
    var location: Location
    var contactInfo: ContactInfo
    var photos: [String] // URLs
    var videos: [String] // URLs
    var averageRating: Double
    var reviewCount: Int
    var aiSummary: String? // AI-generated summary
    var instructors: [Instructor]
    var affiliatedSites: [String] // Dive Site IDs
    var services: [Service]
    var operatingHours: OperatingHours
    var certificationAgency: String? // e.g., "PADI", "SSI", "NAUI"
    var languages: [String] // e.g., ["English", "Spanish", "French"]
    var nitroxAvailable: Bool
    var priceFrom: Double? // Starting price in local currency
    var createdAt: Date
    var updatedAt: Date
    
    struct Location: Codable {
        var latitude: Double
        var longitude: Double
        var address: String
        var city: String
        var country: String
        
        var coordinate: CLLocationCoordinate2D {
            CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        }
    }
    
    struct ContactInfo: Codable {
        var phone: String
        var email: String
        var website: String?
        var socialMedia: [String: String]? // e.g., ["facebook": "url", "instagram": "url"]
    }
    
    struct OperatingHours: Codable {
        var monday: DayHours?
        var tuesday: DayHours?
        var wednesday: DayHours?
        var thursday: DayHours?
        var friday: DayHours?
        var saturday: DayHours?
        var sunday: DayHours?
        
        init(monday: DayHours? = nil, tuesday: DayHours? = nil, wednesday: DayHours? = nil, thursday: DayHours? = nil, friday: DayHours? = nil, saturday: DayHours? = nil, sunday: DayHours? = nil) {
            self.monday = monday
            self.tuesday = tuesday
            self.wednesday = wednesday
            self.thursday = thursday
            self.friday = friday
            self.saturday = saturday
            self.sunday = sunday
        }
        
        struct DayHours: Codable {
            var open: String // "09:00"
            var close: String // "18:00"
        }
    }
    
    // Custom decoding to map flat API structure to nested model
    enum CodingKeys: String, CodingKey {
        case id, name, description
        case localizedDescription
        case latitude, longitude, country, city, address
        case phone, email, website
        case services
        case photos, photoUrls, photo_urls, thumbnail_url
        case videos, videoUrls, video_urls
        case averageRating, reviewCount
        case certificationAgency, languages, nitroxAvailable, priceFrom
        case createdAt, updatedAt
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        // Some list endpoints (e.g. geo search / popular) don't include description.
        // Treat missing description as empty string instead of failing decoding.
        description = try container.decodeIfPresent(String.self, forKey: .description) ?? ""
        localizedDescription = try container.decodeIfPresent([String: String].self, forKey: .localizedDescription)
        
        // Map flat location fields to nested Location
        let latitude = try container.decode(Double.self, forKey: .latitude)
        let longitude = try container.decode(Double.self, forKey: .longitude)
        let country = try container.decodeIfPresent(String.self, forKey: .country) ?? ""
        let city = try container.decodeIfPresent(String.self, forKey: .city) ?? ""
        let address = try container.decodeIfPresent(String.self, forKey: .address) ?? ""
        location = Location(latitude: latitude, longitude: longitude, address: address, city: city, country: country)
        
        // Map flat contact fields to nested ContactInfo
        let phone = try container.decodeIfPresent(String.self, forKey: .phone) ?? ""
        let email = try container.decodeIfPresent(String.self, forKey: .email) ?? ""
        let website = try container.decodeIfPresent(String.self, forKey: .website)
        contactInfo = ContactInfo(phone: phone, email: email, website: website, socialMedia: nil as [String: String]?)
        
        // Map services array of strings to Service objects
        let serviceStrings = try container.decodeIfPresent([String].self, forKey: .services) ?? []
        services = serviceStrings.map { serviceName in
            Service(
                id: UUID().uuidString,
                name: serviceName,
                description: "",
                type: Service.ServiceType.funDive,
                price: Service.Price(amount: 0, currency: "USD"),
                duration: 0,
                maxParticipants: 0,
                requirements: nil as [String]?
            )
        }
        
        // Decode photos - try different possible field names
        if let photoUrls = try? container.decodeIfPresent([String].self, forKey: .photos) {
            photos = photoUrls
        } else if let photoUrls = try? container.decodeIfPresent([String].self, forKey: .photoUrls) {
            photos = photoUrls
        } else if let photoUrls = try? container.decodeIfPresent([String].self, forKey: .photo_urls) {
            photos = photoUrls
        } else if let thumbnailUrl = try? container.decodeIfPresent(String.self, forKey: .thumbnail_url), !thumbnailUrl.isEmpty {
            photos = [thumbnailUrl]
        } else {
            photos = []
        }
        
        // Decode videos - try different possible field names
        if let videoUrls = try? container.decodeIfPresent([String].self, forKey: .videos) {
            videos = videoUrls
        } else if let videoUrls = try? container.decodeIfPresent([String].self, forKey: .videoUrls) {
            videos = videoUrls
        } else if let videoUrls = try? container.decodeIfPresent([String].self, forKey: .video_urls) {
            videos = videoUrls
        } else {
            videos = []
        }
        
        averageRating = try container.decodeIfPresent(Double.self, forKey: .averageRating) ?? 0
        reviewCount = try container.decodeIfPresent(Int.self, forKey: .reviewCount) ?? 0
        aiSummary = nil
        instructors = []
        affiliatedSites = []
        
        // Default operating hours
        operatingHours = OperatingHours()
        
        certificationAgency = try container.decodeIfPresent(String.self, forKey: .certificationAgency)
        languages = try container.decodeIfPresent([String].self, forKey: .languages) ?? []
        nitroxAvailable = try container.decodeIfPresent(Bool.self, forKey: .nitroxAvailable) ?? false
        priceFrom = try container.decodeIfPresent(Double.self, forKey: .priceFrom)
        
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
        try container.encode(location.country, forKey: .country)
        try container.encode(location.city, forKey: .city)
        try container.encode(location.address, forKey: .address)
        try container.encode(contactInfo.phone, forKey: .phone)
        try container.encode(contactInfo.email, forKey: .email)
        try container.encodeIfPresent(contactInfo.website, forKey: .website)
        try container.encode(services.map { $0.name }, forKey: .services)
        try container.encode(averageRating, forKey: .averageRating)
        try container.encode(reviewCount, forKey: .reviewCount)
        try container.encode(createdAt, forKey: .createdAt)
        try container.encode(updatedAt, forKey: .updatedAt)
    }
    
    // Memberwise initializer for manual creation (e.g., in previews)
    init(id: String, name: String, description: String, location: Location, contactInfo: ContactInfo, photos: [String] = [], videos: [String] = [], averageRating: Double = 0, reviewCount: Int = 0, aiSummary: String? = nil, instructors: [Instructor] = [], affiliatedSites: [String] = [], services: [Service] = [], operatingHours: OperatingHours = OperatingHours(), certificationAgency: String? = nil, languages: [String] = [], nitroxAvailable: Bool = false, priceFrom: Double? = nil, createdAt: Date = Date(), updatedAt: Date = Date()) {
        self.id = id
        self.name = name
        self.description = description
        self.location = location
        self.contactInfo = contactInfo
        self.photos = photos
        self.videos = videos
        self.averageRating = averageRating
        self.reviewCount = reviewCount
        self.aiSummary = aiSummary
        self.instructors = instructors
        self.affiliatedSites = affiliatedSites
        self.services = services
        self.operatingHours = operatingHours
        self.certificationAgency = certificationAgency
        self.languages = languages
        self.nitroxAvailable = nitroxAvailable
        self.priceFrom = priceFrom
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
    
    // Equatable conformance - compare by id
    static func == (lhs: DiveCenter, rhs: DiveCenter) -> Bool {
        return lhs.id == rhs.id
    }
    
    // Computed property for localized description
    var displayDescription: String {
        let language = LocalizationService.shared.currentLanguage.rawValue
        if let localized = localizedDescription?[language], !localized.isEmpty {
            return localized
        }
        return description
    }
}

struct Instructor: Identifiable, Codable {
    let id: String
    var userId: String
    var name: String
    var avatarURL: String?
    var photoURL: String? // Main photo for instructor profile
    var certifications: [String]
    var languages: [String]
    var bio: String?
    var localizedBio: [String: String]? // Localized bio
    var description: String? // Detailed description
    var localizedDescription: [String: String]? // Localized description
    var trainingSystems: [String] // e.g., ["PADI", "SSI", "NAUI"]
    var credentials: [InstructorCredential] // Professional credentials and achievements
    var averageRating: Double
    var reviewCount: Int
    var aiSummary: String? // AI-generated summary
    var schedule: [ScheduleSlot]?
    var diveCenterId: String? // Associated dive center
    
    // Custom decoding to handle API response with nested user object
    enum CodingKeys: String, CodingKey {
        case id, userId, name
        case avatarURL, photoURL
        case certifications, languages
        case bio, localizedBio, description, localizedDescription
        case trainingSystems, credentials
        case averageRating, reviewCount
        case aiSummary, schedule
        case diveCenterId
        case user
        case specialties
    }
    
    struct UserResponse: Codable {
        let firstName: String?
        let lastName: String?
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        id = try container.decode(String.self, forKey: .id)
        userId = try container.decode(String.self, forKey: .userId)
        
        // Try to decode name directly, or construct from user.firstName and user.lastName
        if let nameValue = try? container.decode(String.self, forKey: .name) {
            name = nameValue
        } else if let user = try? container.decode(UserResponse.self, forKey: .user) {
            // Construct name from user.firstName and user.lastName
            let firstName = user.firstName ?? ""
            let lastName = user.lastName ?? ""
            name = "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
            if name.isEmpty {
                name = "Unknown Instructor"
            }
        } else {
            name = "Unknown Instructor"
        }
        
        avatarURL = try container.decodeIfPresent(String.self, forKey: .avatarURL)
        photoURL = try container.decodeIfPresent(String.self, forKey: .photoURL)
        
        // Handle certifications - API might return specialties instead
        if let specialties = try? container.decode([String].self, forKey: .specialties) {
            certifications = specialties
        } else {
            certifications = try container.decodeIfPresent([String].self, forKey: .certifications) ?? []
        }
        
        languages = try container.decodeIfPresent([String].self, forKey: .languages) ?? []
        bio = try container.decodeIfPresent(String.self, forKey: .bio)
        localizedBio = try container.decodeIfPresent([String: String].self, forKey: .localizedBio)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        localizedDescription = try container.decodeIfPresent([String: String].self, forKey: .localizedDescription)
        trainingSystems = try container.decodeIfPresent([String].self, forKey: .trainingSystems) ?? []
        credentials = try container.decodeIfPresent([InstructorCredential].self, forKey: .credentials) ?? []
        averageRating = try container.decodeIfPresent(Double.self, forKey: .averageRating) ?? 0.0
        reviewCount = try container.decodeIfPresent(Int.self, forKey: .reviewCount) ?? 0
        aiSummary = try container.decodeIfPresent(String.self, forKey: .aiSummary)
        schedule = try container.decodeIfPresent([ScheduleSlot].self, forKey: .schedule)
        diveCenterId = try container.decodeIfPresent(String.self, forKey: .diveCenterId)
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        
        try container.encode(id, forKey: .id)
        try container.encode(userId, forKey: .userId)
        try container.encode(name, forKey: .name)
        try container.encodeIfPresent(avatarURL, forKey: .avatarURL)
        try container.encodeIfPresent(photoURL, forKey: .photoURL)
        try container.encode(certifications, forKey: .certifications)
        try container.encode(languages, forKey: .languages)
        try container.encodeIfPresent(bio, forKey: .bio)
        try container.encodeIfPresent(localizedBio, forKey: .localizedBio)
        try container.encodeIfPresent(description, forKey: .description)
        try container.encodeIfPresent(localizedDescription, forKey: .localizedDescription)
        try container.encode(trainingSystems, forKey: .trainingSystems)
        try container.encode(credentials, forKey: .credentials)
        try container.encode(averageRating, forKey: .averageRating)
        try container.encode(reviewCount, forKey: .reviewCount)
        try container.encodeIfPresent(aiSummary, forKey: .aiSummary)
        try container.encodeIfPresent(schedule, forKey: .schedule)
        try container.encodeIfPresent(diveCenterId, forKey: .diveCenterId)
    }
    
    // Explicit initializer for manual creation (e.g., test data)
    init(
        id: String,
        userId: String,
        name: String,
        avatarURL: String? = nil,
        photoURL: String? = nil,
        certifications: [String] = [],
        languages: [String] = [],
        bio: String? = nil,
        localizedBio: [String: String]? = nil,
        description: String? = nil,
        localizedDescription: [String: String]? = nil,
        trainingSystems: [String] = [],
        credentials: [InstructorCredential] = [],
        averageRating: Double = 0.0,
        reviewCount: Int = 0,
        aiSummary: String? = nil,
        schedule: [ScheduleSlot]? = nil,
        diveCenterId: String? = nil
    ) {
        self.id = id
        self.userId = userId
        self.name = name
        self.avatarURL = avatarURL
        self.photoURL = photoURL
        self.certifications = certifications
        self.languages = languages
        self.bio = bio
        self.localizedBio = localizedBio
        self.description = description
        self.localizedDescription = localizedDescription
        self.trainingSystems = trainingSystems
        self.credentials = credentials
        self.averageRating = averageRating
        self.reviewCount = reviewCount
        self.aiSummary = aiSummary
        self.schedule = schedule
        self.diveCenterId = diveCenterId
    }
    
    // Computed property for localized bio
    var displayBio: String? {
        guard let bio = bio else { return nil }
        let language = LocalizationService.shared.currentLanguage.rawValue
        if let localized = localizedBio?[language], !localized.isEmpty {
            return localized
        }
        return bio
    }
    
    // Computed property for localized description
    var displayDescription: String? {
        guard let description = description else { return nil }
        let language = LocalizationService.shared.currentLanguage.rawValue
        if let localized = localizedDescription?[language], !localized.isEmpty {
            return localized
        }
        return description
    }
    
    struct ScheduleSlot: Codable {
        var date: Date
        var startTime: String
        var endTime: String
        var isAvailable: Bool
    }
    
    struct InstructorCredential: Identifiable, Codable {
        let id: String
        var title: String // e.g., "PADI Master Instructor", "SSI Course Director"
        var organization: String // e.g., "PADI", "SSI"
        var issueDate: Date?
        var credentialNumber: String?
        var description: String?
        var localizedDescription: [String: String]? // Localized descriptions
        
        // Computed property for localized description
        var displayDescription: String? {
            guard let description = description else { return nil }
            let language = LocalizationService.shared.currentLanguage.rawValue
            if let localized = localizedDescription?[language], !localized.isEmpty {
                return localized
            }
            return description
        }
    }
}

struct Service: Identifiable, Codable {
    let id: String
    var name: String
    var description: String
    var localizedDescription: [String: String]? // Localized descriptions
    var type: ServiceType
    var price: Price
    var duration: Int // in minutes
    var maxParticipants: Int
    var requirements: [String]? // e.g., ["Open Water Certified"]
    
    // Computed property for localized description
    var displayDescription: String {
        let language = LocalizationService.shared.currentLanguage.rawValue
        if let localized = localizedDescription?[language], !localized.isEmpty {
            return localized
        }
        return description
    }
    
    enum ServiceType: String, Codable {
        case funDive = "fun_dive"
        case course = "course"
        case specialty = "specialty"
        case equipmentRental = "equipment_rental"
    }
    
    struct Price: Codable {
        var amount: Double
        var currency: String // "USD", "EUR", etc.
    }
}
