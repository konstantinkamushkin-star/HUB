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
        switch self {
        case .diverBasic: return "Diver (Basic)"
        case .diverPro: return "Diver (PRO)"
        case .instructor: return "Instructor"
        case .diveCenterAdmin: return "Dive Center Admin"
        case .shopAdmin: return "Shop Admin"
        case .superAdmin: return "Super Admin"
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
        verificationStatus = VerificationStatus(rawValue: statusString) ?? .pending
        instructorNumber = try container.decodeIfPresent(String.self, forKey: .instructorNumber)
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
    }
    
    // Memberwise initializer для использования в коде
    init(id: String, organization: String, level: String, cardImageURL: String?, issueDate: Date?, verificationStatus: VerificationStatus, instructorNumber: String?) {
        self.id = id
        self.organization = organization
        self.level = level
        self.cardImageURL = cardImageURL
        self.issueDate = issueDate
        self.verificationStatus = verificationStatus
        self.instructorNumber = instructorNumber
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
}
