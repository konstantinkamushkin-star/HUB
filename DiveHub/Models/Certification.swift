//
//  Certification.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

// Ассоциации дайвинга
enum DivingAssociation: String, Codable, CaseIterable, Identifiable {
    case padi = "PADI"
    case sdi = "SDI"
    case cmas = "CMAS"
    case naui = "NAUI"
    case ssi = "SSI"
    case bsac = "BSAC"
    case gue = "GUE"
    case iantd = "IANTD"
    case tdi = "TDI"
    case other = "OTHER"
    
    var id: String { rawValue }
    
    var displayName: String {
        switch self {
        case .padi: return "PADI"
        case .sdi: return "SDI"
        case .cmas: return "CMAS"
        case .naui: return "NAUI"
        case .ssi: return "SSI"
        case .bsac: return "BSAC"
        case .gue: return "GUE"
        case .iantd: return "IANTD"
        case .tdi: return "TDI"
        case .other: return "Другая"
        }
    }
}

// Уровни сертификации
enum CertificationLevel: String, Codable, CaseIterable, Identifiable {
    // Начальные уровни
    case openWater = "OPEN_WATER"
    case advancedOpenWater = "ADVANCED_OPEN_WATER"
    case rescueDiver = "RESCUE_DIVER"
    case divemaster = "DIVEMASTER"
    case assistantInstructor = "ASSISTANT_INSTRUCTOR"
    case instructor = "INSTRUCTOR"
    case masterInstructor = "MASTER_INSTRUCTOR"
    case courseDirector = "COURSE_DIRECTOR"
    
    // CMAS уровни
    case cmasOneStar = "CMAS_ONE_STAR"
    case cmasTwoStar = "CMAS_TWO_STAR"
    case cmasThreeStar = "CMAS_THREE_STAR"
    case cmasFourStar = "CMAS_FOUR_STAR"
    case cmasInstructor = "CMAS_INSTRUCTOR"
    
    // Технические уровни
    case nitrox = "NITROX"
    case deepDiver = "DEEP_DIVER"
    case wreckDiver = "WRECK_DIVER"
    case caveDiver = "CAVE_DIVER"
    case technicalDiver = "TECHNICAL_DIVER"
    
    var id: String { rawValue }
    
    var displayName: String {
        switch self {
        case .openWater: return "Open Water Diver"
        case .advancedOpenWater: return "Advanced Open Water Diver"
        case .rescueDiver: return "Rescue Diver"
        case .divemaster: return "Divemaster"
        case .assistantInstructor: return "Assistant Instructor"
        case .instructor: return "Instructor"
        case .masterInstructor: return "Master Instructor"
        case .courseDirector: return "Course Director"
        case .cmasOneStar: return "CMAS 1*"
        case .cmasTwoStar: return "CMAS 2*"
        case .cmasThreeStar: return "CMAS 3*"
        case .cmasFourStar: return "CMAS 4*"
        case .cmasInstructor: return "CMAS Instructor"
        case .nitrox: return "Nitrox"
        case .deepDiver: return "Deep Diver"
        case .wreckDiver: return "Wreck Diver"
        case .caveDiver: return "Cave Diver"
        case .technicalDiver: return "Technical Diver"
        }
    }
    
    var category: CertificationCategory {
        switch self {
        case .openWater, .cmasOneStar:
            return .beginner
        case .advancedOpenWater, .cmasTwoStar, .nitrox, .deepDiver, .wreckDiver:
            return .advanced
        case .rescueDiver, .cmasThreeStar, .caveDiver, .technicalDiver:
            return .professional
        case .divemaster, .cmasFourStar:
            return .divemaster
        case .assistantInstructor, .instructor, .masterInstructor, .courseDirector, .cmasInstructor:
            return .instructor
        }
    }
}

enum CertificationCategory: String, Codable {
    case beginner = "beginner"
    case advanced = "advanced"
    case professional = "professional"
    case divemaster = "divemaster"
    case instructor = "instructor"
    
