//
//  AddDiveLogView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine
import PhotosUI

struct AddDiveLogView: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel = AddDiveLogViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var currentStep = 0
    @State private var showError = false
    @State private var errorMessage = ""
    @State private var showDiveCenterPicker = false
    @State private var showDiveSitePicker = false
    @State private var diveCenters: [DiveCenter] = []
    @State private var diveSites: [DiveSite] = []
    @State private var isLoadingCenters = false
    @State private var isLoadingSites = false
    @State private var selectedPhotos: [PhotosPickerItem] = []
    @State private var selectedPhotoImages: [UIImage] = []
    @State private var showFishPicker = false
    @State private var isPublished = false
    private let currentOptions = ["None", "Mild", "Moderate", "Strong", "Very strong"]
    
    var body: some View {
        NavigationView {
            Form {
                Section(localizationService.localizedString("basicInfo", table: "logbook")) {
                    DatePicker(localizationService.localizedString("date", table: "logbook"), selection: $viewModel.date, displayedComponents: .date)
                    Button("ui_today".localized) {
                        viewModel.date = Date()
                    }
                    TextField(localizationService.localizedString("time", table: "logbook"), text: $viewModel.time)
                        .keyboardType(.numbersAndPunctuation)
                    Button("ui_use_current_time".localized) {
                        let formatter = DateFormatter()
                        formatter.dateFormat = "HH:mm"
                        viewModel.time = formatter.string(from: Date())
                    }
                    TextField(localizationService.localizedString("location", table: "logbook"), text: $viewModel.locationName)
                    
                    Button(action: {
                        showDiveCenterPicker = true
                    }) {
                        HStack {
                            Text(localizationService.localizedString("diveCenter", table: "logbook"))
                            Spacer()
                            if let selectedCenter = viewModel.selectedDiveCenter {
                                Text(selectedCenter.name)
                                    .foregroundColor(.secondary)
                            } else {
                                Text(localizationService.localizedString("select", table: "logbook"))
                                    .foregroundColor(.secondary)
                            }
                            Image(systemName: "chevron.right")
                                .foregroundColor(.secondary)
                                .font(.caption)
                        }
                    }
                    
                    Button(action: {
                        showDiveSitePicker = true
                    }) {
                        HStack {
                            Text(localizationService.localizedString("diveSite", table: "logbook"))
                            Spacer()
                            if let selectedSite = viewModel.selectedDiveSite {
                                Text(selectedSite.name)
                                    .foregroundColor(.secondary)
                            } else {
                                Text(localizationService.localizedString("select", table: "logbook"))
                                    .foregroundColor(.secondary)
                            }
                            Image(systemName: "chevron.right")
                                .foregroundColor(.secondary)
                                .font(.caption)
                        }
                    }
                }
                
                Section(localizationService.localizedString("diveDetails", table: "logbook")) {
                    HStack {
                        Text(localizationService.localizedString("maxDepth", table: "logbook"))
                        Spacer()
                        TextField("0", value: $viewModel.maxDepth, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 80)
                        Text(localizationService.localizedString("meters", table: "logbook"))
                    }
                    
                    HStack {
                        Text(localizationService.localizedString("avgDepth", table: "logbook"))
                        Spacer()
                        TextField("0", value: $viewModel.averageDepth, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 80)
                        Text(localizationService.localizedString("meters", table: "logbook"))
                    }
                    
                    HStack {
                        Text(localizationService.localizedString("bottomTime", table: "logbook"))
                        Spacer()
                        TextField("0", value: $viewModel.bottomTime, format: .number)
                            .keyboardType(.numberPad)
                            .frame(width: 80)
                        Text(localizationService.localizedString("min", table: "logbook"))
                    }
                }
                
                Section(localizationService.localizedString("conditions", table: "logbook")) {
                    HStack {
                        Text(localizationService.localizedString("waterTemp", table: "logbook"))
                        Spacer()
                        TextField("0", value: $viewModel.waterTemperature, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 80)
                        Text(localizationService.localizedString("celsius", table: "logbook"))
                    }
                    
                    HStack {
                        Text(localizationService.localizedString("visibility", table: "logbook"))
                        Spacer()
                        TextField("0", value: $viewModel.visibility, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 80)
                        Text(localizationService.localizedString("meters", table: "logbook"))
                    }

                    Picker(localizationService.localizedString("current", table: "logbook"), selection: $viewModel.current) {
                        Text("ui_auth_a".localized).tag("")
                        ForEach(currentOptions, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                }
                
                Section(localizationService.localizedString("photos", table: "logbook")) {
                    PhotosPicker(selection: $selectedPhotos, maxSelectionCount: 10, matching: .images) {
                        Label(localizationService.localizedString("addPhotos", table: "logbook"), systemImage: "photo.on.rectangle")
                    }
                    .onChange(of: selectedPhotos) { oldValue, newItems in
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
                                            Button(action: {
                                                selectedPhotoImages.remove(at: index)
                                                if index < selectedPhotos.count {
                                                    selectedPhotos.remove(at: index)
                                                }
                                            }) {
                                                Image(systemName: "xmark.circle.fill")
                                                    .foregroundColor(.white)
                                                    .background(Color.black.opacity(0.6))
                                                    .clipShape(Circle())
                                            }
                                            .padding(4)
                                        }
                                }
                            }
                            .padding(.horizontal)
                        }
                    }
                }
                
                Section(localizationService.localizedString("marineLife", table: "logbook")) {
                    Button(action: {
                        showFishPicker = true
                    }) {
                        HStack {
                            Text(localizationService.localizedString("selectFishSpecies", table: "logbook"))
                            Spacer()
                            if !viewModel.selectedFishSpecies.isEmpty {
                                Text("\(viewModel.selectedFishSpecies.count) \(localizationService.localizedString("selected", table: "logbook"))")
                                    .foregroundColor(.secondary)
                            }
                            Image(systemName: "chevron.right")
                                .foregroundColor(.secondary)
                                .font(.caption)
                        }
                    }
                    
                    if !viewModel.selectedFishSpecies.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(viewModel.selectedFishSpecies, id: \.self) { fish in
                                    HStack(spacing: 4) {
                                        Text(fish)
                                            .font(.caption)
                                        Button(action: {
                                            viewModel.selectedFishSpecies.removeAll { $0 == fish }
                                        }) {
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
                            .padding(.horizontal)
                        }
                    }
                }
                
                Section(localizationService.localizedString("notes", table: "logbook")) {
                    TextEditor(text: $viewModel.notes)
                        .frame(height: 100)
                }
                
                Section(localizationService.localizedString("publishing", table: "logbook")) {
                    Toggle(localizationService.localizedString("publishToFeed", table: "feed"), isOn: $isPublished)
                }
            }
            .navigationTitle(localizationService.localizedString("addDive", table: "logbook"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel")) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("save")) {
                        Task {
                            if let validationError = viewModel.validate(photoCount: selectedPhotoImages.count) {
                                errorMessage = validationError
                                showError = true
                                return
                            }

                            // Upload photos first if any
                            let photoURLs: [String]
                            do {
                                viewModel.isLoading = true
                                var urls: [String] = []
                                for (index, image) in selectedPhotoImages.enumerated() {
                                    guard let data = image.jpegData(compressionQuality: 0.85) else { continue }
                                    let url = try await NetworkService.shared.uploadMediaImage(
                                        data,
                                        fileName: "dive_log_\(index).jpg"
                                    )
                                    urls.append(url)
                                }
                                
                                photoURLs = urls
                            } catch {
                                viewModel.isLoading = false
                                errorMessage = error.localizedDescription
                                showError = true
                                return
                            }
                            
                            viewModel.photos = photoURLs
                            viewModel.isPublished = isPublished
                            let success = await viewModel.save()
                            if success {
                                dismiss()
                            } else {
                                errorMessage = viewModel.errorMessage ?? localizationService.localizedString("failedToSaveDiveLog", table: "logbook")
                                showError = true
                            }
                        }
                    }
                    .disabled(viewModel.isLoading)
                }
            }
            .alert(localizationService.localizedString("error"), isPresented: $showError) {
                Button(localizationService.localizedString("ok"), role: .cancel) {}
            } message: {
                Text(errorMessage)
            }
            .sheet(isPresented: $showDiveCenterPicker) {
                DiveCenterPickerView(
                    diveCenters: diveCenters,
                    selectedCenter: $viewModel.selectedDiveCenter,
                    isLoading: isLoadingCenters
                )
            }
            .sheet(isPresented: $showDiveSitePicker) {
                DiveSitePickerView(
                    diveSites: diveSites,
                    selectedSite: $viewModel.selectedDiveSite,
                    isLoading: isLoadingSites
                )
            }
            .sheet(isPresented: $showFishPicker) {
                FishSpeciesPickerView(selectedSpecies: $viewModel.selectedFishSpecies)
            }
            .task {
                await loadDiveCenters()
                await loadDiveSites()
                viewModel.restoreDraft()
                isPublished = viewModel.isPublished
                if !viewModel.hasDraft {
                    viewModel.applyAutofillDefaults()
                }
            }
            .onDisappear {
                if viewModel.hasMeaningfulInput {
                    viewModel.saveDraft()
                } else {
                    viewModel.clearDraft()
                }
            }
            .onChange(of: isPublished) { _, newValue in
                viewModel.isPublished = newValue
                viewModel.saveDraft()
            }
            .onChange(of: viewModel.date) { _, _ in viewModel.saveDraft() }
            .onChange(of: viewModel.time) { _, _ in viewModel.saveDraft() }
            .onChange(of: viewModel.locationName) { _, _ in viewModel.saveDraft() }
            .onChange(of: viewModel.maxDepth) { _, _ in viewModel.saveDraft() }
            .onChange(of: viewModel.averageDepth) { _, _ in viewModel.saveDraft() }
            .onChange(of: viewModel.bottomTime) { _, _ in viewModel.saveDraft() }
            .onChange(of: viewModel.waterTemperature) { _, _ in viewModel.saveDraft() }
            .onChange(of: viewModel.visibility) { _, _ in viewModel.saveDraft() }
            .onChange(of: viewModel.current) { _, _ in viewModel.saveDraft() }
            .onChange(of: viewModel.notes) { _, _ in viewModel.saveDraft() }
            .onChange(of: viewModel.selectedFishSpecies) { _, _ in viewModel.saveDraft() }
        }
    }
    
    private func loadDiveCenters() async {
        isLoadingCenters = true
        do {
            diveCenters = try await NetworkService.shared.getDiveCenters()
        } catch {
            errorMessage = "\(localizationService.localizedString("failedToLoadDiveCenters", table: "logbook")): \(error.localizedDescription)"
        }
        isLoadingCenters = false
    }
    
    private func loadDiveSites() async {
        isLoadingSites = true
        do {
            // Picker search works on locally loaded items — legacy API is paged (500/response).
            diveSites = try await NetworkService.shared.getAllDiveSitesLegacy(filters: DiveSiteFilters())
        } catch {
            errorMessage = "\(localizationService.localizedString("failedToLoadDiveSites", table: "logbook")): \(error.localizedDescription)"
        }
        isLoadingSites = false
    }
}

