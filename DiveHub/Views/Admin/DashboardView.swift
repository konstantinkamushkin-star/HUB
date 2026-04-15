//
//  DashboardView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct AdminDashboardView: View {
    @StateObject private var tripViewModel = TripViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedDate = Date()
    
    var tripsForSelectedDate: [Trip] {
        tripViewModel.trips.filter { trip in
            selectedDate >= trip.startDate && selectedDate <= trip.endDate
        }
    }
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Quick Actions
                    Section(header: Text(localizationService.localizedString("quickActions", table: "admin"))
                        .font(.headline)
                        .padding(.horizontal)) {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                            QuickActionButton(
                                title: localizationService.localizedString("instructors", table: "admin"),
                                icon: "person.2",
                                destination: AnyView(InstructorManagementView())
                            )
                            QuickActionButton(
                                title: "Services & Prices",
                                icon: "tag",
                                destination: AnyView(
                                    ServicesManagementView()
                                        .environmentObject(AuthenticationService.shared)
                                )
                            )
                        }
                        .padding(.horizontal)
                    }
                    
                    // Calendar and trips for selected day
                    Section(header: Text(localizationService.localizedString("calendar", table: "admin"))
                        .font(.headline)
                        .padding(.horizontal)) {
                        VStack(spacing: 16) {
                            // Custom Calendar with Status Indicators
                            CustomCalendarView(
                                selectedDate: $selectedDate,
                                bookings: []
                            )
                            .padding()
                            
                            // Trips for selected date
                            if !tripsForSelectedDate.isEmpty {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text(localizationService.localizedString("trips", table: "trips"))
                                        .font(.headline)
                                        .padding(.horizontal)
                                    ForEach(tripsForSelectedDate.prefix(3)) { trip in
                                        TripRow(trip: trip)
                                    }
                                    if tripsForSelectedDate.count > 3 {
                                        NavigationLink(destination: TripsManagementView().environmentObject(AuthenticationService.shared)) {
                                            Text("ui_admin_view_all_trips".localized)
                                                .font(.caption)
                                                .foregroundColor(.divePrimary)
                                                .padding(.horizontal)
                                        }
                                    }
                                }
                            }
                            
                            if tripsForSelectedDate.isEmpty {
                                Text(localizationService.localizedString("noTrips", table: "trips"))
                                    .foregroundColor(.secondary)
                                    .padding()
                            }
                        }
                        .padding(.vertical, 8)
                    }
                }
                .padding(.vertical)
            }
            .navigationTitle(localizationService.localizedString("dashboard", table: "admin"))
            .task {
                await tripViewModel.loadTrips()
            }
        }
    }
}

struct QuickActionButton: View {
    let title: String
    let icon: String
    let destination: AnyView
    
    var body: some View {
        NavigationLink(destination: destination) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.title)
                    .foregroundColor(.divePrimary)
                Text(title)
                    .font(.caption)
                    .foregroundColor(.primary)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.diveCard)
            .cornerRadius(12)
            .shadow(radius: 2)
        }
    }
}

struct TripRow: View {
    let trip: Trip
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(trip.country)
                    .font(.headline)
                if let region = trip.region, !region.isEmpty {
                    Text(region)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Text("ui_admin_value_value_3".localized)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing) {
                Text("ui_admin_value_value".localized)
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(localizationService.localizedString("spots", table: "trips"))
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color.diveCard)
        .cornerRadius(8)
        .padding(.horizontal)
    }
}

#Preview {
    AdminDashboardView()
}
