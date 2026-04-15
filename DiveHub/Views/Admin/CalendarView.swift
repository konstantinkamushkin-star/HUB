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
        .navigationTitle(localizationService.localizedString("calendar", table: "admin"))
        .task {
            await viewModel.loadBookings()
        }
    }
}

private struct CalendarBookingRow: View {
    let booking: Booking

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
            Text(booking.status.rawValue.capitalized)
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
}

#Preview {
    CalendarView()
}
