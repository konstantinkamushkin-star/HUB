//
//  Country.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

struct Country: Identifiable, Codable, Equatable {
    let id: String // ISO country code (e.g., "US", "RU", "EG")
    var name: String // Default name (usually English)
    var localizedNames: [String: String] // Localized names: {"en": "United States", "ru": "Соединенные Штаты", "es": "Estados Unidos", ...}
    var regions: [Region]? // Optional list of diving regions
    
    struct Region: Identifiable, Codable, Equatable {
        var name: String
        var localizedNames: [String: String]
        
        var id: String { name } // Use name as ID
        
        // Computed property to get the name in the current language
        var displayName: String {
            let language = LocalizationService.shared.currentLanguage.rawValue
            if let localized = localizedNames[language], !localized.isEmpty {
                return localized
            }
            return name
        }
    }
    
    // Computed property to get the name in the current language
    var displayName: String {
        let language = LocalizationService.shared.currentLanguage.rawValue
        if let localized = localizedNames[language], !localized.isEmpty {
            return localized
        }
        return name
    }
    
    enum CodingKeys: String, CodingKey {
        case id
        case name
        case localizedNames
        case regions
    }
    
    init(id: String, name: String, localizedNames: [String: String] = [:], regions: [Region]? = nil) {
        self.id = id
        self.name = name
        self.localizedNames = localizedNames
        self.regions = regions
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        
        // Safely decode localizedNames, handling potential duplicate keys
        if let localizedNamesData = try? container.decodeIfPresent([String: String].self, forKey: .localizedNames) {
            localizedNames = localizedNamesData
        } else {
            localizedNames = [:]
        }
        
        regions = try container.decodeIfPresent([Region].self, forKey: .regions)
    }
}
