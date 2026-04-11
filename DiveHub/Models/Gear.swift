//
//  Gear.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

struct GearItem: Identifiable, Codable {
    let id: String
    var diveCenterId: String
    var name: String
    var description: String
    var category: GearCategory
    var manufacturer: String?
    var model: String?
    var size: String? // Single size for this specific item
    var sizes: [String] // Available sizes (for templates)
    var photos: [String] // URLs
    var status: GearStatus
    var condition: GearCondition
    var rentalPrice: Price?
    var maintenance: MaintenanceInfo?
    var createdAt: Date
    var updatedAt: Date
    
    // New fields for comprehensive management
    var serialNumber: String? // Unique serial number
    var barcode: String? // Barcode
    var qrCode: String? // QR code
    var locationId: String? // Location/warehouse ID
    var locationName: String? // Location name (denormalized)
    var responsibleUserId: String? // User responsible for this item
    var responsibleUserName: String? // User name (denormalized)
    var lastInspectionDate: Date? // Last inspection date
    var nextInspectionDate: Date? // Next scheduled inspection
    var inspectionIntervalDays: Int? // Days between inspections
    var purchaseDate: Date? // Purchase date
    var supplier: String? // Supplier name
    var purchasePrice: Double? // Purchase price
    var warrantyExpiresAt: Date? // Warranty expiration
    var productionYear: Int? // Year of production
    var maxPressure: Double? // Max pressure (for tanks)
    var material: String? // Material description
    var tags: [String] // Tags for categorization
    var relatedItemIds: [String] // IDs of related items (kits)
    var documents: [Document] // Attached documents
    var notes: String? // General notes
    var isDeleted: Bool // Soft delete flag
    var deletedAt: Date? // Deletion timestamp
    var createdBy: String? // User ID who created
    var insuranceStatus: InsuranceStatus? // Insurance information
    
    enum GearCategory: String, Codable, CaseIterable {
        case wetsuit = "wetsuit"
        case bcd = "bcd"
        case regulator = "regulator"
        case fins = "fins"
        case mask = "mask"
        case snorkel = "snorkel"
        case boot = "boot"
        case glove = "glove"
        case weight = "weight"
        case tank = "tank"
        case computer = "computer"
        case camera = "camera"
        case flashlight = "flashlight"
        case compass = "compensator"
        case other = "other"
        
        var displayName: String {
            switch self {
            case .wetsuit: return "Wetsuit"
            case .bcd: return "BCD"
            case .regulator: return "Regulator"
            case .fins: return "Fins"
            case .mask: return "Mask"
            case .snorkel: return "Snorkel"
            case .boot: return "Boots"
            case .glove: return "Gloves"
            case .weight: return "Weights"
            case .tank: return "Tank"
            case .computer: return "Computer"
            case .camera: return "Camera"
            case .flashlight: return "Flashlight"
            case .compass: return "Compass"
            case .other: return "Other"
            }
        }
    }
    
    enum GearStatus: String, Codable, CaseIterable {
        case available = "available"
        case issued = "issued" // Currently checked out
        case maintenance = "maintenance"
        case lost = "lost"
        case retired = "retired"
        case scrapped = "scrapped"
        
        var displayName: String {
            switch self {
            case .available: return "Available"
            case .issued: return "Issued"
            case .maintenance: return "Maintenance"
            case .lost: return "Lost"
            case .retired: return "Retired"
            case .scrapped: return "Scrapped"
            }
        }
        
        var color: String {
            switch self {
            case .available: return "green"
            case .issued: return "blue"
            case .maintenance: return "orange"
            case .lost: return "red"
            case .retired: return "gray"
            case .scrapped: return "black"
            }
        }
    }
    
    enum GearCondition: String, Codable, CaseIterable {
        case new = "new"
        case good = "good"
        case needsService = "needs_service"
        case damaged = "damaged"
        case lost = "lost"
        case retired = "retired"
        
        var displayName: String {
            switch self {
            case .new: return "New"
            case .good: return "Good"
            case .needsService: return "Needs Service"
            case .damaged: return "Damaged"
            case .lost: return "Lost"
            case .retired: return "Retired"
            }
        }
    }
    
    enum InsuranceStatus: String, Codable {
        case active = "active"
        case expired = "expired"
        case none = "none"
    }
    
    struct Price: Codable {
        var amount: Double
        var currency: String
        var period: RentalPeriod
        
        enum RentalPeriod: String, Codable {
            case perDive = "per_dive"
            case perDay = "per_day"
            case perWeek = "per_week"
        }
    }
    
