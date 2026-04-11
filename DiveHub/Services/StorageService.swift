//
//  StorageService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

class StorageService {
    static let shared = StorageService()
    
    private let fileManager = FileManager.default
    private let documentsDirectory: URL
    
    private init() {
        documentsDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }
    
    // MARK: - Local Storage
    
    func save<T: Codable>(_ object: T, forKey key: String) throws {
        let data = try JSONEncoder().encode(object)
        let url = documentsDirectory.appendingPathComponent("\(key).json")
        try data.write(to: url)
    }
    
    func load<T: Codable>(_ type: T.Type, forKey key: String) throws -> T? {
        let url = documentsDirectory.appendingPathComponent("\(key).json")
        guard fileManager.fileExists(atPath: url.path) else { return nil }
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(T.self, from: data)
    }
    
    func delete(forKey key: String) throws {
        let url = documentsDirectory.appendingPathComponent("\(key).json")
        if fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
    }
    
    // MARK: - Cache Management
    
    func cacheImage(data: Data, key: String) throws {
        let cacheDir = documentsDirectory.appendingPathComponent("Cache/Images")
        try fileManager.createDirectory(at: cacheDir, withIntermediateDirectories: true)
        
        let url = cacheDir.appendingPathComponent("\(key).jpg")
        try data.write(to: url)
    }
    
    func getCachedImage(key: String) -> Data? {
        let url = documentsDirectory.appendingPathComponent("Cache/Images/\(key).jpg")
        return try? Data(contentsOf: url)
    }
    
    func clearCache() throws {
        let cacheDir = documentsDirectory.appendingPathComponent("Cache")
        if fileManager.fileExists(atPath: cacheDir.path) {
            try fileManager.removeItem(at: cacheDir)
        }
    }
    
    // MARK: - Offline Data
    
    func saveOfflineDiveSites(_ sites: [DiveSite]) throws {
        try save(sites, forKey: "offline_dive_sites")
    }
    
    func loadOfflineDiveSites() throws -> [DiveSite]? {
        return try load([DiveSite].self, forKey: "offline_dive_sites")
    }
    
    func saveOfflineDiveLogs(_ logs: [DiveLog]) throws {
        try save(logs, forKey: "offline_dive_logs")
    }
    
    func loadOfflineDiveLogs() throws -> [DiveLog]? {
        return try load([DiveLog].self, forKey: "offline_dive_logs")
    }
}
