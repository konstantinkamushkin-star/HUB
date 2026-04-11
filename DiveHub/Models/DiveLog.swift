//
//  DiveLog.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import CoreLocation

struct DiveLog: Identifiable, Codable {
    let id: String
    var userId: String
    var diveNumber: Int
    var date: Date
    var time: String // Entry time
    var location: Location
    var diveSiteId: String?
    var diveCenterId: String?
    var instructorId: String?
    var buddy: String? // Buddy name
    var maxDepth: Double // in meters
    var averageDepth: Double // in meters
    var bottomTime: Int // in minutes
    var surfaceInterval: Int? // in minutes (for repetitive dives)
    var waterTemperature: Double? // in Celsius
    var visibility: Double? // in meters
    var current: String? // e.g., "None", "Moderate", "Strong"
    var conditions: String? // General conditions description
    var gearUsed: [GearItem]
    var notes: String
    var photos: [String] // URLs (PRO only)
    var videos: [String] // URLs (PRO only)
    var fishSpecies: [String] // List of fish species seen
    var sensorData: SensorData? // PRO only
    var isPublished: Bool? // Whether this dive is published to feed
    var createdAt: Date
    var updatedAt: Date
    
    // Custom decoding to handle backend response structure
    enum CodingKeys: String, CodingKey {
        case id, userId, diveSiteId, date, startTime, endTime, duration, maxDepth, averageDepth
        case waterTemperature, visibility, current, diveType, notes, gearUsed
        case photoUrls, videoUrls, diveComputerData, createdAt, updatedAt
        // Frontend-only fields (not in backend response)
        case diveNumber, time, location, diveCenterId, instructorId, buddy
        case bottomTime, surfaceInterval, conditions, photos, videos, sensorData, fishSpecies, isPublished
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        // Backend fields
        id = try container.decode(String.self, forKey: .id)
        userId = try container.decode(String.self, forKey: .userId)
        diveSiteId = try container.decodeIfPresent(String.self, forKey: .diveSiteId)
        
        // Date handling
        date = try container.decode(Date.self, forKey: .date)
        
        // Time handling - convert startTime DateTime to time String
        if let startTime = try? container.decodeIfPresent(Date.self, forKey: .startTime) {
            let formatter = DateFormatter()
            formatter.dateFormat = "HH:mm"
            time = formatter.string(from: startTime)
        } else {
            time = ""
        }
        
        // Location - not in backend response, use default
        // We'll populate name from diveSiteId later if available
        location = Location(latitude: 0, longitude: 0, name: "")
        
        // Frontend-only fields
        diveNumber = 0
        diveCenterId = nil
        instructorId = nil
        buddy = nil
        surfaceInterval = nil
        conditions = nil
        
        // Depth and time
        maxDepth = try container.decode(Double.self, forKey: .maxDepth)
        averageDepth = try container.decodeIfPresent(Double.self, forKey: .averageDepth) ?? 0
        bottomTime = try container.decode(Int.self, forKey: .duration) // Backend uses "duration"
        
        // Optional fields
        waterTemperature = try container.decodeIfPresent(Double.self, forKey: .waterTemperature)
        visibility = try container.decodeIfPresent(Double.self, forKey: .visibility)
        current = try container.decodeIfPresent(String.self, forKey: .current)
        notes = try container.decodeIfPresent(String.self, forKey: .notes) ?? ""
        
        // Arrays
        photos = try container.decodeIfPresent([String].self, forKey: .photoUrls) ?? []
        videos = try container.decodeIfPresent([String].self, forKey: .videoUrls) ?? []
        fishSpecies = try container.decodeIfPresent([String].self, forKey: .fishSpecies) ?? []
        
        // Gear - backend uses Json, we'll decode as empty array for now
        gearUsed = []
        
        // Published flag
        isPublished = try container.decodeIfPresent(Bool.self, forKey: .isPublished)
        
        // Sensor data - not in backend response
        sensorData = nil
        
        // Timestamps
        createdAt = try container.decode(Date.self, forKey: .createdAt)
        updatedAt = try container.decode(Date.self, forKey: .updatedAt)
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        
        // Backend fields
        try container.encode(id, forKey: .id)
        try container.encode(userId, forKey: .userId)
        try container.encodeIfPresent(diveSiteId, forKey: .diveSiteId)
        try container.encode(date, forKey: .date)
        
        // Convert time string to startTime Date
        if !time.isEmpty {
            let formatter = DateFormatter()
            formatter.dateFormat = "HH:mm"
            if let timeDate = formatter.date(from: time) {
                let calendar = Calendar.current
                let components = calendar.dateComponents([.hour, .minute], from: timeDate)
                if let combinedDate = calendar.date(bySettingHour: components.hour ?? 0, minute: components.minute ?? 0, second: 0, of: date) {
                    try container.encode(combinedDate, forKey: .startTime)
                }
            }
        }
        
