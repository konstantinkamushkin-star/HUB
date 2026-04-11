//
//  BookingCalendarView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct BookingCalendarView: View {
    @StateObject private var viewModel = AdminViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedStatus: String = "all"
    @State private var selectedDate = Date()
    @State private var selectedBooking: Booking?
    @State private var viewMode: ViewMode = .calendar
    
    enum ViewMode {
        case list
        case calendar
    }
    
    var filteredBookings: [Booking] {
        let dateFiltered = viewModel.bookings.filter { Calendar.current.isDate($0.date, inSameDayAs: selectedDate) }
        
        if selectedStatus == "all" {
            return dateFiltered
        }
        if let status = Booking.BookingStatus(rawValue: selectedStatus) {
            return dateFiltered.filter { $0.status == status }
        }
        return dateFiltered
    }
    
    var bookingsForSelectedDate: [Booking] {
        viewModel.bookings.filter { Calendar.current.isDate($0.date, inSameDayAs: selectedDate) }
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Mode Picker
                Picker("View Mode", selection: $viewMode) {
                    Text(localizationService.localizedString("calendar", table: "admin")).tag(ViewMode.calendar)
                    Text(localizationService.localizedString("list", table: "common")).tag(ViewMode.list)
                }
                .pickerStyle(.segmented)
                .padding()
                
                if viewMode == .calendar {
                    calendarView
                } else {
                    listView
                }
            }
            .navigationTitle(localizationService.localizedString("bookings", table: "admin"))
            .sheet(item: $selectedBooking) { booking in
                BookingDetailView(booking: booking, viewModel: viewModel)
            }
            .task {
                await viewModel.loadBookings()
            }
        }
    }
    
    private var calendarView: some View {
        VStack {
            // Custom Calendar with Status Indicators
            CustomCalendarView(
                selectedDate: $selectedDate,
                bookings: viewModel.bookings
            )
            .padding()
            
            // Status Filter
            Section {
                Picker(localizationService.localizedString("filterByStatus", table: "admin"), selection: $selectedStatus) {
                    Text(localizationService.localizedString("all", table: "common")).tag("all")
                    ForEach([Booking.BookingStatus.pending, .confirmed, .completed, .cancelled], id: \.self) { status in
                        Text(status.rawValue.capitalized).tag(status.rawValue)
                    }
                }
                .pickerStyle(.menu)
                .padding(.horizontal)
            }
            
            // Bookings for selected date
            List {
                Section(localizationService.localizedString("bookingsForDate", table: "admin")) {
                    if filteredBookings.isEmpty {
                        Text(localizationService.localizedString("noBookings", table: "admin"))
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(filteredBookings) { booking in
                            Button(action: {
                                selectedBooking = booking
                            }) {
                                BookingRowView(booking: booking, onTap: {})
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }
    
    private var listView: some View {
        List {
            Section {
                Picker(localizationService.localizedString("filterByStatus", table: "admin"), selection: $selectedStatus) {
                    Text(localizationService.localizedString("all", table: "common")).tag("all")
                    ForEach([Booking.BookingStatus.pending, .confirmed, .completed, .cancelled], id: \.self) { status in
                        Text(status.rawValue.capitalized).tag(status.rawValue)
                    }
                }
            }
            
            Section {
                let allFiltered = selectedStatus == "all" ? viewModel.bookings : viewModel.bookings.filter { $0.status.rawValue == selectedStatus }
                ForEach(allFiltered) { booking in
                    Button(action: {
                        selectedBooking = booking
                    }) {
                        BookingRowView(booking: booking, onTap: {})
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

#Preview {
    BookingCalendarView()
}