struct DiveSitePickerView: View {
    let diveSites: [DiveSite]
    @Binding var selectedSite: DiveSite?
    let isLoading: Bool
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var searchText = ""
    
    var filteredSites: [DiveSite] {
        if searchText.isEmpty {
            return diveSites
        }
        return diveSites.filter { site in
            site.displayName.localizedCaseInsensitiveContains(searchText) ||
            (site.location.address?.localizedCaseInsensitiveContains(searchText) ?? false)
        }
    }
    
    var body: some View {
        NavigationView {
            List {
                if isLoading {
                    ProgressView()
                } else if filteredSites.isEmpty {
                    Text(localizationService.localizedString("noDiveSitesFound", table: "logbook"))
                        .foregroundColor(.secondary)
                } else {
                    ForEach(filteredSites) { site in
                        Button(action: {
                            selectedSite = site
                            dismiss()
                        }) {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(site.displayName)
                                        .font(.headline)
                                        .foregroundColor(.primary)
                                    Text(site.siteType.displayName)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                if selectedSite?.id == site.id {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(.divePrimary)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("selectDiveSite", table: "logbook"))
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $searchText, prompt: localizationService.localizedString("searchDiveSites", table: "logbook"))
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("clear", table: "logbook")) {
                        selectedSite = nil
                        dismiss()
                    }
                }
            }
        }
    }
}

