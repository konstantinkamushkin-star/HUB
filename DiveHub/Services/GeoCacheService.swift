//
//  GeoCacheService.swift
//  DiveHub
//
//  Created for optimized geo API caching
//

import Foundation
import CoreLocation

/// Service for caching geo search results on the client
@MainActor
class GeoCacheService {
    static let shared = GeoCacheService()
    
    private struct CachedResult {
        let sites: [DiveSite]
        let timestamp: Date
        let cacheKey: String
    }
    
    private var cache: [String: CachedResult] = [:]
    private let maxCacheSize = 50 // Maximum number of cached results
    private let cacheExpirationTime: TimeInterval = 300 // 5 minutes
    
    private init() {}
    
    /// Generate cache key from search parameters
    private func cacheKey(
        latitude: Double,
        longitude: Double,
        radius: Int,
        filters: DiveSiteFilters?,
        sortBy: String,
        limit: Int
    ) -> String {
        // Round coordinates to ~100m precision for cache key
        let latRounded = Int(latitude * 1000)
        let lngRounded = Int(longitude * 1000)
        
        // Create filter hash
        var filterString = ""
        if let filters = filters {
            filterString = "\(filters.siteType?.rawValue ?? "")_\(filters.difficulty?.rawValue ?? "")_\(filters.minDepth ?? 0)_\(filters.maxDepth ?? 0)_\(filters.minRating ?? 0)"
        }
        
        return "geo_\(latRounded)_\(lngRounded)_r\(radius)_\(filterString)_\(sortBy)_l\(limit)"
    }
    
    /// Get cached result if available and not expired
    func getCached(
        latitude: Double,
        longitude: Double,
        radius: Int,
        filters: DiveSiteFilters?,
        sortBy: String,
        limit: Int
    ) -> [DiveSite]? {
        let key = cacheKey(
            latitude: latitude,
            longitude: longitude,
            radius: radius,
            filters: filters,
            sortBy: sortBy,
            limit: limit
        )
        
        guard let cached = cache[key] else {
            return nil
        }
        
        // Check if expired
        let age = Date().timeIntervalSince(cached.timestamp)
        if age > cacheExpirationTime {
            cache.removeValue(forKey: key)
            return nil
        }
        
        return cached.sites
    }
    
    /// Cache search result
    func cache(
        sites: [DiveSite],
        latitude: Double,
        longitude: Double,
        radius: Int,
        filters: DiveSiteFilters?,
        sortBy: String,
        limit: Int
    ) {
        let key = cacheKey(
            latitude: latitude,
            longitude: longitude,
            radius: radius,
            filters: filters,
            sortBy: sortBy,
            limit: limit
        )
        
        // Remove oldest entries if cache is full
        if cache.count >= maxCacheSize {
            let sortedEntries = cache.sorted { $0.value.timestamp < $1.value.timestamp }
            for (oldKey, _) in sortedEntries.prefix(cache.count - maxCacheSize + 1) {
                cache.removeValue(forKey: oldKey)
            }
        }
        
        cache[key] = CachedResult(
            sites: sites,
            timestamp: Date(),
            cacheKey: key
        )
    }
    
    /// Clear all cache
    func clearCache() {
        cache.removeAll()
    }
    
    /// Clear expired entries
    func clearExpired() {
        let now = Date()
        let expiredKeys = cache.compactMap { (key, value) -> String? in
            let age = now.timeIntervalSince(value.timestamp)
            return age > cacheExpirationTime ? key : nil
        }
        
        for key in expiredKeys {
            cache.removeValue(forKey: key)
        }
    }
    
    /// Cache key for bounding box search
    private func boundsCacheKey(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        filters: DiveSiteFilters?
    ) -> String {
        let northRounded = Int(north * 1000)
        let southRounded = Int(south * 1000)
        let eastRounded = Int(east * 1000)
        let westRounded = Int(west * 1000)
        
        var filterString = ""
        if let filters = filters {
            filterString = "\(filters.siteType?.rawValue ?? "")_\(filters.difficulty?.rawValue ?? "")"
        }
        
        return "bounds_\(northRounded)_\(southRounded)_\(eastRounded)_\(westRounded)_\(filterString)"
    }
    
    /// Get cached bounding box result
    func getCachedBounds(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        filters: DiveSiteFilters?
    ) -> [DiveSite]? {
        let key = boundsCacheKey(
            north: north,
            south: south,
            east: east,
            west: west,
            filters: filters
        )
        
        guard let cached = cache[key] else {
            return nil
        }
        
        let age = Date().timeIntervalSince(cached.timestamp)
        if age > cacheExpirationTime {
            cache.removeValue(forKey: key)
            return nil
        }
        
        return cached.sites
    }
    
    /// Cache bounding box result
    func cacheBounds(
        sites: [DiveSite],
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        filters: DiveSiteFilters?
    ) {
        let key = boundsCacheKey(
            north: north,
            south: south,
            east: east,
            west: west,
            filters: filters
        )
        
        if cache.count >= maxCacheSize {
            let sortedEntries = cache.sorted { $0.value.timestamp < $1.value.timestamp }
            for (oldKey, _) in sortedEntries.prefix(cache.count - maxCacheSize + 1) {
                cache.removeValue(forKey: oldKey)
            }
        }
        
        cache[key] = CachedResult(
            sites: sites,
            timestamp: Date(),
            cacheKey: key
        )
    }
}
