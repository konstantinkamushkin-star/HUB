//
//  GenericExploreViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine
import CoreLocation

@MainActor
class GenericExploreViewModel: ObservableObject {
    // Category state
    @Published var selectedCategory: ExploreCategory = .diveSites
    
    // View mode state (per category)
    @Published var viewModes: [ExploreCategory: ExploreViewMode] = [
        .diveSites: .list,
        .diveCenters: .list,
        .shops: .list
    ]
    
    // Search state (per category)
    @Published var searchQueries: [ExploreCategory: String] = [
        .diveSites: "",
        .diveCenters: "",
        .shops: ""
    ]
    
    // Sort state (per category)
    @Published var sortOptions: [ExploreCategory: ExploreSortOption] = [
        .diveSites: .distance,
        .diveCenters: .distance,
        .shops: .distance
    ]
    
    // Filter state (per category)
    @Published var diveSiteFilters = DiveSiteFilters()
    @Published var diveCenterFilters = DiveCenterFilters()
    @Published var shopFilters = ShopFilters()
    
    // Data state
    @Published var diveSites: [DiveSite] = []
    @Published var diveCenters: [DiveCenter] = []
    @Published var shops: [Shop] = []
    
    // User location
    @Published var userLocation: CLLocation?
    
    // Loading and error state
    @Published var isLoading = false
    @Published var error: Error?
    
    // Pagination state
    @Published var currentPage: [ExploreCategory: Int] = [
        .diveSites: 1,
        .diveCenters: 1,
        .shops: 1
    ]
    @Published var hasMorePages: [ExploreCategory: Bool] = [
        .diveSites: true,
        .diveCenters: true,
        .shops: true
    ]
    // Store total count for pagination
    private var totalCounts: [ExploreCategory: Int] = [:]
    
    // Cache
    private var cache: [ExploreCategory: [Any]] = [:]
    private let cacheService = ExploreCacheService.shared
    
    // Location manager
    private let locationManager = CLLocationManager()
    private let locationDelegate = ExploreLocationManagerDelegate()
    private var cancellables = Set<AnyCancellable>()
    
    var currentSortOption: ExploreSortOption {
        get { sortOptions[selectedCategory] ?? .distance }
        set { sortOptions[selectedCategory] = newValue }
    }
    
    init() {
        setupLocationManager()
        observeLocationUpdates()
    }
    
