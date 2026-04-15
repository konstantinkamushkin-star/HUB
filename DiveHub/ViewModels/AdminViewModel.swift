//
//  AdminViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine

@MainActor
class AdminViewModel: ObservableObject {
    @Published var bookings: [Booking] = []
    @Published var gearItems: [GearItem] = []
    @Published var instructors: [User] = []
    @Published var isLoading = false
    @Published var error: Error?
    @Published var stats: AdminStats = AdminStats()
    @Published var errorStats: ErrorStats = ErrorStats()
    
    struct AdminStats {
        var totalBookings: Int = 0
        var pendingBookings: Int = 0
        var todayBookings: Int = 0
        var totalRevenue: Double = 0
        var gearInMaintenance: Int = 0
        var availableGear: Int = 0
    }

    struct ErrorStats: Codable {
        struct Totals: Codable {
            var httpErrors: Int = 0
            var uncaughtExceptions: Int = 0
            var unhandledRejections: Int = 0
            var allErrors: Int = 0
        }
        
        var totals: Totals = Totals()
        var httpByStatus: [String: Int] = [:]
    }
    
    func loadDashboardData() async {
        isLoading = true
        error = nil
        bookings = []
        gearItems = []
        
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.loadInstructors() }
            group.addTask { await self.loadErrorStats() }
        }
        
        calculateStats()
        isLoading = false
    }
    
    /// Оставлено для совместимости со старыми экранами (не в основной панели).
    func loadBookings() async {
        bookings = []
        guard let centerId = AuthenticationService.shared.currentUser?.diveCenterId else { return }
        do {
            bookings = try await NetworkService.shared.getCenterBookings(centerId: centerId)
        } catch {
            bookings = []
        }
    }
    
    func loadGear() async {
        gearItems = []
        guard let centerId = AuthenticationService.shared.currentUser?.diveCenterId else { return }
        do {
            gearItems = try await NetworkService.shared.getCenterGear(centerId: centerId)
        } catch {
            gearItems = []
        }
    }
    
    func loadInstructors() async {
        instructors = []
        guard let centerId = AuthenticationService.shared.currentUser?.diveCenterId else { return }
        do {
            instructors = try await NetworkService.shared.getCenterInstructors(centerId: centerId)
        } catch {
            instructors = []
        }
    }
    
    func updateBookingStatus(
        _ bookingId: String,
        status: Booking.BookingStatus,
        finalPriceAmount: Double? = nil,
        finalPriceCurrency: String? = nil,
        manualVerificationNote: String? = nil
    ) async throws {
        let updatedBooking = try await NetworkService.shared.updateBookingStatus(
            bookingId: bookingId,
            status: status,
            finalPriceAmount: finalPriceAmount,
            finalPriceCurrency: finalPriceCurrency,
            manualVerificationNote: manualVerificationNote
        )
        if let index = bookings.firstIndex(where: { $0.id == bookingId }) {
            bookings[index] = updatedBooking
        }
    }
    
    func updateGearStatus(_ gearId: String, status: GearItem.GearStatus) async throws {
        let updatedGear = try await NetworkService.shared.updateGearStatus(gearId: gearId, status: status)
        if let index = gearItems.firstIndex(where: { $0.id == gearId }) {
            gearItems[index] = updatedGear
        }
    }
    
    private func calculateStats() {
        stats.totalBookings = bookings.count
        stats.pendingBookings = bookings.filter { $0.status == .pending }.count
        stats.todayBookings = bookings.filter { Calendar.current.isDateInToday($0.date) }.count
        stats.totalRevenue = bookings.filter { $0.payment.status == .paid }.reduce(0) { $0 + $1.payment.amount }
        stats.gearInMaintenance = gearItems.filter { $0.status == .maintenance }.count
        stats.availableGear = gearItems.filter { $0.status == .available }.count
    }
    
    func loadErrorStats() async {
        do {
            errorStats = try await NetworkService.shared.getAdminErrorStats()
        } catch {
            // This endpoint is SUPER_ADMIN-only; for other admins we keep zero values.
        }
    }
}
