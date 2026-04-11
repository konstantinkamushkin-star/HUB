//
//  Trip.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

struct Trip: Identifiable, Codable, Equatable {
    let id: String
    var organizerId: String // User ID or Dive Center ID
    var organizerType: OrganizerType // dive_center or user
    var tripType: TripType
    var hotelId: String? // For daily trips
    var yachtId: String? // For safari trips
    var country: String
    var region: String?
    var startDate: Date
    var endDate: Date
    var minimumCertificationLevel: String?
    var minimumDives: Int?
    var description: String
    var photos: [String] // URLs
    var totalSpots: Int
    var bookedSpots: Int
    var participants: [TripParticipant]
    var availableCourses: [String] // Course IDs
    var nitroxAvailable: Bool
    var groupLeaderId: String? // Instructor ID (only for dive centers)
    var groupLeaderDiveCenterId: String? // Dive Center ID from groupLeader (for filtering trips by dive center)
    var program: [TripProgramDay]
    var additionalExpenses: [AdditionalExpense]
    var equipmentRentalAvailable: Bool
    var priceDetails: PriceDetails
    var createdAt: Date
    var updatedAt: Date
    
    // Map backend's "programDays" to frontend's "program"
    enum CodingKeys: String, CodingKey {
        case id
        case organizerId
        case organizerType
        case tripType
        case hotelId
        case yachtId
        case country
        case region
        case startDate
        case endDate
        case minimumCertificationLevel
        case minimumDives
        case description
        case photos
        case totalSpots
        case bookedSpots
        case participants
        case availableCourses
        case nitroxAvailable
        case groupLeaderId
        case groupLeader
        case program = "programDays" // Backend uses "programDays", frontend uses "program"
        case additionalExpenses
        case equipmentRentalAvailable
        case priceDetails
        case createdAt
        case updatedAt
    }
    
    // Nested coding keys for groupLeader
    enum GroupLeaderCodingKeys: String, CodingKey {
        case diveCenterId
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        organizerId = try container.decode(String.self, forKey: .organizerId)
        organizerType = try container.decode(OrganizerType.self, forKey: .organizerType)
        tripType = try container.decode(TripType.self, forKey: .tripType)
        hotelId = try container.decodeIfPresent(String.self, forKey: .hotelId)
        yachtId = try container.decodeIfPresent(String.self, forKey: .yachtId)
        country = try container.decode(String.self, forKey: .country)
        region = try container.decodeIfPresent(String.self, forKey: .region)
        startDate = try container.decode(Date.self, forKey: .startDate)
        endDate = try container.decode(Date.self, forKey: .endDate)
        minimumCertificationLevel = try container.decodeIfPresent(String.self, forKey: .minimumCertificationLevel)
        minimumDives = try container.decodeIfPresent(Int.self, forKey: .minimumDives)
        description = try container.decode(String.self, forKey: .description)
        photos = try container.decode([String].self, forKey: .photos)
        totalSpots = try container.decode(Int.self, forKey: .totalSpots)
        bookedSpots = try container.decode(Int.self, forKey: .bookedSpots)
        participants = try container.decodeIfPresent([TripParticipant].self, forKey: .participants) ?? []
        availableCourses = try container.decode([String].self, forKey: .availableCourses)
        nitroxAvailable = try container.decode(Bool.self, forKey: .nitroxAvailable)
        groupLeaderId = try container.decodeIfPresent(String.self, forKey: .groupLeaderId)
        
        // Decode groupLeader.diveCenterId if present (for filtering trips by dive center)
        if let groupLeaderContainer = try? container.nestedContainer(keyedBy: GroupLeaderCodingKeys.self, forKey: .groupLeader) {
            groupLeaderDiveCenterId = try? groupLeaderContainer.decodeIfPresent(String.self, forKey: .diveCenterId)
        } else {
            groupLeaderDiveCenterId = nil
        }
        
