//
//  Course.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

struct Course: Identifiable, Codable, Equatable {
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
    var instructorId: String? // If course is taught by specific instructor
    var photos: [String] // URLs
    var createdAt: Date
    var updatedAt: Date
    
    // Computed property for localized description
    var displayDescription: String {
        let language = LocalizationService.shared.currentLanguage.rawValue
        if let localized = localizedDescription?[language], !localized.isEmpty {
            return localized
        }
        return description
    }
    
    // CodingKeys для маппинга полей бэкенда
    enum CodingKeys: String, CodingKey {
        case id
        case name
        case level
        case description
        case trainingSystems
        case program = "modules" // Бэкенд использует "modules", фронтенд - "program"
        case duration
        case prerequisites
        case diveCenterId
        case instructorId
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
            switch self {
            case .basic: return "Basic"
            case .advanced: return "Advanced"
            case .professional: return "Professional"
            case .technical: return "Technical"
            case .specialization: return "Specialization"
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
        }
    }
}
