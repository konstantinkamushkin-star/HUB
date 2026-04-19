//
//  LogbookTabView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct LogbookTabView: View {
    @StateObject private var viewModel = LogbookViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var settingsService = SettingsService.shared
    @State private var showAddLog = false
    @State private var shareLog: DiveLog?
    
    var body: some View {
        NavigationStack {
            VStack {
                if viewModel.diveLogs.isEmpty {
                    EmptyLogbookView {
                        showAddLog = true
                    }
                } else {
                    List {
                        Section {
                            StatisticsCard(statistics: viewModel.statistics)
                        }
                        
                        Section {
                            ForEach(Array(viewModel.filteredAndSortedLogs.enumerated()), id: \.element.id) { index, log in
                                NavigationLink(destination: DiveLogDetailView(log: log)) {
                                    DiveLogRow(log: log, fallbackIndex: index + 1)
                                }
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                    Button(role: .destructive) {
                                        viewModel.deleteLog(log)
                                    } label: {
                                        Label(localizationService.localizedString("delete"), systemImage: "trash")
                                    }

                                    Button {
                                        shareLog = log
                                    } label: {
                                        Label(localizationService.localizedString("share", table: "social"), systemImage: "square.and.arrow.up")
                                    }
                                    .tint(.divePrimary)
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle(localizationService.localizedString("logbook"))
            .diveHubNavigationChrome()
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 16) {
                        Menu {
                            ForEach(LogbookViewModel.SortOption.allCases, id: \.rawValue) { option in
                                Button {
                                    viewModel.sortOption = option
                                } label: {
                                    Label(sortTitle(option), systemImage: viewModel.sortOption == option ? "checkmark" : "")
                                }
                            }
                        } label: {
                            Image(systemName: "arrow.up.arrow.down.circle")
                                .font(.title3)
                        }
                        Button(action: { showAddLog = true }) {
                            Image(systemName: "plus.circle.fill")
                                .font(.title3)
                        }
                    }
                }
            }
            .searchable(
                text: $viewModel.searchText,
                placement: .navigationBarDrawer(displayMode: .automatic),
                prompt: "Search by title, location, notes, marine life"
            )
            .sheet(isPresented: $showAddLog) {
                AddDiveLogView()
                    .onDisappear {
                        Task {
                            await viewModel.loadLogs()
                        }
                    }
            }
            .sheet(item: $shareLog) { log in
                ShareDiveView(log: log)
            }
            .task {
                await viewModel.loadLogs()
            }
            .onReceive(NotificationCenter.default.publisher(for: .languageChanged)) { _ in
                // Force view refresh when language changes
            }
            .onReceive(NotificationCenter.default.publisher(for: .measurementUnitsChanged)) { _ in
                // Force view refresh when units change
            }
        }
    }

    private func sortTitle(_ option: LogbookViewModel.SortOption) -> String {
        switch option {
        case .newestFirst: return "Newest first"
        case .oldestFirst: return "Oldest first"
        case .depth: return "By depth"
        case .duration: return "By duration"
        case .alphabet: return "Alphabetical"
        }
    }
}

struct StatisticsCard: View {
    let statistics: DiveStatistics
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var settingsService = SettingsService.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localizationService.localizedString("statistics", table: "logbook"))
                .font(.headline)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    StatItem(title: "Total dives", value: "\(statistics.totalDives)")
                    StatItem(title: "Total time", value: formatBottomTime(statistics.totalBottomTime))
                    StatItem(title: "Max depth", value: formatDepth(statistics.deepestDive))
                    StatItem(title: "Avg depth", value: formatDepth(statistics.averageDepth))
                    StatItem(title: "Avg temp", value: formatTemperature(statistics.averageWaterTemperature))
                    StatItem(title: "Avg visibility", value: formatDistance(statistics.averageVisibility))
                    StatItem(title: "Unique sites", value: "\(statistics.uniqueDiveSitesCount)")
                    StatItem(title: "Unique centers", value: "\(statistics.uniqueDiveCentersCount)")
                }
            }
        }
        .padding()
        .cardStyle()
    }
    
    private func formatDepth(_ meters: Double) -> String {
        if settingsService.measurementUnits.depth == .feet {
            let feet = Int(meters.metersToFeet())
            return "\(feet)ft"
        }
        return "\(Int(meters))m"
    }
    
    private func formatBottomTime(_ minutes: Int) -> String {
        return "\(minutes) \(localizationService.localizedString("min", table: "logbook"))"
    }

    private func formatTemperature(_ value: Double?) -> String {
        guard let value else { return "—" }
        if settingsService.measurementUnits.temperature == .fahrenheit {
            return "\(Int(value.celsiusToFahrenheit()))°F"
        }
        return "\(Int(value))°C"
    }

    private func formatDistance(_ value: Double?) -> String {
        guard let value else { return "—" }
        return formatDepth(value)
    }
}

