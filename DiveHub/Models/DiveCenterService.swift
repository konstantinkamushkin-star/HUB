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
            case .funDive: return "ui_service_type_fun_dive".localized
            case .package: return "ui_service_type_package".localized
            case .nightDive: return "ui_service_type_night_dive".localized
            case .poolSession: return "ui_service_type_pool_session".localized
            case .equipmentRental: return "ui_service_type_equipment_rental".localized
            case .course: return "ui_service_type_course".localized
            case .other: return "ui_service_type_other".localized
            }
        }
    }

    struct Price: Codable, Hashable {
        var amount: Double
        var currency: String
    }
}
