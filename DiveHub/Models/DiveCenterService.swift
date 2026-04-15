import Foundation

struct DiveCenterService: Identifiable, Codable, Hashable {
    let id: String
    var diveCenterId: String
    var name: String
    var description: String
    var type: ServiceType
    var price: Price
    var pricingUnit: String
    var duration: Int
    var maxParticipants: Int
    var requirements: [String]
    var includedItems: [String]
    var pricingRules: [String: String]?
    var ownGearDiscountPercent: Double?
    var groupDiscountThreshold: Int?
    var groupDiscountPercent: Double?
    var nightDiveSurchargeAmount: Double?
    var privateInstructorSurchargeAmount: Double?
    var isActive: Bool
    var createdAt: Date
    var updatedAt: Date

    enum ServiceType: String, Codable, CaseIterable {
        case funDive = "fun_dive"
        case package = "package"
        case nightDive = "night_dive"
        case poolSession = "pool_session"
        case equipmentRental = "equipment_rental"
        case course = "course"
        case other = "other"

        var displayName: String {
            switch self {
            case .funDive: return "Fun Dive"
            case .package: return "Package"
            case .nightDive: return "Night Dive"
            case .poolSession: return "Pool Session"
            case .equipmentRental: return "Equipment Rental"
            case .course: return "Course"
            case .other: return "Other"
            }
        }
    }

    struct Price: Codable, Hashable {
        var amount: Double
        var currency: String
    }
}
