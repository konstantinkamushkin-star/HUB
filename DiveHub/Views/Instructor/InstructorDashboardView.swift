//
//  InstructorDashboardView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct InstructorDashboardView: View {
    @StateObject private var viewModel = InstructorViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    
    var todayBookings: [InstructorViewModel.ScheduleItem] {
        viewModel.schedule.filter { Calendar.current.isDateInToday($0.date) }
    }
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Statistics Cards
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                        InstructorStatCard(
                            title: localizationService.localizedString("today", table: "instructor"),
                            value: "\(viewModel.todayBookingsCount)",
                            icon: "calendar.badge.clock",
                            color: .blue
                        )
                        InstructorStatCard(
                            title: localizationService.localizedString("thisWeek", table: "instructor"),
                            value: "\(viewModel.thisWeekBookingsCount)",
                            icon: "calendar",
                            color: .green
                        )
                        InstructorStatCard(
                            title: localizationService.localizedString("upcoming", table: "instructor"),
                            value: "\(viewModel.upcomingBookingsCount)",
                            icon: "clock",
                            color: .orange
                        )
                        InstructorStatCard(
                            title: localizationService.localizedString("completed", table: "instructor"),
                            value: "\(viewModel.completedBookingsCount)",
                            icon: "checkmark.circle",
                            color: .purple
                        )
                    }
                    .padding()
                    
                    // Today's Schedule
                    if !todayBookings.isEmpty {
                        Section(header: Text(localizationService.localizedString("todaySchedule", table: "instructor"))
                            .font(.headline)
                            .padding(.horizontal)) {
                            ForEach(todayBookings) { item in
                                ScheduleItemRow(item: item, viewModel: viewModel)
                            }
                        }
                    } else {
                        Section(header: Text(localizationService.localizedString("todaySchedule", table: "instructor"))
                            .font(.headline)
                            .padding(.horizontal)) {
                            Text(localizationService.localizedString("noBookingsToday", table: "instructor"))
                                .foregroundColor(.secondary)
                                .padding()
                        }
                    }
                }
                .padding(.vertical)
            }
            .navigationTitle(localizationService.localizedString("dashboard", table: "instructor"))
            .refreshable {
                await viewModel.loadMyBookings()
            }
            .task {
                await viewModel.loadMyBookings()
            }
        }
    }
}

struct InstructorStatCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)
            Text(value)
                .font(.title)
                .fontWeight(.bold)
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(Color.diveCard)
        .cornerRadius(12)
    }
}

struct ScheduleItemRow: View {
    let item: InstructorViewModel.ScheduleItem
    @ObservedObject var viewModel: InstructorViewModel
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading) {
                    Text(item.date.formatted(date: .abbreviated, time: .omitted))
                        .font(.headline)
                    Text(item.time)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Text(item.booking.status.rawValue.capitalized)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(statusColor(for: item.booking.status))
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
            
            Text(LocalizationService.shared.localizedString("client", table: "instructor") + ": \(item.clientName)")
                .font(.subheadline)
            
            if item.booking.status == .confirmed {
                Button(LocalizationService.shared.localizedString("markCompleted", table: "instructor")) {
                    Task {
                        try? await viewModel.markDiveCompleted(item.booking.id)
                    }
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding()
        .background(Color.diveCard)
        .cornerRadius(12)
        .padding(.horizontal)
    }
    
    private func statusColor(for status: Booking.BookingStatus) -> Color {
        switch status {
        case .pending: return .orange
        case .confirmed: return .blue
        case .completed: return .green
        case .cancelled: return .red
        case .refunded: return .gray
        }
    }
}

#Preview {
    InstructorDashboardView()
}