    var displayName: String {
        switch self {
        case .beginner: return "Начальный"
        case .advanced: return "Продвинутый"
        case .professional: return "Профессиональный"
        case .divemaster: return "Divemaster"
        case .instructor: return "Инструктор"
        }
    }
}

// Специализации
enum Specialization: String, Codable, CaseIterable, Identifiable {
    case nightDiver = "NIGHT_DIVER"
    case navigationDiver = "NAVIGATION_DIVER"
    case searchAndRecovery = "SEARCH_AND_RECOVERY"
    case driftDiver = "DRIFT_DIVER"
    case altitudeDiver = "ALTITUDE_DIVER"
    case drySuitDiver = "DRY_SUIT_DIVER"
    case underwaterPhotography = "UNDERWATER_PHOTOGRAPHY"
    case underwaterVideography = "UNDERWATER_VIDEOGRAPHY"
    case fishIdentification = "FISH_IDENTIFICATION"
    case coralReefConservation = "CORAL_REEF_CONSERVATION"
    case peakPerformanceBuoyancy = "PEAK_PERFORMANCE_BUOYANCY"
    case equipmentSpecialist = "EQUIPMENT_SPECIALIST"
    case enrichedAirNitrox = "ENRICHED_AIR_NITROX"
    case deepDiver = "DEEP_DIVER"
    case wreckDiver = "WRECK_DIVER"
    case caveDiver = "CAVE_DIVER"
    case iceDiver = "ICE_DIVER"
    case sidemountDiver = "SIDEMOUNT_DIVER"
    case rebreatherDiver = "REBREATHER_DIVER"
    
    var id: String { rawValue }
    
    var displayName: String {
        switch self {
        case .nightDiver: return "Ночной дайвер"
        case .navigationDiver: return "Навигация"
        case .searchAndRecovery: return "Поиск и подъем"
        case .driftDiver: return "Дрифт-дайвинг"
        case .altitudeDiver: return "Высотный дайвинг"
        case .drySuitDiver: return "Дайвинг в сухом костюме"
        case .underwaterPhotography: return "Подводная фотография"
        case .underwaterVideography: return "Подводная видеосъемка"
        case .fishIdentification: return "Идентификация рыб"
        case .coralReefConservation: return "Сохранение коралловых рифов"
        case .peakPerformanceBuoyancy: return "Пиковая плавучесть"
        case .equipmentSpecialist: return "Специалист по оборудованию"
        case .enrichedAirNitrox: return "Обогащенный воздух (Nitrox)"
        case .deepDiver: return "Глубоководный дайвер"
        case .wreckDiver: return "Дайвер по затонувшим судам"
        case .caveDiver: return "Пещерный дайвер"
        case .iceDiver: return "Подледный дайвинг"
        case .sidemountDiver: return "Сайдмаунт"
        case .rebreatherDiver: return "Ребризер"
        }
    }
}

// Полная сертификация с ассоциацией
struct DivingCertification: Identifiable, Codable, Equatable {
    let id: String
    var level: CertificationLevel
    var association: DivingAssociation
    var specialization: Specialization?
    var customName: String? // Для авторских курсов
    var isCustom: Bool // Является ли курс авторским
    
    var displayName: String {
        if isCustom, let customName = customName {
            return "\(association.displayName) - \(customName)"
        }
        if let specialization = specialization {
            return "\(association.displayName) - \(level.displayName) - \(specialization.displayName)"
        }
        return "\(association.displayName) - \(level.displayName)"
    }
    
    init(id: String = UUID().uuidString, level: CertificationLevel, association: DivingAssociation, specialization: Specialization? = nil, customName: String? = nil, isCustom: Bool = false) {
        self.id = id
        self.level = level
        self.association = association
        self.specialization = specialization
        self.customName = customName
        self.isCustom = isCustom
    }
}

