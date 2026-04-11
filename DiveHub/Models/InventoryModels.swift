//
//  InventoryModels.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

// MARK: - Location
struct Location: Identifiable, Codable {
    let id: String
    var diveCenterId: String
    var name: String
    var type: LocationType
    var parentLocationId: String? // For nested locations (warehouse -> shelf -> box)
    var address: String?
    var notes: String?
    var isActive: Bool
    var createdAt: Date
    var updatedAt: Date
    
    enum LocationType: String, Codable {
        case warehouse = "warehouse"
        case room = "room"
        case shelf = "shelf"
        case box = "box"
        case container = "container"
        case vehicle = "vehicle"
        case other = "other"
    }
}

// MARK: - Inspection
struct Inspection: Identifiable, Codable {
    let id: String
    var gearItemId: String
    var checklistTemplateId: String?
    var performedBy: String // User ID
    var performedByName: String? // User name (denormalized)
    var date: Date
    var status: InspectionStatus
    var result: InspectionResult
    var notes: String?
    var checklistItems: [ChecklistItem]
    var photos: [InspectionPhoto]
    var signature: String? // Base64 encoded signature
    var nextInspectionDate: Date? // Calculated based on interval
    var createdAt: Date
    var updatedAt: Date
    
    enum InspectionStatus: String, Codable {
        case scheduled = "scheduled"
        case inProgress = "in_progress"
        case completed = "completed"
        case cancelled = "cancelled"
    }
    
    enum InspectionResult: String, Codable {
        case passed = "passed"
        case failed = "failed"
        case conditional = "conditional" // Passed with conditions
    }
    
    struct ChecklistItem: Identifiable, Codable {
        let id: String
        var title: String
        var description: String?
        var isRequired: Bool
        var status: ChecklistItemStatus
        var comment: String?
        var photos: [String] // Photo URLs
        var checkedAt: Date?
        
        enum ChecklistItemStatus: String, Codable {
            case notChecked = "not_checked"
            case passed = "passed"
            case failed = "failed"
            case notApplicable = "not_applicable"
        }
    }
    
    struct InspectionPhoto: Identifiable, Codable {
        let id: String
        var url: String
        var checklistItemId: String? // If photo is for specific checklist item
        var description: String?
        var uploadedAt: Date
    }
}

// MARK: - Checklist Template
struct ChecklistTemplate: Identifiable, Codable {
    let id: String
    var diveCenterId: String
    var name: String
    var description: String?
    var category: GearItem.GearCategory
    var items: [ChecklistTemplateItem]
    var inspectionIntervalDays: Int? // Default interval for this type
    var isDefault: Bool
    var createdAt: Date
    var updatedAt: Date
    
    struct ChecklistTemplateItem: Identifiable, Codable {
        let id: String
        var title: String
        var description: String?
        var isRequired: Bool
        var order: Int
        var hint: String? // Tooltip text
        var examplePhotoUrl: String? // Example photo URL
    }
}

// MARK: - Checkout (Equipment Issue/Return)
struct Checkout: Identifiable, Codable {
    let id: String
    var diveCenterId: String
    var gearItemIds: [String] // Multiple items can be checked out together
    var issuedBy: String // User ID
    var issuedByName: String? // User name (denormalized)
    var issuedTo: String // User ID (client or instructor)
    var issuedToName: String? // User name (denormalized)
    var issuedToType: IssuedToType
    var bookingId: String? // If related to a booking
    var dueDate: Date
    var returnedAt: Date?
    var status: CheckoutStatus
    var conditionAtIssue: [ItemCondition] // Condition of each item at checkout
    var conditionAtReturn: [ItemCondition]? // Condition at return
    var notes: String?
    var signature: String? // Base64 encoded signature
    var depositAmount: Double?
    var depositCurrency: String?
    var depositReturned: Bool
    var createdAt: Date
    var updatedAt: Date
    
    enum IssuedToType: String, Codable {
        case client = "client"
        case instructor = "instructor"
        case employee = "employee"
    }
    
