//
//  DiveLogDetailView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct DiveLogDetailView: View {
    let log: DiveLog
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var settingsService = SettingsService.shared
    @State private var showShareSheet = false
    @State private var diveSiteName: String?
    @State private var diveCenterName: String?
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(displayTitle)
                        .font(.title)
                        .fontWeight(.bold)
                    Text(log.date.formatted(date: .long, time: .short))
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .padding()
                
                Divider()
                
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizationService.localizedString("diveDetails", table: "logbook"))
                        .font(.headline)
                    
                    if let diveSiteName = diveSiteName {
                        DiveLogInfoRow(icon: "divehub.logo", title: localizationService.localizedString("diveSite", table: "logbook"), value: diveSiteName)
                    }
                    
                    if let diveCenterName = diveCenterName {
                        DiveLogInfoRow(icon: "building.2", title: localizationService.localizedString("diveCenter", table: "logbook"), value: diveCenterName)
                    }
                    
                    DiveLogInfoRow(icon: "gauge", title: localizationService.localizedString("maxDepth", table: "logbook"), value: formatDepth(log.maxDepth))
                    DiveLogInfoRow(icon: "chart.bar", title: localizationService.localizedString("avgDepth", table: "logbook"), value: formatDepth(log.averageDepth))
                    DiveLogInfoRow(icon: "clock", title: localizationService.localizedString("bottomTime", table: "logbook"), value: "\(log.bottomTime) \(localizationService.localizedString("min", table: "logbook"))")
                    
                    if let temp = log.waterTemperature {
                        DiveLogInfoRow(icon: "thermometer", title: localizationService.localizedString("waterTemp", table: "logbook"), value: formatTemperature(temp))
                    }
                    
                    if let visibility = log.visibility {
                        DiveLogInfoRow(icon: "eye", title: localizationService.localizedString("visibility", table: "logbook"), value: formatDepth(visibility))
                    }
                    
                    if let current = log.current {
                        DiveLogInfoRow(icon: "arrow.triangle.2.circlepath", title: localizationService.localizedString("current", table: "logbook"), value: current)
                    }
                }
                .padding()
                .task {
                    await loadDiveSiteAndCenterNames()
                }
                
                if !log.notes.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizationService.localizedString("notes", table: "logbook"))
                            .font(.headline)
                        Text(log.notes)
                            .font(.body)
                    }
                    .padding()
                }
                
                if !log.photos.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizationService.localizedString("photos", table: "logbook"))
                            .font(.headline)
                        
                        TabView {
                            ForEach(log.photos, id: \.self) { photoURL in
                                if let fullURL = NetworkService.shared.fullImageURL(from: photoURL),
                                   let url = URL(string: fullURL) {
                                    AsyncImage(url: url) { image in
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                    } placeholder: {
                                        Rectangle()
                                            .fill(Color.gray.opacity(0.25))
                                    }
                                } else {
                                    Rectangle()
                                        .fill(Color.gray.opacity(0.25))
                                }
                            }
                        }
                        .frame(height: 220)
                        .tabViewStyle(.page)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .padding()
                }
            }
        }
        .navigationTitle(localizationService.localizedString("diveLog", table: "logbook"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: {
                    showShareSheet = true
                }) {
                    Image(systemName: "square.and.arrow.up")
                }
            }
        }
        .sheet(isPresented: $showShareSheet) {
            ShareDiveView(log: log)
        }
    }
    
    private func formatDepth(_ meters: Double) -> String {
        if settingsService.measurementUnits.depth == .feet {
            let feet = Int(meters.metersToFeet())
            return "\(feet)ft"
        }
        return "\(Int(meters))m"
    }
    
    private var displayTitle: String {
        if let diveSiteName = diveSiteName {
            return diveSiteName
        } else if let diveCenterName = diveCenterName {
            return diveCenterName
        } else if !log.location.name.isEmpty {
            return log.location.name
        } else {
            return "Dive \(max(log.diveNumber, 1))"
        }
    }
    
    private func formatTemperature(_ celsius: Double) -> String {
        if settingsService.measurementUnits.temperature == .fahrenheit {
            let fahrenheit = Int(celsius.celsiusToFahrenheit())
            return "\(fahrenheit)°F"
        }
        return "\(Int(celsius))°C"
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
}