struct StatItem: View {
    let title: String
    let value: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.headline)
                .fontWeight(.bold)
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(width: 120, alignment: .leading)
        .padding(.vertical, 8)
        .padding(.horizontal, 10)
        .background(Color.diveBackground)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

struct DiveLogRow: View {
    let log: DiveLog
    let fallbackIndex: Int
    @StateObject private var settingsService = SettingsService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @State private var diveSiteName: String?
    @State private var diveCenterName: String?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            headerContent
            badgesContent
        }
        .padding(.vertical, 8)
        .task {
            await loadDiveSiteAndCenterNames()
        }
    }

    private var headerContent: some View {
        HStack(alignment: .top, spacing: 10) {
            coverImage

            VStack(alignment: .leading, spacing: 4) {
                Text(displayName)
                    .font(.headline)
                Text(formattedDate)
                    .font(.caption)
                    .foregroundColor(.secondary)
                if let sub = subTitle {
                    Text(sub)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                Text(formatDepth(log.maxDepth))
                    .font(.headline)
                Text("\(log.bottomTime) \(localizationService.localizedString("min", table: "logbook"))")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }

    private var badgesContent: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                if log.isPublished == true {
                    BadgeView(text: "Published", color: .green)
                }
                ForEach(conditionBadges, id: \.self) { badge in
                    BadgeView(text: badge.capitalized, color: .divePrimary)
                }
            }
        }
    }

    @ViewBuilder
    private var coverImage: some View {
        if let coverURL = log.photos.first,
           let fullURL = NetworkService.shared.fullImageURL(from: coverURL),
           let url = URL(string: fullURL) {
            AsyncImage(url: url) { image in
                image.resizable().aspectRatio(contentMode: .fill)
            } placeholder: {
                Color.diveBackground
            }
            .frame(width: 56, height: 56)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        } else {
            EmptyView()
        }
    }

    private var formattedDate: String {
        DateFormatter.localizedString(from: log.date, dateStyle: .medium, timeStyle: .short)
    }
    
    private var displayName: String {
        if let diveSiteName = diveSiteName {
            return diveSiteName
        } else if let diveCenterName = diveCenterName {
            return diveCenterName
        } else if !log.location.name.isEmpty {
            return log.location.name
        } else {
            let safeNumber = max(log.diveNumber, fallbackIndex)
            return "Dive \(safeNumber)"
        }
    }

    private var subTitle: String? {
        if let diveSiteName = diveSiteName {
            return diveSiteName
        }
        if let diveCenterName = diveCenterName {
            return diveCenterName
        }
        return nil
    }

    private var conditionBadges: [String] {
        guard let conditions = log.conditions, !conditions.isEmpty else { return [] }
        return conditions
            .components(separatedBy: CharacterSet(charactersIn: ",;| "))
            .filter { !$0.isEmpty }
            .map { $0.lowercased() }
            .filter { ["night", "wreck", "drift", "deep", "training"].contains($0) }
    }
    
    private func loadDiveSiteAndCenterNames() async {
        // Load dive site name if diveSiteId is available
        if let diveSiteId = log.diveSiteId {
            do {
                let diveSite = try await NetworkService.shared.getDiveSite(id: diveSiteId)
                diveSiteName = diveSite.name
            } catch {
                // Silently fail - dive site name will not be displayed
            }
        }
        
        // Load dive center name if diveCenterId is available
        if let diveCenterId = log.diveCenterId {
            do {
                let diveCenter = try await NetworkService.shared.getDiveCenter(id: diveCenterId)
                diveCenterName = diveCenter.name
            } catch {
                // Silently fail - dive center name will not be displayed
            }
        }
    }
    
    private func formatDepth(_ meters: Double) -> String {
        if settingsService.measurementUnits.depth == .feet {
            let feet = Int(meters.metersToFeet())
            return "\(feet)ft"
        }
        return "\(Int(meters))m"
    }
}

struct BadgeView: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.caption2)
            .foregroundColor(color)
            .padding(.vertical, 4)
            .padding(.horizontal, 8)
            .background(color.opacity(0.12))
            .clipShape(Capsule())
    }
}

struct EmptyLogbookView: View {
    let onAddDive: () -> Void
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "book.closed")
                .font(.system(size: 60))
                .foregroundColor(.secondary)
            
            Text(localizationService.localizedString("noDivesLogged", table: "logbook"))
                .font(.title2)
                .fontWeight(.semibold)
            
            Text(localizationService.localizedString("startLoggingDives", table: "logbook"))
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            
            Button(action: onAddDive) {
                Label(localizationService.localizedString("addFirstDive", table: "logbook"), systemImage: "plus.circle.fill")
                    .font(.headline)
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.divePrimary)
                    .cornerRadius(12)
            }
        }
    }
}

#Preview {
    LogbookTabView()
}