    enum CheckoutStatus: String, Codable {
        case open = "open"
        case returned = "returned"
        case overdue = "overdue"
        case lost = "lost"
        case cancelled = "cancelled"
        
        var displayName: String {
            switch self {
            case .open: return "Open"
            case .returned: return "Returned"
            case .overdue: return "Overdue"
            case .lost: return "Lost"
            case .cancelled: return "Cancelled"
            }
        }
    }
    
    struct ItemCondition: Identifiable, Codable {
        let id: String
        var gearItemId: String
        var hasScratches: Bool
        var hasPunctures: Bool
        var hasSealIssues: Bool
        var otherDefects: String?
        var photos: [String] // Photo URLs
        var notes: String?
    }
    
    var isOverdue: Bool {
        guard status == .open else { return false }
        return dueDate < Date()
    }
}

// MARK: - Maintenance Ticket
struct MaintenanceTicket: Identifiable, Codable {
    let id: String
    var diveCenterId: String
    var gearItemId: String
    var title: String
    var description: String
    var priority: Priority
    var status: TicketStatus
    var initiatedBy: String // User ID
    var initiatedByName: String? // User name (denormalized)
    var assignedTo: String? // Technician user ID
    var assignedToName: String? // Technician name (denormalized)
    var estimatedCost: Double?
    var actualCost: Double?
    var currency: String
    var estimatedHours: Double?
    var actualHours: Double?
    var partsUsed: [PartUsed]
    var photos: [String] // Photo URLs
    var workLog: [WorkLogEntry]
    var checklist: [WorkChecklistItem]
    var documents: [String] // Document URLs (receipts, warranties)
    var openedAt: Date
    var startedAt: Date?
    var completedAt: Date?
    var closedAt: Date?
    var createdAt: Date
    var updatedAt: Date
    
    enum Priority: String, Codable, CaseIterable {
        case low = "low"
        case medium = "medium"
        case high = "high"
        case urgent = "urgent"
        
        var displayName: String {
            switch self {
            case .low: return "Low"
            case .medium: return "Medium"
            case .high: return "High"
            case .urgent: return "Urgent"
            }
        }
    }
    
    enum TicketStatus: String, Codable, CaseIterable {
        case open = "open"
        case inProgress = "in_progress"
        case awaitingParts = "awaiting_parts"
        case completed = "completed"
        case closed = "closed"
        case cancelled = "cancelled"
        
        var displayName: String {
            switch self {
            case .open: return "Open"
            case .inProgress: return "In Progress"
            case .awaitingParts: return "Awaiting Parts"
            case .completed: return "Completed"
            case .closed: return "Closed"
            case .cancelled: return "Cancelled"
            }
        }
    }
    
    struct PartUsed: Identifiable, Codable {
        let id: String
        var name: String
        var partNumber: String?
        var quantity: Int
        var unitPrice: Double
        var supplier: String?
        var orderNumber: String?
    }
    
    struct WorkLogEntry: Identifiable, Codable {
        let id: String
        var date: Date
        var technicianId: String
        var technicianName: String?
        var hours: Double
        var description: String
        var photos: [String] // Photo URLs
    }
    
    struct WorkChecklistItem: Identifiable, Codable {
        let id: String
        var title: String
        var isCompleted: Bool
        var completedAt: Date?
        var completedBy: String?
    }
    
    var daysOpen: Int {
        let calendar = Calendar.current
        return calendar.dateComponents([.day], from: openedAt, to: Date()).day ?? 0
    }
    
    var isOverdue: Bool {
        // Consider overdue if open for more than 30 days (configurable)
        return daysOpen > 30 && (status == .open || status == .inProgress)
    }
}

// MARK: - Inventory Audit
struct InventoryAudit: Identifiable, Codable {
    let id: String
    var diveCenterId: String
    var name: String
    var description: String?
    var locationId: String? // If auditing specific location
    var category: GearItem.GearCategory? // If auditing specific category
    var assignedTo: [String] // User IDs
    var status: AuditStatus
    var startedAt: Date?
    var completedAt: Date?
    var items: [AuditItem]
    var discrepancies: [AuditDiscrepancy]
    var createdAt: Date
    var updatedAt: Date
    
