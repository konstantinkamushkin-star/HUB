//
//  CreateTripView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import PhotosUI

struct CreateTripView: View {
    var trip: Trip? // For editing
    var onTripSaved: ((Trip) -> Void)? = nil // Optional callback when trip is saved
    
    @StateObject private var viewModel = TripViewModel()
    var sharedViewModel: TripViewModel? = nil // Optional shared view model from parent
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var authService: AuthenticationService
    @StateObject private var localizationService = LocalizationService.shared
    
    // Use shared view model if provided, otherwise use local one
    private var activeViewModel: TripViewModel {
        if let shared = sharedViewModel {
            return shared
        }
        return viewModel
    }
    
    @State private var tripType: Trip.TripType = .daily
    @State private var hotelName: String = ""
    @State private var hotelUrl: String = ""
    @State private var yachtName: String = ""
    @State private var yachtUrl: String = ""
    @State private var country: String = ""
    @State private var countries: [Country] = [] // Store Country objects instead of strings for proper localization
    @State private var isLoadingCountries = false
    @State private var countrySearchText: String = ""
    @State private var showCountrySuggestions = false
    @FocusState private var isCountryFieldFocused: Bool
    
    @State private var region: String = ""
    @State private var regions: [Country.Region] = [] // Store Region objects instead of strings for proper localization
    @State private var isLoadingRegions = false
    @State private var regionSearchText: String = ""
    @State private var showRegionSuggestions = false
    @FocusState private var isRegionFieldFocused: Bool
    
    // Filtered countries based on search text
    // Countries starting with search text come first, then countries containing the text
    private var filteredCountries: [Country] {
        if countrySearchText.isEmpty {
            return countries
        }
        let searchText = countrySearchText.lowercased()
        let matchingCountries = countries.filter { country in
            country.displayName.localizedCaseInsensitiveContains(searchText)
        }
        
        // Sort: countries starting with search text first, then others
        return matchingCountries.sorted { country1, country2 in
            let country1Lower = country1.displayName.lowercased()
            let country2Lower = country2.displayName.lowercased()
            
            let country1Starts = country1Lower.hasPrefix(searchText)
            let country2Starts = country2Lower.hasPrefix(searchText)
            
            if country1Starts && !country2Starts {
                return true // country1 comes first
            } else if !country1Starts && country2Starts {
                return false // country2 comes first
            } else {
                // Both start with search text or both don't - sort alphabetically
                return country1Lower < country2Lower
            }
        }
    }
    
    // Filtered regions based on search text
    // Regions starting with search text come first, then regions containing the text
    private var filteredRegions: [Country.Region] {
        if regionSearchText.isEmpty {
            return regions
        }
        let searchText = regionSearchText.lowercased()
        let matchingRegions = regions.filter { region in
            region.displayName.localizedCaseInsensitiveContains(searchText)
        }
        
        // Sort: regions starting with search text first, then others
        return matchingRegions.sorted { region1, region2 in
            let region1Lower = region1.displayName.lowercased()
            let region2Lower = region2.displayName.lowercased()
            
            let region1Starts = region1Lower.hasPrefix(searchText)
            let region2Starts = region2Lower.hasPrefix(searchText)
            
            if region1Starts && !region2Starts {
                return true // region1 comes first
            } else if !region1Starts && region2Starts {
                return false // region2 comes first
            } else {
                // Both start with search text or both don't - sort alphabetically
                return region1Lower < region2Lower
            }
        }
    }
    @State private var startDate = Date()
    @State private var endDate = Date()
    @State private var minimumCertificationLevel: String = "Open Water"
    @State private var minimumDives: Int = 0
    @State private var description: String = ""
    @State private var totalSpots: Int = 10
    @State private var nitroxAvailable: Bool = false
    @State private var equipmentRentalAvailable: Bool = false
    @State private var selectedCourses: Set<String> = []
    @State private var selectedGroupLeaderId: String?
    @State private var selectedPhotos: [PhotosPickerItem] = []
    @State private var photoImages: [UIImage] = []
    @State private var programDays: [TripProgramDay] = []
    @State private var additionalExpenses: [Trip.AdditionalExpense] = []
    @State private var priceDetails = Trip.PriceDetails(currency: "USD")
    @State private var participants: [Trip.TripParticipant] = []
    @State private var showRoomPriceForm = false
    @State private var showCabinPriceForm = false
    @State private var showExpenseForm = false
    @State private var showParticipantPicker = false
    @State private var editingRoomPrice: Trip.PriceDetails.RoomPrice?
    @State private var editingCabinPrice: Trip.PriceDetails.YachtPrice?
    @State private var editingExpense: Trip.AdditionalExpense?
    // Flag to prevent reloading data on every view appearance
    // Store in UserDefaults with trip ID as key to persist across view recreations
    private func getHasLoadedInitialData(for tripId: String) -> Bool {
        return UserDefaults.standard.bool(forKey: "hasLoadedInitialData_\(tripId)")
    }
    
    private func setHasLoadedInitialData(_ value: Bool, for tripId: String) {
        UserDefaults.standard.set(value, forKey: "hasLoadedInitialData_\(tripId)")
    }
    @State private var existingRoomsSnapshot: [Trip.PriceDetails.RoomPrice] = []
    @State private var existingCabinsSnapshot: [Trip.PriceDetails.YachtPrice] = []
    @State private var currencySnapshot: String = "USD"
    @State private var totalSpotsSnapshot: Int = 10
    
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showImportFromSiteSheet = false
    @State private var importUrl = ""
    @State private var isImportingFromSite = false
    @State private var importSheetError: String?
    
    var body: some View {
        NavigationStack {
            configuredForm
        }
    }
    
