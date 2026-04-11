//
//  TripViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine

@MainActor
class TripViewModel: ObservableObject {
    @Published var trips: [Trip] = []
    @Published var allTrips: [Trip] = [] // All trips without filters, for filter options
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var filters = TripFilters()
    
    // For trip creation/editing
    @Published var hotels: [Hotel] = []
    @Published var yachts: [Yacht] = []
    @Published var courses: [Course] = []
    @Published var instructors: [Instructor] = []
    
    private var cancellables = Set<AnyCancellable>()
    private var newlyCreatedTripIds: Set<String> = []
    
    struct TripFilters {
        var tripType: Trip.TripType?
        var country: String?
        var startDate: Date?
        var endDate: Date?
        var minCertificationLevel: String?
        var nitroxAvailable: Bool?
        var equipmentRentalAvailable: Bool?
        var availableSpots: Bool? // Only trips with available spots
    }
    
    func loadTrips() async {
        isLoading = true
        errorMessage = nil
        
        do {
            trips = try await NetworkService.shared.getTrips(filters: filters)
            
            if allTrips.isEmpty {
                let emptyFilters = TripFilters()
                allTrips = try await NetworkService.shared.getTrips(filters: emptyFilters)
            }
            
            isLoading = false
        } catch {
            let tripsToPreserve = trips.filter { newlyCreatedTripIds.contains($0.id) }
            trips = tripsToPreserve
            allTrips = allTrips.filter { newlyCreatedTripIds.contains($0.id) }
            if allTrips.isEmpty, !tripsToPreserve.isEmpty {
                allTrips = tripsToPreserve
            }
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }
    
    func loadAllTripsForFilters() async {
        do {
            let emptyFilters = TripFilters()
            allTrips = try await NetworkService.shared.getTrips(filters: emptyFilters)
        } catch {
            print("Error loading all trips for filters: \(error)")
        }
    }
    
    func loadHotels() async {
        do {
            hotels = try await NetworkService.shared.getHotels()
        } catch {
            print("Error loading hotels: \(error)")
        }
    }
    
    func loadYachts() async {
        do {
            yachts = try await NetworkService.shared.getYachts()
        } catch {
            print("Error loading yachts: \(error)")
        }
    }
    
    func loadCourses() async {
        do {
            courses = try await NetworkService.shared.getCourses()
        } catch {
            print("Error loading courses: \(error)")
        }
    }
    
    func loadInstructors(diveCenterId: String) async {
        do {
            instructors = try await NetworkService.shared.getDiveCenterInstructors(diveCenterId: diveCenterId)
        } catch {
            print("Error loading instructors: \(error)")
        }
    }
    
    func createTrip(_ trip: Trip, hotelName: String? = nil, hotelUrl: String? = nil, yachtName: String? = nil, yachtUrl: String? = nil) async throws -> Trip {
        let createdTrip = try await NetworkService.shared.createTrip(trip, hotelName: hotelName, hotelUrl: hotelUrl, yachtName: yachtName, yachtUrl: yachtUrl)
        // Reload trips to get the latest data from server
        await loadTrips()
        // Ensure the newly created trip is in the array (in case loadTrips failed and replaced with test data)
        if !trips.contains(where: { $0.id == createdTrip.id }) {
            trips.append(createdTrip)
        }
        // Track this trip as newly created so it's preserved if loadTrips() fails
        newlyCreatedTripIds.insert(createdTrip.id)
        return createdTrip
    }
    
    func updateTrip(_ trip: Trip, hotelName: String? = nil, hotelUrl: String? = nil, yachtName: String? = nil, yachtUrl: String? = nil) async throws -> Trip {
        do {
            let updatedTrip = try await NetworkService.shared.updateTrip(trip, hotelName: hotelName, hotelUrl: hotelUrl, yachtName: yachtName, yachtUrl: yachtUrl)
            await loadTrips()
            return updatedTrip
        } catch let error as NetworkError {
            if case .serverError(404) = error {
                return try await createTrip(trip, hotelName: hotelName, hotelUrl: hotelUrl, yachtName: yachtName, yachtUrl: yachtUrl)
            }
            throw error
        }
    }
    
    func deleteTrip(tripId: String) async throws {
        try await NetworkService.shared.deleteTrip(tripId: tripId)
    }
    
    func bookTrip(tripId: String, participants: [Trip.TripParticipant]) async throws -> Trip {
        return try await NetworkService.shared.bookTrip(tripId: tripId, participants: participants)
    }
    
    func canCreateTrip(user: User) -> Bool {
        // Only dive centers or users with professional subscription can create trips
        if user.role == .diveCenterAdmin {
            return true
        }
        if user.role == .diverPro, user.subscriptionStatus == .active {
            return true
        }
        return false
    }
}
