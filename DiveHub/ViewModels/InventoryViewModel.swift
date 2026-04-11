//
//  InventoryViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import SwiftUI
import Combine

@MainActor
class InventoryViewModel: ObservableObject {
    @Published var gearItems: [GearItem] = []
    @Published var locations: [Location] = []
    @Published var inspections: [Inspection] = []
    @Published var checkouts: [Checkout] = []
    @Published var maintenanceTickets: [MaintenanceTicket] = []
    @Published var checklistTemplates: [ChecklistTemplate] = []
    @Published var eventLogs: [EventLog] = []
    @Published var kpis: InventoryKPIs?
    @Published var warnings: [WarningItem] = []
    
    @Published var isLoading = false
    @Published var error: Error?
    @Published var errorMessage: String?
    
    @Published var selectedLocationId: String?
    @Published var searchText: String = ""
    @Published var selectedCategory: GearItem.GearCategory?
    @Published var selectedStatus: GearItem.GearStatus?
    @Published var selectedCondition: GearItem.GearCondition?
    @Published var sortOption: SortOption = .name
    
    @Published var selectedItems: Set<String> = [] // For multi-select
    
    private let networkService = NetworkService.shared
    private let storageService = StorageService.shared
    
    enum SortOption: String, CaseIterable {
        case name = "name"
        case lastInspection = "last_inspection"
        case status = "status"
        case checkoutTime = "checkout_time"
        case category = "category"
        
        var displayName: String {
            switch self {
            case .name: return "Name"
            case .lastInspection: return "Last Inspection"
            case .status: return "Status"
            case .checkoutTime: return "Checkout Time"
            case .category: return "Category"
            }
        }
    }
    
    // MARK: - Filtered Items
    var filteredItems: [GearItem] {
        var items = gearItems.filter { !$0.isDeleted }
        
        // Location filter
        if let locationId = selectedLocationId {
            items = items.filter { $0.locationId == locationId }
        }
        
        // Category filter
        if let category = selectedCategory {
            items = items.filter { $0.category == category }
        }
        
        // Status filter
        if let status = selectedStatus {
            items = items.filter { $0.status == status }
        }
        
        // Condition filter
        if let condition = selectedCondition {
            items = items.filter { $0.condition == condition }
        }
        
        // Search filter
        if !searchText.isEmpty {
            let searchLower = searchText.lowercased()
            items = items.filter { item in
                item.name.lowercased().contains(searchLower) ||
                item.manufacturer?.lowercased().contains(searchLower) ?? false ||
                item.model?.lowercased().contains(searchLower) ?? false ||
                item.serialNumber?.lowercased().contains(searchLower) ?? false ||
                item.barcode?.lowercased().contains(searchLower) ?? false ||
                item.qrCode?.lowercased().contains(searchLower) ?? false
            }
        }
        
        // Sort
        items = sortedItems(items)
        
        return items
    }
    
    private func sortedItems(_ items: [GearItem]) -> [GearItem] {
        switch sortOption {
        case .name:
            return items.sorted { $0.displayName < $1.displayName }
        case .lastInspection:
            return items.sorted { item1, item2 in
                let date1 = item1.lastInspectionDate ?? Date.distantPast
                let date2 = item2.lastInspectionDate ?? Date.distantPast
                return date1 > date2
            }
        case .status:
            return items.sorted { $0.status.rawValue < $1.status.rawValue }
        case .checkoutTime:
            // Sort by most recently checked out
            return items.sorted { item1, item2 in
                let checkout1 = checkouts.first { $0.gearItemIds.contains(item1.id) && $0.status == .open }
                let checkout2 = checkouts.first { $0.gearItemIds.contains(item2.id) && $0.status == .open }
                let date1 = checkout1?.createdAt ?? Date.distantPast
                let date2 = checkout2?.createdAt ?? Date.distantPast
                return date1 > date2
            }
        case .category:
            return items.sorted { $0.category.rawValue < $1.category.rawValue }
        }
    }
    
    // MARK: - Grouped Items
    var groupedByCategory: [GearItem.GearCategory: [GearItem]] {
        Dictionary(grouping: filteredItems, by: { $0.category })
    }
    
    // MARK: - Load Data
    func loadAllData() async {
        isLoading = true
        error = nil
        
        await withThrowingTaskGroup(of: Void.self) { group in
            group.addTask { await self.loadGearItems() }
            group.addTask { await self.loadLocations() }
            group.addTask { await self.loadInspections() }
            group.addTask { await self.loadCheckouts() }
            group.addTask { await self.loadMaintenanceTickets() }
            group.addTask { await self.loadChecklistTemplates() }
            group.addTask { await self.loadKPIs() }
            
            do {
                try await group.waitForAll()
            } catch {
                self.error = error
                self.errorMessage = "Failed to load data: \(error.localizedDescription)"
            }
        }
        
        await generateWarnings()
        
        isLoading = false
    }
    
