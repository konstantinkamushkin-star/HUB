//
//  ExploreViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine
import CoreLocation

@MainActor
class ExploreViewModel: ObservableObject {
    @Published var diveSites: [DiveSite] = []
    @Published var allDiveSites: [DiveSite] = [] // All dive sites without filters, for filter options
    @Published var diveCenters: [DiveCenter] = []
    @Published var filters = DiveSiteFilters()
    @Published var isLoading = false
    @Published var error: Error?
    
    // Location manager for getting user location
    private let locationManager = CLLocationManager()
    private let locationDelegate = LocationManagerDelegate()
    private var cancellables = Set<AnyCancellable>()
    
    // User location for geo search
    @Published var userLocation: CLLocation? {
        didSet {
            // Automatically update filters when location is available
            if let location = userLocation {
                // Only update location if geo search is enabled (maxDistance is not nil)
                if filters.maxDistance != nil {
                    filters.centerLatitude = location.coordinate.latitude
                    filters.centerLongitude = location.coordinate.longitude
                }
                // Set default radius if not set (first time location is available)
                if filters.maxDistance == nil && oldValue == nil {
                    filters.maxDistance = 500 // 500km default (increased for large regions like Red Sea)
                    filters.centerLatitude = location.coordinate.latitude
                    filters.centerLongitude = location.coordinate.longitude
                }
                #if DEBUG
                print("📍 [ExploreViewModel] Location updated in filters: \(location.coordinate.latitude), \(location.coordinate.longitude), maxDistance: \(filters.maxDistance?.description ?? "nil")")
                #endif
            }
        }
    }
    
    init() {
        setupLocationManager()
    }
    
    private func setupLocationManager() {
        locationManager.delegate = locationDelegate
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer // Use kilometer accuracy for better battery
        locationManager.distanceFilter = 1000 // Update every 1km
        
        let status = locationManager.authorizationStatus
        
        if status == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        } else if status == .authorizedWhenInUse || status == .authorizedAlways {
            locationManager.startUpdatingLocation()
        }
        
        // Observe location updates
        locationDelegate.lastLocationSubject
            .compactMap { $0 }
            .debounce(for: .seconds(1), scheduler: DispatchQueue.main)
            .assign(to: &$userLocation)
        