struct DiveCenterPickerView: View {
    let diveCenters: [DiveCenter]
    @Binding var selectedCenter: DiveCenter?
    let isLoading: Bool
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var searchText = ""
    
    var filteredCenters: [DiveCenter] {
        if searchText.isEmpty {
            return diveCenters
        }
        return diveCenters.filter { center in
            center.name.localizedCaseInsensitiveContains(searchText) ||
            center.location.city.localizedCaseInsensitiveContains(searchText) ||
            center.location.country.localizedCaseInsensitiveContains(searchText)
        }
    }
    
    var body: some View {
        NavigationView {
            List {
                if isLoading {
                    ProgressView()
                } else if filteredCenters.isEmpty {
                    Text(localizationService.localizedString("noDiveCentersFound", table: "logbook"))
                        .foregroundColor(.secondary)
                } else {
                    ForEach(filteredCenters) { center in
                        Button(action: {
                            selectedCenter = center
                            dismiss()
                        }) {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(center.name)
                                        .font(.headline)
                                        .foregroundColor(.primary)
                                    Text("ui_logbook_value_value".localized)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                if selectedCenter?.id == center.id {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(.divePrimary)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("selectDiveCenter", table: "logbook"))
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $searchText, prompt: localizationService.localizedString("searchDiveCenters", table: "logbook"))
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("clear", table: "logbook")) {
                        selectedCenter = nil
                        dismiss()
                    }
                }
            }
        }
    }
    }


