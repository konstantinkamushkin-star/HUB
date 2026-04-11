//
//  ExploreDataService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import CoreLocation

actor ExploreDataService {
    static let shared = ExploreDataService()
    
    private init() {}
    
    func getDiveSites(filters: DiveSiteFilters, searchQuery: String = "", page: Int = 1, userLocation: CLLocation? = nil) async throws -> [DiveSite] {
        var allSites: [DiveSite] = []
        
        // Use geo search if location is available and geo search is enabled
        // If maxDistance is nil, it means user wants global search (all countries)
        if filters.shouldUseGeoSearch,
           let lat = filters.centerLatitude,
           let lng = filters.centerLongitude,
           filters.maxDistance != nil {
            // Use optimized geo search API
            do {
                var cursor: String? = nil
                var hasMore = true
                
                // Load all pages using cursor pagination
                while hasMore {
                    let result = try await NetworkService.shared.searchDiveSitesByLocation(
                        latitude: lat,
                        longitude: lng,
                        radius: filters.radiusMeters,
                        filters: filters,
                        sortBy: "distance",
                        limit: 100, // Max limit per request
                        cursor: cursor
                    )
                    
                    allSites.append(contentsOf: result.data)
                    
                    // Check if there's more data
                    if let pagination = result.pagination, pagination.hasMore, let nextCursor = pagination.nextCursor {
                        cursor = nextCursor
                    } else {
                        hasMore = false
                    }
                }
            } catch {
                // Fallback to legacy API
                let pageSites = try await NetworkService.shared.getDiveSites(filters: filters, page: page, limit: 50)
                allSites = pageSites
            }
        } else {
            // No location — legacy `/api/dive-sites` (server caps 500/response; fetch in pages).
            allSites = try await NetworkService.shared.getAllDiveSitesLegacy(filters: filters)
        }
        
        var filteredSites = allSites
        
        // Apply client-side filters (in case backend doesn't support all filters)
        // Note: Most filters are already applied on backend, but we keep this for safety
        if let siteType = filters.siteType {
            // Filter by checking if diveTypes array contains the selected type (not just siteType)
            // This allows sites with ["wreck", "reef"] to match filter "wreck"
            filteredSites = filteredSites.filter { $0.diveTypes.contains(siteType.rawValue) }
        }
        if let difficulty = filters.difficulty {
            filteredSites = filteredSites.filter { $0.difficulty == difficulty }
        }
        if let minDepth = filters.minDepth {
            filteredSites = filteredSites.filter { $0.maxDepth >= minDepth }
        }
        if let maxDepth = filters.maxDepth {
            filteredSites = filteredSites.filter { $0.maxDepth <= maxDepth }
        }
        if let minRating = filters.minRating {
            filteredSites = filteredSites.filter { $0.averageRating >= minRating }
        }
        if let country = filters.country, !country.isEmpty {
            filteredSites = filteredSites.filter { $0.country.localizedCaseInsensitiveContains(country) }
        }
        
        // Apply search
        if !searchQuery.isEmpty {
            let sitesToSearch = filteredSites
            let query = searchQuery
            filteredSites = await MainActor.run {
                sitesToSearch.filter { site in
                    site.displayName.localizedCaseInsensitiveContains(query) ||
                    site.description.localizedCaseInsensitiveContains(query)
                }
            }
        }
        
        // Note: We return ALL filtered sites here, not paginated
        // Pagination and sorting are handled in GenericExploreViewModel
        // This allows sorting to work on ALL sites, not just the first page
        return filteredSites
    }
    
    func getTotalDiveSitesCount(filters: DiveSiteFilters, searchQuery: String = "") async throws -> Int {
        let uniqueSites = try await NetworkService.shared.getAllDiveSitesLegacy(filters: filters)
        var filteredSites = uniqueSites
        
        // Apply filters (same logic as getDiveSites)
        if let siteType = filters.siteType {
            // Filter by checking if diveTypes array contains the selected type (not just siteType)
            // This allows sites with ["wreck", "reef"] to match filter "wreck"
            filteredSites = filteredSites.filter { $0.diveTypes.contains(siteType.rawValue) }
        }
        if let difficulty = filters.difficulty {
            filteredSites = filteredSites.filter { $0.difficulty == difficulty }
        }
        if let minDepth = filters.minDepth {
            filteredSites = filteredSites.filter { $0.maxDepth >= minDepth }
        }
        if let maxDepth = filters.maxDepth {
            filteredSites = filteredSites.filter { $0.maxDepth <= maxDepth }
        }
        if let minRating = filters.minRating {
            filteredSites = filteredSites.filter { $0.averageRating >= minRating }
        }
        if let country = filters.country, !country.isEmpty {
            filteredSites = filteredSites.filter { $0.country.localizedCaseInsensitiveContains(country) }
        }
        
        // Apply search
        if !searchQuery.isEmpty {
            let sitesToSearch = filteredSites
            let query = searchQuery
            filteredSites = await MainActor.run {
                sitesToSearch.filter { site in
                    site.displayName.localizedCaseInsensitiveContains(query) ||
                    site.description.localizedCaseInsensitiveContains(query)
                }
            }
        }
        
        return filteredSites.count
    }
    
    func getDiveCenters(filters: DiveCenterFilters, searchQuery: String = "", page: Int = 1) async throws -> [DiveCenter] {
        
        var allCenters: [DiveCenter] = []
        
        // Use geo search if location is available
        if let lat = filters.centerLatitude,
           let lng = filters.centerLongitude {
            
            // Use optimized geo search API
            do {
                var cursor: String? = nil
                var loadedCount = 0
                let targetCount = 150 // Load enough for filtering and sorting
                
                // Load multiple pages using cursor pagination
                while loadedCount < targetCount {
                    let result = try await NetworkService.shared.searchDiveCentersByLocation(
                        latitude: lat,
                        longitude: lng,
                        radius: filters.maxDistance != nil ? Int(filters.maxDistance! * 1000) : 50000,
                        filters: filters,
                        sortBy: "distance",
                        limit: 50,
                        cursor: cursor
                    )
                    
                    
                    allCenters.append(contentsOf: result.data)
                    loadedCount += result.data.count
                    
                    // Check if there's more data
                    if let pagination = result.pagination, pagination.hasMore, let nextCursor = pagination.nextCursor {
                        cursor = nextCursor
                    } else {
                        break // No more data
                    }
                }
            } catch {
                
                // Fallback to popular centers
                do {
                    allCenters = try await NetworkService.shared.getPopularDiveCenters(
                        country: filters.country,
                        limit: 150
                    )
                    
                } catch {
                    throw error
                }
            }
            
            // If geo search returned no results, fallback to popular centers
            if allCenters.isEmpty {
                
                allCenters = try await NetworkService.shared.getPopularDiveCenters(
                    country: filters.country,
                    limit: 150
                )
                
            }
        } else {
            
            // No location - use popular centers
            // Use popular centers endpoint (no fallback to legacy API as it doesn't exist)
            allCenters = try await NetworkService.shared.getPopularDiveCenters(
                country: filters.country,
                limit: 150
            )
            
        }
        
        
        var filteredCenters = allCenters
        
        // Apply filters
        if let city = filters.city {
            filteredCenters = filteredCenters.filter { $0.location.city.localizedCaseInsensitiveContains(city) }
        }
        if let country = filters.country {
            filteredCenters = filteredCenters.filter { $0.location.country.localizedCaseInsensitiveContains(country) }
        }
        if let minRating = filters.minRating {
            filteredCenters = filteredCenters.filter { $0.averageRating >= minRating }
        }
        if let certificationAgency = filters.certificationAgency {
            filteredCenters = filteredCenters.filter { $0.certificationAgency == certificationAgency }
        }
        
        if let languages = filters.languages, !languages.isEmpty {
            filteredCenters = filteredCenters.filter { center in
                !Set(center.languages).isDisjoint(with: Set(languages))
            }
        }
        if let nitroxAvailable = filters.nitroxAvailable {
            filteredCenters = filteredCenters.filter { $0.nitroxAvailable == nitroxAvailable }
        }
        if let maxPrice = filters.maxPrice {
            filteredCenters = filteredCenters.filter { ($0.priceFrom ?? Double.infinity) <= maxPrice }
        }
        
        // Apply search
        if !searchQuery.isEmpty {
            filteredCenters = filteredCenters.filter { center in
                center.name.localizedCaseInsensitiveContains(searchQuery) ||
                center.description.localizedCaseInsensitiveContains(searchQuery)
            }
        }
        
        
        // Pagination
        let itemsPerPage = 20
        let startIndex = (page - 1) * itemsPerPage
        let endIndex = min(startIndex + itemsPerPage, filteredCenters.count)
        
        if startIndex >= filteredCenters.count {
            return []
        }
        
        let paginatedCenters = Array(filteredCenters[startIndex..<endIndex])
        
        
        return paginatedCenters
    }
    
    func getShops(filters: ShopFilters, searchQuery: String = "", page: Int = 1) async throws -> [Shop] {
        try await Task.sleep(nanoseconds: 500_000_000)
        
        let shops = await MainActor.run {
            MockExploreData.shops
        }
        var filteredShops = shops
        
        // Apply filters
        if let shopType = filters.shopType {
            filteredShops = filteredShops.filter { $0.type == shopType }
        }
        if let brands = filters.brands, !brands.isEmpty {
            filteredShops = filteredShops.filter { shop in
                !Set(shop.brands).isDisjoint(with: Set(brands))
            }
        }
        if let serviceAvailable = filters.serviceAvailable {
            filteredShops = filteredShops.filter { $0.serviceAvailable == serviceAvailable }
        }
        if let minRating = filters.minRating {
            filteredShops = filteredShops.filter { $0.averageRating >= minRating }
        }
        
        // Apply search
        if !searchQuery.isEmpty {
            let shopsToSearch = filteredShops
            let query = searchQuery
            filteredShops = await MainActor.run {
                shopsToSearch.filter { shop in
                    shop.displayName.localizedCaseInsensitiveContains(query) ||
                    shop.description.localizedCaseInsensitiveContains(query) ||
                    shop.brands.joined(separator: " ").localizedCaseInsensitiveContains(query)
                }
            }
        }
        
        // Pagination
        let itemsPerPage = 20
        let startIndex = (page - 1) * itemsPerPage
        let endIndex = min(startIndex + itemsPerPage, filteredShops.count)
        
        if startIndex >= filteredShops.count {
            return []
        }
        
        return Array(filteredShops[startIndex..<endIndex])
    }
}
