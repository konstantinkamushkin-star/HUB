//
//  DiverProfileModels.swift
//  DiveHub
//
//  Extended diver profile stored in `users.diver_profile` (merged on PATCH /api/auth/me).
//

import Foundation

struct DiverPrivacyPayload: Codable, Hashable {
    var showProfilePhoto: Bool?
    var showCertificationLevel: Bool?
    var showNumberOfDives: Bool?
    var showLocation: Bool?
    var showLastDive: Bool?
    var showEquipment: Bool?
    var showBuddySearchStatus: Bool?
    var showLogbook: Bool?
    var showContactOptions: Bool?
}

struct DiverEmergencyPayload: Codable, Hashable {
    var emergencyContactName: String?
    var emergencyContactPhone: String?
    var medicalNotes: String?
    var insuranceStatus: String?
    var insuranceExpiryDate: String?
}

/// Partner/admin dashboard layout stored under `diver_profile.adminDashboardLayout` (server merge on PATCH).
/// Keys match Android JSON: `managed`, `kpi`, `bookings`, `inventory`, `trips`, plus iOS-only `quick`, `cal`.
struct AdminDashboardLayoutPayload: Codable, Hashable {
    var managed: Bool?
    var kpi: Bool?
    var bookings: Bool?
    var inventory: Bool?
    var trips: Bool?
    var quick: Bool?
    var cal: Bool?
    /// Порядок блоков на главной админки: `quick`, затем `cal` (или наоборот).
    var sectionOrder: [String]?
    /// Видимость быстрых действий внутри блока `quick`.
    var quickActionInstructors: Bool?
    var quickActionServices: Bool?
    /// Порядок карточек быстрых действий: `instructors`, `services`.
    var quickActionOrder: [String]?
    /// Ключи вкладок, скрытые из нижней панели партнёра (`dashboard` игнорируется — главная всегда видна).
    var bottomBarHiddenTabs: [String]?
    /// Порядок вкладок в нижней панели (подмножество `PartnerShellTab.orderedKeys`); пусто = порядок по умолчанию.
    var bottomBarOrder: [String]?
    /// Быстрые действия на главной: ключи вкладок (`dashboard`…`profile`) и/или `instructors` (ярлык на профиль).
    var quickActionTargets: [String]?

    static let accountDefaults = AdminDashboardLayoutPayload(
        managed: true,
        kpi: true,
        bookings: true,
        inventory: true,
        trips: true,
        quick: true,
        cal: true,
        sectionOrder: ["quick", "cal"],
        quickActionInstructors: true,
        quickActionServices: true,
        quickActionOrder: ["instructors", "services"],
        bottomBarHiddenTabs: [],
        bottomBarOrder: nil,
        quickActionTargets: ["instructors", "services"],
    )
}

struct DiverProfilePayload: Codable, Hashable {
    var displayName: String?
    var username: String?
    var city: String?
    var certificationLevel: String?
    var certifyingAgency: String?
    /// Несколько агентств (дубликаты с `certifyingAgency` для обратной совместимости).
    var certifyingAgencies: [String]?
    /// When agency is "None yet" — informational for UI / buddy matching.
    var noCertYet: Bool?
    var totalDivesRange: String?
    var lastDiveDate: String?
    var preferredDiveTypes: [String]?
    var preferredDepthRange: String?
    var diveInterests: [String]?
    var ownEquipment: [String]?
    var languagesSpoken: [String]?
    var lookingForBuddy: Bool?
    var privacy: DiverPrivacyPayload?
    var emergency: DiverEmergencyPayload?
    var onboardingCompleted: Bool?
    var adminDashboardLayout: AdminDashboardLayoutPayload?
}

enum DiverProfileCatalog {
    static let certificationLevels: [String] = [
        "TRY_SCUBA",
        "OPEN_WATER",
        "ADVANCED_OPEN_WATER",
        "RESCUE",
        "DIVEMASTER",
        "INSTRUCTOR",
        "TECHNICAL",
        "FREEDIVER",
        "OTHER",
    ]

    static let certifyingAgencies: [String] = [
        "PADI", "SSI", "CMAS", "NAUI", "RAID", "GUE", "OTHER", "NONE_YET",
    ]

    static let diveCountRanges: [String] = [
        "0", "1_10", "11_25", "26_50", "51_100", "100_PLUS",
    ]

    static let diveInterests: [String] = [
        "WRECK", "DRIFT", "NIGHT", "CAVE", "PHOTOGRAPHY", "VIDEOGRAPHY",
        "MACRO", "BIG_ANIMALS", "TECHNICAL", "FREEDIVING", "COLD_WATER", "CORAL_REEFS",
    ]

    static let equipmentKeys: [String] = [
        "BCD", "REGULATOR", "COMPUTER", "MASK_FINS_SNORKEL", "WETSUIT_DRY_SUIT",
        "TORCH", "CAMERA",
    ]
}

extension User {
    /// Heuristic 0…1 for Explore banner (ТЗ ~70%).
    func profileCompletionFraction() -> Double {
        var score = 0.0
        let parts: [Bool] = [
            diverProfile?.onboardingCompleted == true,
            !(diverProfile?.displayName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true)
                || !((firstName ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && (lastName ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty),
            !(countryCode ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            diverProfile?.certificationLevel != nil || diverProfile?.noCertYet == true,
            diverProfile?.totalDivesRange != nil,
            diverProfile?.certifyingAgency != nil || !(diverProfile?.certifyingAgencies ?? []).isEmpty,
            diverProfile?.privacy != nil,
            !(avatarURL ?? "").isEmpty,
            !(diverProfile?.city ?? "").isEmpty,
            !(diverProfile?.diveInterests ?? []).isEmpty,
        ]
        score = parts.reduce(0) { $0 + ($1 ? 1 : 0) }
        return score / Double(parts.count)
    }

    var needsDiverProfileOnboarding: Bool {
        if role == .diveCenterAdmin || role == .instructor || role == .shopAdmin || role == .superAdmin {
            return false
        }
        return diverProfile?.onboardingCompleted != true
    }
}