    struct MaintenanceInfo: Codable {
        var lastServiceDate: Date?
        var nextServiceDate: Date?
        var serviceHistory: [ServiceRecord]
        var notes: String?
        
        struct ServiceRecord: Identifiable, Codable {
            let id: String
            var date: Date
            var type: ServiceType
            var description: String
            var performedBy: String?
            
            enum ServiceType: String, Codable {
                case inspection = "inspection"
                case repair = "repair"
                case replacement = "replacement"
                case cleaning = "cleaning"
            }
        }
    }
    
    struct Document: Identifiable, Codable {
        let id: String
        var name: String
        var url: String
        var type: DocumentType
        var uploadedAt: Date
        var uploadedBy: String?
        
        enum DocumentType: String, Codable {
            case invoice = "invoice"
            case warranty = "warranty"
            case manual = "manual"
            case certificate = "certificate"
            case photo = "photo"
            case other = "other"
        }
    }
    
    // Computed properties
    var displayName: String {
        var parts: [String] = []
        if let manufacturer = manufacturer {
            parts.append(manufacturer)
        }
        if let model = model {
            parts.append(model)
        }
        if let size = size {
            parts.append(size)
        }
        return parts.isEmpty ? name : parts.joined(separator: " ")
    }
    
    var isInspectionOverdue: Bool {
        guard let nextInspectionDate = nextInspectionDate else { return false }
        return nextInspectionDate < Date()
    }
    
    var daysUntilInspection: Int? {
        guard let nextInspectionDate = nextInspectionDate else { return nil }
        let calendar = Calendar.current
        let days = calendar.dateComponents([.day], from: Date(), to: nextInspectionDate).day
        return days
    }
    
    var inspectionExpiringSoon: Bool {
        guard let days = daysUntilInspection else { return false }
        return days > 0 && days <= 30
    }
    
    init(id: String = UUID().uuidString,
         diveCenterId: String,
         name: String,
         description: String = "",
         category: GearCategory,
         manufacturer: String? = nil,
         model: String? = nil,
         size: String? = nil,
         sizes: [String] = [],
         photos: [String] = [],
         status: GearStatus = .available,
         condition: GearCondition = .good,
         rentalPrice: Price? = nil,
         maintenance: MaintenanceInfo? = nil,
         createdAt: Date = Date(),
         updatedAt: Date = Date(),
         serialNumber: String? = nil,
         barcode: String? = nil,
         qrCode: String? = nil,
         locationId: String? = nil,
         locationName: String? = nil,
         responsibleUserId: String? = nil,
         responsibleUserName: String? = nil,
         lastInspectionDate: Date? = nil,
         nextInspectionDate: Date? = nil,
         inspectionIntervalDays: Int? = nil,
         purchaseDate: Date? = nil,
         supplier: String? = nil,
         purchasePrice: Double? = nil,
         warrantyExpiresAt: Date? = nil,
         productionYear: Int? = nil,
         maxPressure: Double? = nil,
         material: String? = nil,
         tags: [String] = [],
         relatedItemIds: [String] = [],
         documents: [Document] = [],
         notes: String? = nil,
         isDeleted: Bool = false,
         deletedAt: Date? = nil,
         createdBy: String? = nil,
         insuranceStatus: InsuranceStatus? = nil) {
        self.id = id
        self.diveCenterId = diveCenterId
        self.name = name
        self.description = description
        self.category = category
        self.manufacturer = manufacturer
        self.model = model
        self.size = size
        self.sizes = sizes
        self.photos = photos
        self.status = status
        self.condition = condition
        self.rentalPrice = rentalPrice
        self.maintenance = maintenance
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.serialNumber = serialNumber
        self.barcode = barcode
        self.qrCode = qrCode
        self.locationId = locationId
        self.locationName = locationName
        self.responsibleUserId = responsibleUserId
        self.responsibleUserName = responsibleUserName
        self.lastInspectionDate = lastInspectionDate
        self.nextInspectionDate = nextInspectionDate
        self.inspectionIntervalDays = inspectionIntervalDays
        self.purchaseDate = purchaseDate
        self.supplier = supplier
        self.purchasePrice = purchasePrice
        self.warrantyExpiresAt = warrantyExpiresAt
        self.productionYear = productionYear
        self.maxPressure = maxPressure
        self.material = material
        self.tags = tags
        self.relatedItemIds = relatedItemIds
        self.documents = documents
        self.notes = notes
        self.isDeleted = isDeleted
        self.deletedAt = deletedAt
        self.createdBy = createdBy
        self.insuranceStatus = insuranceStatus
    }
}
