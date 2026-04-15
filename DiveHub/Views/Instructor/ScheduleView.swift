//
//  ScheduleView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct InstructorScheduleView: View {
    @StateObject private var viewModel = InstructorViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedDate = Date()
    @State private var viewMode: ViewMode = .calendar
    @State private var selectedStatus: String = "all"
    
    enum ViewMode {
        case calendar
        case list
    }
    
    var bookingsForSelectedDate: [InstructorViewModel.ScheduleItem] {
        viewModel.schedule.filter { Calendar.current.isDate($0.date, inSameDayAs: selectedDate) }
    }
    
    var filteredBookings: [InstructorViewModel.ScheduleItem] {
        let filtered: [InstructorViewModel.ScheduleItem]
        if selectedStatus == "all" {
            filtered = viewModel.schedule
        } else if let status = Booking.BookingStatus(rawValue: selectedStatus) {
            filtered = viewModel.schedule.filter { $0.booking.status == status }
        } else {
            filtered = viewModel.schedule
        }
        return filtered.sorted { $0.date < $1.date }
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // View Mode Picker
                Picker("ui_admin_view_mode".localized, selection: $viewMode) {
                    Text(localizationService.localizedString("calendar", table: "common")).tag(ViewMode.calendar)
                    Text(localizationService.localizedString("list", table: "common")).tag(ViewMode.list)
                }
                .pickerStyle(.segmented)
                .padding()
                
                if viewMode == .calendar {
                    // Calendar View
                    DatePicker(
                        localizationService.localizedString("selectDate", table: "instructor"),
                        selection: $selectedDate,
                        displayedComponents: .date
                    )
                    .datePickerStyle(.graphical)
                    .padding()
                    
                    // Bookings for selected date
                    List {
                        Section(localizationService.localizedString("scheduleForDate", table: "instructor")) {
                            if bookingsForSelectedDate.isEmpty {
                                Text(localizationService.localizedString("noBookings", table: "instructor"))
                                    .foregroundColor(.secondary)
                            } else {
                                ForEach(bookingsForSelectedDate) { item in
                                    ScheduleItemRow(item: item, viewModel: viewModel)
                                }
                            }
                        }
                    }
                } else {
                    // List View with Filters
                    VStack(spacing: 0) {
                        // Filter Picker
                        Picker(localizationService.localizedString("filterByStatus", table: "instructor"), selection: $selectedStatus) {
                            Text(localizationService.localizedString("all", table: "common")).tag("all")
                            ForEach([Booking.BookingStatus.pending, .quoted, .confirmed, .completed, .cancelled], id: \.self) { status in
                                Text(status.rawValue.capitalized).tag(status.rawValue)
                            }
                        }
                        .pickerStyle(.segmented)
                        .padding()
                        
                        // Bookings List
                        List {
                            if filteredBookings.isEmpty {
                                Text(localizationService.localizedString("noBookings", table: "instructor"))
                                    .foregroundColor(.secondary)
                                    .frame(maxWidth: .infinity, alignment: .center)
                                    .padding()
                            } else {
                                ForEach(filteredBookings) { item in
                                    ScheduleItemRow(item: item, viewModel: viewModel)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("schedule", table: "instructor"))
            .refreshable {
                await viewModel.loadMyBookings()
            }
            .task {
                await viewModel.loadMyBookings()
            }
        }
    }
}

#Preview {
    InstructorScheduleView()
}
