//
//  InstructorViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine

@MainActor
class InstructorViewModel: ObservableObject {
    @Published var myBookings: [Booking] = []
    @Published var schedule: [ScheduleItem] = []
    @Published var isLoading = false
    @Published var error: Error?
    
    struct ScheduleItem: Identifiable {
        let id: String
        let date: Date
        let time: String
        let booking: Booking
        let clientName: String
    }
    
    // Statistics
    var todayBookingsCount: Int {
        schedule.filter { Calendar.current.isDateInToday($0.date) }.count
    }
    
    var upcomingBookingsCount: Int {
        schedule.filter { $0.date > Date() }.count
    }
    
    var completedBookingsCount: Int {
        myBookings.filter { $0.status == .completed }.count
    }
    
    var pendingBookingsCount: Int {
        myBookings.filter { $0.status == .pending || $0.status == .confirmed }.count
    }
    
    var thisWeekBookingsCount: Int {
        let calendar = Calendar.current
        let now = Date()
        guard let weekStart = calendar.dateInterval(of: .weekOfYear, for: now)?.start,
              let weekEnd = calendar.dateInterval(of: .weekOfYear, for: now)?.end else {
            return 0
        }
        return schedule.filter { $0.date >= weekStart && $0.date < weekEnd }.count
    }
    
    func loadMyBookings() async {
        isLoading = true
        error = nil
        
        #if DEBUG
        // Use test data if explicitly requested or if no user is logged in
        if ProcessInfo.processInfo.environment["USE_TEST_DATA"] == "true" || AuthenticationService.shared.currentUser == nil {
            myBookings = TestData.instructorBookings
            buildSchedule()
            isLoading = false
            return
        }
        #endif
        
        do {
            myBookings = try await NetworkService.shared.getInstructorBookings(instructorId: AuthenticationService.shared.currentUser?.id)
            buildSchedule()
            isLoading = false
        } catch {
            #if DEBUG
            // Fallback to test data on error in DEBUG mode for testing
            myBookings = TestData.instructorBookings
            buildSchedule()
            #endif
            self.error = error
            isLoading = false
        }
    }
    
    func markDiveCompleted(_ bookingId: String) async throws {
        let updatedBooking = try await NetworkService.shared.markDiveCompleted(bookingId: bookingId)
        if let index = myBookings.firstIndex(where: { $0.id == bookingId }) {
            myBookings[index] = updatedBooking
        }
        buildSchedule()
    }
    
    private func buildSchedule() {
        schedule = myBookings.map { booking in
            ScheduleItem(
                id: booking.id,
                date: booking.date,
                time: booking.startTime,
                booking: booking,
                clientName: booking.participants.first?.name ?? "Unknown"
            )
        }.sorted { $0.date < $1.date }
    }
}