@MainActor
class AddDiveLogViewModel: ObservableObject {
    private struct DiveLogDraft: Codable {
        var date: Date
        var time: String
        var locationName: String
        var maxDepth: Double
        var averageDepth: Double
        var bottomTime: Int
        var waterTemperature: Double?
        var visibility: Double?
        var current: String
        var notes: String
        var selectedFishSpecies: [String]
        var isPublished: Bool
    }

    private struct DiveLogAutofill: Codable {
        var locationName: String
        var waterTemperature: Double?
        var visibility: Double?
        var current: String
    }

    private let draftKey = "add_dive_log_draft_v1"
    private let autofillKey = "add_dive_log_autofill_v1"

    @Published var date = Date()
    @Published var time = ""
    @Published var locationName = ""
    @Published var maxDepth: Double = 0
    @Published var averageDepth: Double = 0
    @Published var bottomTime: Int = 0
    @Published var waterTemperature: Double?
    @Published var visibility: Double?
    @Published var current: String = ""
    @Published var notes: String = ""
    @Published var selectedDiveCenter: DiveCenter?
    @Published var selectedDiveSite: DiveSite?
    @Published var selectedFishSpecies: [String] = []
    @Published var photos: [String] = []
    @Published var isPublished: Bool = false
    @Published var isLoading = false
    @Published var hasDraft = false
    var errorMessage: String?
    
    private let authService = AuthenticationService.shared
    
    func save() async -> Bool {
        
        guard let userId = authService.currentUser?.id else {
            errorMessage = LocalizationService.shared.localizedString("userNotLoggedIn", table: "logbook")
            isLoading = false
            return false
        }
        
        isLoading = true
        
        let log = DiveLog(
            id: UUID().uuidString,
            userId: userId,
            diveNumber: 0, // Will be calculated on backend
            date: date,
            time: time,
            location: DiveLog.Location(
                latitude: selectedDiveSite?.location.latitude ?? 0,
                longitude: selectedDiveSite?.location.longitude ?? 0,
                name: selectedDiveSite?.name ?? locationName
            ),
            diveSiteId: selectedDiveSite?.id,
            diveCenterId: selectedDiveCenter?.id,
            instructorId: nil,
            buddy: nil,
            maxDepth: maxDepth,
            averageDepth: averageDepth,
            bottomTime: bottomTime,
            surfaceInterval: nil,
            waterTemperature: waterTemperature,
            visibility: visibility,
            current: current.isEmpty ? nil : current,
            conditions: nil,
            gearUsed: [],
            notes: notes,
            photos: photos,
            videos: [],
            fishSpecies: selectedFishSpecies,
            sensorData: nil,
            isPublished: isPublished,
            createdAt: Date(),
            updatedAt: Date()
        )
        
        
        do {
            let savedLog = try await NetworkService.shared.createDiveLog(log)
            
            // If dive is published, create a feed post
            if isPublished {
                do {
                    _ = try await NetworkService.shared.createFeedPost(
                        type: .dive,
                        content: notes.isEmpty ? nil : notes,
                        diveLogId: savedLog.id,
                        photos: photos
                    )
                } catch {
                    // Log error but don't fail the dive save
                    print("⚠️ Failed to create feed post: \(error.localizedDescription)")
                }
            }
            
            // Also save offline
            try? StorageService.shared.saveOfflineDiveLogs([log])
            saveAutofillDefaults()
            clearDraft()
            errorMessage = nil
            isLoading = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            // Save offline if network fails
            try? StorageService.shared.saveOfflineDiveLogs([log])
            isLoading = false
            return false
        }
    }