    enum AuditStatus: String, Codable {
        case draft = "draft"
        case inProgress = "in_progress"
        case completed = "completed"
        case cancelled = "cancelled"
    }
    
    struct AuditItem: Identifiable, Codable {
        let id: String
        var gearItemId: String
        var expectedLocationId: String?
        var foundLocationId: String?
        var status: ItemAuditStatus
        var scannedAt: Date?
        var scannedBy: String?
        var notes: String?
        
        enum ItemAuditStatus: String, Codable {
            case notScanned = "not_scanned"
            case found = "found"
            case foundInDifferentLocation = "found_in_different_location"
            case notFound = "not_found"
            case extra = "extra" // Item found but not in expected list
        }
    }
    
    struct AuditDiscrepancy: Identifiable, Codable {
        let id: String
        var type: DiscrepancyType
        var gearItemId: String?
        var description: String
        var resolved: Bool
        var resolvedAt: Date?
        var resolvedBy: String?
        var resolutionNotes: String?
        
        enum DiscrepancyType: String, Codable {
            case missing = "missing"
            case extra = "extra"
            case locationMismatch = "location_mismatch"
            case conditionMismatch = "condition_mismatch"
        }
    }
}

// MARK: - Event Log (History)
struct EventLog: Identifiable, Codable {
    let id: String
    var gearItemId: String
    var type: EventType
    var title: String
    var description: String?
    var userId: String
    var userName: String?
    var relatedEntityId: String? // ID of related checkout, inspection, ticket, etc.
    var relatedEntityType: String? // "checkout", "inspection", "maintenance_ticket", etc.
    var photos: [String] // Photo URLs
    var metadata: [String: String]? // Additional structured data
    var createdAt: Date
    
    enum EventType: String, Codable {
        case created = "created"
        case updated = "updated"
        case statusChanged = "status_changed"
        case conditionChanged = "condition_changed"
        case locationChanged = "location_changed"
        case checkedOut = "checked_out"
        case checkedIn = "checked_in"
        case inspection = "inspection"
        case maintenanceStarted = "maintenance_started"
        case maintenanceCompleted = "maintenance_completed"
        case deleted = "deleted"
        case restored = "restored"
        case photoAdded = "photo_added"
        case documentAdded = "document_added"
    }
}

// MARK: - KPI Statistics
struct InventoryKPIs: Codable {
    var totalItems: Int
    var uniqueSKUs: Int
    var availableNow: Int
    var inMaintenance: Int
    var issuedNow: Int
    var inspectionOverdue: Int
    var needsService: Int
    var expiringInspections: Int // Inspections due in next 30 days
    var damagedItems: Int
    var lostItems: Int
    
    // Trends (for charts)
    var checkoutsLast30Days: [DateCount]
    var checkoutsLast90Days: [DateCount]
    var maintenanceLast30Days: [DateCount]
    var maintenanceLast90Days: [DateCount]
    
    struct DateCount: Codable {
        var date: Date
        var count: Int
    }
}

// MARK: - Warning Item
struct WarningItem: Identifiable {
    let id: String
    var gearItemId: String
    var type: WarningType
    var title: String
    var message: String
    var severity: WarningSeverity
    var gearItemName: String?
    var actionUrl: String? // Deep link to item or action
    
    enum WarningType: String {
        case needsService = "needs_service"
        case inspectionOverdue = "inspection_overdue"
        case inspectionExpiring = "inspection_expiring"
        case damaged = "damaged"
        case lost = "lost"
        case checkoutOverdue = "checkout_overdue"
        case maintenanceOverdue = "maintenance_overdue"
    }
    
    enum WarningSeverity: String {
        case low = "low"
        case medium = "medium"
        case high = "high"
        case critical = "critical"
    }
}
