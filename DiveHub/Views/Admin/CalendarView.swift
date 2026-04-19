//
//  CalendarView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct CalendarView: View {
    @StateObject private var viewModel = AdminViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedDate = Date()
    
    var bookingsForSelectedDate: [Booking] {
        viewModel.bookings.filter { Calendar.current.isDate($0.date, inSameDayAs: selectedDate) }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            VStack {
                // Calendar Picker
                DatePicker(
                    localizationService.localizedString("selectDate", table: "admin"),
                    selection: $selectedDate,
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .padding()

                // Bookings for selected date
                List {
                    Section(localizationService.localizedString("bookingsForDate", table: "admin")) {
                        if bookingsForSelectedDate.isEmpty {
                            Text(localizationService.localizedString("noBookings", table: "admin"))
                                .foregroundColor(.secondary)
                        } else {
                            ForEach(bookingsForSelectedDate) { booking in
                                CalendarBookingRow(booking: booking)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("calendar", table: "admin"))
        .diveHubNavigationChrome()
        .task {
            await viewModel.loadBookings()
        }
    }
}

private struct CalendarBookingRow: View {
    let booking: Booking
    @StateObject private var localizationService = LocalizationService.shared

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(booking.date.formatted(date: .abbreviated, time: .omitted))
                    .font(.headline)
                Text(booking.startTime)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            Text(localizedStatus(booking.status))
                .font(.caption)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(statusColor(for: booking.status))
                .foregroundColor(.white)
                .cornerRadius(8)
        }
        .padding(.vertical, 4)
    }

    private func statusColor(for status: Booking.BookingStatus) -> Color {
        switch status {
        case .pending: return .orange
        case .quoted: return .purple
        case .confirmed: return .blue
        case .completed: return .green
        case .cancelled: return .red
        case .refunded: return .gray
        }
    }

    private func localizedStatus(_ status: Booking.BookingStatus) -> String {
        switch status {
        case .pending:
            return localizationService.localizedString("bookingStatusPending", table: "admin")
        case .quoted:
            return localizationService.localizedString("bookingStatusQuoted", table: "admin")
        case .confirmed:
            return localizationService.localizedString("bookingStatusConfirmed", table: "admin")
        case .completed:
            return localizationService.localizedString("bookingStatusCompleted", table: "admin")
        case .cancelled:
            return localizationService.localizedString("bookingStatusCancelled", table: "admin")
        case .refunded:
            return localizationService.localizedString("bookingStatusRefunded", table: "admin")
        }
    }
}

#Preview {
    CalendarView()
}