    private var configuredForm: some View {
        let navigationTitle = trip == nil 
            ? localizationService.localizedString("createTrip", table: "trips") 
            : localizationService.localizedString("editTrip", table: "trips")
        
        return formWithBasicModifiers(navigationTitle: navigationTitle)
            .sheet(isPresented: $showRoomPriceForm) {
                RoomPriceFormView(
                    roomPrice: editingRoomPrice, // Pass value instead of binding
                    currency: currencySnapshot, // Use snapshot to prevent view recreation
                    totalSpots: totalSpotsSnapshot, // Use snapshot to prevent view recreation
                    existingRooms: existingRoomsSnapshot,
                    onSave: { newRoomPrice in// Use DispatchQueue to ensure state updates happen after form dismisses
                        DispatchQueue.main.async {
                            if let index = priceDetails.roomPrices?.firstIndex(where: { $0.id == newRoomPrice.id }) {
                                priceDetails.roomPrices?[index] = newRoomPrice} else {
                                if priceDetails.roomPrices == nil {
                                    priceDetails.roomPrices = []
                                }
                                priceDetails.roomPrices?.append(newRoomPrice)}
                            editingRoomPrice = nil
                        }
                    },
                    onCancel: {editingRoomPrice = nil
                        // Don't set showRoomPriceForm = false here, let dismiss() handle it
                    }
                )
                .onDisappear {}
            }
            .onChange(of: showRoomPriceForm) { oldValue, newValue in}
            .sheet(isPresented: $showCabinPriceForm) {
                CabinPriceFormView(
                    cabinPrice: editingCabinPrice, // Pass value instead of binding
                    currency: currencySnapshot, // Use snapshot to prevent view recreation
                    totalSpots: totalSpotsSnapshot, // Use snapshot to prevent view recreation
                    existingCabins: existingCabinsSnapshot,
                    onSave: { newCabinPrice in
                        // Use DispatchQueue to ensure state updates happen after form dismisses
                        DispatchQueue.main.async {
                            if let index = priceDetails.yachtPrices?.firstIndex(where: { $0.id == newCabinPrice.id }) {
                                priceDetails.yachtPrices?[index] = newCabinPrice
                            } else {
                                if priceDetails.yachtPrices == nil {
                                    priceDetails.yachtPrices = []
                                }
                                priceDetails.yachtPrices?.append(newCabinPrice)
                            }
                            editingCabinPrice = nil
                        }
                    },
                    onCancel: {
                        editingCabinPrice = nil
                        // Don't set showCabinPriceForm = false here, let dismiss() handle it
                    }
                )
                .onDisappear {}
            }
            .sheet(isPresented: $showExpenseForm) {
                ExpenseFormView(
                    expense: editingExpense,
                    currency: priceDetails.currency,
                    onSave: { newExpense in
                        DispatchQueue.main.async {
                            if let index = additionalExpenses.firstIndex(where: { $0.id == newExpense.id }) {
                                additionalExpenses[index] = newExpense
                            } else {
                                additionalExpenses.append(newExpense)}
                            editingExpense = nil
                        }
                    },
                    onCancel: {
                        editingExpense = nil
                    }
                )
            }
            .sheet(isPresented: $showParticipantPicker) {
                ParticipantPickerView(
                    onSelect: { participant in
                        participants.append(participant)
                    }
                )
            }
            .onChange(of: showExpenseForm) { oldValue, newValue in}
            .onChange(of: showParticipantPicker) { oldValue, newValue in}
            .onChange(of: showCabinPriceForm) { oldValue, newValue in}
    }
    
