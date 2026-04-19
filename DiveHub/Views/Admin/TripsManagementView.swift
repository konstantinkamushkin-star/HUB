//
//  TripsManagementView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct TripsManagementView: View {
    @StateObject private var viewModel = TripViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @EnvironmentObject var authService: AuthenticationService
    @State private var showCreateTrip = false
    @State private var selectedTrip: Trip?
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                contentView
            }
            .navigationTitle(localizationService.localizedString("tripManagement", table: "trips"))
            .diveHubNavigationChrome()
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showCreateTrip = true }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title3)
                    }
                }
            }
            .sheet(isPresented: $showCreateTrip) {
                CreateTripView(sharedViewModel: viewModel)
                    .environmentObject(AuthenticationService.shared)
                    .environmentObject(LocalizationService.shared)
            }
            .sheet(item: $selectedTrip) { trip in
                TripManagementDetailView(trip: trip, selectedTrip: $selectedTrip, viewModel: viewModel)
            }
            .task {
                if let user = authService.currentUser, let diveCenterId = user.diveCenterId {
                    viewModel.filters = TripViewModel.TripFilters()
                    await viewModel.loadTrips()
                    viewModel.trips = viewModel.trips.filter {
                        ($0.organizerId == diveCenterId || $0.organizerId == user.id) && $0.organizerType == .diveCenter
                    }
                }
            }
        }
        .onChange(of: showCreateTrip) { oldValue, newValue in
                // Reload trips when the create sheet is dismissed
                if oldValue == true && newValue == false {
                    Task {
                        if let user = authService.currentUser, let diveCenterId = user.diveCenterId {
                            viewModel.filters = TripViewModel.TripFilters()
                            await viewModel.loadTrips()
                            // Filter trips by organizer (check both diveCenterId and userId for diveCenterAdmin)
                            viewModel.trips = viewModel.trips.filter {
                                ($0.organizerId == diveCenterId || $0.organizerId == user.id) && $0.organizerType == .diveCenter
                            }
                        }
                    }
                }
        }
        .onChange(of: selectedTrip) { oldValue, newValue in
            // Reload trips when detail sheet is dismissed
            if oldValue != nil && newValue == nil {
                Task {
                    if let user = authService.currentUser, let diveCenterId = user.diveCenterId {
                        viewModel.filters = TripViewModel.TripFilters()
                        await viewModel.loadTrips()
                        viewModel.trips = viewModel.trips.filter {
                            ($0.organizerId == diveCenterId || $0.organizerId == user.id) && $0.organizerType == .diveCenter
                        }
                    }
                }
            }
        }
    }
    
    @ViewBuilder
    private var contentView: some View {
        VStack {
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.trips.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "airplane.departure")
                        .font(.system(size: 60))
                        .foregroundColor(.gray)
                    Text(localizationService.localizedString("noTripsAvailable", table: "trips"))
                        .font(.headline)
                        .foregroundColor(.gray)
                    Button(localizationService.localizedString("createFirstTrip", table: "trips")) {
                        showCreateTrip = true
                    }
                    .buttonStyle(.borderedProminent)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(viewModel.trips) { trip in
                        TripManagementRowView(trip: trip)
                            .onTapGesture {
                                selectedTrip = trip
                            }
                    }
                    .onDelete(perform: deleteTrips)
                }
            }
        }
    }
    
    private func deleteTrips(at offsets: IndexSet) {
        for index in offsets {
            let trip = viewModel.trips[index]
            Task {
                do {
                    try await viewModel.deleteTrip(tripId: trip.id)
                } catch {
                    print("Error deleting trip: \(error)")
                }
            }
        }
    }
}

struct TripManagementRowView: View {
    let trip: Trip
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(trip.country)
                    .font(.headline)
                Spacer()
                Text(trip.tripType.rawValue.capitalized)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.blue.opacity(0.2))
                    .cornerRadius(8)
            }
            
            Text(trip.description)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .lineLimit(2)
            
            HStack {
                Label("\(trip.startDate.formatted(date: .abbreviated, time: .omitted)) - \(trip.endDate.formatted(date: .abbreviated, time: .omitted))", systemImage: "calendar")
                    .font(.caption)
                Spacer()
                Label("\(trip.bookedSpots)/\(trip.totalSpots)", systemImage: "person.2")
                    .font(.caption)
                    .foregroundColor(trip.isFullyBooked ? .red : .green)
            }
        }
        .padding(.vertical, 4)
    }
}

struct TripManagementDetailView: View {
    let trip: Trip
    @ObservedObject var viewModel: TripViewModel
    @Binding var selectedTrip: Trip?
    @Environment(\.dismiss) var dismiss
    @State private var showEdit = false
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var authService = AuthenticationService.shared
    @State private var currentTrip: Trip
    @State private var refreshId = UUID()
    
    init(trip: Trip, selectedTrip: Binding<Trip?>, viewModel: TripViewModel) {
        self.trip = trip
        self._selectedTrip = selectedTrip
        self.viewModel = viewModel
        _currentTrip = State(initialValue: trip)
    }
    
