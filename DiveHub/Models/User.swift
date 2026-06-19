//
//  User.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

enum UserRole: String, Codable, CaseIterable, Hashable {
    case diverBasic = "DIVER_BASIC"
    case diverPro = "DIVER_PRO"
    case instructor = "INSTRUCTOR"
    case diveCenterAdmin = "DIVE_CENTER_ADMIN"
    case shopAdmin = "SHOP_ADMIN"
    case superAdmin = "SUPER_ADMIN"
    
    // Для обратной совместимости со старым форматом
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let rawValue = try container.decode(String.self)
        
        // Поддержка обоих форматов
        switch rawValue.uppercased() {
        case "DIVER_BASIC", "diver_basic":
            self = .diverBasic
        case "DIVER_PRO", "diver_pro":
            self = .diverPro
        case "INSTRUCTOR", "instructor":
            self = .instructor
        case "DIVE_CENTER_ADMIN", "dive_center_admin":
            self = .diveCenterAdmin
        case "SHOP_ADMIN", "shop_admin":
            self = .shopAdmin
        case "SUPER_ADMIN", "super_admin":
            self = .superAdmin
        default:
            self = .diverBasic
        }
    }
    
    var displayName: String {
        let loc = LocalizationService.shared
        switch self {
        case .diverBasic: return loc.localizedString("roleDiverBasic")
        case .diverPro: return loc.localizedString("roleDiverPro")
        case .instructor: return loc.localizedString("roleInstructor")
        case .diveCenterAdmin: return loc.localizedString("roleDiveCenterAdmin")
        case .shopAdmin: return loc.localizedString("roleShopAdmin")
        case .superAdmin: return loc.localizedString("roleSuperAdmin")
        }
    }
}

struct User: Identifiable, Codable, Hashable {
    let id: String
    var email: String
    var phoneNumber: String?
    var firstName: String?
    var lastName: String?
    var avatarURL: String?
    var role: UserRole
    var subscriptionStatus: SubscriptionStatus?
    var subscriptionExpiresAt: Date?
    var certificationLevel: String?
    /// Текст «о себе» для карточки инструктора (редактирует админ центра).
    var bio: String?
    var diveCenterId: String? // For instructors and admins
    /// id магазина, если пользователь владелец (с бэкенда по owner_id).
    var shopId: String?
    var language: String?
    /// ISO 3166-1 alpha-2 from server (`countryCode`).
    var countryCode: String?
    /// Extended diver profile from `diver_profile` JSON.
    var diverProfile: DiverProfilePayload?
    var totalDives: Int? // Total number of dives
    /// С бэкенда: обязательная смена пароля после временного пароля партнёра.
    var mustChangePassword: Bool?
    var createdAt: Date
    var updatedAt: Date?
    
    // Computed property для обратной совместимости
    var displayName: String {
        if let firstName = firstName, let lastName = lastName {
            return "\(firstName) \(lastName)"
        } else if let firstName = firstName {
            return firstName
        } else if let lastName = lastName {
            return lastName
        }
        return email.components(separatedBy: "@").first ?? "User"
    }

    /// @handle из `diver_profile.username` (с бэкенда).
    var username: String? {
        diverProfile?.username
    }

    /// PRO trip creation / entitlements (nil tier treated as active until expiry).
    var hasActiveProSubscription: Bool {
        guard role == .diverPro else { return false }
        if subscriptionStatus == .expired || subscriptionStatus == .cancelled { return false }
        if let exp = subscriptionExpiresAt, exp < Date() { return false }
        return true
    }
    
    enum SubscriptionStatus: String, Codable, Hashable {
        case active
        case expired
        case cancelled
    }
    
    // CodingKeys для маппинга полей бэкенда
    enum CodingKeys: String, CodingKey {
        case id
        case email
        case phoneNumber = "phone"
        case firstName
        case lastName
        case avatarURL = "avatarUrl"
        case role
        case subscriptionStatus = "subscriptionTier"
        case subscriptionExpiresAt
        case certificationLevel
        case bio
        case diveCenterId
        case shopId
        case language
        case countryCode
        case diverProfile
        case totalDives
        case mustChangePassword
        case createdAt
        case updatedAt
    }
    