        program = try container.decodeIfPresent([TripProgramDay].self, forKey: .program) ?? []
        additionalExpenses = try container.decodeIfPresent([AdditionalExpense].self, forKey: .additionalExpenses) ?? []
        equipmentRentalAvailable = try container.decode(Bool.self, forKey: .equipmentRentalAvailable)
        priceDetails = try container.decode(PriceDetails.self, forKey: .priceDetails)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
        updatedAt = try container.decode(Date.self, forKey: .updatedAt)
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(organizerId, forKey: .organizerId)
        try container.encode(organizerType, forKey: .organizerType)
        try container.encode(tripType, forKey: .tripType)
        try container.encodeIfPresent(hotelId, forKey: .hotelId)
        try container.encodeIfPresent(yachtId, forKey: .yachtId)
        try container.encode(country, forKey: .country)
        try container.encodeIfPresent(region, forKey: .region)
        try container.encode(startDate, forKey: .startDate)
        try container.encode(endDate, forKey: .endDate)
        try container.encodeIfPresent(minimumCertificationLevel, forKey: .minimumCertificationLevel)
        try container.encodeIfPresent(minimumDives, forKey: .minimumDives)
        try container.encode(description, forKey: .description)
        try container.encode(photos, forKey: .photos)
        try container.encode(totalSpots, forKey: .totalSpots)
        try container.encode(bookedSpots, forKey: .bookedSpots)
        try container.encode(participants, forKey: .participants)
        try container.encode(availableCourses, forKey: .availableCourses)
        try container.encode(nitroxAvailable, forKey: .nitroxAvailable)
        try container.encodeIfPresent(groupLeaderId, forKey: .groupLeaderId)
        try container.encode(program, forKey: .program)
        try container.encode(additionalExpenses, forKey: .additionalExpenses)
        try container.encode(equipmentRentalAvailable, forKey: .equipmentRentalAvailable)
        try container.encode(priceDetails, forKey: .priceDetails)
        try container.encode(createdAt, forKey: .createdAt)
        try container.encode(updatedAt, forKey: .updatedAt)
    }
    
    init(
        id: String,
        organizerId: String,
        organizerType: OrganizerType,
        tripType: TripType,
        hotelId: String?,
        yachtId: String?,
        country: String,
        region: String? = nil,
        startDate: Date,
        endDate: Date,
        minimumCertificationLevel: String? = nil,
        minimumDives: Int? = nil,
        description: String,
        photos: [String],
        totalSpots: Int,
        bookedSpots: Int,
        participants: [TripParticipant],
        availableCourses: [String],
        nitroxAvailable: Bool,
        groupLeaderId: String?,
        groupLeaderDiveCenterId: String? = nil,
        program: [TripProgramDay],
        additionalExpenses: [AdditionalExpense],
        equipmentRentalAvailable: Bool,
        priceDetails: PriceDetails,
        createdAt: Date,
        updatedAt: Date
    ) {
        self.id = id
        self.organizerId = organizerId
        self.organizerType = organizerType
        self.tripType = tripType
        self.hotelId = hotelId
        self.yachtId = yachtId
        self.country = country
        self.region = region
        self.startDate = startDate
        self.endDate = endDate
        self.minimumCertificationLevel = minimumCertificationLevel
        self.minimumDives = minimumDives
        self.description = description
        self.photos = photos
        self.totalSpots = totalSpots
        self.bookedSpots = bookedSpots
        self.participants = participants
        self.availableCourses = availableCourses
        self.nitroxAvailable = nitroxAvailable
        self.groupLeaderId = groupLeaderId
        self.groupLeaderDiveCenterId = groupLeaderDiveCenterId
        self.program = program
        self.additionalExpenses = additionalExpenses
        self.equipmentRentalAvailable = equipmentRentalAvailable
        self.priceDetails = priceDetails
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
    
    enum OrganizerType: String, Codable {
        case diveCenter = "dive_center"
        case user = "user"
    }
    
    enum TripType: String, Codable {
        case daily = "daily"
        case safari = "safari"
    }
    
    struct TripParticipant: Identifiable, Codable, Equatable {
        let id: String
        var userId: String
        var name: String
        var email: String?
        var phoneNumber: String?
        var certificationLevel: String?
        var isDiving: Bool // Diving or non-diving participant
        var bookedAt: Date
    }
    
    struct TripProgramDay: Identifiable, Codable, Equatable {
        let id: String
        var date: Date
        var activities: [ProgramActivity]
        var description: String?
        
        struct ProgramActivity: Identifiable, Codable, Equatable {
            let id: String
            var time: String // "09:00"
            var activity: String
            var diveSiteId: String?
            var diveCenterId: String?
            var notes: String?
        }
    }
    
    struct AdditionalExpense: Identifiable, Codable, Equatable {
        let id: String
        var expenseType: ExpenseType
        var description: String
        var cost: Double
        var currency: String
        
        enum ExpenseType: String, Codable {
            case flight = "flight"
            case transfer = "transfer"
            case nutrition = "nutrition"
            case reserve = "reserve"
            case other = "other"
        }
    }
    
    struct PriceDetails: Codable, Equatable {
        var roomPrices: [RoomPrice]? // For daily trips with hotel
        var yachtPrices: [YachtPrice]? // For safari trips
        var divingPrice: Double? // Price for diving participants
        var nonDivingPrice: Double? // Price for non-diving participants
        var currency: String
        
        struct RoomPrice: Identifiable, Codable, Equatable {
            let id: String
            var roomType: String // e.g., "Single", "Double", "Triple"
            var roomCount: Int // Количество номеров данного типа
            var divingPrice: Double // Цена для ныряющих
            var nonDivingPrice: Double // Цена для неныряющих
            
            init(id: String, roomType: String, roomCount: Int, divingPrice: Double, nonDivingPrice: Double) {
                self.id = id
                self.roomType = roomType
                self.roomCount = roomCount
                self.divingPrice = divingPrice
                self.nonDivingPrice = nonDivingPrice
            }
            
            init(from decoder: Decoder) throws {
                let container = try decoder.container(keyedBy: CodingKeys.self)
                id = try container.decode(String.self, forKey: .id)
                roomType = try container.decode(String.self, forKey: .roomType)
                // Default to 1 if roomCount is missing (backward compatibility)
                roomCount = try container.decodeIfPresent(Int.self, forKey: .roomCount) ?? 1
                divingPrice = try container.decode(Double.self, forKey: .divingPrice)
                nonDivingPrice = try container.decode(Double.self, forKey: .nonDivingPrice)
            }
            
            enum CodingKeys: String, CodingKey {
                case id, roomType, roomCount, divingPrice, nonDivingPrice
            }
        }
        
        struct YachtPrice: Identifiable, Codable, Equatable {
            let id: String
            var cabinType: String // e.g., "Standard", "Deluxe", "Master"
            var cabinCount: Int // Количество кают данного типа
            var divingPrice: Double // Цена для ныряющих
            var nonDivingPrice: Double // Цена для неныряющих
            
            init(id: String, cabinType: String, cabinCount: Int, divingPrice: Double, nonDivingPrice: Double) {
                self.id = id
                self.cabinType = cabinType
                self.cabinCount = cabinCount
                self.divingPrice = divingPrice
                self.nonDivingPrice = nonDivingPrice
            }
            
            init(from decoder: Decoder) throws {
                let container = try decoder.container(keyedBy: CodingKeys.self)
                id = try container.decode(String.self, forKey: .id)
                cabinType = try container.decode(String.self, forKey: .cabinType)
                // Default to 1 if cabinCount is missing (backward compatibility)
                cabinCount = try container.decodeIfPresent(Int.self, forKey: .cabinCount) ?? 1
                divingPrice = try container.decode(Double.self, forKey: .divingPrice)
                nonDivingPrice = try container.decode(Double.self, forKey: .nonDivingPrice)
            }
            
            enum CodingKeys: String, CodingKey {
                case id, cabinType, cabinCount, divingPrice, nonDivingPrice
            }
        }
    }
    
    var availableSpots: Int {
        totalSpots - bookedSpots
    }
    
    var isFullyBooked: Bool {
        bookedSpots >= totalSpots
    }
}

// MARK: - Hotel and Yacht Models

struct Hotel: Identifiable, Codable {
    let id: String
    var name: String
    var description: String
    var address: String
    var city: String
    var country: String
    var latitude: Double?
    var longitude: Double?
    var photos: [String]
    var rating: Double?
    var amenities: [String]
    var roomTypes: [RoomType]
    var createdAt: Date
    var updatedAt: Date
    