    private func formWithBasicModifiers(navigationTitle: String) -> some View {
        return mainForm
            .navigationTitle(navigationTitle)
            .scrollDismissesKeyboard(.interactively)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("save", table: "common")) {Task {
                            await saveTrip()
                        }
                    }
                    .disabled(!isFormValid || isLoading)
                }
            }
            .sheet(isPresented: $showImportFromSiteSheet) {
                NavigationStack {
                    Form {
                        Section("ui_url_34_o".localized) {
                            TextField("ui_trips_https_example_com_trips".localized, text: $importUrl)
                                .keyboardType(.URL)
                                .autocapitalization(.none)
                                .disableAutocorrection(true)
                                .onChange(of: importUrl) { _, _ in
                                    importSheetError = nil
                                }
                            Text("ui_trips_supports_public_http_https".localized)
                                .font(.caption)
                                .foregroundColor(.secondary)
                            if let importSheetError, !importSheetError.isEmpty {
                                Text(importSheetError)
                                    .font(.caption)
                                    .foregroundColor(.red)
                            }
                            if isImportingFromSite {
                                HStack {
                                    ProgressView()
                                    Text("ui_trips_import_loading".localized)
                                }
                            }
                        }
                    }
                    .navigationTitle("ui_trips_import_from_site".localized)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button(localizationService.localizedString("cancel", table: "common")) {
                                showImportFromSiteSheet = false
                                importSheetError = nil
                            }
                        }
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("ui_1434nn_n342nn".localized) {
                                Task { await importTripFromWebsite() }
                            }
                            .disabled(importUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isImportingFromSite)
                        }
                    }
                }
            }
            .onAppear {}
            .onChange(of: trip?.id) { oldValue, newValue in}
            .onChange(of: localizationService.currentLanguage) { oldValue, newValue in// Reload countries and regions when language changes to update localized names
                // Note: We don't need to reload the data, just trigger a view update
                // The displayName computed property will automatically use the new language
                Task {
                    // Force view update by reloading countries (this will update the displayName)
                    if !countries.isEmpty {
                        await loadCountries()
                    }
                    // Reload regions if we have a country selected
                    if !country.isEmpty {
                        await loadRegions(for: country)
                    }
                }
            }
            .task {
                await loadData()
                // Load trip data only if it hasn't been loaded yet (first time opening edit screen)
                if let trip = trip {// Only load data if it hasn't been loaded yet (prevents overwriting user edits)
                    if !getHasLoadedInitialData(for: trip.id) {
                        // Fetch full trip data from API to get program, additionalExpenses, and participants
                        // The list endpoint doesn't return these fields, so we need to fetch the full trip
                        do {
                            let fullTrip = try await NetworkService.shared.getTrip(id: trip.id)
                            loadTripData(fullTrip)
                        } catch {
                            // Fallback to provided trip if API call fails
                            loadTripData(trip)
                        }
                        // Set flag after loading to prevent overwriting during editing
                        setHasLoadedInitialData(true, for: trip.id)
                    }
                }
            }
            .onDisappear {
                // Clear the flag when view disappears to ensure data is loaded on next open
                if let trip = trip {
                    setHasLoadedInitialData(false, for: trip.id)}
            }
    }
    
    private var mainForm: some View {
        Form {
            if let err = errorMessage, !err.isEmpty {
                Section {
                    Text(err)
                        .font(.caption)
                        .foregroundColor(.red)
                }
            }
            importFromWebsiteSection
            basicInformationSection
            requirementsSection
            descriptionSection
            photosSection
            capacitySection
            coursesSection
            optionsSection
            groupLeaderSection
            priceDetailsSection
            programSection
            additionalExpensesSection
        }
    }

    private var importFromWebsiteSection: some View {
        Section("ui_trips_import_from_site".localized) {
            if trip == nil, let user = authService.currentUser, let dcId = user.diveCenterId, !dcId.isEmpty {
                Button {
                    importUrl = ""
                    importSheetError = nil
                    showImportFromSiteSheet = true
                } label: {
                    Label("ui_trips_add_trip_by_link".localized, systemImage: "link.badge.plus")
                }
                .buttonStyle(.borderless)
                .disabled(isLoading || isImportingFromSite)
                Text("ui_trips_open_trip_url_form_hint".localized)
                    .font(.caption)
                    .foregroundColor(.secondary)
            } else if trip == nil {
                Text("ui_trips_import_link_available_hint".localized)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }
    
    private var basicInformationSection: some View {
        Section(localizationService.localizedString("basicInformation", table: "trips")) {
            Picker(localizationService.localizedString("tripType", table: "trips"), selection: $tripType) {
                Text(localizationService.localizedString("daily", table: "trips")).tag(Trip.TripType.daily)
                Text(localizationService.localizedString("safari", table: "trips")).tag(Trip.TripType.safari)
            }
            .onChange(of: tripType) { oldValue, newValue in
                // Clear hotel/yacht data when trip type changes
                if oldValue != newValue {
                    if newValue == .daily {
                        yachtName = ""
                        yachtUrl = ""
                    } else {
                        hotelName = ""
                        hotelUrl = ""
                    }
                }
            }
            
            if tripType == .daily {
                TextField("ui_trips_hotel_name_required".localized, text: $hotelName)
                TextField("ui_trips_hotel_link_optional".localized, text: $hotelUrl)
                    .keyboardType(.URL)
                    .autocapitalization(.none)
            } else {
                TextField("ui_trips_yacht_name_required".localized, text: $yachtName)
                TextField("ui_trips_yacht_link_optional".localized, text: $yachtUrl)
                    .keyboardType(.URL)
                    .autocapitalization(.none)
            }
            
            if isLoadingCountries {
                HStack {
                    Text(localizationService.localizedString("country", table: "trips"))
                    Spacer()
                    ProgressView()
                }
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    TextField(localizationService.localizedString("country", table: "trips"), text: $countrySearchText)
                        .textFieldStyle(.roundedBorder)
                        .focused($isCountryFieldFocused)
                        .onChange(of: countrySearchText) { oldValue, newValue in
                            showCountrySuggestions = isCountryFieldFocused && !newValue.isEmpty && !filteredCountries.isEmpty
                        }
                        .onChange(of: country) { oldValue, newValue in// Load regions when country changes
                            if !newValue.isEmpty && newValue != oldValue {Task {
                                    await loadRegions(for: newValue)
                                }
                            } else if newValue.isEmpty {regions = []
                                region = ""
                                regionSearchText = ""
                            }
                        }
                        .onChange(of: isCountryFieldFocused) { oldValue, newValue in
                            if !newValue {// When field loses focus, update country value
                                if let exactMatch = countries.first(where: { $0.displayName.caseInsensitiveCompare(countrySearchText) == .orderedSame }) {country = exactMatch.displayName
                                    countrySearchText = exactMatch.displayName
                                } else {// Allow free text if no exact match
                                    country = countrySearchText
                                }
                                showCountrySuggestions = false
                            } else {
                                // When field gains focus, show suggestions if there's text
                                if !countrySearchText.isEmpty {
                                    showCountrySuggestions = !filteredCountries.isEmpty
                                }
                            }
                        }
                        .onSubmit {
                            // When user presses return, try to find exact match
                            if let exactMatch = countries.first(where: { $0.displayName.caseInsensitiveCompare(countrySearchText) == .orderedSame }) {
                                country = exactMatch.displayName
                                countrySearchText = exactMatch.displayName
                            } else {
                                // Allow free text if no exact match
                                country = countrySearchText
                            }
                            showCountrySuggestions = false
                            isCountryFieldFocused = false
                        }
                        .onAppear {
                            if countrySearchText.isEmpty {
                                countrySearchText = country
                            }
                        }
                    
                    if showCountrySuggestions && !filteredCountries.isEmpty {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 0) {
                                ForEach(filteredCountries.prefix(10), id: \.id) { countryObj in
                                    Button(action: {country = countryObj.displayName
                                        countrySearchText = countryObj.displayName
                                        showCountrySuggestions = false
                                        isCountryFieldFocused = false
                                    }) {
                                        HStack {
                                            Text(countryObj.displayName)
                                                .foregroundColor(.primary)
                                            Spacer()
                                        }
                                        .padding(.vertical, 8)
                                        .padding(.horizontal, 12)
                                    }
                                    .buttonStyle(.plain)
                                    
                                    if countryObj.id != filteredCountries.prefix(10).last?.id {
                                        Divider()
                                    }
                                }
                            }
                        }
                        .frame(maxHeight: 200)
                        .background(Color(.systemBackground))
                        .cornerRadius(8)
                        .shadow(radius: 2)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color(.separator), lineWidth: 1)
                        )
                    }
                }
            }
            
            // Region field
            VStack(alignment: .leading, spacing: 4) {
                TextField(localizationService.localizedString("region", table: "trips"), text: $regionSearchText)
                    .textFieldStyle(.roundedBorder)
                    .focused($isRegionFieldFocused)
                    .disabled(isLoadingRegions)
                    .onChange(of: regionSearchText) { oldValue, newValue in
                        showRegionSuggestions = isRegionFieldFocused && !newValue.isEmpty && !filteredRegions.isEmpty
                    }
                    .onChange(of: isRegionFieldFocused) { oldValue, newValue in
                        if !newValue {
                            // When field loses focus, update region value
                            if let exactMatch = regions.first(where: { $0.displayName.caseInsensitiveCompare(regionSearchText) == .orderedSame }) {
                                region = exactMatch.displayName
                                regionSearchText = exactMatch.displayName
                            } else {
                                // Allow free text if no exact match
                                region = regionSearchText
                            }
                            showRegionSuggestions = false
                        } else {
                            // When field gains focus, show suggestions if there's text
                            if !regionSearchText.isEmpty {
                                showRegionSuggestions = !filteredRegions.isEmpty
                            }
                        }
                    }
                    .onSubmit {
                        // When user presses return, try to find exact match
                        if let exactMatch = regions.first(where: { $0.displayName.caseInsensitiveCompare(regionSearchText) == .orderedSame }) {
                            region = exactMatch.displayName
                            regionSearchText = exactMatch.displayName
                        } else {
                            // Allow free text if no exact match
                            region = regionSearchText
                        }
                        showRegionSuggestions = false
                        isRegionFieldFocused = false
                    }
                    .onAppear {
                        if regionSearchText.isEmpty {
                            regionSearchText = region
                        }
                    }
                
                if isLoadingRegions {
                    HStack {
                        Spacer()
                        ProgressView()
                            .scaleEffect(0.8)
                        Spacer()
                    }
                    .padding(.vertical, 4)
                }
                
                if showRegionSuggestions && !filteredRegions.isEmpty {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            ForEach(filteredRegions.prefix(10), id: \.id) { regionObj in
                                Button(action: {
                                    region = regionObj.displayName
                                    regionSearchText = regionObj.displayName
                                    showRegionSuggestions = false
                                    isRegionFieldFocused = false
                                }) {
                                    HStack {
                                        Text(regionObj.displayName)
                                            .foregroundColor(.primary)
                                        Spacer()
                                    }
                                    .padding(.vertical, 8)
                                    .padding(.horizontal, 12)
                                }
                                .buttonStyle(.plain)
                                
                                if regionObj.id != filteredRegions.prefix(10).last?.id {
                                    Divider()
                                }
                            }
                        }
                    }
                    .frame(maxHeight: 200)
                    .background(Color(.systemBackground))
                    .cornerRadius(8)
                    .shadow(radius: 2)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color(.separator), lineWidth: 1)
                    )
                }
            }
            
            DatePicker(localizationService.localizedString("startDate", table: "trips"), selection: $startDate, displayedComponents: .date)
            DatePicker(localizationService.localizedString("endDate", table: "trips"), selection: $endDate, displayedComponents: .date)
        }
    }
    
    private var requirementsSection: some View {
        Section(localizationService.localizedString("requirements", table: "trips")) {
            TextField(localizationService.localizedString("minimumCertification", table: "trips"), text: $minimumCertificationLevel)
            Stepper("\(localizationService.localizedString("minimumDives", table: "trips")): \(minimumDives)", value: $minimumDives, in: 0...1000)
        }
    }
    
    private var descriptionSection: some View {
        Section(localizationService.localizedString("description", table: "trips")) {
            TextEditor(text: $description)
                .frame(height: 100)
        }
    }
    
    private var photosSection: some View {
        Section(localizationService.localizedString("photos", table: "trips")) {
            PhotosPicker(selection: $selectedPhotos, maxSelectionCount: 10, matching: .images) {
                Label(localizationService.localizedString("addPhotos", table: "trips"), systemImage: "photo")
            }
            .buttonStyle(.plain)
            .onChange(of: selectedPhotos) { oldValue, newItems in
                Task {
                    photoImages = []
                    for item in newItems {
                        if let data = try? await item.loadTransferable(type: Data.self),
                           let image = UIImage(data: data) {
                            photoImages.append(image)
                        }
                    }
                }
            }
            
            if !photoImages.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(Array(photoImages.enumerated()), id: \.offset) { index, image in
                            Image(uiImage: image)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 100, height: 100)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                }
            }
        }
    }
    
    private var capacitySection: some View {
        Section(localizationService.localizedString("capacity", table: "trips")) {
            Stepper("\(localizationService.localizedString("totalSpots", table: "trips")): \(totalSpots)", value: $totalSpots, in: 1...100)
            
            if !participants.isEmpty {
                ForEach(participants) { participant in
                    HStack {
                        Text(participant.name)
                        Spacer()
                        Text(participant.isDiving ? localizationService.localizedString("diving", table: "trips") : localizationService.localizedString("nonDiving", table: "trips"))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            
            Button(localizationService.localizedString("addKnownParticipant", table: "trips")) {showParticipantPicker = true}
            .buttonStyle(.plain)
        }
    }
    
    private var coursesSection: some View {
        Section(localizationService.localizedString("courses", table: "trips")) {
            if activeViewModel.courses.isEmpty {
                Text(localizationService.localizedString("noCoursesAvailable", table: "trips"))
                    .foregroundColor(.secondary)
            } else {
                ForEach(activeViewModel.courses) { course in
                    Toggle(course.name, isOn: Binding(
                        get: { selectedCourses.contains(course.id) },
                        set: { isOn in
                            if isOn {
                                selectedCourses.insert(course.id)
                            } else {
                                selectedCourses.remove(course.id)
                            }
                        }
                    ))
                }
            }
        }
    }
    
    private var optionsSection: some View {
        Section(localizationService.localizedString("options", table: "trips")) {
            Toggle(localizationService.localizedString("nitroxAvailable", table: "trips"), isOn: $nitroxAvailable)
            Toggle(localizationService.localizedString("equipmentRentalAvailable", table: "trips"), isOn: $equipmentRentalAvailable)
        }
    }
    
    @ViewBuilder
    private var groupLeaderSection: some View {
        if let user = authService.currentUser, user.role == .diveCenterAdmin {
            Section(localizationService.localizedString("groupLeader", table: "trips")) {
                if activeViewModel.instructors.isEmpty {
                    Text(localizationService.localizedString("noInstructorsAvailable", table: "trips"))
                        .foregroundColor(.secondary)
                } else {
                    Picker(localizationService.localizedString("groupLeader", table: "trips"), selection: $selectedGroupLeaderId) {
                        Text(localizationService.localizedString("none", table: "trips")).tag(nil as String?)
                        ForEach(activeViewModel.instructors) { instructor in
                            Text(instructor.name).tag(instructor.id as String?)
                        }
                    }
                }
            }
        }
    }
    
    private var priceDetailsSection: some View {
        Section(localizationService.localizedString("priceDetails", table: "trips")) {
            if tripType == .daily {
                // Room prices section
                if let roomPrices = priceDetails.roomPrices, !roomPrices.isEmpty {
                    ForEach(roomPrices) { roomPrice in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(roomPrice.roomType)
                                    .font(.headline)
                                Spacer()
                                Button(action: {
                                    editingRoomPrice = roomPrice
                                    existingRoomsSnapshot = priceDetails.roomPrices ?? []
                                    currencySnapshot = priceDetails.currency
                                    totalSpotsSnapshot = totalSpots
                                    showRoomPriceForm = true
                                }) {
                                    Image(systemName: "pencil")
                                }
                                Button(action: {
                                    if let index = priceDetails.roomPrices?.firstIndex(where: { $0.id == roomPrice.id }) {
                                        priceDetails.roomPrices?.remove(at: index)
                                    }
                                }) {
                                    Image(systemName: "trash")
                                        .foregroundColor(.red)
                                }
                            }
                            Text("\(localizationService.localizedString("quantity", table: "trips")): \(roomPrice.roomCount)")
                            Text("\(localizationService.localizedString("divingPrice", table: "trips")): \(roomPrice.divingPrice, format: .currency(code: priceDetails.currency))")
                            Text("\(localizationService.localizedString("nonDivingPrice", table: "trips")): \(roomPrice.nonDivingPrice, format: .currency(code: priceDetails.currency))")
                        }
                        .padding(.vertical, 4)
                    }
                }
                
                Button(action: {editingRoomPrice = nil
                    existingRoomsSnapshot = priceDetails.roomPrices ?? []
                    currencySnapshot = priceDetails.currency
                    totalSpotsSnapshot = totalSpots
                    showRoomPriceForm = true}) {
                    HStack {
                        Image(systemName: "plus.circle.fill")
                        Text(localizationService.localizedString("addRoomPrice", table: "trips"))
                    }
                }
                .buttonStyle(.plain)
                
                // Validation message
                if let totalRooms = priceDetails.roomPrices?.reduce(0, { $0 + $1.roomCount }), totalRooms > totalSpots {
                    Text("⚠️ \(localizationService.localizedString("totalRoomsExceedSpots", table: "trips")): \(totalRooms) > \(totalSpots)")
                        .font(.caption)
                        .foregroundColor(.red)
                }
            } else {
                // Yacht prices section
                if let yachtPrices = priceDetails.yachtPrices, !yachtPrices.isEmpty {
                    ForEach(yachtPrices) { yachtPrice in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(yachtPrice.cabinType)
                                    .font(.headline)
                                Spacer()
                                Button(action: {
                                    editingCabinPrice = yachtPrice
                                    existingCabinsSnapshot = priceDetails.yachtPrices ?? []
                                    currencySnapshot = priceDetails.currency
                                    totalSpotsSnapshot = totalSpots
                                    showCabinPriceForm = true
                                }) {
                                    Image(systemName: "pencil")
                                }
                                Button(action: {
                                    if let index = priceDetails.yachtPrices?.firstIndex(where: { $0.id == yachtPrice.id }) {
                                        priceDetails.yachtPrices?.remove(at: index)
                                    }
                                }) {
                                    Image(systemName: "trash")
                                        .foregroundColor(.red)
                                }
                            }
                            Text("\(localizationService.localizedString("quantity", table: "trips")): \(yachtPrice.cabinCount)")
                            Text("\(localizationService.localizedString("divingPrice", table: "trips")): \(yachtPrice.divingPrice, format: .currency(code: priceDetails.currency))")
                            Text("\(localizationService.localizedString("nonDivingPrice", table: "trips")): \(yachtPrice.nonDivingPrice, format: .currency(code: priceDetails.currency))")
                        }
                        .padding(.vertical, 4)
                    }
                }
                
                Button(action: {
                    editingCabinPrice = nil
                    existingCabinsSnapshot = priceDetails.yachtPrices ?? []
                    currencySnapshot = priceDetails.currency
                    totalSpotsSnapshot = totalSpots
                    showCabinPriceForm = true
                }) {
                    HStack {
                        Image(systemName: "plus.circle.fill")
                        Text(localizationService.localizedString("addCabinPrice", table: "trips"))
                    }
                }
                
                // Validation message
                if let totalCabins = priceDetails.yachtPrices?.reduce(0, { $0 + $1.cabinCount }), totalCabins > totalSpots {
                    Text("⚠️ \(localizationService.localizedString("totalCabinsExceedSpots", table: "trips")): \(totalCabins) > \(totalSpots)")
                        .font(.caption)
                        .foregroundColor(.red)
                }
            }
        }
    }
    
    @State private var showDayForm = false
    @State private var editingDay: TripProgramDay?
    
    private var programSection: some View {
        Section(localizationService.localizedString("program", table: "trips")) {
            ForEach(programDays) { day in
                Button(action: {
                    editingDay = day
                    showDayForm = true
                }) {
                    VStack(alignment: .leading) {
                        Text(day.date, style: .date)
                            .font(.headline)
                            .foregroundColor(.primary)
                        if let description = day.description, !description.isEmpty {
                            Text(description)
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .lineLimit(2)
                        } else if day.activities.isEmpty {
                            Text("1 \(localizationService.localizedString("day", table: "common"))")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        } else {
                            Text("\(day.activities.count) \(localizationService.localizedString("activities", table: "trips"))")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
            
            Button(localizationService.localizedString("addDay", table: "trips")) {
                // Create new empty day
                let newDay = TripProgramDay(
                    id: UUID().uuidString,
                    date: programDays.isEmpty ? startDate : (programDays.last?.date.addingTimeInterval(86400) ?? startDate),
                    activities: [],
                    description: nil
                )
                editingDay = newDay
                showDayForm = true
            }
            .buttonStyle(.plain)
        }
        .sheet(isPresented: $showDayForm) {
            if let day = editingDay {
                NavigationView {
                    TripProgramDayFormView(
                        day: Binding(
                            get: { day },
                            set: { updatedDay in
                                editingDay = updatedDay
                            }
                        ),
                        onSave: { savedDay in
                            if let index = programDays.firstIndex(where: { $0.id == savedDay.id }) {
                                programDays[index] = savedDay
                            } else {
                                programDays.append(savedDay)
                            }
                            editingDay = nil
                            showDayForm = false
                        },
                        onCancel: {
                            editingDay = nil
                            showDayForm = false
                        }
                    )
                }
            }
        }
    }
    
    private var additionalExpensesSection: some View {
        Section(localizationService.localizedString("additionalExpenses", table: "trips")) {
            ForEach(additionalExpenses) { expense in
                HStack {
                    VStack(alignment: .leading) {
                        Text(expense.expenseType.rawValue.capitalized)
                            .font(.headline)
                        Text(expense.description)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Text("ui_trips_value".localized)
                }
                .onTapGesture {
                    editingExpense = expense
                    showExpenseForm = true
                }
            }
            .onDelete { indexSet in
                additionalExpenses.remove(atOffsets: indexSet)
            }
            
            Button(localizationService.localizedString("addExpense", table: "trips")) {editingExpense = nil
                showExpenseForm = true}
            .buttonStyle(.plain)
        }
    }
    
    private var isFormValid: Bool {
        let countryValid = !country.isEmpty
        // Hotel/yacht name is required
        let hotelOrYachtValid: Bool
        if tripType == .daily {
            hotelOrYachtValid = !hotelName.isEmpty
        } else {
            hotelOrYachtValid = !yachtName.isEmpty
        }
        let datesValid = endDate > startDate
        let spotsValid = totalSpots > 0
        
        return countryValid && hotelOrYachtValid && datesValid && spotsValid
    }
    
    private func loadData() async {
        await activeViewModel.loadHotels()
        await activeViewModel.loadYachts()
        await activeViewModel.loadCourses()
        
        if let user = authService.currentUser,
           let diveCenterId = user.diveCenterId {
            await activeViewModel.loadInstructors(diveCenterId: diveCenterId)
        }
        
        // Load countries from backend
        await loadCountries()
        
        // Load regions if country is already set
        if !country.isEmpty {
            await loadRegions(for: country)
        }
    }
    
    private func loadCountries() async {
        isLoadingCountries = true
        do {
            // Use getCountriesFull() to get Country objects with localization support
            countries = try await NetworkService.shared.getCountriesFull()} catch {
            // If loading fails, use empty list or fallback
            print("Failed to load countries: \(error.localizedDescription)")
            countries = []
        }
        isLoadingCountries = false
    }
    
    private func loadRegions(for countryName: String) async {isLoadingRegions = true
        do {
            // Get all countries with full data including regions
            let allCountries = try await NetworkService.shared.getCountriesFull()
            if let matchingCountry = allCountries.first(where: { $0.name == countryName || $0.displayName == countryName }) {
                // Extract region objects from the country's regions (keep Region objects for localization)
                if let countryRegions = matchingCountry.regions, !countryRegions.isEmpty {
                    regions = countryRegions
                } else {
                    regions = []
                }
            } else {// Try to get regions using the old API method as fallback
                // Note: Fallback API returns [String], so we need to convert to Region objects
                let loadedRegionNames = try await NetworkService.shared.getRegions(country: countryName)// Convert string region names to Region objects (without localized names)
                regions = loadedRegionNames.map { regionName in
                    Country.Region(name: regionName, localizedNames: [:])
                }
            }
        } catch {
            // If loading fails, use empty list
            print("Failed to load regions for \(countryName): \(error.localizedDescription)")
            regions = []
        }
        isLoadingRegions = false
    }
    
    private func loadTripData(_ trip: Trip) {
        tripType = trip.tripType
        // Load hotel/yacht names - if hotelId/yachtId is a name (not an ID), use it directly
        // Otherwise, try to find the hotel/yacht by ID and get its name
        if tripType == .daily {
            if let hotelId = trip.hotelId {
                if let hotel = activeViewModel.hotels.first(where: { $0.id == hotelId }) {
                    hotelName = hotel.name
                } else {
                    // If not found in list, assume it's a name (for backward compatibility)
                    hotelName = hotelId
                }
            }
        } else {
            if let yachtId = trip.yachtId {
                if let yacht = activeViewModel.yachts.first(where: { $0.id == yachtId }) {
                    yachtName = yacht.name
                } else {
                    // If not found in list, assume it's a name (for backward compatibility)
                    yachtName = yachtId
                }
            }
        }
        country = trip.country
        countrySearchText = trip.country
        region = trip.region ?? ""
        regionSearchText = trip.region ?? ""
        // Load regions for the country
        Task {
            await loadRegions(for: trip.country)
        }
        startDate = trip.startDate
        endDate = trip.endDate
        minimumCertificationLevel = trip.minimumCertificationLevel ?? "Open Water"
        minimumDives = trip.minimumDives ?? 0
        description = trip.description
        totalSpots = trip.totalSpots
        nitroxAvailable = trip.nitroxAvailable
        equipmentRentalAvailable = trip.equipmentRentalAvailable
        selectedCourses = Set(trip.availableCourses)
        selectedGroupLeaderId = trip.groupLeaderId
        
        // Only overwrite if we haven't loaded initial data yet
        // This prevents losing user's edits when view reloads
        // IMPORTANT: Always check hasLoadedInitialData first - if it's true, never overwrite
        // This prevents losing user's edits when view is recreated
        let hasLoaded = getHasLoadedInitialData(for: trip.id)
        if !hasLoaded {
            participants = trip.participants
            priceDetails = trip.priceDetails
            additionalExpenses = trip.additionalExpenses
            programDays = trip.program.map { day in
                TripProgramDay(
                    id: day.id,
                    date: day.date,
                    activities: day.activities.map { activity in
                        TripProgramDay.ProgramActivity(
                            id: activity.id,
                            time: activity.time,
                            activity: activity.activity,
                            diveSiteId: activity.diveSiteId,
                            diveCenterId: activity.diveCenterId,
                            notes: activity.notes
                        )
                    },
                    description: day.description
                )
            }
            // Load photos from trip.photos URLs
            photoImages = []
            Task {
                var loadedImages: [UIImage] = []
                for photoURL in trip.photos {
                    if let fullURL = NetworkService.shared.fullImageURL(from: photoURL),
                       let url = URL(string: fullURL) {
                        do {
                            let (data, _) = try await URLSession.shared.data(from: url)
                            if let image = UIImage(data: data) {
                                loadedImages.append(image)
                            }
                        } catch {}
                    }
                }
                await MainActor.run {
                    photoImages = loadedImages}
            }} else {}}
    
    private func importTripFromWebsite() async {
        let sourceUrl = importUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        importSheetError = nil
        guard let user = authService.currentUser else {
            importSheetError = "Требуется авторизация."
            return
        }
        guard let diveCenterId = user.diveCenterId, !diveCenterId.isEmpty else {
            importSheetError = "В профиле не указан дайв-центр (diveCenterId)."
            return
        }
        guard let parsed = URL(string: sourceUrl), ["http", "https"].contains(parsed.scheme?.lowercased() ?? "") else {
            importSheetError = "Укажите корректный URL (http/https)."
            return
        }
        isImportingFromSite = true
        defer { isImportingFromSite = false }
        do {
            let imported = try await NetworkService.shared.importTripFromWebsite(
                url: sourceUrl,
                diveCenterId: diveCenterId
            )
            showImportFromSiteSheet = false
            importSheetError = nil
            onTripSaved?(imported)
            dismiss()
        } catch {
            importSheetError = error.localizedDescription
        }
    }

    private func saveTrip() async {
        guard let user = authService.currentUser else {
            return
        }
        
        // Update country value before saving if user typed something
        if !countrySearchText.isEmpty {
            if let exactMatch = countries.first(where: { $0.displayName.caseInsensitiveCompare(countrySearchText) == .orderedSame }) {
                country = exactMatch.displayName
            } else {
                country = countrySearchText
            }
        }
        
        // Update region value before saving if user typed something
        if !regionSearchText.isEmpty {
            if let exactMatch = regions.first(where: { $0.displayName.caseInsensitiveCompare(regionSearchText) == .orderedSame }) {
                region = exactMatch.displayName
            } else {
                region = regionSearchText
            }
        }
        
        isLoading = true
        errorMessage = nil
        
        // TODO: Upload photos first to get URLs
        
        let organizerType: Trip.OrganizerType = user.role == .diveCenterAdmin ? .diveCenter : .user
        let organizerId = user.diveCenterId ?? user.id
        
        // Determine hotelId/yachtId - will be resolved in updateTrip based on tripType
        // For editing, pass hotelName/yachtName as hotelId/yachtId, updateTrip will resolve to actual IDs
        // IMPORTANT: When tripType changes, we must clear the opposite type's ID
        let finalHotelId: String?
        let finalYachtId: String?
        
        if tripType == .daily {
            finalHotelId = !hotelName.isEmpty ? hotelName : nil
            finalYachtId = nil // Clear yachtId when type is daily
        } else {
            finalHotelId = nil // Clear hotelId when type is safari
            finalYachtId = !yachtName.isEmpty ? yachtName : nil
        }
        
        let finalRegion = region.isEmpty ? nil : region// Calculate bookedSpots: use existing booked spots if editing, otherwise count participants
        let finalBookedSpots: Int
        if let existingTrip = trip {
            // When editing, preserve existing booked spots (they come from actual bookings)
            // But if we have new participants added, we might want to add them
            // For now, keep existing booked spots
            finalBookedSpots = existingTrip.bookedSpots
        } else {
            // New trip: count participants
            finalBookedSpots = participants.count
        }
        
        // Upload photos and get URLs
        var finalPhotos: [String] = trip?.photos ?? []
        
        if !photoImages.isEmpty {
            // Upload new photos
            var uploadedPhotoUrls: [String] = []
            for image in photoImages {
                if let imageData = image.jpegData(compressionQuality: 0.8) {
                    do {
                        let photoUrl = try await NetworkService.shared.uploadImage(imageData: imageData)
                        uploadedPhotoUrls.append(photoUrl)} catch {// Continue with other photos if one fails
                        print("Failed to upload photo: \(error.localizedDescription)")
                    }
                } else {}
            }
            
            // Combine existing photos with newly uploaded ones
            finalPhotos = finalPhotos + uploadedPhotoUrls
        } else {
        }
        let tripToSave = Trip(
            id: trip?.id ?? UUID().uuidString,
            organizerId: organizerId,
            organizerType: organizerType,
            tripType: tripType,
            hotelId: finalHotelId,
            yachtId: finalYachtId,
            country: country,
            region: finalRegion,
            startDate: startDate,
            endDate: endDate,
            minimumCertificationLevel: minimumCertificationLevel.isEmpty ? nil : minimumCertificationLevel,
            minimumDives: minimumDives == 0 ? nil : minimumDives,
            description: description,
            photos: finalPhotos,
            totalSpots: totalSpots,
            bookedSpots: finalBookedSpots,
            participants: participants, // Always use current participants, not old ones
            availableCourses: Array(selectedCourses),
            nitroxAvailable: nitroxAvailable,
            groupLeaderId: selectedGroupLeaderId,
            program: programDays.map { day in
                Trip.TripProgramDay(
                    id: day.id,
                    date: day.date,
                    activities: day.activities.map { activity in
                        Trip.TripProgramDay.ProgramActivity(
                            id: activity.id,
                            time: activity.time,
                            activity: activity.activity,
                            diveSiteId: activity.diveSiteId,
                            diveCenterId: activity.diveCenterId,
                            notes: activity.notes
                        )
                    },
                    description: day.description
                )
            },
            additionalExpenses: additionalExpenses, // Always use current expenses
            equipmentRentalAvailable: equipmentRentalAvailable,
            priceDetails: priceDetails, // Always use current price details
            createdAt: trip?.createdAt ?? Date(),
            updatedAt: Date()
        )
        do {
            let savedTrip: Trip
            if trip == nil {
                savedTrip = try await activeViewModel.createTrip(
                    tripToSave,
                    hotelName: tripType == .daily ? hotelName : nil,
                    hotelUrl: tripType == .daily ? (hotelUrl.isEmpty ? nil : hotelUrl) : nil,
                    yachtName: tripType == .safari ? yachtName : nil,
                    yachtUrl: tripType == .safari ? (yachtUrl.isEmpty ? nil : yachtUrl) : nil
                )} else {savedTrip = try await activeViewModel.updateTrip(
                    tripToSave,
                    hotelName: tripType == .daily ? hotelName : nil,
                    hotelUrl: tripType == .daily ? (hotelUrl.isEmpty ? nil : hotelUrl) : nil,
                    yachtName: tripType == .safari ? yachtName : nil,
                    yachtUrl: tripType == .safari ? (yachtUrl.isEmpty ? nil : yachtUrl) : nil
                )}
            
            // Call callback if provided
            onTripSaved?(savedTrip)
            
            dismiss()
        } catch {// Provide more user-friendly error messages
            if error.localizedDescription.contains("hotelName is required") || error.localizedDescription.contains("hotelId is required") {
                errorMessage = "Название отеля обязательно для поездок типа 'daily'."
            } else if error.localizedDescription.contains("yachtName is required") || error.localizedDescription.contains("yachtId is required") {
                errorMessage = "Название яхты обязательно для поездок типа 'safari'."
            } else if error.localizedDescription.contains("Hotel not found") {
                errorMessage = "Отель с таким названием не найден в базе данных. Пожалуйста, создайте отель сначала или используйте существующий."
            } else if error.localizedDescription.contains("Yacht not found") {
                errorMessage = "Яхта с таким названием не найдена в базе данных. Пожалуйста, создайте яхту сначала или используйте существующую."
            } else {
                errorMessage = error.localizedDescription
            }
        }
        
        isLoading = false}
}

// Helper struct for program day editing
struct TripProgramDay: Identifiable {
    let id: String
    var date: Date
    var activities: [ProgramActivity]
    var description: String?
    
    struct ProgramActivity: Identifiable {
        let id: String
        var time: String
        var activity: String
        var diveSiteId: String?
        var diveCenterId: String?
        var notes: String?
    }
}

struct TripProgramDayFormView: View {
    @Binding var day: TripProgramDay
    let onSave: (TripProgramDay) -> Void
    let onCancel: () -> Void
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var editedDay: TripProgramDay
    
    init(day: Binding<TripProgramDay>, onSave: @escaping (TripProgramDay) -> Void, onCancel: @escaping () -> Void) {
        self._day = day
        self.onSave = onSave
        self.onCancel = onCancel
        _editedDay = State(initialValue: day.wrappedValue)
    }
    
    var body: some View {
        Form {
            Section(localizationService.localizedString("date", table: "trips")) {
                DatePicker(localizationService.localizedString("date", table: "trips"), selection: $editedDay.date, displayedComponents: .date)
            }
            
            Section(localizationService.localizedString("description", table: "trips")) {
                TextEditor(text: Binding(
                    get: { editedDay.description ?? "" },
                    set: { editedDay.description = $0.isEmpty ? nil : $0 }
                ))
                .frame(height: 100)
            }
            
            Section(localizationService.localizedString("activities", table: "trips")) {
                ForEach(editedDay.activities) { activity in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(activity.time)
                                .font(.headline)
                            Spacer()
                        }
                        Text(activity.activity)
                            .font(.subheadline)
                        if let notes = activity.notes {
                            Text(notes)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                Button(localizationService.localizedString("addActivity", table: "trips")) {
                    let newActivity = TripProgramDay.ProgramActivity(
                        id: UUID().uuidString,
                        time: "09:00",
                        activity: "",
                        diveSiteId: nil,
                        diveCenterId: nil,
                        notes: nil
                    )
                    editedDay.activities.append(newActivity)
                }
                .buttonStyle(.plain)
            }
        }
        .navigationTitle(localizationService.localizedString("programDay", table: "trips"))
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(localizationService.localizedString("cancel", table: "common")) {
                    onCancel()
                }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(localizationService.localizedString("save", table: "common")) {
                    day = editedDay
                    onSave(editedDay)
                }
            }
        }
    }
}

// Keep old view for backward compatibility if needed
struct TripProgramDayView: View {
    @Binding var day: TripProgramDay
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        Form {
            Section(localizationService.localizedString("date", table: "trips")) {
                DatePicker(localizationService.localizedString("date", table: "trips"), selection: $day.date, displayedComponents: .date)
            }
            
            Section(localizationService.localizedString("description", table: "trips")) {
                TextEditor(text: Binding(
                    get: { day.description ?? "" },
                    set: { day.description = $0.isEmpty ? nil : $0 }
                ))
                .frame(height: 100)
            }
            
            Section(localizationService.localizedString("activities", table: "trips")) {
                ForEach(day.activities) { activity in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(activity.time)
                                .font(.headline)
                            Spacer()
                        }
                        Text(activity.activity)
                            .font(.subheadline)
                        if let notes = activity.notes {
                            Text(notes)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                Button(localizationService.localizedString("addActivity", table: "trips")) {
                    let newActivity = TripProgramDay.ProgramActivity(
                        id: UUID().uuidString,
                        time: "09:00",
                        activity: "",
                        diveSiteId: nil,
                        diveCenterId: nil,
                        notes: nil
                    )
                    day.activities.append(newActivity)
                }
                .buttonStyle(.plain)
            }
        }
        .navigationTitle(localizationService.localizedString("programDay", table: "trips"))
    }
}

// MARK: - Room Price Form
struct RoomPriceFormView: View {
    let roomPrice: Trip.PriceDetails.RoomPrice? // Changed from @Binding to let to prevent view recreation
    let currency: String
    let totalSpots: Int
    let existingRooms: [Trip.PriceDetails.RoomPrice]
    let onSave: (Trip.PriceDetails.RoomPrice) -> Void
    let onCancel: () -> Void
    
    @Environment(\.dismiss) var dismiss
    @State private var roomType: String = ""
    @State private var roomCount: Int = 1
    @State private var divingPrice: Double = 0
    @State private var nonDivingPrice: Double = 0
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        NavigationView {
            Form {
                Section(localizationService.localizedString("roomType", table: "trips")) {
                    TextField(localizationService.localizedString("roomType", table: "trips"), text: $roomType)
                }
                
                Section(localizationService.localizedString("quantity", table: "trips")) {
                    Stepper("\(roomCount)", value: $roomCount, in: 1...totalSpots)
                    
                    let otherRoomsCount = existingRooms.filter { $0.id != roomPrice?.id }.reduce(0) { $0 + $1.roomCount }
                    let availableSpots = totalSpots - otherRoomsCount
                    
                    if roomCount > availableSpots {
                        Text("⚠️ \(localizationService.localizedString("exceedsAvailableSpots", table: "trips")): \(availableSpots)")
                            .font(.caption)
                            .foregroundColor(.red)
                    }
                }
                
                Section(localizationService.localizedString("prices", table: "trips")) {
                    HStack {
                        Text(localizationService.localizedString("divingPrice", table: "trips"))
                        Spacer()
                        TextField("0", value: $divingPrice, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 100)
                        Text(currency)
                    }
                    
                    HStack {
                        Text(localizationService.localizedString("nonDivingPrice", table: "trips"))
                        Spacer()
                        TextField("0", value: $nonDivingPrice, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 100)
                        Text(currency)
                    }
                }
            }
            .navigationTitle(roomPrice == nil ? localizationService.localizedString("addRoomPrice", table: "trips") : localizationService.localizedString("editRoomPrice", table: "trips"))
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel", table: "common")) {onCancel()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("save", table: "common")) {let newRoomPrice = Trip.PriceDetails.RoomPrice(
                            id: roomPrice?.id ?? UUID().uuidString,
                            roomType: roomType,
                            roomCount: roomCount,
                            divingPrice: divingPrice,
                            nonDivingPrice: nonDivingPrice
                        )
                        onSave(newRoomPrice)
                        dismiss()
                    }
                    .disabled(roomType.isEmpty || divingPrice < 0 || nonDivingPrice < 0)
                }
            }
            .onAppear {if let existing = roomPrice {
                    roomType = existing.roomType
                    roomCount = existing.roomCount
                    divingPrice = existing.divingPrice
                    nonDivingPrice = existing.nonDivingPrice} else {
                    // Reset fields for new room price
                    roomType = ""
                    roomCount = 1
                    divingPrice = 0
                    nonDivingPrice = 0}
            }
        }
    }
}