struct DiveLogInfoRow: View {
    let icon: String
    let title: String
    let value: String
    
    var body: some View {
        HStack {
            DiveHubSystemIcon(name: icon, color: .divePrimary, size: 18)
                .frame(width: 24)
            Text(title)
            Spacer()
            Text(value)
                .fontWeight(.semibold)
        }
    }
}

struct ShareDiveView: View {
    let log: DiveLog
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var settingsService = SettingsService.shared
    @State private var shareText = ""
    @State private var isSharing = false
    @State private var showError = false
    @State private var errorMessage = ""
    
    var body: some View {
        NavigationView {
            Form {
                Section {
                    TextEditor(text: $shareText)
                        .frame(height: 100)
                } header: {
                    Text(localizationService.localizedString("shareMessage", table: "social"))
                }
                
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(log.location.name)
                            .font(.headline)
                        Text(log.date.formatted(date: .abbreviated, time: .omitted))
                            .font(.caption)
                            .foregroundColor(.secondary)
                        HStack {
                            Text(formatDepth(log.maxDepth))
                            Text("ui_explore_a".localized)
                            Text("\(log.bottomTime) \(localizationService.localizedString("min", table: "logbook"))")
                        }
                        .font(.caption)
                        .foregroundColor(.secondary)
                    }
                } header: {
                    Text(localizationService.localizedString("diveInfo", table: "social"))
                }
            }
            .navigationTitle(localizationService.localizedString("shareDive", table: "logbook"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel")) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("share", table: "social")) {
                        Task {
                            await shareDive()
                        }
                    }
                    .disabled(isSharing || shareText.isEmpty)
                }
            }
            .alert("ui_logbook_error".localized, isPresented: $showError) {
                Button("ok".localized) {
                    showError = false
                }
            } message: {
                Text(errorMessage)
            }
            .onAppear {
                shareText = generateDefaultShareText()
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
    
    private func generateDefaultShareText() -> String {
        let maxDepthLabel = localizationService.localizedString("maxDepth", table: "logbook")
        let bottomTimeLabel = localizationService.localizedString("bottomTime", table: "logbook")
        let minutes = localizationService.localizedString("min", table: "logbook")
        return "\(log.location.name) - \(maxDepthLabel): \(formatDepth(log.maxDepth)), \(bottomTimeLabel): \(log.bottomTime) \(minutes)"
    }
    
    private func shareDive() async {
        isSharing = true
        do {
            // Create a feed post with the dive information
            _ = try await NetworkService.shared.createFeedPost(
                type: .dive,
                content: shareText.isEmpty ? nil : shareText,
                diveLogId: log.id,
                photos: log.photos
            )
            await MainActor.run {
                isSharing = false
                dismiss()
            }
        } catch {
            await MainActor.run {
                isSharing = false
                errorMessage = error.localizedDescription
                showError = true
            }
        }
    }
}

#Preview {
    NavigationView {
        DiveLogDetailView(log: DiveLog(
            id: "1",
            userId: "user1",
            diveNumber: 1,
            date: Date(),
            time: "10:00",
            location: DiveLog.Location(latitude: 20.0, longitude: -80.0, name: "Blue Hole"),
            diveSiteId: nil,
            diveCenterId: nil,
            instructorId: nil,
            buddy: nil,
            maxDepth: 25,
            averageDepth: 18,
            bottomTime: 45,
            surfaceInterval: nil,
            waterTemperature: 28,
            visibility: 20,
            current: "Moderate",
            conditions: nil,
            gearUsed: [],
            notes: "Great dive!",
            photos: [],
            videos: [],
            sensorData: nil,
            createdAt: Date(),
            updatedAt: Date()
        ))
    }
}
