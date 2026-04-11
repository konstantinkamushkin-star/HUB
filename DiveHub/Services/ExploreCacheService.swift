//
//  ExploreCacheService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

actor ExploreCacheService {
    static let shared = ExploreCacheService()
    
    private var diveSitesCache: [String: [DiveSite]] = [:]
    private var diveCentersCache: [String: [DiveCenter]] = [:]
    private var shopsCache: [String: [Shop]] = [:]
    
    private let cacheExpirationTime: TimeInterval = 300 // 5 minutes
    
    private struct CacheEntry<T> {
        let data: T
        let timestamp: Date
    }
    
    private var diveSitesCacheEntries: [String: CacheEntry<[DiveSite]>] = [:]
    private var diveCentersCacheEntries: [String: CacheEntry<[DiveCenter]>] = [:]
    private var shopsCacheEntries: [String: CacheEntry<[Shop]>] = [:]
    
    private init() {}
    
    func cacheDiveSites(_ sites: [DiveSite], filters: DiveSiteFilters) async {
        let key = await filtersCacheKey(filters: filters)
        diveSitesCacheEntries[key] = CacheEntry(data: sites, timestamp: Date())
    }
    
    func getDiveSites(filters: DiveSiteFilters) async -> [DiveSite]? {
        let key = await filtersCacheKey(filters: filters)
        guard let entry = diveSitesCacheEntries[key],
              Date().timeIntervalSince(entry.timestamp) < cacheExpirationTime else {
            return nil
        }
        return entry.data
    }
    
    func cacheDiveCenters(_ centers: [DiveCenter], filters: DiveCenterFilters) async {
        let key = await filtersCacheKey(filters: filters)
        diveCentersCacheEntries[key] = CacheEntry(data: centers, timestamp: Date())
    }
    
    func getDiveCenters(filters: DiveCenterFilters) async -> [DiveCenter]? {
        let key = await filtersCacheKey(filters: filters)
        guard let entry = diveCentersCacheEntries[key],
              Date().timeIntervalSince(entry.timestamp) < cacheExpirationTime else {
            return nil
        }
        return entry.data
    }
    
    func cacheShops(_ shops: [Shop], filters: ShopFilters) async {
        let key = await filtersCacheKey(filters: filters)
        shopsCacheEntries[key] = CacheEntry(data: shops, timestamp: Date())
    }
    
    func getShops(filters: ShopFilters) async -> [Shop]? {
        let key = await filtersCacheKey(filters: filters)
        guard let entry = shopsCacheEntries[key],
              Date().timeIntervalSince(entry.timestamp) < cacheExpirationTime else {
            return nil
        }
        return entry.data
    }
    
    private func filtersCacheKey(filters: DiveSiteFilters) async -> String {
        await MainActor.run {
            do {
                let data = try JSONEncoder().encode(filters)
                return String(data: data, encoding: .utf8) ?? "default"
            } catch {
                return "default"
            }
        }
    }
    
    private func filtersCacheKey(filters: DiveCenterFilters) async -> String {
        await MainActor.run {
            do {
                let data = try JSONEncoder().encode(filters)
                return String(data: data, encoding: .utf8) ?? "default"
            } catch {
                return "default"
            }
        }
    }
    
    private func filtersCacheKey(filters: ShopFilters) async -> String {
        await MainActor.run {
            do {
                let data = try JSONEncoder().encode(filters)
                return String(data: data, encoding: .utf8) ?? "default"
            } catch {
                return "default"
            }
        }
    }
    
    func clearCache() {
        diveSitesCacheEntries.removeAll()
        diveCentersCacheEntries.removeAll()
        shopsCacheEntries.removeAll()
    }
}