    private func setupLocationManager() {
        locationManager.delegate = locationDelegate
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = 100 // Update every 100 meters
        
        let status = locationManager.authorizationStatus
        
        if status == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        } else if status == .authorizedWhenInUse || status == .authorizedAlways {
            locationManager.startUpdatingLocation()
        }
    }
    
    private func observeLocationUpdates() {
        locationDelegate.lastLocationSubject
            .compactMap { $0 }
            .sink { [weak self] (location: CLLocation) in
                guard let self = self else { return }
                let hadLocation = self.userLocation != nil
                self.userLocation = location
                
                // Update filters with user location for geo search
                // Only update location if geo search is enabled (maxDistance is not nil)
                if self.diveSiteFilters.maxDistance != nil {
                    self.diveSiteFilters.centerLatitude = location.coordinate.latitude
                    self.diveSiteFilters.centerLongitude = location.coordinate.longitude
                }
                // Don't set default maxDistance here - let user control it via filters
                
                self.diveCenterFilters.centerLatitude = location.coordinate.latitude
                self.diveCenterFilters.centerLongitude = location.coordinate.longitude
                if self.diveCenterFilters.maxDistance == nil {
                    self.diveCenterFilters.maxDistance = 50 // 50km default
                }
                
                
                // If we just got location and current sort is by distance, re-sort
                if !hadLocation && self.currentSortOption == .distance {
                    Task {
                        await self.applySorting()
                    }
                }
            }
            .store(in: &cancellables)
        
        locationDelegate.authorizationStatusSubject
            .sink { [weak self] (status: CLAuthorizationStatus) in
                guard let self = self else { return }
                if status == .authorizedWhenInUse || status == .authorizedAlways {
                    self.locationManager.startUpdatingLocation()
                } else {
                    self.locationManager.stopUpdatingLocation()
                }
            }
            .store(in: &cancellables)
    }
    
    // Active filter counts
    var activeFilterCount: Int {
        switch selectedCategory {
        case .diveSites:
            return diveSiteFilters.activeCount
        case .diveCenters:
            return diveCenterFilters.activeCount
        case .shops:
            return shopFilters.activeCount
        }
    }
    
    var currentViewMode: ExploreViewMode {
        get { viewModes[selectedCategory] ?? .list }
        set { viewModes[selectedCategory] = newValue }
    }
    
    var currentSearchQuery: String {
        get { searchQueries[selectedCategory] ?? "" }
        set { searchQueries[selectedCategory] = newValue }
    }
    
    // Load data for current category
    func loadData(refresh: Bool = false) async {
        if refresh {
            currentPage[selectedCategory] = 1
            hasMorePages[selectedCategory] = true
            totalCounts[selectedCategory] = nil // Reset total count on refresh
        }
        
        isLoading = true
        error = nil
        
        do {
            switch selectedCategory {
            case .diveSites:
                try await loadDiveSites(page: currentPage[selectedCategory] ?? 1)
            case .diveCenters:
                try await loadDiveCenters(page: currentPage[selectedCategory] ?? 1)
            case .shops:
                try await loadShops(page: currentPage[selectedCategory] ?? 1)
            }
            isLoading = false
        } catch {
            self.error = error
            isLoading = false
        }
    }
    
    func loadMore() async {
        guard hasMorePages[selectedCategory] == true, !isLoading else {
            return
        }
        
        let nextPage = (currentPage[selectedCategory] ?? 1) + 1
        currentPage[selectedCategory] = nextPage
        
        do {
            switch selectedCategory {
            case .diveSites:
                try await loadDiveSites(page: nextPage, append: true)
            case .diveCenters:
                try await loadDiveCenters(page: nextPage, append: true)
            case .shops:
                try await loadShops(page: nextPage, append: true)
            }
        } catch {
            self.error = error
        }
    }
    
    private func loadDiveSites(page: Int, append: Bool = false) async throws {
        // Try cache first
        if page == 1, let cached = await cacheService.getDiveSites(filters: diveSiteFilters) {
            // Sort + apply search + pagination for cache path.
            // Without this, search on Explore can show unfiltered cached data.
            let sortedSites = sortItems(cached, userLocation: userLocation, sortOption: currentSortOption) as? [DiveSite] ?? cached
            let query = currentSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
            let filteredSites: [DiveSite]
            if query.isEmpty {
                filteredSites = sortedSites
            } else {
                filteredSites = sortedSites.filter { site in
                    site.displayName.localizedCaseInsensitiveContains(query) ||
                    site.description.localizedCaseInsensitiveContains(query)
                }
            }
            
            let itemsPerPage = 20
            let endIndex = min(itemsPerPage, filteredSites.count)
            diveSites = Array(filteredSites.prefix(endIndex))
            totalCounts[selectedCategory] = filteredSites.count
            hasMorePages[selectedCategory] = filteredSites.count > itemsPerPage
            return
        }
        
        // Load from network
        let sites = try await ExploreDataService.shared.getDiveSites(
            filters: diveSiteFilters,
            searchQuery: currentSearchQuery,
            page: page,
            userLocation: userLocation
        )
        // Sort ALL sites before pagination (important for distance sorting to work correctly)
        let sortedSites = sortItems(sites, userLocation: userLocation, sortOption: currentSortOption) as? [DiveSite] ?? sites
        
        // Apply client-side pagination AFTER sorting
        let itemsPerPage = 20
        let startIndex = (page - 1) * itemsPerPage
        let endIndex = min(startIndex + itemsPerPage, sortedSites.count)
        
        let paginatedSites: [DiveSite]
        if startIndex >= sortedSites.count {
            paginatedSites = []
        } else {
            paginatedSites = Array(sortedSites[startIndex..<endIndex])
        }
        
        if append {
            diveSites.append(contentsOf: paginatedSites)
        } else {
            diveSites = paginatedSites
            // Cache all sites (not just first page) for sorting
            if page == 1 {
                await cacheService.cacheDiveSites(sortedSites, filters: diveSiteFilters)
            }
        }
        
        // Check if there are more pages
        // Since we get all data from API and paginate on client, we need to check total count
        // Only check on first page to avoid multiple API calls
        if page == 1 {
            let totalFilteredCount = try await ExploreDataService.shared.getTotalDiveSitesCount(
                filters: diveSiteFilters,
                searchQuery: currentSearchQuery
            )
            totalCounts[selectedCategory] = totalFilteredCount
            let itemsPerPage = 20
            let totalPages = (totalFilteredCount + itemsPerPage - 1) / itemsPerPage
            hasMorePages[selectedCategory] = page < totalPages
        } else {
            // For subsequent pages, use stored total count
            if let totalCount = totalCounts[selectedCategory] {
                let itemsPerPage = 20
                let totalPages = (totalCount + itemsPerPage - 1) / itemsPerPage
                hasMorePages[selectedCategory] = page < totalPages
            } else {
                // Fallback: check if we got a full page
                hasMorePages[selectedCategory] = sites.count >= 20
            }
        }
    }
    
    private func loadDiveCenters(page: Int, append: Bool = false) async throws {
        
        if page == 1, let cached = await cacheService.getDiveCenters(filters: diveCenterFilters) {
            // Sort cached centers
            let sortedCenters = sortItems(cached, userLocation: userLocation, sortOption: currentSortOption) as? [DiveCenter] ?? cached
            diveCenters = sortedCenters
            return
        }
        
        let centers = try await ExploreDataService.shared.getDiveCenters(
            filters: diveCenterFilters,
            searchQuery: currentSearchQuery,
            page: page
        )
        
        
        // Sort centers
        let sortedCenters = sortItems(centers, userLocation: userLocation, sortOption: currentSortOption) as? [DiveCenter] ?? centers
        
        if append {
            diveCenters.append(contentsOf: sortedCenters)
        } else {
            diveCenters = sortedCenters
            if page == 1 {
                await cacheService.cacheDiveCenters(centers, filters: diveCenterFilters)
            }
        }
        
        
        hasMorePages[selectedCategory] = centers.count >= 20
    }
    
    private func loadShops(page: Int, append: Bool = false) async throws {
        if page == 1, let cached = await cacheService.getShops(filters: shopFilters) {
            // Sort cached shops
            let sortedShops = sortItems(cached, userLocation: userLocation, sortOption: currentSortOption) as? [Shop] ?? cached
            shops = sortedShops
            return
        }
        
        let loadedShops = try await ExploreDataService.shared.getShops(
            filters: shopFilters,
            searchQuery: currentSearchQuery,
            page: page
        )
        
        // Sort shops
        let sortedShops = sortItems(loadedShops, userLocation: userLocation, sortOption: currentSortOption) as? [Shop] ?? loadedShops
        
        if append {
            shops.append(contentsOf: sortedShops)
        } else {
            shops = sortedShops
            if page == 1 {
                await cacheService.cacheShops(loadedShops, filters: shopFilters)
            }
        }
        
        hasMorePages[selectedCategory] = loadedShops.count >= 20
    }
    
    func search(query: String) {
        currentSearchQuery = query
        Task {
            await loadData(refresh: true)
        }
    }
    
    func switchCategory(_ category: ExploreCategory) {
        selectedCategory = category
        Task {
            await loadData()
        }
    }
    
    func changeSortOption(_ option: ExploreSortOption) {
        currentSortOption = option
        Task {
            await applySorting()
        }
    }
    
    private func applySorting() async {
        switch selectedCategory {
        case .diveSites:
            // Reload all sites, sort them, then re-apply pagination
            // This ensures sorting works on ALL sites, not just the current page
            do {
                let allSites = try await ExploreDataService.shared.getDiveSites(
                    filters: diveSiteFilters,
                    searchQuery: currentSearchQuery,
                    page: 1, // Get all sites (no pagination in service)
                    userLocation: userLocation
                )
                
                // Sort all sites
                let sortedSites = sortItems(allSites, userLocation: userLocation, sortOption: currentSortOption) as? [DiveSite] ?? allSites
                
                // Re-apply pagination for current page
                let currentPageNum = currentPage[selectedCategory] ?? 1
                let itemsPerPage = 20
                let startIndex = (currentPageNum - 1) * itemsPerPage
                let endIndex = min(startIndex + itemsPerPage, sortedSites.count)
                
                if startIndex < sortedSites.count {
                    diveSites = Array(sortedSites[startIndex..<endIndex])
                } else {
                    diveSites = []
                }
            } catch {
                // Fallback: just sort current page if reload fails
                diveSites = sortItems(diveSites, userLocation: userLocation, sortOption: currentSortOption) as? [DiveSite] ?? diveSites
            }
        case .diveCenters:
            diveCenters = sortItems(diveCenters, userLocation: userLocation, sortOption: currentSortOption) as? [DiveCenter] ?? diveCenters
        case .shops:
            shops = sortItems(shops, userLocation: userLocation, sortOption: currentSortOption) as? [Shop] ?? shops
        }
    }
    
    // Generic sorting function
    private func sortItems<T: ExploreItem>(_ items: [T], userLocation: CLLocation?, sortOption: ExploreSortOption) -> [T] {
        guard !items.isEmpty else { 
            return items 
        }
        
        let result: [T]
        switch sortOption {
        case .distance:
            guard let userLocation = userLocation else {
                // If no user location, fallback to rating
                result = items.sorted { $0.rating > $1.rating }
                return result
            }
            result = items.sorted { item1, item2 in
                let distance1 = distance(from: userLocation, to: item1.exploreLocation)
                let distance2 = distance(from: userLocation, to: item2.exploreLocation)
                return distance1 < distance2
            }
            return result
        case .rating:
            result = items.sorted { $0.rating > $1.rating }
            return result
        case .name:
            result = items.sorted { $0.exploreName.localizedCaseInsensitiveCompare($1.exploreName) == .orderedAscending }
            return result
        case .reviewCount:
            result = items.sorted { $0.reviewCount > $1.reviewCount }
            return result
        }
    }
    
    // Calculate distance in meters
    private func distance(from location: CLLocation, to exploreLocation: ExploreLocation) -> Double {
        let targetLocation = CLLocation(
            latitude: exploreLocation.latitude,
            longitude: exploreLocation.longitude
        )
        return location.distance(from: targetLocation)
    }
}