    // Memberwise initializer for manual creation (e.g., in test data)
    init(
        id: String,
        email: String,
        phoneNumber: String? = nil,
        firstName: String? = nil,
        lastName: String? = nil,
        avatarURL: String? = nil,
        role: UserRole,
        subscriptionStatus: SubscriptionStatus? = nil,
        subscriptionExpiresAt: Date? = nil,
        certificationLevel: String? = nil,
        bio: String? = nil,
        diveCenterId: String? = nil,
        shopId: String? = nil,
        language: String? = nil,
        countryCode: String? = nil,
        diverProfile: DiverProfilePayload? = nil,
        totalDives: Int? = nil,
        mustChangePassword: Bool? = nil,
        createdAt: Date,
        updatedAt: Date? = nil
    ) {
        self.id = id
        self.email = email
        self.phoneNumber = phoneNumber
        self.firstName = firstName
        self.lastName = lastName
        self.avatarURL = avatarURL
        self.role = role
        self.subscriptionStatus = subscriptionStatus
        self.subscriptionExpiresAt = subscriptionExpiresAt
        self.certificationLevel = certificationLevel
        self.bio = bio
        self.diveCenterId = diveCenterId
        self.shopId = shopId
        self.language = language
        self.countryCode = countryCode
        self.diverProfile = diverProfile
        self.totalDives = totalDives
        self.mustChangePassword = mustChangePassword
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

struct UserProfile: Codable {
    var user: User
    var totalDives: Int
    var totalBottomTime: Int // in minutes
    var certifications: [Certification]
    var savedGearProfiles: [GearProfile]
    var friends: [String] // User IDs
    var achievements: [Achievement]
}

struct Certification: Identifiable, Codable {
    let id: String
    var organization: String // PADI, SSI, etc. (frontend name)
    var level: String
    var cardImageURL: String? // frontend name
    var issueDate: Date?
    var verificationStatus: VerificationStatus
    var instructorNumber: String?
    var certificateNumber: String?
    
    var displayName: String {
        return "\(organization) - \(level)"
    }
    
    enum VerificationStatus: String, Codable {
        case pending = "PENDING"
        case verified = "VERIFIED"
        case rejected = "REJECTED"
    }
    
    // CodingKeys для маппинга с бэкендом
    enum CodingKeys: String, CodingKey {
        case id
        case organization = "agency" // бэкенд использует "agency"
        case level
        case cardImageURL = "cardImageUrl" // бэкенд использует "cardImageUrl"
        case issueDate
        case verificationStatus
        case instructorNumber
        case certificateNumber
    }
    
    // Custom decoder для правильного маппинга
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        organization = try container.decode(String.self, forKey: .organization)
        level = try container.decode(String.self, forKey: .level)
        cardImageURL = try container.decodeIfPresent(String.self, forKey: .cardImageURL)
        issueDate = try container.decodeIfPresent(Date.self, forKey: .issueDate)
        let statusString = try container.decodeIfPresent(String.self, forKey: .verificationStatus) ?? "PENDING"
        verificationStatus = VerificationStatus(rawValue: statusString.uppercased()) ?? .pending
        instructorNumber = try container.decodeIfPresent(String.self, forKey: .instructorNumber)
        certificateNumber = try container.decodeIfPresent(String.self, forKey: .certificateNumber)
    }
    
    // Custom encoder для правильного маппинга
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(organization, forKey: .organization)
        try container.encode(level, forKey: .level)
        try container.encodeIfPresent(cardImageURL, forKey: .cardImageURL)
        try container.encodeIfPresent(issueDate, forKey: .issueDate)
        try container.encode(verificationStatus.rawValue, forKey: .verificationStatus)
        try container.encodeIfPresent(instructorNumber, forKey: .instructorNumber)
        try container.encodeIfPresent(certificateNumber, forKey: .certificateNumber)
    }
    
    // Memberwise initializer для использования в коде
    init(
        id: String,
        organization: String,
        level: String,
        cardImageURL: String?,
        issueDate: Date?,
        verificationStatus: VerificationStatus,
        instructorNumber: String?,
        certificateNumber: String? = nil
    ) {
        self.id = id
        self.organization = organization
        self.level = level
        self.cardImageURL = cardImageURL
        self.issueDate = issueDate
        self.verificationStatus = verificationStatus
        self.instructorNumber = instructorNumber
        self.certificateNumber = certificateNumber
    }
}


struct GearProfile: Identifiable, Codable {
    let id: String
    var name: String
    var items: [GearProfileItem]
    var createdAt: Date
    var updatedAt: Date
    
    struct GearProfileItem: Identifiable, Codable {
        let id: String
        var category: GearItem.GearCategory
        var size: String
        var notes: String?
    }
}

struct Achievement: Identifiable, Codable {
    let id: String
    var title: String
    var description: String
    var iconName: String
    var unlockedAt: Date?
    /// 0…1 для заблокированных; `nil` если прогресс не показываем.
    var progressFraction: Double?
    var progressText: String?

    init(
        id: String,
        title: String,
        description: String,
        iconName: String,
        unlockedAt: Date?,
        progressFraction: Double? = nil,
        progressText: String? = nil
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.iconName = iconName
        self.unlockedAt = unlockedAt
        self.progressFraction = progressFraction
        self.progressText = progressText
    }

    enum CodingKeys: String, CodingKey {
        case id
        case title
        case description
        case iconName
        case unlockedAt
        case progressFraction
        case progressText
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decode(String.self, forKey: .title)
        description = try c.decode(String.self, forKey: .description)
        iconName = try c.decode(String.self, forKey: .iconName)
        unlockedAt = try c.decodeIfPresent(Date.self, forKey: .unlockedAt)
        progressFraction = try c.decodeIfPresent(Double.self, forKey: .progressFraction)
        progressText = try c.decodeIfPresent(String.self, forKey: .progressText)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(title, forKey: .title)
        try c.encode(description, forKey: .description)
        try c.encode(iconName, forKey: .iconName)
        try c.encodeIfPresent(unlockedAt, forKey: .unlockedAt)
        try c.encodeIfPresent(progressFraction, forKey: .progressFraction)
        try c.encodeIfPresent(progressText, forKey: .progressText)
    }
}