// MARK: - Cabin Price Form
struct CabinPriceFormView: View {
    let cabinPrice: Trip.PriceDetails.YachtPrice? // Changed from @Binding to let to prevent view recreation
    let currency: String
    let totalSpots: Int
    let existingCabins: [Trip.PriceDetails.YachtPrice]
    let onSave: (Trip.PriceDetails.YachtPrice) -> Void
    let onCancel: () -> Void
    
    @Environment(\.dismiss) var dismiss
    @State private var cabinType: String = ""
    @State private var cabinCount: Int = 1
    @State private var divingPrice: Double = 0
    @State private var nonDivingPrice: Double = 0
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        NavigationView {
            Form {
                Section(localizationService.localizedString("cabinType", table: "trips")) {
                    TextField(localizationService.localizedString("cabinType", table: "trips"), text: $cabinType)
                }
                
                Section(localizationService.localizedString("quantity", table: "trips")) {
                    Stepper("\(cabinCount)", value: $cabinCount, in: 1...totalSpots)
                    
                    let otherCabinsCount = existingCabins.filter { $0.id != cabinPrice?.id ?? "" }.reduce(0) { $0 + $1.cabinCount }
                    let availableSpots = totalSpots - otherCabinsCount
                    
                    if cabinCount > availableSpots {
                        Text("⚠️ \(localizationService.localizedString("exceedsAvailableSpots", table: "trips")): \(availableSpots)")
                            .font(.caption)
                            .foregroundColor(.red)
                    }
                }
                
                Section(localizationService.localizedString("prices", table: "trips")) {
                    HStack {
                        Text(localizationService.localizedString("divingPrice", table: "trips"))
                        Spacer()
                        TextField("0", value: $divingPrice, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 100)
                        Text(currency)
                    }
                    
                    HStack {
                        Text(localizationService.localizedString("nonDivingPrice", table: "trips"))
                        Spacer()
                        TextField("0", value: $nonDivingPrice, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 100)
                        Text(currency)
                    }
                }
            }
            .navigationTitle(cabinPrice == nil ? localizationService.localizedString("addCabinPrice", table: "trips") : localizationService.localizedString("editCabinPrice", table: "trips"))
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        onCancel()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("save", table: "common")) {
                        let newCabinPrice = Trip.PriceDetails.YachtPrice(
                            id: cabinPrice?.id ?? UUID().uuidString,
                            cabinType: cabinType,
                            cabinCount: cabinCount,
                            divingPrice: divingPrice,
                            nonDivingPrice: nonDivingPrice
                        )
                        onSave(newCabinPrice)
                        dismiss()
                    }
                    .disabled(cabinType.isEmpty || divingPrice < 0 || nonDivingPrice < 0)
                }
            }
            .onAppear {
                if let existing = cabinPrice {
                    cabinType = existing.cabinType
                    cabinCount = existing.cabinCount
                    divingPrice = existing.divingPrice
                    nonDivingPrice = existing.nonDivingPrice
                }
            }
        }
    }
}