// Система взаимо-зачета курсов
class CertificationEquivalencyService {
    static let shared = CertificationEquivalencyService()
    
    // Маппинг эквивалентных сертификаций
    // Ключ - основная сертификация, значение - список эквивалентных
    private let equivalencyMap: [CertificationLevel: Set<CertificationLevel>] = [
        // PADI Divemaster = CMAS 3*
        .divemaster: [.cmasThreeStar],
        .cmasThreeStar: [.divemaster],
        
        // PADI Advanced Open Water = CMAS 2*
        .advancedOpenWater: [.cmasTwoStar],
        .cmasTwoStar: [.advancedOpenWater],
        
        // PADI Open Water = CMAS 1*
        .openWater: [.cmasOneStar],
        .cmasOneStar: [.openWater],
        
        // PADI Rescue Diver = CMAS 2* (с некоторыми оговорками)
        .rescueDiver: [.cmasTwoStar],
        
        // CMAS 4* = PADI Divemaster (высокий уровень)
        .cmasFourStar: [.divemaster],
        
        // Инструкторские уровни
        .instructor: [.cmasInstructor],
        .cmasInstructor: [.instructor]
    ]
    
    // Проверка, эквивалентны ли две сертификации
    func areEquivalent(_ cert1: CertificationLevel, _ cert2: CertificationLevel) -> Bool {
        if cert1 == cert2 {
            return true
        }
        
        if let equivalents = equivalencyMap[cert1] {
            return equivalents.contains(cert2)
        }
        
        if let equivalents = equivalencyMap[cert2] {
            return equivalents.contains(cert1)
        }
        
        return false
    }
    
    // Получить все эквивалентные сертификации для данного уровня
    func getEquivalents(for level: CertificationLevel) -> Set<CertificationLevel> {
        var result: Set<CertificationLevel> = [level]
        
        if let equivalents = equivalencyMap[level] {
            result.formUnion(equivalents)
        }
        
        // Добавляем обратные связи
        for (key, values) in equivalencyMap {
            if values.contains(level) {
                result.insert(key)
            }
        }
        
        return result
    }
    
    // Проверка, соответствует ли сертификация пользователя требованиям поездки
    func meetsRequirement(userCert: DivingCertification?, requiredLevel: String) -> Bool {
        guard let userCert = userCert else { return false }
        
        // Парсим требуемый уровень (может быть в формате "Divemaster" или "PADI - Divemaster")
        let requiredLevelLower = requiredLevel.lowercased()
        
        // Проверяем прямое соответствие
        if userCert.level.displayName.lowercased().contains(requiredLevelLower) ||
           requiredLevelLower.contains(userCert.level.displayName.lowercased()) {
            return true
        }
        
        // Парсим требуемый уровень в CertificationLevel если возможно
        if let requiredCertLevel = parseCertificationLevel(from: requiredLevel) {
            return areEquivalent(userCert.level, requiredCertLevel)
        }
        
        // Проверяем по категории
        let requiredCategory = getCategoryForLevel(requiredLevel)
        return userCert.level.category.rawValue >= requiredCategory.rawValue
    }
    
    private func parseCertificationLevel(from string: String) -> CertificationLevel? {
        let lowercased = string.lowercased()
        
        for level in CertificationLevel.allCases {
            if level.displayName.lowercased().contains(lowercased) ||
               lowercased.contains(level.displayName.lowercased()) {
                return level
            }
        }
        
        return nil
    }
    
    private func getCategoryForLevel(_ level: String) -> CertificationCategory {
        let lowercased = level.lowercased()
        
        if lowercased.contains("divemaster") || lowercased.contains("3*") {
            return .divemaster
        } else if lowercased.contains("advanced") || lowercased.contains("2*") {
            return .advanced
        } else if lowercased.contains("rescue") || lowercased.contains("professional") {
            return .professional
        } else if lowercased.contains("instructor") {
            return .instructor
        }
        
        return .beginner
    }
}