        locationDelegate.authorizationStatusSubject
            .sink { [weak self] status in
                guard let self = self else { return }
                if status == .authorizedWhenInUse || status == .authorizedAlways {
                    self.locationManager.startUpdatingLocation()
                } else {
                    self.locationManager.stopUpdatingLocation()
                }
            }
            .store(in: &cancellables)
    }
    
    func loadData() async {
        isLoading = true
        error = nil
        
        // Clear expired cache entries
        GeoCacheService.shared.clearExpired()
        
        
        #if DEBUG
        print("🔍 [ExploreViewModel] Loading data - Location: \(userLocation != nil ? "✅" : "❌"), Geo search: \(filters.shouldUseGeoSearch ? "✅" : "❌")")
        #endif
        
        do {
            let loadedSites: [DiveSite]
            let loadedCenters: [DiveCenter]
            
            // Use geo search if location is available, otherwise use legacy API or popular sites
            if filters.shouldUseGeoSearch,
               let lat = filters.centerLatitude,
               let lng = filters.centerLongitude {
                // Check cache first
                if let cached = GeoCacheService.shared.getCached(
                    latitude: lat,
                    longitude: lng,
                    radius: filters.radiusMeters,
                    filters: filters,
                    sortBy: "distance",
                    limit: 50
                ) {
                    loadedSites = cached
                    #if DEBUG
                    print("📍 [ExploreViewModel] Using cached geo search results: \(cached.count) sites")
                    #endif
                } else {
                    // Try optimized geo search API first
                    do {
                        let result = try await NetworkService.shared.searchDiveSitesByLocation(
                            latitude: lat,
                            longitude: lng,
                            radius: filters.radiusMeters,
                            filters: filters,
                            sortBy: "distance",
                            limit: 50
                        )
                        loadedSites = result.data
                        
                        #if DEBUG
                        print("📍 [ExploreViewModel] Geo search successful: \(loadedSites.count) sites")
                        #endif
                        
                        // Cache the result
                        GeoCacheService.shared.cache(
                            sites: loadedSites,
                            latitude: lat,
                            longitude: lng,
                            radius: filters.radiusMeters,
                            filters: filters,
                            sortBy: "distance",
                            limit: 50
                        )
                    } catch let error as NetworkError {
                        // If new geo API fails (404 = endpoint doesn't exist, or other error), fallback to legacy API
                        #if DEBUG
                        if case .serverError(404) = error {
                            print("⚠️ [ExploreViewModel] Geo search API endpoint not found (404), using legacy API")
                        } else {
                            print("⚠️ [ExploreViewModel] Geo search API failed, using legacy API: \(error.localizedDescription)")
                        }
                        #endif
                        // Fallback to legacy API with location in filters
                        loadedSites = try await NetworkService.shared.getDiveSites(filters: filters)
                    } catch {
                        // Any other error - fallback to legacy API
                        #if DEBUG
                        print("⚠️ [ExploreViewModel] Geo search API error, using legacy API: \(error.localizedDescription)")
                        #endif
                        loadedSites = try await NetworkService.shared.getDiveSites(filters: filters)
                    }
                }
            } else {
                // No location available - use legacy API or popular sites
                #if DEBUG
                print("📍 [ExploreViewModel] No location, using legacy API")
                #endif
                do {
                    loadedSites = try await NetworkService.shared.getDiveSites(filters: filters)
                } catch {
                    // If legacy API fails, try popular sites
                    #if DEBUG
                    print("⚠️ [ExploreViewModel] Legacy API failed, using popular sites: \(error.localizedDescription)")
                    #endif
                    loadedSites = try await NetworkService.shared.getPopularDiveSites(
                        country: filters.country,
                        limit: 50
                    )
                }
            }
            
            // Load centers using new optimized geo search API
            if let lat = filters.centerLatitude,
               let lng = filters.centerLongitude {
                // Try optimized geo search API first
                do {
                    let result = try await NetworkService.shared.searchDiveCentersByLocation(
                        latitude: lat,
                        longitude: lng,
                        radius: filters.radiusMeters,
                        filters: nil,
                        sortBy: "distance",
                        limit: 50
                    )
                    loadedCenters = result.data
                    
                    #if DEBUG
                    print("📍 [ExploreViewModel] Dive centers geo search successful: \(loadedCenters.count) centers")
                    #endif
                } catch {
                    // Fallback to popular centers (legacy API doesn't exist)
                    #if DEBUG
                    print("⚠️ [ExploreViewModel] Dive centers geo search failed, using popular centers: \(error.localizedDescription)")
                    #endif
                    loadedCenters = try await NetworkService.shared.getPopularDiveCenters(
                        country: filters.country,
                        limit: 50
                    )
                }
            } else {
                // No location - use popular centers
                loadedCenters = try await NetworkService.shared.getPopularDiveCenters(
                    country: filters.country,
                    limit: 50
                )
            }
            
            
            diveSites = loadedSites
            diveCenters = loadedCenters
            
            // Load all dive sites (without filters) for filter options if not already loaded
            if allDiveSites.isEmpty {
                let emptyFilters = DiveSiteFilters()
                // Try to use geo search if we have location
                if emptyFilters.shouldUseGeoSearch,
                   let lat = emptyFilters.centerLatitude,
                   let lng = emptyFilters.centerLongitude {
                    let result = try await NetworkService.shared.searchDiveSitesByLocation(
                        latitude: lat,
                        longitude: lng,
                        radius: 100000, // 100km for all sites
                        filters: nil,
                        limit: 200
                    )
                    allDiveSites = result.data
                } else {
                    allDiveSites = try await NetworkService.shared.getDiveSites(filters: emptyFilters)
                }
            }
            
            isLoading = false
        } catch {
            self.error = error
            isLoading = false
        }
    }
    
    func loadAllDiveSitesForFilters() async {
        // Load all dive sites without filters for filter options
        do {
            let emptyFilters = DiveSiteFilters()
            allDiveSites = try await NetworkService.shared.getDiveSites(filters: emptyFilters)
        } catch {
            print("Error loading all dive sites for filters: \(error)")
        }
    }
    
    func search(query: String) {
        // TODO: Implement search logic
        Task {
            await loadData()
        }
    }
}