// MARK: - ExpenseFormView
struct ExpenseFormView: View {
    let expense: Trip.AdditionalExpense?
    let currency: String
    let onSave: (Trip.AdditionalExpense) -> Void
    let onCancel: () -> Void
    
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var expenseType: Trip.AdditionalExpense.ExpenseType = .other
    @State private var description: String = ""
    @State private var cost: Double = 0
    
    var body: some View {
        NavigationView {
            Form {
                Section(localizationService.localizedString("expenseType", table: "trips")) {
                    Picker(localizationService.localizedString("expenseType", table: "trips"), selection: $expenseType) {
                        ForEach([Trip.AdditionalExpense.ExpenseType.flight, .transfer, .nutrition, .reserve, .other], id: \.self) { type in
                            Text(type.rawValue.capitalized).tag(type)
                        }
                    }
                }
                
                Section(localizationService.localizedString("description", table: "trips")) {
                    TextField(localizationService.localizedString("description", table: "trips"), text: $description)
                }
                
                Section(localizationService.localizedString("cost", table: "trips")) {
                    HStack {
                        Text(currency)
                        Spacer()
                        TextField("0", value: $cost, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 100)
                    }
                }
            }
            .navigationTitle(expense == nil ? localizationService.localizedString("addExpense", table: "trips") : localizationService.localizedString("editExpense", table: "trips"))
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        onCancel()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("save", table: "common")) {
                        let newExpense = Trip.AdditionalExpense(
                            id: expense?.id ?? UUID().uuidString,
                            expenseType: expenseType,
                            description: description,
                            cost: cost,
                            currency: currency
                        )
                        onSave(newExpense)
                        dismiss()
                    }
                    .disabled(description.isEmpty || cost < 0)
                }
            }
            .onAppear {
                if let existing = expense {
                    expenseType = existing.expenseType
                    description = existing.description
                    cost = existing.cost
                }
            }
        }
    }
}