    // Computed property for backward compatibility
    var location: Location {
        get {
            Location(address: address, city: city, country: country, latitude: latitude, longitude: longitude)
        }
        set {
            address = newValue.address
            city = newValue.city
            country = newValue.country
            latitude = newValue.latitude
            longitude = newValue.longitude
        }
    }
    
    // Exclude location from encoding/decoding since it's a computed property
    enum CodingKeys: String, CodingKey {
        case id
        case name
        case description
        case address
        case city
        case country
        case latitude
        case longitude
        case photos
        case rating
        case amenities
        case roomTypes
        case createdAt
        case updatedAt
    }
    
    struct Location: Codable {
        var address: String
        var city: String
        var country: String
        var latitude: Double?
        var longitude: Double?
    }
    
    struct RoomType: Identifiable, Codable {
        let id: String
        var name: String // "Single", "Double", "Triple"
        var description: String
        var maxOccupancy: Int
    }
}

struct Yacht: Identifiable, Codable {
    let id: String
    var name: String
    var description: String
    var photos: [String]
    var length: Double? // in meters
    var capacity: Int
    var cabinTypes: [CabinType]
    var amenities: [String]
    var createdAt: Date
    var updatedAt: Date
    
    struct CabinType: Identifiable, Codable {
        let id: String
        var name: String // "Standard", "Deluxe", "Master"
        var description: String
        var capacity: Int
    }
}