    func loadGearItems() async {
        // TODO: Replace with actual API call
        // For now, load from storage or mock data
        if let stored = try? storageService.load([GearItem].self, forKey: "gearItems") {
            gearItems = stored
        }
    }
    
    func loadLocations() async {
        if let stored = try? storageService.load([Location].self, forKey: "locations") {
            locations = stored
        }
    }
    
    func loadInspections() async {
        if let stored = try? storageService.load([Inspection].self, forKey: "inspections") {
            inspections = stored
        }
    }
    
    func loadCheckouts() async {
        if let stored = try? storageService.load([Checkout].self, forKey: "checkouts") {
            checkouts = stored
        }
    }
    
    func loadMaintenanceTickets() async {
        if let stored = try? storageService.load([MaintenanceTicket].self, forKey: "maintenanceTickets") {
            maintenanceTickets = stored
        }
    }
    
    func loadChecklistTemplates() async {
        if let stored = try? storageService.load([ChecklistTemplate].self, forKey: "checklistTemplates") {
            checklistTemplates = stored
        }
    }
    
    func loadKPIs() async {
        // Calculate KPIs from loaded data
        let totalItems = gearItems.filter { !$0.isDeleted }.count
        let uniqueSKUs = Set(gearItems.map { "\($0.manufacturer ?? "")-\($0.model ?? "")-\($0.size ?? "")" }).count
        let availableNow = gearItems.filter { $0.status == .available && !$0.isDeleted }.count
        let inMaintenance = gearItems.filter { $0.status == .maintenance && !$0.isDeleted }.count
        let issuedNow = gearItems.filter { $0.status == .issued && !$0.isDeleted }.count
        let inspectionOverdue = gearItems.filter { $0.isInspectionOverdue && !$0.isDeleted }.count
        let needsService = gearItems.filter { $0.condition == .needsService && !$0.isDeleted }.count
        let expiringInspections = gearItems.filter { $0.inspectionExpiringSoon && !$0.isDeleted }.count
        let damagedItems = gearItems.filter { $0.condition == .damaged && !$0.isDeleted }.count
        let lostItems = gearItems.filter { $0.status == .lost && !$0.isDeleted }.count
        
        kpis = InventoryKPIs(
            totalItems: totalItems,
            uniqueSKUs: uniqueSKUs,
            availableNow: availableNow,
            inMaintenance: inMaintenance,
            issuedNow: issuedNow,
            inspectionOverdue: inspectionOverdue,
            needsService: needsService,
            expiringInspections: expiringInspections,
            damagedItems: damagedItems,
            lostItems: lostItems,
            checkoutsLast30Days: [],
            checkoutsLast90Days: [],
            maintenanceLast30Days: [],
            maintenanceLast90Days: []
        )
    }
    
    func generateWarnings() async {
        var warningsList: [WarningItem] = []
        
        for item in gearItems.filter({ !$0.isDeleted }) {
            // Needs service
            if item.condition == .needsService {
                warningsList.append(WarningItem(
                    id: UUID().uuidString,
                    gearItemId: item.id,
                    type: .needsService,
                    title: "Needs Service",
                    message: "\(item.displayName) requires service",
                    severity: .high,
                    gearItemName: item.displayName
                ))
            }
            
            // Inspection overdue
            if item.isInspectionOverdue {
                warningsList.append(WarningItem(
                    id: UUID().uuidString,
                    gearItemId: item.id,
                    type: .inspectionOverdue,
                    title: "Inspection Overdue",
                    message: "\(item.displayName) inspection is overdue",
                    severity: .critical,
                    gearItemName: item.displayName
                ))
            }
            
            // Inspection expiring
            if let days = item.daysUntilInspection, days > 0 && days <= 30 {
                warningsList.append(WarningItem(
                    id: UUID().uuidString,
                    gearItemId: item.id,
                    type: .inspectionExpiring,
                    title: "Inspection Expiring Soon",
                    message: "\(item.displayName) inspection due in \(days) days",
                    severity: .medium,
                    gearItemName: item.displayName
                ))
            }
            
            // Damaged
            if item.condition == .damaged {
                warningsList.append(WarningItem(
                    id: UUID().uuidString,
                    gearItemId: item.id,
                    type: .damaged,
                    title: "Damaged",
                    message: "\(item.displayName) is marked as damaged",
                    severity: .high,
                    gearItemName: item.displayName
                ))
            }
        }
        
        // Checkout overdue
        for checkout in checkouts.filter({ $0.isOverdue }) {
            for itemId in checkout.gearItemIds {
                if let item = gearItems.first(where: { $0.id == itemId }) {
                    warningsList.append(WarningItem(
                        id: UUID().uuidString,
                        gearItemId: itemId,
                        type: .checkoutOverdue,
                        title: "Checkout Overdue",
                        message: "\(item.displayName) checkout is overdue",
                        severity: .high,
                        gearItemName: item.displayName
                    ))
                }
            }
        }
        
        // Maintenance overdue
        for ticket in maintenanceTickets.filter({ $0.isOverdue }) {
            if let item = gearItems.first(where: { $0.id == ticket.gearItemId }) {
                warningsList.append(WarningItem(
                    id: UUID().uuidString,
                    gearItemId: ticket.gearItemId,
                    type: .maintenanceOverdue,
                    title: "Maintenance Overdue",
                    message: "\(item.displayName) maintenance ticket is overdue",
                    severity: .high,
                    gearItemName: item.displayName
                ))
            }
        }
        
        warnings = warningsList.sorted { warning1, warning2 in
            let severityOrder: [WarningItem.WarningSeverity] = [.critical, .high, .medium, .low]
            let order1 = severityOrder.firstIndex(of: warning1.severity) ?? 999
            let order2 = severityOrder.firstIndex(of: warning2.severity) ?? 999
            return order1 < order2
        }
    }
    
