//
//  LogbookViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine

@MainActor
class LogbookViewModel: ObservableObject {
    enum SortOption: String, CaseIterable {
        case newestFirst
        case oldestFirst
        case depth
        case duration
        case alphabet
    }

    @Published var diveLogs: [DiveLog] = []
    @Published var searchText: String = ""
    @Published var sortOption: SortOption = .newestFirst
    @Published var statistics = DiveStatistics(
        totalDives: 0,
        totalBottomTime: 0,
        deepestDive: 0,
        longestDive: 0,
        averageDepth: 0,
        averageWaterTemperature: nil,
        averageVisibility: nil,
        uniqueDiveSitesCount: 0,
        uniqueDiveCentersCount: 0,
        favoriteSites: [],
        diveByMonth: [:],
        diveByType: [:],
        milestones: []
    )
    @Published var isLoading = false
    @Published var error: Error?
    
    private let authService = AuthenticationService.shared
    
    var filteredAndSortedLogs: [DiveLog] {
        let filtered = diveLogs.filter { log in
            guard !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return true }
            let query = searchText.lowercased()
            let haystack = [
                log.location.name,
                log.notes,
                log.current ?? "",
                log.fishSpecies.joined(separator: " "),
                log.conditions ?? ""
            ].joined(separator: " ").lowercased()
            return haystack.contains(query)
        }

        switch sortOption {
        case .newestFirst:
            return filtered.sorted { $0.date > $1.date }
        case .oldestFirst:
            return filtered.sorted { $0.date < $1.date }
        case .depth:
            return filtered.sorted { $0.maxDepth > $1.maxDepth }
        case .duration:
            return filtered.sorted { $0.bottomTime > $1.bottomTime }
        case .alphabet:
            return filtered.sorted { displayTitle(for: $0).localizedCaseInsensitiveCompare(displayTitle(for: $1)) == .orderedAscending }
        }
    }

    func loadLogs() async {
        guard let userId = authService.currentUser?.id else { return }
        
        isLoading = true
        error = nil
        
        do {
            diveLogs = try await NetworkService.shared.getDiveLogs(userId: userId)
            calculateStatistics()
            isLoading = false
        } catch {
            // Try loading from offline storage
            if let offlineLogs = try? StorageService.shared.loadOfflineDiveLogs() {
                diveLogs = offlineLogs
                calculateStatistics()
            }
            self.error = error
            isLoading = false
        }
    }
    
    private func calculateStatistics() {
        guard !diveLogs.isEmpty else {
            statistics = DiveStatistics(
                totalDives: 0,
                totalBottomTime: 0,
                deepestDive: 0,
                longestDive: 0,
                averageDepth: 0,
                averageWaterTemperature: nil,
                averageVisibility: nil,
                uniqueDiveSitesCount: 0,
                uniqueDiveCentersCount: 0,
                favoriteSites: [],
                diveByMonth: [:],
                diveByType: [:],
                milestones: []
            )
            return
        }
        
        let totalDives = diveLogs.count
        let totalBottomTime = diveLogs.reduce(0) { $0 + $1.bottomTime }
        let deepestDive = diveLogs.map { $0.maxDepth }.max() ?? 0
        let longestDive = diveLogs.map { $0.bottomTime }.max() ?? 0
        let averageDepth = diveLogs.map { $0.averageDepth }.reduce(0, +) / Double(diveLogs.count)
        let waterTemps = diveLogs.compactMap(\.waterTemperature)
        let visibilities = diveLogs.compactMap(\.visibility)
        let averageWaterTemperature = waterTemps.isEmpty ? nil : waterTemps.reduce(0, +) / Double(waterTemps.count)
        let averageVisibility = visibilities.isEmpty ? nil : visibilities.reduce(0, +) / Double(visibilities.count)
        let uniqueDiveSitesCount = Set(diveLogs.compactMap(\.diveSiteId)).count
        let uniqueDiveCentersCount = Set(diveLogs.compactMap(\.diveCenterId)).count
        
        // Count dives by month
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM"
        var diveByMonth: [String: Int] = [:]
        for log in diveLogs {
            let key = formatter.string(from: log.date)
            diveByMonth[key, default: 0] += 1
        }
        
        statistics = DiveStatistics(
            totalDives: totalDives,
            totalBottomTime: totalBottomTime,
            deepestDive: deepestDive,
            longestDive: longestDive,
            averageDepth: averageDepth,
            averageWaterTemperature: averageWaterTemperature,
            averageVisibility: averageVisibility,
            uniqueDiveSitesCount: uniqueDiveSitesCount,
            uniqueDiveCentersCount: uniqueDiveCentersCount,
            favoriteSites: [],
            diveByMonth: diveByMonth,
            diveByType: [:],
            milestones: []
        )
    }

    func deleteLog(_ log: DiveLog) {
        diveLogs.removeAll { $0.id == log.id }
        calculateStatistics()
        try? StorageService.shared.saveOfflineDiveLogs(diveLogs)
    }

    func displayTitle(for log: DiveLog) -> String {
        if !log.location.name.isEmpty {
            return log.location.name
        }
        let safeNumber = max(log.diveNumber, 1)
        return "Dive \(safeNumber)"
    }
}