    var hasMeaningfulInput: Bool {
        return !locationName.isEmpty ||
        maxDepth > 0 ||
        averageDepth > 0 ||
        bottomTime > 0 ||
        waterTemperature != nil ||
        visibility != nil ||
        !current.isEmpty ||
        !notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
        !selectedFishSpecies.isEmpty
    }

    func validate(photoCount: Int) -> String? {
        if bottomTime <= 0 {
            return "Bottom time must be greater than 0"
        }
        if maxDepth <= 0 {
            return "Max depth must be greater than 0"
        }
        if averageDepth > maxDepth {
            return "Avg depth cannot be greater than max depth"
        }
        if photoCount > 10 {
            return "Maximum 10 photos allowed"
        }
        if !time.isEmpty {
            let regex = try? NSRegularExpression(pattern: #"^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$"#)
            let range = NSRange(location: 0, length: time.utf16.count)
            if regex?.firstMatch(in: time, range: range) == nil {
                return "Time should be in HH:mm format"
            }
        }
        return nil
    }

    func saveDraft() {
        let draft = DiveLogDraft(
            date: date,
            time: time,
            locationName: locationName,
            maxDepth: maxDepth,
            averageDepth: averageDepth,
            bottomTime: bottomTime,
            waterTemperature: waterTemperature,
            visibility: visibility,
            current: current,
            notes: notes,
            selectedFishSpecies: selectedFishSpecies,
            isPublished: isPublished
        )
        if let data = try? JSONEncoder().encode(draft) {
            UserDefaults.standard.set(data, forKey: draftKey)
            hasDraft = true
        }
    }

    func restoreDraft() {
        guard let data = UserDefaults.standard.data(forKey: draftKey),
              let draft = try? JSONDecoder().decode(DiveLogDraft.self, from: data) else {
            hasDraft = false
            return
        }
        date = draft.date
        time = draft.time
        locationName = draft.locationName
        maxDepth = draft.maxDepth
        averageDepth = draft.averageDepth
        bottomTime = draft.bottomTime
        waterTemperature = draft.waterTemperature
        visibility = draft.visibility
        current = draft.current
        notes = draft.notes
        selectedFishSpecies = draft.selectedFishSpecies
        isPublished = draft.isPublished
        hasDraft = true
    }

    func clearDraft() {
        UserDefaults.standard.removeObject(forKey: draftKey)
        hasDraft = false
    }

    func applyAutofillDefaults() {
        guard let data = UserDefaults.standard.data(forKey: autofillKey),
              let autofill = try? JSONDecoder().decode(DiveLogAutofill.self, from: data) else {
            return
        }
        if locationName.isEmpty { locationName = autofill.locationName }
        if waterTemperature == nil { waterTemperature = autofill.waterTemperature }
        if visibility == nil { visibility = autofill.visibility }
        if current.isEmpty { current = autofill.current }
    }

    private func saveAutofillDefaults() {
        let payload = DiveLogAutofill(
            locationName: locationName,
            waterTemperature: waterTemperature,
            visibility: visibility,
            current: current
        )
        if let data = try? JSONEncoder().encode(payload) {
            UserDefaults.standard.set(data, forKey: autofillKey)
        }
    }
}

#Preview {
    AddDiveLogView()
}