    // Flag to prevent infinite loops
    @State private var isUpdating = false
    
    // Update currentTrip when viewModel.trips changes
    private func updateCurrentTrip() {
        // Prevent infinite loops
        guard !isUpdating else {
            return
        }
        
        isUpdating = true
        defer { 
            // Use Task to reset flag asynchronously to avoid immediate re-triggering
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
                isUpdating = false
            }
        }
        
        // First, try to find trip by original ID
        if let updatedTrip = viewModel.trips.first(where: { $0.id == trip.id }) {
            // Only update if trip data actually changed to avoid infinite loops
            // Check all relevant fields: tripType, hotelId, yachtId, country, description
            if updatedTrip.id == currentTrip.id && 
               updatedTrip.tripType == currentTrip.tripType &&
               updatedTrip.hotelId == currentTrip.hotelId &&
               updatedTrip.yachtId == currentTrip.yachtId &&
               updatedTrip.country == currentTrip.country &&
               updatedTrip.description == currentTrip.description {
                return
            }
            
            // Only update if actually different to avoid unnecessary refreshes
            if currentTrip.id != updatedTrip.id {
                currentTrip = updatedTrip
                refreshId = UUID() // Force view refresh
            }
        } else {
            // Trip with original ID not found - might have been created as new
            // Try to find a trip that matches the currentTrip's data (same country, dates, etc.)
            // This handles the case where a test trip was created as a new trip on backend
            if let matchingTrip = viewModel.trips.first(where: { trip in
                trip.country == currentTrip.country &&
                trip.startDate == currentTrip.startDate &&
                trip.endDate == currentTrip.endDate &&
                trip.description == currentTrip.description
            }) {
                // Only update if the ID is actually different to avoid infinite loops
                if matchingTrip.id != currentTrip.id {
                    // Found matching trip - update to use it
                    currentTrip = matchingTrip
                    // Only update selectedTrip if it's different to avoid triggering onChange
                    if selectedTrip?.id != matchingTrip.id {
                        selectedTrip = matchingTrip
                    }
                    refreshId = UUID()
                } else {
                }
            }
        }
    }
    
    var body: some View {
        NavigationView {
            ScrollView {
                contentView
                    .padding()
            }
            .navigationTitle(localizationService.localizedString("tripDetails", table: "trips"))
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("edit", table: "common")) {
                        showEdit = true
                    }
                }
            }
            .sheet(isPresented: $showEdit) {
                CreateTripView(
                    trip: currentTrip,
                    onTripSaved: { savedTrip in
                        // Callback when trip is saved - update currentTrip and selectedTrip if ID changed
                        
                        // Reload trips to ensure the new/updated trip is in the list
                        Task {
                            if let user = authService.currentUser, let diveCenterId = user.diveCenterId {
                                // Save current trips count before reload
                                let currentTripsCount = viewModel.trips.count
                                
                                viewModel.filters = TripViewModel.TripFilters()
                                await viewModel.loadTrips()
                                
                                // Filter trips by organizer (check both diveCenterId and userId for diveCenterAdmin)
                                let filteredTrips = viewModel.trips.filter { 
                                    ($0.organizerId == diveCenterId || $0.organizerId == user.id) && $0.organizerType == .diveCenter
                                }
                                
                                
                                // Only update viewModel.trips if we have filtered trips, otherwise keep existing trips
                                // This prevents the list from shrinking to just one trip after saving
                                if !filteredTrips.isEmpty {
                                    // If we had trips before and now have fewer, it might be a filtering issue
                                    // In that case, merge the saved trip into existing trips if it's not already there
                                    if currentTripsCount > 0 && filteredTrips.count < currentTripsCount {
                                        // Keep existing trips and add/update the saved trip
                                        var updatedTrips = viewModel.trips
                                        if let index = updatedTrips.firstIndex(where: { $0.id == savedTrip.id }) {
                                            updatedTrips[index] = savedTrip
                                        } else {
                                            updatedTrips.append(savedTrip)
                                        }
                                        // Re-filter to ensure we only show trips for this dive center
                                        viewModel.trips = updatedTrips.filter { 
                                            ($0.organizerId == diveCenterId || $0.organizerId == user.id) && $0.organizerType == .diveCenter
                                        }
                                    } else {
                                        viewModel.trips = filteredTrips
                                    }
                                } else {
                                    let onlySaved = [savedTrip].filter {
                                        ($0.organizerId == diveCenterId || $0.organizerId == user.id)
                                            && $0.organizerType == .diveCenter
                                    }
                                    if !onlySaved.isEmpty {
                                        viewModel.trips = onlySaved
                                    }
                                }
                                
                                // Update selectedTrip and currentTrip
                                if savedTrip.id != currentTrip.id {
                                    // Trip was created as new (test trip didn't exist on backend)
                                    // Find the saved trip in the list
                                    if let foundTrip = viewModel.trips.first(where: { $0.id == savedTrip.id }) {
                                        selectedTrip = foundTrip
                                        currentTrip = foundTrip
                                    } else {
                                        // Trip not found - use savedTrip directly
                                        selectedTrip = savedTrip
                                        currentTrip = savedTrip
                                    }
                                    refreshId = UUID()
                                } else {
                                    // Trip was updated - update currentTrip directly
                                    if let updatedTrip = viewModel.trips.first(where: { $0.id == savedTrip.id }) {
                                        currentTrip = updatedTrip
                                    } else {
                                        // If trip not found, use savedTrip
                                        currentTrip = savedTrip
                                    }
                                }
                            }
                        }
                    },
                    sharedViewModel: viewModel
                )
                .environmentObject(authService)
                .environmentObject(LocalizationService.shared)
                .id(currentTrip.id) // Stable ID to prevent view recreation
            }
            .onChange(of: showEdit) { oldValue, newValue in
                // Reload trip data when edit sheet is dismissed
                // Only update if we're not already updating to avoid cycles
                if oldValue == true && newValue == false && !isUpdating {
                    Task {
                        // Small delay to ensure viewModel.trips is updated
                        try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds
                        updateCurrentTrip()
                    }
                }
            }
            .onAppear {
                updateCurrentTrip()
            }
            .id(refreshId) // Force view refresh when refreshId changes
        }
    }
    
    @ViewBuilder
    private var contentView: some View {
        VStack(alignment: .leading, spacing: 16) {
            basicInfoSection
            Divider()
            hotelYachtSection
            statsSection
            Divider()
            participantsSection
        }
    }
    
    @ViewBuilder
    private var basicInfoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(currentTrip.country)
                .font(.title)
                .bold()
            
            Text(currentTrip.tripType.rawValue.capitalized)
                .font(.subheadline)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.blue.opacity(0.2))
                .cornerRadius(8)
            
            Text(currentTrip.description)
                .font(.body)
                .foregroundColor(.secondary)
        }
    }
    
    @ViewBuilder
    private var hotelYachtSection: some View {
        if currentTrip.tripType == .daily, let hotelId = currentTrip.hotelId {
            VStack(alignment: .leading, spacing: 8) {
                Text(localizationService.localizedString("hotel", table: "trips"))
                    .font(.headline)
                
                if let hotel = viewModel.hotels.first(where: { $0.id == hotelId }) {
                    HStack {
                        Image(systemName: "bed.double.fill")
                            .foregroundColor(.blue)
                        Text("\(localizationService.localizedString("hotel", table: "trips")): \(hotel.name)")
                            .font(.subheadline)
                        Spacer()
                    }
                } else {
                    HStack {
                        Image(systemName: "bed.double.fill")
                            .foregroundColor(.blue)
                        Text("\(localizationService.localizedString("hotel", table: "trips")): \(hotelId)")
                            .font(.subheadline)
                    }
                }
            }
            Divider()
        } else if currentTrip.tripType == .safari, let yachtId = currentTrip.yachtId {
            VStack(alignment: .leading, spacing: 8) {
                Text(localizationService.localizedString("yacht", table: "trips"))
                    .font(.headline)
                
                if let yacht = viewModel.yachts.first(where: { $0.id == yachtId }) {
                    HStack {
                        Image(systemName: "sailboat.fill")
                            .foregroundColor(.blue)
                        Text("\(localizationService.localizedString("yacht", table: "trips")): \(yacht.name)")
                            .font(.subheadline)
                        Spacer()
                    }
                } else {
                    HStack {
                        Image(systemName: "sailboat.fill")
                            .foregroundColor(.blue)
                        Text("\(localizationService.localizedString("yacht", table: "trips")): \(yachtId)")
                            .font(.subheadline)
                    }
                }
            }
            Divider()
        }
    }
    
    @ViewBuilder
    private var statsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizationService.localizedString("statistics", table: "trips"))
                .font(.headline)
            HStack {
                VStack(alignment: .leading) {
                    Text(localizationService.localizedString("booked", table: "trips"))
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text("\(currentTrip.bookedSpots)")
                        .font(.title2)
                        .bold()
                }
                Spacer()
                VStack(alignment: .leading) {
                    Text(localizationService.localizedString("available", table: "trips"))
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text("\(currentTrip.availableSpots)")
                        .font(.title2)
                        .bold()
                        .foregroundColor(.green)
                }
                Spacer()
                VStack(alignment: .leading) {
                    Text(localizationService.localizedString("total", table: "trips"))
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text("\(currentTrip.totalSpots)")
                        .font(.title2)
                        .bold()
                }
            }
        }
    }
    
    @ViewBuilder
    private var participantsSection: some View {
        if !currentTrip.participants.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("\(localizationService.localizedString("participants", table: "trips")) (\(currentTrip.participants.count))")
                    .font(.headline)
                ForEach(currentTrip.participants) { participant in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(participant.name)
                                .font(.subheadline)
                            if let email = participant.email {
                                Text(email)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        Spacer()
                        Text(participant.isDiving ? localizationService.localizedString("diving", table: "trips") : localizationService.localizedString("nonDiving", table: "trips"))
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(participant.isDiving ? Color.blue.opacity(0.2) : Color.gray.opacity(0.2))
                            .cornerRadius(8)
                    }
                }
            }
        }
    }
}