        // Depth and time
        try container.encode(maxDepth, forKey: .maxDepth)
        try container.encodeIfPresent(averageDepth, forKey: .averageDepth)
        try container.encode(bottomTime, forKey: .duration)
        
        // Optional fields
        try container.encodeIfPresent(waterTemperature, forKey: .waterTemperature)
        try container.encodeIfPresent(visibility, forKey: .visibility)
        try container.encodeIfPresent(current, forKey: .current)
        try container.encodeIfPresent(notes.isEmpty ? nil : notes, forKey: .notes)
        
        // Arrays - backend uses photoUrls and videoUrls
        try container.encodeIfPresent(photos.isEmpty ? nil : photos, forKey: .photoUrls)
        try container.encodeIfPresent(videos.isEmpty ? nil : videos, forKey: .videoUrls)
        try container.encodeIfPresent(fishSpecies.isEmpty ? nil : fishSpecies, forKey: .fishSpecies)
        
        // Gear - encode as empty JSON array for now (backend expects Json type)
        try container.encode([String: String](), forKey: .gearUsed)
        
        // Published flag
        try container.encodeIfPresent(isPublished, forKey: .isPublished)
        
        // Timestamps
        try container.encode(createdAt, forKey: .createdAt)
        try container.encode(updatedAt, forKey: .updatedAt)
    }
    
    // Default initializer for creating new logs
    init(id: String = UUID().uuidString, userId: String, diveNumber: Int = 0, date: Date, time: String = "", location: Location, diveSiteId: String? = nil, diveCenterId: String? = nil, instructorId: String? = nil, buddy: String? = nil, maxDepth: Double, averageDepth: Double = 0, bottomTime: Int, surfaceInterval: Int? = nil, waterTemperature: Double? = nil, visibility: Double? = nil, current: String? = nil, conditions: String? = nil, gearUsed: [GearItem] = [], notes: String = "", photos: [String] = [], videos: [String] = [], fishSpecies: [String] = [], sensorData: SensorData? = nil, isPublished: Bool? = nil, createdAt: Date = Date(), updatedAt: Date = Date()) {
        self.id = id
        self.userId = userId
        self.diveNumber = diveNumber
        self.date = date
        self.time = time
        self.location = location
        self.diveSiteId = diveSiteId
        self.diveCenterId = diveCenterId
        self.instructorId = instructorId
        self.buddy = buddy
        self.maxDepth = maxDepth
        self.averageDepth = averageDepth
        self.bottomTime = bottomTime
        self.surfaceInterval = surfaceInterval
        self.waterTemperature = waterTemperature
        self.visibility = visibility
        self.current = current
        self.conditions = conditions
        self.gearUsed = gearUsed
        self.notes = notes
        self.photos = photos
        self.videos = videos
        self.fishSpecies = fishSpecies
        self.sensorData = sensorData
        self.isPublished = isPublished
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
    
    struct Location: Codable {
        var latitude: Double
        var longitude: Double
        var name: String
        var coordinate: CLLocationCoordinate2D {
            CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        }
    }
    
    struct GearItem: Identifiable, Codable {
        let id: String
        var name: String
        var type: GearType
        var manufacturer: String?
        var model: String?
        
        enum GearType: String, Codable {
            case wetsuit = "wetsuit"
            case bcd = "bcd"
            case regulator = "regulator"
            case fins = "fins"
            case mask = "mask"
            case computer = "computer"
            case other = "other"
        }
    }
    
    struct SensorData: Codable {
        var depthProfile: [DepthPoint]? // Time series data
        var temperatureProfile: [TemperaturePoint]?
        var airConsumption: Double? // Bar/min
        
        struct DepthPoint: Codable {
            var time: Int // seconds from start
            var depth: Double // meters
        }
        
        struct TemperaturePoint: Codable {
            var time: Int // seconds from start
            var temperature: Double // Celsius
        }
    }
}

struct DiveStatistics: Codable {
    var totalDives: Int
    var totalBottomTime: Int // minutes
    var deepestDive: Double // meters
    var longestDive: Int // minutes
    var averageDepth: Double // meters
    var averageWaterTemperature: Double?
    var averageVisibility: Double?
    var uniqueDiveSitesCount: Int
    var uniqueDiveCentersCount: Int
    var favoriteSites: [String] // Site IDs
    var diveByMonth: [String: Int] // "2026-01": 5
    var diveByType: [String: Int] // "wreck": 10, "reef": 25
    var milestones: [Milestone]
    
    struct Milestone: Identifiable, Codable {
        let id: String
        var title: String
        var description: String
        var achievedAt: Date
    }
}
