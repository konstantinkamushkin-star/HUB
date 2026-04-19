//
//  DiveSiteContributionSheet.swift
//  DiveHub
//

import SwiftUI
import MapKit
import CoreLocation
import PhotosUI
import UIKit

enum DiveSiteContributionMode: Identifiable {
    case correction(DiveSite)
    case newSite
    
    var id: String {
        switch self {
        case .correction(let s): return "c-\(s.id)"
        case .newSite: return "new"
        }
    }
}

/// Snapshot of dive site fields when opening «correction» — used to send only deltas in `proposedData`.
private struct CorrectionBaseline: Equatable {
    var name: String
    var description: String
    var latitude: Double
    var longitude: Double
    var country: String
    var region: String
    var siteTypes: Set<DiveSiteType>
    var difficultyLevel: Int
    var depthMin: Double
    var depthMax: Double
    var waterTempMin: String
    var waterTempMax: String
    var accessShore: Bool
    var accessBoat: Bool
    var marineLife: [String]
    var photoURLs: [String]
}

struct DiveSiteContributionSheet: View {
    let mode: DiveSiteContributionMode
    @Environment(\.dismiss) private var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var messageText = ""
    
    // MARK: New site — location
    @State private var pickedCoordinate: CLLocationCoordinate2D?
    @State private var cameraPosition: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 27.25, longitude: 33.83),
            span: MKCoordinateSpan(latitudeDelta: 8, longitudeDelta: 8)
        )
    )
    @State private var coordLatText = ""
    @State private var coordLngText = ""
    
    // MARK: New site — основные поля
    @State private var newName = ""
    @State private var newCountry = ""
    @State private var newRegion = ""
    @State private var newDescription = ""
    
    @State private var geocoder = CLGeocoder()
    @State private var isGeocodingRegion = false
    @State private var reverseGeocodeDebounceTask: Task<Void, Never>?
    
    @State private var selectedSiteTypes: Set<DiveSiteType> = [.reef]
    @State private var difficultyLevel: Int = 2
    @State private var depthMinText = "5"
    @State private var depthMaxText = "30"
    @State private var waterTempMinText = ""
    @State private var waterTempMaxText = ""
    @State private var accessShore = false
    @State private var accessBoat = true
    @State private var selectedMarineLife: [String] = []
    @State private var showMarineLifePicker = false
    @State private var selectedPhotos: [PhotosPickerItem] = []
    @State private var selectedPhotoImages: [UIImage] = []
    
    /// Filled when mode is `.correction` after `prefillFromSite`.
    @State private var correctionBaseline: CorrectionBaseline?
    
    @State private var isSending = false
    @State private var errorText: String?
    
    var body: some View {
        NavigationStack {
            Form {
                switch mode {
                case .correction(let site):
                    correctionSections(site: site)
                case .newSite:
                    diveSiteFormSections
                }
                if let errorText {
                    Section {
                        Text(errorText)
                            .foregroundColor(.red)
                            .font(.footnote)
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizationService.localizedString("cancel")) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizationService.localizedString("contributionSend", table: "diveSite")) {
                        Task { await send() }
                    }
                    .disabled(isSending || !canSend)
                }
            }
            .sheet(isPresented: $showMarineLifePicker) {
                FishSpeciesPickerView(selectedSpecies: $selectedMarineLife)
            }
            .onAppear {
                if case .correction(let site) = mode {
                    prefillFromSite(site)
                }
            }
        }
    }
    
    @ViewBuilder
    private func correctionSections(site: DiveSite) -> some View {
        Section {
            Text(site.displayName)
                .font(.headline)
        } footer: {
            Text(localizationService.localizedString("contributionCorrectionIntro", table: "diveSite"))
        }
        diveSiteFormSections
        Section {
            TextEditor(text: $messageText)
                .frame(minHeight: 100)
        } header: {
            Text(localizationService.localizedString("contributionMessageLabel", table: "diveSite"))
        } footer: {
            Text(localizationService.localizedString("contributionCorrectionFooter", table: "diveSite"))
        }
    }
    
    private var diveSiteFormSections: some View {
        Group {
            Section {
                TextField(localizationService.localizedString("contributionSiteName", table: "diveSite"), text: $newName)
            } header: {
                Text(localizationService.localizedString("contributionRequiredSection", table: "diveSite"))
            }
            
            Section {
                MapReader { proxy in
                    Map(position: $cameraPosition) {
                        if let coord = pickedCoordinate {
                            Annotation("", coordinate: coord) {
                                Image(systemName: "mappin.circle.fill")
                                    .font(.title)
                                    .foregroundStyle(.red)
                                    .background(Circle().fill(.white).padding(-4))
                            }
                        }
                    }
                    .mapStyle(.standard(elevation: .realistic))
                    .frame(height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .contentShape(Rectangle())
                    .onTapGesture { point in
                        if let coord = proxy.convert(point, from: .local) {
                            applyPickedCoordinate(coord)
                            errorText = nil
                        }
                    }
                }
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                
                TextField(localizationService.localizedString("contributionLatitude", table: "diveSite"), text: $coordLatText)
                    .keyboardType(.numbersAndPunctuation)
                    .onChange(of: coordLatText) { _ in
                        syncCoordinateFromFields()
                    }
                TextField(localizationService.localizedString("contributionLongitude", table: "diveSite"), text: $coordLngText)
                    .keyboardType(.numbersAndPunctuation)
                    .onChange(of: coordLngText) { _ in
                        syncCoordinateFromFields()
                    }
            } header: {
                Text(localizationService.localizedString("contributionMapSection", table: "diveSite"))
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    Text(localizationService.localizedString("contributionMapFooter", table: "diveSite"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(localizationService.localizedString("contributionMapGeoAutoHint", table: "diveSite"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if isGeocodingRegion {
                        HStack(spacing: 8) {
                            ProgressView()
                                .scaleEffect(0.85)
                            Text(localizationService.localizedString("contributionGeoCoding", table: "diveSite"))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if !newCountry.isEmpty || !newRegion.isEmpty {
                        Text([newRegion, newCountry].filter { !$0.isEmpty }.joined(separator: ", "))
                            .font(.caption.weight(.medium))
                    }
                    if let waterLine = formattedWaterTempEstimateLine() {
                        Text(waterLine)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            
            Section {
                TextEditor(text: $newDescription)
                    .frame(minHeight: 120)
            } header: {
                Text(localizationService.localizedString("contributionSiteDescriptionTitle", table: "diveSite"))
            } footer: {
                Text(localizationService.localizedString("contributionSiteDescriptionFooter", table: "diveSite"))
            }
            
            Section {
                ForEach(DiveSiteType.allCases, id: \.self) { type in
                    Toggle(isOn: bindingForSiteType(type)) {
                        Text(type.displayName)
                    }
                }
            } header: {
                Text(localizationService.localizedString("contributionSiteTypesSection", table: "diveSite"))
            } footer: {
                Text(localizationService.localizedString("contributionSiteTypesFooter", table: "diveSite"))
            }
            
            Section {
                Picker(localizationService.localizedString("contributionDifficultyLevel", table: "diveSite"), selection: $difficultyLevel) {
                    Text(localizationService.localizedString("difficulty.beginner", table: "diveSite")).tag(1)
                    Text(localizationService.localizedString("difficulty.intermediate", table: "diveSite")).tag(2)
                    Text(localizationService.localizedString("difficulty.advanced", table: "diveSite")).tag(3)
                    Text(localizationService.localizedString("difficulty.expert", table: "diveSite")).tag(4)
                }
            }
            
            Section {
                TextField(localizationService.localizedString("contributionDepthMin", table: "diveSite"), text: $depthMinText)
                    .keyboardType(.decimalPad)
                TextField(localizationService.localizedString("contributionDepthMax", table: "diveSite"), text: $depthMaxText)
                    .keyboardType(.decimalPad)
            } header: {
                Text(localizationService.localizedString("contributionDepthSection", table: "diveSite"))
            }
            
            Section {
                Toggle(localizationService.localizedString("contributionAccessShore", table: "diveSite"), isOn: $accessShore)
                Toggle(localizationService.localizedString("contributionAccessBoat", table: "diveSite"), isOn: $accessBoat)
            } header: {
                Text(localizationService.localizedString("contributionAccessSection", table: "diveSite"))
            }
            
            Section {
                Button {
                    showMarineLifePicker = true
                } label: {
                    HStack {
                        Text(localizationService.localizedString("selectFishSpecies", table: "logbook"))
                        Spacer()
                        if !selectedMarineLife.isEmpty {
                            Text("\(selectedMarineLife.count) \(localizationService.localizedString("selected", table: "logbook"))")
                                .foregroundColor(.secondary)
                        }
                        Image(systemName: "chevron.right")
                            .foregroundColor(.secondary)
                            .font(.caption)
                    }
                }
                if !selectedMarineLife.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(selectedMarineLife, id: \.self) { name in
                                HStack(spacing: 4) {
                                    Text(name)
                                        .font(.caption)
                                    Button {
                                        selectedMarineLife.removeAll { $0 == name }
                                    } label: {
                                        Image(systemName: "xmark.circle.fill")
                                            .foregroundColor(.secondary)
                                            .font(.caption2)
                                    }
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.diveBackground)
                                .cornerRadius(12)
                            }
                        }
                        .padding(.horizontal, 4)
                    }
                }
            } header: {
                Text(localizationService.localizedString("marineLife", table: "diveSite"))
            } footer: {
                Text(localizationService.localizedString("contributionMarineLifeCatalogFooter", table: "diveSite"))
            }
            
            Section {
                PhotosPicker(selection: $selectedPhotos, maxSelectionCount: 10, matching: .images) {
                    Label(localizationService.localizedString("addPhotos", table: "logbook"), systemImage: "photo.on.rectangle")
                }
                .onChange(of: selectedPhotos) { _, newItems in
                    Task {
                        selectedPhotoImages = []
                        for item in newItems {
                            if let data = try? await item.loadTransferable(type: Data.self),
                               let image = UIImage(data: data) {
                                selectedPhotoImages.append(image)
                            }
                        }
                    }
                }
                if !selectedPhotoImages.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(Array(selectedPhotoImages.enumerated()), id: \.offset) { index, image in
                                Image(uiImage: image)
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(width: 100, height: 100)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .overlay(alignment: .topTrailing) {
                                        Button {
                                            selectedPhotoImages.remove(at: index)
                                            if index < selectedPhotos.count {
                                                selectedPhotos.remove(at: index)
                                            }
                                        } label: {
                                            Image(systemName: "xmark.circle.fill")
                                                .foregroundColor(.white)
                                                .background(Color.black.opacity(0.6))
                                                .clipShape(Circle())
                                        }
                                        .padding(4)
                                    }
                            }
                        }
                        .padding(.horizontal, 4)
                    }
                }
            } header: {
                Text(localizationService.localizedString("contributionPhotosSection", table: "diveSite"))
            } footer: {
                Text(localizationService.localizedString("contributionPhotosFooter", table: "diveSite"))
            }
        }
    }
    
    private func bindingForSiteType(_ type: DiveSiteType) -> Binding<Bool> {
        Binding(
            get: { selectedSiteTypes.contains(type) },
            set: { on in
                if on {
                    selectedSiteTypes.insert(type)
                } else {
                    selectedSiteTypes.remove(type)
                    if selectedSiteTypes.isEmpty {
                        selectedSiteTypes = [.reef]
                    }
                }
            }
        )
    }
    
    private func applyPickedCoordinate(_ coord: CLLocationCoordinate2D) {
        reverseGeocodeDebounceTask?.cancel()
        pickedCoordinate = coord
        coordLatText = String(format: "%.6f", coord.latitude)
        coordLngText = String(format: "%.6f", coord.longitude)
        applyWaterTempEstimateFromLatitude(coord.latitude)
        performReverseGeocode(coordinate: coord)
    }
    
    private func syncCoordinateFromFields() {
        let lat = Double(coordLatText.replacingOccurrences(of: ",", with: "."))
        let lng = Double(coordLngText.replacingOccurrences(of: ",", with: "."))
        guard let lat, let lng,
              lat >= -90, lat <= 90,
              lng >= -180, lng <= 180 else {
            pickedCoordinate = nil
            clearWaterTempEstimate()
            return
        }
        let coord = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        pickedCoordinate = coord
        applyWaterTempEstimateFromLatitude(lat)
        reverseGeocodeDebounceTask?.cancel()
        let capturedLat = lat
        let capturedLng = lng
        reverseGeocodeDebounceTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 650_000_000)
            guard !Task.isCancelled else { return }
            performReverseGeocode(coordinate: CLLocationCoordinate2D(latitude: capturedLat, longitude: capturedLng))
        }
    }
    
    /// Страна и регион по координатам (Apple / системный геокодер).
    private func performReverseGeocode(coordinate: CLLocationCoordinate2D) {
        isGeocodingRegion = true
        geocoder.cancelGeocode()
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        geocoder.reverseGeocodeLocation(location) { placemarks, error in
            Task { @MainActor in
                isGeocodingRegion = false
                guard error == nil, let placemark = placemarks?.first else { return }
                if let country = placemark.country, !country.isEmpty {
                    newCountry = country
                } else if let iso = placemark.isoCountryCode, !iso.isEmpty {
                    newCountry = iso
                }
                let regionCandidate = [
                    placemark.administrativeArea,
                    placemark.subAdministrativeArea,
                    placemark.locality,
                    placemark.name,
                ]
                    .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .first { !$0.isEmpty }
                if let regionCandidate {
                    newRegion = regionCandidate
                }
            }
        }
    }
    
    private func currentCoordinate() -> CLLocationCoordinate2D? {
        if let p = pickedCoordinate { return p }
        let lat = Double(coordLatText.replacingOccurrences(of: ",", with: "."))
        let lng = Double(coordLngText.replacingOccurrences(of: ",", with: "."))
        guard let lat, let lng,
              lat >= -90, lat <= 90,
              lng >= -180, lng <= 180 else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }
    
    private var title: String {
        switch mode {
        case .correction:
            return localizationService.localizedString("reportDiveSiteInaccuracy", table: "diveSite")
        case .newSite:
            return localizationService.localizedString("contributionNewSiteNavTitle", table: "diveSite")
        }
    }
    
    private var canSend: Bool {
        switch mode {
        case .correction:
            return correctionHasPayload()
        case .newSite:
            guard !newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  currentCoordinate() != nil else { return false }
            let dmin = Double(depthMinText.replacingOccurrences(of: ",", with: "."))
            let dmax = Double(depthMaxText.replacingOccurrences(of: ",", with: "."))
            if let a = dmin, let b = dmax, a > b { return false }
            return true
        }
    }
    
    private func send() async {
        guard AuthenticationService.shared.isAuthenticated else {
            errorText = localizationService.localizedString("contributionLoginRequired", table: "diveSite")
            return
        }
        isSending = true
        errorText = nil
        defer { isSending = false }
        do {
            switch mode {
            case .correction(let site):
                var uploaded: [String] = []
                for (index, image) in selectedPhotoImages.enumerated() {
                    guard let data = image.jpegData(compressionQuality: 0.85) else { continue }
                    let url = try await NetworkService.shared.uploadMediaImage(
                        data,
                        fileName: "dive_site_correction_\(site.id.prefix(8))_\(index).jpg"
                    )
                    uploaded.append(url)
                }
                let proposed = buildCorrectionDelta(newPhotoURLs: uploaded)
                let trimmedMessage = messageText.trimmingCharacters(in: .whitespacesAndNewlines)
                if proposed.isEmpty && trimmedMessage.isEmpty {
                    errorText = localizationService.localizedString("contributionCorrectionEmptyPayload", table: "diveSite")
                    return
                }
                try await NetworkService.shared.submitDiveSiteContribution(
                    type: "correction",
                    diveSiteId: site.id,
                    message: trimmedMessage.isEmpty ? nil : messageText,
                    proposedData: proposed
                )
            case .newSite:
                guard let coord = currentCoordinate() else {
                    errorText = localizationService.localizedString("contributionCoordsRequired", table: "diveSite")
                    return
                }
                var photoURLs: [String] = []
                for (index, image) in selectedPhotoImages.enumerated() {
                    guard let data = image.jpegData(compressionQuality: 0.85) else { continue }
                    let url = try await NetworkService.shared.uploadMediaImage(
                        data,
                        fileName: "dive_site_suggest_\(index).jpg"
                    )
                    photoURLs.append(url)
                }
                let proposed = buildNewSiteProposed(coordinate: coord, photoURLs: photoURLs)
                try await NetworkService.shared.submitDiveSiteContribution(
                    type: "new_site",
                    diveSiteId: nil,
                    message: nil,
                    proposedData: proposed
                )
            }
            dismiss()
        } catch {
            errorText = error.localizedDescription
        }
    }
    
    private func buildNewSiteProposed(coordinate: CLLocationCoordinate2D, photoURLs: [String]) -> [String: Any] {
        var proposed: [String: Any] = [
            "name": newName.trimmingCharacters(in: .whitespacesAndNewlines),
            "latitude": coordinate.latitude,
            "longitude": coordinate.longitude,
        ]
        let country = newCountry.trimmingCharacters(in: .whitespacesAndNewlines)
        if !country.isEmpty { proposed["country"] = country }
        let region = newRegion.trimmingCharacters(in: .whitespacesAndNewlines)
        if !region.isEmpty { proposed["region"] = region }
        let desc = newDescription.trimmingCharacters(in: .whitespacesAndNewlines)
        if !desc.isEmpty { proposed["description"] = desc }
        
        let types = selectedSiteTypes.map(\.rawValue).sorted()
        proposed["site_types"] = types.isEmpty ? ["reef"] : types
        
        proposed["difficulty_level"] = difficultyLevel
        
        let dmin = Double(depthMinText.replacingOccurrences(of: ",", with: ".")) ?? 5
        let dmax = Double(depthMaxText.replacingOccurrences(of: ",", with: ".")) ?? 30
        proposed["depth_min"] = min(dmin, dmax)
        proposed["depth_max"] = max(dmin, dmax)
        
        if let tmin = Double(waterTempMinText.replacingOccurrences(of: ",", with: ".")) {
            proposed["water_temp_min"] = tmin
        }
        if let tmax = Double(waterTempMaxText.replacingOccurrences(of: ",", with: ".")) {
            proposed["water_temp_max"] = tmax
        }
        
        var access: [String] = []
        if accessShore { access.append("shore") }
        if accessBoat { access.append("boat") }
        proposed["access_type"] = access.isEmpty ? ["boat"] : access
        
        if !selectedMarineLife.isEmpty {
            proposed["marine_life"] = selectedMarineLife
        }
        
        if !photoURLs.isEmpty {
            proposed["photo_urls"] = photoURLs
        }
        
        return proposed
    }
    
    private func difficultyLevelInt(_ d: DifficultyLevel) -> Int {
        switch d {
        case .beginner: return 1
        case .intermediate: return 2
        case .advanced: return 3
        case .expert: return 4
        }
    }
    
    private func accessTypeArray(shore: Bool, boat: Bool) -> [String] {
        var access: [String] = []
        if shore { access.append("shore") }
        if boat { access.append("boat") }
        return access.isEmpty ? ["boat"] : access
    }
    
    private func prefillFromSite(_ site: DiveSite) {
        newName = site.name
        newDescription = site.displayDescription
        newCountry = site.country
        if let addr = site.location.address {
            let parts = addr.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
            if let first = parts.first, !first.isEmpty {
                newRegion = first
            } else {
                newRegion = ""
            }
        } else {
            newRegion = ""
        }
        
        let c = site.location.coordinate
        pickedCoordinate = c
        coordLatText = String(format: "%.6f", c.latitude)
        coordLngText = String(format: "%.6f", c.longitude)
        cameraPosition = .region(
            MKCoordinateRegion(
                center: c,
                span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)
            )
        )
        
        if !site.diveTypes.isEmpty {
            let mapped = Set(site.diveTypes.compactMap { DiveSiteType(rawValue: $0) })
            selectedSiteTypes = mapped.isEmpty ? [site.siteType] : mapped
        } else {
            selectedSiteTypes = [site.siteType]
        }
        
        difficultyLevel = difficultyLevelInt(site.difficulty)
        
        let dmin = site.averageDepth * 2 - site.maxDepth
        let dmax = site.maxDepth
        depthMinText = formatDepthField(dmin)
        depthMaxText = formatDepthField(dmax)
        
        if let w = site.waterTemp {
            let s = formatTemperatureField(w)
            waterTempMinText = s
            waterTempMaxText = s
        } else {
            applyWaterTempEstimateFromLatitude(c.latitude)
        }
        
        accessShore = false
        accessBoat = true
        selectedMarineLife = site.marineLife
        selectedPhotoImages = []
        selectedPhotos = []
        messageText = ""
        
        correctionBaseline = CorrectionBaseline(
            name: site.name,
            description: site.displayDescription,
            latitude: c.latitude,
            longitude: c.longitude,
            country: site.country,
            region: newRegion.trimmingCharacters(in: .whitespacesAndNewlines),
            siteTypes: selectedSiteTypes,
            difficultyLevel: difficultyLevel,
            depthMin: dmin,
            depthMax: dmax,
            waterTempMin: waterTempMinText.trimmingCharacters(in: .whitespacesAndNewlines),
            waterTempMax: waterTempMaxText.trimmingCharacters(in: .whitespacesAndNewlines),
            accessShore: accessShore,
            accessBoat: accessBoat,
            marineLife: site.marineLife,
            photoURLs: site.photos
        )
    }
    
    /// Оценка диапазона температуры воды по широте точки (SST с карты недоступен).
    private func applyWaterTempEstimateFromLatitude(_ latitude: Double) {
        let absLat = abs(latitude)
        let minT: Double
        let maxT: Double
        switch absLat {
        case ..<20:
            minT = 26
            maxT = 30
        case ..<35:
            minT = 24
            maxT = 29
        case ..<50:
            minT = 14
            maxT = 22
        default:
            minT = 6
            maxT = 14
        }
        waterTempMinText = String(format: "%.0f", minT)
        waterTempMaxText = String(format: "%.0f", maxT)
    }
    
    private func clearWaterTempEstimate() {
        waterTempMinText = ""
        waterTempMaxText = ""
    }
    
    private func formattedWaterTempEstimateLine() -> String? {
        guard let tmin = parseOptionalDouble(waterTempMinText),
              let tmax = parseOptionalDouble(waterTempMaxText) else { return nil }
        let a = min(tmin, tmax)
        let b = max(tmin, tmax)
        return String(
            format: localizationService.localizedString("contributionMapWaterEstimate", table: "diveSite"),
            formatTemperatureField(a),
            formatTemperatureField(b)
        )
    }
    
    private func formatDepthField(_ value: Double) -> String {
        if value.rounded() == value {
            return String(Int(value.rounded()))
        }
        return String(format: "%.1f", value)
    }
    
    private func formatTemperatureField(_ value: Double) -> String {
        if value == floor(value) {
            return String(Int(value))
        }
        return String(format: "%.1f", value)
    }
    
    private func correctionHasPayload() -> Bool {
        if !messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return true }
        let dmin = Double(depthMinText.replacingOccurrences(of: ",", with: "."))
        let dmax = Double(depthMaxText.replacingOccurrences(of: ",", with: "."))
        if let a = dmin, let b = dmax, a > b { return false }
        if !selectedPhotoImages.isEmpty { return true }
        guard correctionBaseline != nil else { return false }
        let delta = buildCorrectionDelta(newPhotoURLs: [])
        return !delta.isEmpty
    }
    
    private func buildCorrectionDelta(newPhotoURLs: [String]) -> [String: Any] {
        guard let baseline = correctionBaseline else { return [:] }
        
        var proposed: [String: Any] = [:]
        
        let name = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        if name != baseline.name { proposed["name"] = name }
        
        let desc = newDescription.trimmingCharacters(in: .whitespacesAndNewlines)
        if desc != baseline.description { proposed["description"] = desc }
        
        let country = newCountry.trimmingCharacters(in: .whitespacesAndNewlines)
        if country != baseline.country { proposed["country"] = country }
        
        let region = newRegion.trimmingCharacters(in: .whitespacesAndNewlines)
        if region != baseline.region { proposed["region"] = region }
        
        if let coord = currentCoordinate() {
            if abs(coord.latitude - baseline.latitude) > 1e-5
                || abs(coord.longitude - baseline.longitude) > 1e-5 {
                proposed["latitude"] = coord.latitude
                proposed["longitude"] = coord.longitude
            }
        }
        
        if selectedSiteTypes != baseline.siteTypes {
            let types = selectedSiteTypes.map(\.rawValue).sorted()
            proposed["site_types"] = types.isEmpty ? ["reef"] : types
        }
        
        if difficultyLevel != baseline.difficultyLevel {
            proposed["difficulty_level"] = difficultyLevel
        }
        
        let dmin = Double(depthMinText.replacingOccurrences(of: ",", with: ".")) ?? baseline.depthMin
        let dmax = Double(depthMaxText.replacingOccurrences(of: ",", with: ".")) ?? baseline.depthMax
        let orderedMin = min(dmin, dmax)
        let orderedMax = max(dmin, dmax)
        if abs(orderedMin - baseline.depthMin) > 1e-6 || abs(orderedMax - baseline.depthMax) > 1e-6 {
            proposed["depth_min"] = orderedMin
            proposed["depth_max"] = orderedMax
        }
        
        let wMinStr = waterTempMinText.trimmingCharacters(in: .whitespacesAndNewlines)
        let wMaxStr = waterTempMaxText.trimmingCharacters(in: .whitespacesAndNewlines)
        if wMinStr != baseline.waterTempMin {
            if let tmin = parseOptionalDouble(wMinStr) {
                proposed["water_temp_min"] = tmin
            } else if wMinStr.isEmpty, !baseline.waterTempMin.isEmpty {
                proposed["water_temp_min"] = NSNull()
            }
        }
        if wMaxStr != baseline.waterTempMax {
            if let tmax = parseOptionalDouble(wMaxStr) {
                proposed["water_temp_max"] = tmax
            } else if wMaxStr.isEmpty, !baseline.waterTempMax.isEmpty {
                proposed["water_temp_max"] = NSNull()
            }
        }
        
        let accessNow = accessTypeArray(shore: accessShore, boat: accessBoat)
        let accessWas = accessTypeArray(shore: baseline.accessShore, boat: baseline.accessBoat)
        if accessNow != accessWas {
            proposed["access_type"] = accessNow
        }
        
        if selectedMarineLife != baseline.marineLife {
            proposed["marine_life"] = selectedMarineLife
        }
        
        if !newPhotoURLs.isEmpty {
            proposed["photo_urls"] = baseline.photoURLs + newPhotoURLs
        }
        
        return proposed
    }
    
    private func parseOptionalDouble(_ s: String) -> Double? {
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return nil }
        return Double(t.replacingOccurrences(of: ",", with: "."))
    }
}
