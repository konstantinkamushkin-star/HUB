//
//  Course.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

struct Course: Identifiable, Equatable {
    let id: String
    var name: String
    var level: CourseLevel
    var description: String
    var localizedDescription: [String: String]? // Localized descriptions
    var trainingSystems: [String] // e.g., ["PADI", "SSI", "NAUI"]
    var program: [CourseModule]
    var duration: Int // in days
    var prerequisites: [String]? // Required certifications
    var diveCenterId: String? // If course belongs to a dive center
    /// Первый из списка инструкторов (совместимость со старым API).
    var instructorId: String?
    /// User IDs инструкторов центра, ведущих курс (из `dive_centers.instructor_ids`).
    var instructorIds: [String]
    var photos: [String] // URLs
    var createdAt: Date
    var updatedAt: Date
    
    /// Нормализованный список: `instructorIds`, иначе одиночный `instructorId`.
    var assignedInstructorUserIds: [String] {
        if !instructorIds.isEmpty { return instructorIds }
        if let i = instructorId { return [i] }
        return []
    }
    
    // Computed property for localized description
    var displayDescription: String {
        let language = LocalizationService.shared.currentLanguage.rawValue
        if let localized = localizedDescription?[language], !localized.isEmpty {
            return localized
        }
        return description
    }
    
    init(
        id: String,
        name: String,
        level: CourseLevel,
        description: String,
        localizedDescription: [String: String]? = nil,
        trainingSystems: [String],
        program: [CourseModule],
        duration: Int,
        prerequisites: [String]?,
        diveCenterId: String?,
        instructorId: String?,
        instructorIds: [String] = [],
        photos: [String],
        createdAt: Date,
        updatedAt: Date
    ) {
        self.id = id
        self.name = name
        self.level = level
        self.description = description
        self.localizedDescription = localizedDescription
        self.trainingSystems = trainingSystems
        self.program = program
        self.duration = duration
        self.prerequisites = prerequisites
        self.diveCenterId = diveCenterId
        let norm = !instructorIds.isEmpty
            ? instructorIds
            : (instructorId.map { [$0] } ?? [])
        self.instructorIds = norm
        self.instructorId = norm.first
        self.photos = photos
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
    
    enum CodingKeys: String, CodingKey {
        case id
        case name
        case level
        case description
        case localizedDescription
        case trainingSystems
        case program = "modules" // Бэкенд использует "modules", фронтенд - "program"
        case duration
        case prerequisites
        case diveCenterId
        case instructorId
        case instructorIds
        case photos
        case createdAt
        case updatedAt
    }
    
    enum CourseLevel: String, Codable {
        case basic = "basic"
        case advanced = "advanced"
        case professional = "professional"
        case technical = "technical"
        case specialization = "specialization"
        
        var displayName: String {
            let L = LocalizationService.shared
            switch self {
            case .basic: return L.localizedString("basic", table: "courses")
            case .advanced: return L.localizedString("advanced", table: "courses")
            case .professional: return L.localizedString("professional", table: "courses")
            case .technical: return L.localizedString("technical", table: "courses")
            case .specialization: return L.localizedString("specialization", table: "courses")
            }
        }
    }
    
    struct CourseModule: Identifiable, Codable, Equatable {
        let id: String
        var title: String
        var description: String
        var localizedDescription: [String: String]? // Localized descriptions
        var duration: Int // in hours
        var moduleType: ModuleType
        var order: Int
        
        // Computed property for localized description
        var displayDescription: String {
            let language = LocalizationService.shared.currentLanguage.rawValue
            if let localized = localizedDescription?[language], !localized.isEmpty {
                return localized
            }
            return description
        }
        
        enum ModuleType: String, Codable {
            case theory = "theory"
            case confinedWater = "confined_water"
            case openWater = "open_water"
            case exam = "exam"

            /// Локализованная подпись для UI (таблица `courses`: theory, openWater, …).
            var localizedTitle: String {
                let L = LocalizationService.shared
                switch self {
                case .theory: return L.localizedString("theory", table: "courses")
                case .confinedWater: return L.localizedString("confinedWater", table: "courses")
                case .openWater: return L.localizedString("openWater", table: "courses")
                case .exam: return L.localizedString("exam", table: "courses")
                }
            }
        }
    }
}

extension Course: Codable {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        let levelRaw = try c.decode(String.self, forKey: .level)
        level = CourseLevel(rawValue: levelRaw) ?? .basic
        description = try c.decodeIfPresent(String.self, forKey: .description) ?? ""
        localizedDescription = try c.decodeIfPresent([String: String].self, forKey: .localizedDescription)
        trainingSystems = try c.decodeIfPresent([String].self, forKey: .trainingSystems) ?? []
        program = try c.decodeIfPresent([CourseModule].self, forKey: .program) ?? []
        duration = try c.decodeIfPresent(Int.self, forKey: .duration) ?? 0
        prerequisites = try c.decodeIfPresent([String].self, forKey: .prerequisites)
        diveCenterId = try c.decodeIfPresent(String.self, forKey: .diveCenterId)
        let decodedSingle = try c.decodeIfPresent(String.self, forKey: .instructorId)
        let decodedMany = try c.decodeIfPresent([String].self, forKey: .instructorIds) ?? []
        let norm = !decodedMany.isEmpty ? decodedMany : (decodedSingle.map { [$0] } ?? [])
        instructorIds = norm
        instructorId = norm.first
        photos = try c.decodeIfPresent([String].self, forKey: .photos) ?? []
        createdAt = try c.decode(Date.self, forKey: .createdAt)
        updatedAt = try c.decode(Date.self, forKey: .updatedAt)
    }
    
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(level.rawValue, forKey: .level)
        try c.encode(description, forKey: .description)
        try c.encodeIfPresent(localizedDescription, forKey: .localizedDescription)
        try c.encode(trainingSystems, forKey: .trainingSystems)
        try c.encode(program, forKey: .program)
        try c.encode(duration, forKey: .duration)
        try c.encodeIfPresent(prerequisites, forKey: .prerequisites)
        try c.encodeIfPresent(diveCenterId, forKey: .diveCenterId)
        try c.encodeIfPresent(instructorId, forKey: .instructorId)
        try c.encode(instructorIds, forKey: .instructorIds)
        try c.encode(photos, forKey: .photos)
        try c.encode(createdAt, forKey: .createdAt)
        try c.encode(updatedAt, forKey: .updatedAt)
    }
}