// MARK: - ParticipantPickerView
struct ParticipantPickerView: View {
    let onSelect: (Trip.TripParticipant) -> Void
    
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var adminViewModel = AdminViewModel()
    @State private var selectedParticipant: User?
    @State private var isDiving: Bool = true
    
    var body: some View {
        NavigationView {
            Form {
                Section(localizationService.localizedString("selectParticipant", table: "trips")) {
                    if adminViewModel.instructors.isEmpty {
                        Text(localizationService.localizedString("noParticipantsAvailable", table: "trips"))
                            .foregroundColor(.secondary)
                    } else {
                        Picker(localizationService.localizedString("participant", table: "trips"), selection: $selectedParticipant) {
                            Text(localizationService.localizedString("select", table: "common")).tag(Optional<User>.none)
                            ForEach(adminViewModel.instructors) { instructor in
                                Text(instructor.displayName).tag(Optional<User>.some(instructor))
                            }
                        }
                    }
                }
                
                Section(localizationService.localizedString("participationType", table: "trips")) {
                    Toggle(localizationService.localizedString("diving", table: "trips"), isOn: $isDiving)
                }
            }
            .navigationTitle(localizationService.localizedString("addKnownParticipant", table: "trips"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                toolbarContent
            }
            .task {
                await adminViewModel.loadInstructors()
            }
        }
    }
    
    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            Button(localizationService.localizedString("cancel", table: "common")) {
                dismiss()
            }
        }
        ToolbarItem(placement: .navigationBarTrailing) {
            Button(localizationService.localizedString("add", table: "common")) {
                if let user = selectedParticipant {
                    let participant = Trip.TripParticipant(
                        id: UUID().uuidString,
                        userId: user.id,
                        name: user.displayName,
                        email: user.email,
                        phoneNumber: user.phoneNumber,
                        certificationLevel: user.certificationLevel,
                        isDiving: isDiving,
                        bookedAt: Date()
                    )
                    onSelect(participant)
                    dismiss()
                }
            }
            .disabled(selectedParticipant == nil)
        }
    }
}