    // MARK: - CRUD Operations
    func createGearItem(_ item: GearItem) async throws {
        gearItems.append(item)
        try? storageService.save(gearItems, forKey: "gearItems")
        await loadKPIs()
        await generateWarnings()
    }
    
    func updateGearItem(_ item: GearItem) async throws {
        if let index = gearItems.firstIndex(where: { $0.id == item.id }) {
            gearItems[index] = item
            try? storageService.save(gearItems, forKey: "gearItems")
            await loadKPIs()
            await generateWarnings()
        }
    }
    
    func deleteGearItem(_ itemId: String) async throws {
        if let index = gearItems.firstIndex(where: { $0.id == itemId }) {
            var item = gearItems[index]
            // Check if item is currently checked out
            let isCheckedOut = checkouts.contains { checkout in
                checkout.gearItemIds.contains(itemId) && checkout.status == .open
            }
            
            if isCheckedOut {
                throw InventoryError.itemCurrentlyCheckedOut
            }
            
            // Soft delete
            item.isDeleted = true
            item.deletedAt = Date()
            gearItems[index] = item
            try? storageService.save(gearItems, forKey: "gearItems")
            await loadKPIs()
            await generateWarnings()
        }
    }
    
    func restoreGearItem(_ itemId: String) async throws {
        if let index = gearItems.firstIndex(where: { $0.id == itemId }) {
            var item = gearItems[index]
            item.isDeleted = false
            item.deletedAt = nil
            gearItems[index] = item
            try? storageService.save(gearItems, forKey: "gearItems")
            await loadKPIs()
            await generateWarnings()
        }
    }
    
    // MARK: - Multi-select
    func toggleSelection(_ itemId: String) {
        if selectedItems.contains(itemId) {
            selectedItems.remove(itemId)
        } else {
            selectedItems.insert(itemId)
        }
    }
    
    func selectAll() {
        selectedItems = Set(filteredItems.map { $0.id })
    }
    
    func deselectAll() {
        selectedItems.removeAll()
    }
    
    // MARK: - Bulk Operations
    func bulkUpdateLocation(_ locationId: String) async throws {
        for itemId in selectedItems {
            if let index = gearItems.firstIndex(where: { $0.id == itemId }) {
                gearItems[index].locationId = locationId
                if let location = locations.first(where: { $0.id == locationId }) {
                    gearItems[index].locationName = location.name
                }
            }
        }
        try? storageService.save(gearItems, forKey: "gearItems")
        selectedItems.removeAll()
    }
    
    func bulkUpdateStatus(_ status: GearItem.GearStatus) async throws {
        for itemId in selectedItems {
            if let index = gearItems.firstIndex(where: { $0.id == itemId }) {
                gearItems[index].status = status
            }
        }
        try? storageService.save(gearItems, forKey: "gearItems")
        selectedItems.removeAll()
        await loadKPIs()
        await generateWarnings()
    }
}

// MARK: - Errors
enum InventoryError: LocalizedError {
    case itemCurrentlyCheckedOut
    case itemNotFound
    case invalidData
    case networkError
    
    var errorDescription: String? {
        switch self {
        case .itemCurrentlyCheckedOut:
            return "Cannot delete item that is currently checked out"
        case .itemNotFound:
            return "Item not found"
        case .invalidData:
            return "Invalid data provided"
        case .networkError:
            return "Network error occurred"
        }
    }
}