// LocationManagerDelegate for GenericExploreViewModel
private class ExploreLocationManagerDelegate: NSObject, CLLocationManagerDelegate {
    let lastLocationSubject = PassthroughSubject<CLLocation?, Never>()
    let authorizationStatusSubject = CurrentValueSubject<CLAuthorizationStatus, Never>(.notDetermined)
    
    var lastLocation: CLLocation? {
        didSet {
            lastLocationSubject.send(lastLocation)
        }
    }
    
    var authorizationStatus: CLAuthorizationStatus = .notDetermined {
        didSet {
            authorizationStatusSubject.send(authorizationStatus)
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        lastLocation = locations.last
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Handle location errors if needed
    }
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
    }
}

enum ExploreViewMode {
    case list
    case map
}

// Extensions for filter active count
extension DiveSiteFilters {
    var activeCount: Int {
        var count = 0
        if siteType != nil { count += 1 }
        if difficulty != nil { count += 1 }
        if minDepth != nil { count += 1 }
        if maxDepth != nil { count += 1 }
        if minRating != nil { count += 1 }
        if maxDistance != nil { count += 1 }
        if country != nil { count += 1 }
        return count
    }
}

extension DiveCenterFilters {
    var activeCount: Int {
        var count = 0
        if city != nil { count += 1 }
        if country != nil { count += 1 }
        if minRating != nil { count += 1 }
        if serviceType != nil { count += 1 }
        if certificationAgency != nil { count += 1 }
        if languages != nil && !(languages?.isEmpty ?? true) { count += 1 }
        if nitroxAvailable != nil { count += 1 }
        if maxPrice != nil { count += 1 }
        if maxDistance != nil { count += 1 }
        return count
    }
}

extension ShopFilters {
    var activeCount: Int {
        var count = 0
        if shopType != nil { count += 1 }
        if brands != nil && !(brands?.isEmpty ?? true) { count += 1 }
        if serviceAvailable != nil { count += 1 }
        if minRating != nil { count += 1 }
        if maxDistance != nil { count += 1 }
        return count
    }
}
