//
//  TripsListView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

// Helper class to cache countries from backend and provide localized names for the entire app
@MainActor
class CountryLocalizationHelper: ObservableObject {
    static let shared = CountryLocalizationHelper()
    
    /// Raw English country names from backend (used as filter values)
    @Published var countryNames: [String] = []
    @Published private(set) var isLoading = false
    
    /// Mapping: English name → ISO 3166-1 alpha-2 code, built once from system Locale
    private let nameToISOCode: [String: String] = {
        var map: [String: String] = [:]
        let englishLocale = Locale(identifier: "en")
        for region in Locale.Region.isoRegions {
            let code = region.identifier
            if let name = englishLocale.localizedString(forRegionCode: code) {
                map[name.lowercased()] = code
            }
        }
        // Manual overrides: native-language names and alternative spellings → ISO code
        let overrides: [String: String] = [
            // Ireland (native bilingual name)
            "éire / ireland": "IE",
            "éire/ireland": "IE",
            "eire / ireland": "IE",
            "éire": "IE",
            "eire": "IE",
            // Spain
            "españa": "ES",
            "espana": "ES",
            // Croatia
            "hrvatska": "HR",
            // Italy
            "italia": "IT",
            // New Zealand (bilingual)
            "new zealand / aotearoa": "NZ",
            "new zealand/aotearoa": "NZ",
            "aotearoa": "NZ",
            // Norway
            "norge": "NO",
            // Austria
            "österreich": "AT",
            "osterreich": "AT",
            // Papua New Guinea (Tok Pisin)
            "papua niugini": "PG",
            // Russia
            "russian federation": "RU",
            "россия": "RU",
            "russia": "RU",
            // Switzerland (quadrilingual official name)
            "schweiz/suisse/svizzera/svizra": "CH",
            "schweiz": "CH",
            "suisse": "CH",
            "svizzera": "CH",
            // Fiji (Fijian name)
            "viti": "FJ",
            // Turkey
            "türkiye": "TR",
            "turkiye": "TR",
            "turkey": "TR",
            // Other common alternatives
            "south korea": "KR",
            "north korea": "KP",
            "iran": "IR",
            "syria": "SY",
            "taiwan": "TW",
            "hong kong": "HK",
            "macau": "MO",
            "macao": "MO",
            "vietnam": "VN",
            "viet nam": "VN",
            "laos": "LA",
            "moldova": "MD",
            "czech republic": "CZ",
            "czechia": "CZ",
            "ivory coast": "CI",
            "côte d'ivoire": "CI",
            "cape verde": "CV",
            "cabo verde": "CV",
            "eswatini": "SZ",
            "swaziland": "SZ",
            "north macedonia": "MK",
            "myanmar": "MM",
            "burma": "MM",
            "timor-leste": "TL",
            "east timor": "TL",
            "micronesia": "FM",
            "united states": "US",
            "usa": "US",
            "united kingdom": "GB",
            "uk": "GB",
            "uae": "AE",
            "united arab emirates": "AE",
        ]
        for (name, code) in overrides {
            map[name] = code
        }
        return map
    }()
    
    private init() {
        Task { await loadCountries() }
        NotificationCenter.default.addObserver(forName: .languageChanged, object: nil, queue: .main) { [weak self] _ in
            // No need to reload from backend — just UI will re-render with new Locale
            self?.objectWillChange.send()
        }
    }
    
    func loadCountries() async {
        guard !isLoading else { return }
        isLoading = true
        do {
            let response = try await NetworkService.shared.getCountriesFromDiveSites()
            countryNames = response.data
        } catch {
            print("[CountryLocalizationHelper] Failed to load countries: \(error)")
        }
        isLoading = false
    }
    
    func ensureLoaded() {
        guard countryNames.isEmpty && !isLoading else { return }
        Task { await loadCountries() }
    }
    
    /// Returns the localized display name for a country stored as English name in DB
    func getLocalizedCountryName(_ englishName: String) -> String {
        if let isoCode = nameToISOCode[englishName.lowercased()] {
            let appLocale = LocalizationService.shared.currentLanguage.locale
            if let localized = appLocale.localizedString(forRegionCode: isoCode), !localized.isEmpty {
                return localized
            }
        } else {
            print("[CountryLocalizationHelper] No ISO code for: '\(englishName)'")
        }
        return englishName
    }
    
    // Legacy: kept for backward compatibility with TripsListView
    var countries: [Country] {
        countryNames.map { Country(id: $0, name: $0) }
    }
    
    func getLocalizedRegionName(_ savedName: String, countryName: String) -> String {
        return savedName
    }
}


struct TripsListView: View {
    @StateObject private var viewModel = TripViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var countryHelper = CountryLocalizationHelper.shared
    @State private var showFilters = false
    @State private var showCreateTrip = false
    @State private var selectedTrip: Trip?
    @State private var showBooking = false
    @State private var bookingTrip: Trip? // Store trip for booking sheet
    @State private var isClosingDetailForBooking = false // Flag to prevent clearing bookingTrip prematurely
    @StateObject private var authService = AuthenticationService.shared
    
    var body: some View {
        Group {
            NavigationView {
                contentView
                    .navigationTitle(localizationService.localizedString("trips", table: "trips"))
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button(action: { showFilters = true }) {
                                Image(systemName: "line.3.horizontal.decrease.circle")
                            }
                        }
                        
                        ToolbarItem(placement: .navigationBarTrailing) {
                            if let user = authService.currentUser, viewModel.canCreateTrip(user: user) {
                                Button(action: { showCreateTrip = true }) {
                                    Image(systemName: "plus")
                                }
                            }
                        }
                    }
                    .sheet(isPresented: $showFilters) {
                        TripFiltersView(filters: $viewModel.filters, trips: viewModel.allTrips.isEmpty ? viewModel.trips : viewModel.allTrips, onApply: {
                            Task {
                                await viewModel.loadTrips()
                            }
                        })
                        .task {
                            // Load all trips for filter options if not already loaded
                            if viewModel.allTrips.isEmpty {
                                await viewModel.loadAllTripsForFilters()
                            }
                        }
                    }
                    .sheet(isPresented: $showCreateTrip) {
                        CreateTripView()
                            .environmentObject(AuthenticationService.shared)
                            .environmentObject(LocalizationService.shared)
                    }
                    .sheet(item: $selectedTrip) { trip in
                        TripDetailView(trip: trip, showBooking: $showBooking)
                            .onDisappear {
                                // If booking was requested, open booking sheet after detail view closes
                                if isClosingDetailForBooking, bookingTrip != nil {
                                    // Reset flag
                                    isClosingDetailForBooking = false
                                    // Small delay to ensure the first sheet is fully dismissed
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                        // Ensure bookingTrip is still set
                                        if bookingTrip != nil {
                                            showBooking = true
                                        }
                                    }
                                } else if !isClosingDetailForBooking {
                                    // If we're not closing for booking, reset the flag
                                    isClosingDetailForBooking = false
                                }
                            }
                    }
                    .sheet(isPresented: $showBooking) {
                        if let trip = bookingTrip {
                            TripBookingView(trip: trip)
                        }
                    }
                    .onChange(of: showBooking) { oldValue, newValue in
                        // Close TripDetailView sheet before opening TripBookingView sheet
                        if newValue, let trip = selectedTrip {
                            // Store trip for booking sheet BEFORE closing detail view
                            bookingTrip = trip
                            // Set flag to prevent clearing bookingTrip prematurely
                            isClosingDetailForBooking = true
                            // Close the detail view sheet first (this will trigger onDisappear)
                            selectedTrip = nil
                            // Temporarily set showBooking to false to prevent immediate sheet presentation
                            // It will be set to true in onDisappear after detail view closes
                            showBooking = false
                        } else if !newValue && !isClosingDetailForBooking {
                            // Only clear booking trip when booking sheet is dismissed AND we're not in the process of closing detail for booking
                            bookingTrip = nil
                        }
                    }
                    .task {
                        await viewModel.loadTrips()
                        // Ensure all trips are loaded for filter options
                        if viewModel.allTrips.isEmpty {
                            await viewModel.loadAllTripsForFilters()
                        }
                        // Ensure countries are loaded for localization
                        await countryHelper.loadCountries()
                    }
                    .onChange(of: localizationService.currentLanguage) { oldValue, newValue in
                        // Reload countries when language changes to update localized names
                        Task {
                            await countryHelper.loadCountries()
                        }
                    }
            }
        }
    }
    
    @ViewBuilder
    private var contentView: some View {
        VStack {
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.trips.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "airplane.departure")
                        .font(.system(size: 60))
                        .foregroundColor(.gray)
                    Text(localizationService.localizedString("noTripsAvailable", table: "trips"))
                        .foregroundColor(.gray)
                        .font(.headline)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(viewModel.trips) { trip in
                        TripRowView(trip: trip)
                            .onTapGesture {
                                selectedTrip = trip
                            }
                    }
                }
            }
        }
    }
}

struct TripRowView: View {
    let trip: Trip
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var countryHelper = CountryLocalizationHelper.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(countryHelper.getLocalizedCountryName(trip.country))
                    .font(.headline)
                Spacer()
                Text(trip.tripType.rawValue.capitalized)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.blue.opacity(0.2))
                    .cornerRadius(8)
            }
            
            Text(trip.description)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .lineLimit(2)
            
            HStack {
                Label("\(trip.startDate.formatted(date: .abbreviated, time: .omitted)) - \(trip.endDate.formatted(date: .abbreviated, time: .omitted))", systemImage: "calendar")
                    .font(.caption)
                Spacer()
                Label("\(trip.availableSpots) \(localizationService.localizedString("spots", table: "trips"))", systemImage: "person.2")
                    .font(.caption)
                    .foregroundColor(trip.isFullyBooked ? .red : .green)
            }
            
            if trip.nitroxAvailable {
                HStack {
                    Image(systemName: "air.purifier")
                        .foregroundColor(.blue)
                    Text(localizationService.localizedString("nitroxAvailable", table: "trips"))
                        .font(.caption)
                }
            }
        }
        .padding(.vertical, 4)
        .id(localizationService.currentLanguage) // Force view update when language changes
    }
}

struct TripFiltersView: View {
    @Binding var filters: TripViewModel.TripFilters
    let trips: [Trip]
    @Environment(\.dismiss) var dismiss
    let onApply: () -> Void
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var countryHelper = CountryLocalizationHelper.shared
    
    // Extract available options from trips — ordered by backend list if loaded
    private var availableCountries: [String] {
        let tripCountries = Set(trips.map { $0.country }.filter { !$0.isEmpty })
        if countryHelper.countryNames.isEmpty {
            return tripCountries.sorted()
        }
        return countryHelper.countryNames.filter { tripCountries.contains($0) }
    }
    
    private func getLocalizedCountryName(_ countryName: String) -> String {
        countryHelper.getLocalizedCountryName(countryName)
    }
    
    private var availableTripTypes: [Trip.TripType] {
        Array(Set(trips.map { $0.tripType }))
            .sorted { $0.rawValue < $1.rawValue }
    }
    
    private var availableCertificationLevels: [String] {
        Array(Set(trips.compactMap { $0.minimumCertificationLevel }))
            .sorted()
    }
    
    private var hasNitroxAvailable: Bool {
        trips.contains { $0.nitroxAvailable }
    }
    
    private var hasEquipmentRentalAvailable: Bool {
        trips.contains { $0.equipmentRentalAvailable }
    }
    
    private var hasAvailableSpots: Bool {
        trips.contains { $0.availableSpots > 0 }
    }
    
    var body: some View {
        NavigationView {
            Form {
                Section(localizationService.localizedString("tripTypeFilter", table: "trips")) {
                    if availableTripTypes.isEmpty {
                        Text(localizationService.localizedString("noOptionsAvailable", table: "common"))
                            .foregroundColor(.secondary)
                    } else {
                        Picker(localizationService.localizedString("tripType", table: "trips"), selection: Binding(
                            get: { filters.tripType },
                            set: { filters.tripType = $0 }
                        )) {
                            Text(localizationService.localizedString("all", table: "common")).tag(nil as Trip.TripType?)
                            ForEach(availableTripTypes, id: \.self) { tripType in
                                Text(tripType == .daily ? localizationService.localizedString("daily", table: "trips") : localizationService.localizedString("safari", table: "trips"))
                                    .tag(tripType as Trip.TripType?)
                            }
                        }
                    }
                }
                
                Section(localizationService.localizedString("location", table: "trips")) {
                    if availableCountries.isEmpty {
                        Text(localizationService.localizedString("noOptionsAvailable", table: "common"))
                            .foregroundColor(.secondary)
                    } else {
                        Picker(localizationService.localizedString("country", table: "trips"), selection: Binding(
                            get: { filters.country ?? "" },
                            set: { filters.country = $0.isEmpty ? nil : $0 }
                        )) {
                            Text(localizationService.localizedString("all", table: "common")).tag("" as String)
                            ForEach(availableCountries, id: \.self) { countryName in
                                Text(getLocalizedCountryName(countryName)).tag(countryName)
                            }
                        }
                    }
                }
                
                Section(localizationService.localizedString("certification", table: "trips")) {
                    if availableCertificationLevels.isEmpty {
                        Text(localizationService.localizedString("noOptionsAvailable", table: "common"))
                            .foregroundColor(.secondary)
                    } else {
                        Picker(localizationService.localizedString("minimumCertification", table: "trips"), selection: Binding(
                            get: { filters.minCertificationLevel ?? "" },
                            set: { filters.minCertificationLevel = $0.isEmpty ? nil : $0 }
                        )) {
                            Text(localizationService.localizedString("all", table: "common")).tag("" as String)
                            ForEach(availableCertificationLevels, id: \.self) { level in
                                Text(level).tag(level)
                            }
                        }
                    }
                }
                
                Section(localizationService.localizedString("dates", table: "trips")) {
                    DatePicker(localizationService.localizedString("startDate", table: "trips"), selection: Binding(
                        get: { filters.startDate ?? Date() },
                        set: { filters.startDate = $0 }
                    ), displayedComponents: .date)
                    
                    DatePicker(localizationService.localizedString("endDate", table: "trips"), selection: Binding(
                        get: { filters.endDate ?? Date() },
                        set: { filters.endDate = $0 }
                    ), displayedComponents: .date)
                }
                
                Section(localizationService.localizedString("options", table: "trips")) {
                    if hasNitroxAvailable {
                        Toggle(localizationService.localizedString("nitroxAvailable", table: "trips"), isOn: Binding(
                            get: { filters.nitroxAvailable ?? false },
                            set: { filters.nitroxAvailable = $0 ? true : nil }
                        ))
                    }
                    
                    if hasEquipmentRentalAvailable {
                        Toggle(localizationService.localizedString("equipmentRentalAvailable", table: "trips"), isOn: Binding(
                            get: { filters.equipmentRentalAvailable ?? false },
                            set: { filters.equipmentRentalAvailable = $0 ? true : nil }
                        ))
                    }
                    
                    if hasAvailableSpots {
                        Toggle(localizationService.localizedString("onlyAvailableSpots", table: "trips"), isOn: Binding(
                            get: { filters.availableSpots ?? false },
                            set: { filters.availableSpots = $0 ? true : nil }
                        ))
                    }
                    
                    if !hasNitroxAvailable && !hasEquipmentRentalAvailable && !hasAvailableSpots {
                        Text(localizationService.localizedString("noOptionsAvailable", table: "common"))
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("filters", table: "trips"))
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("reset", table: "trips")) {
                        filters = TripViewModel.TripFilters()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("apply", table: "trips")) {
                        onApply()
                        dismiss()
                    }
                }
            }
            .task {
                // Ensure countries are loaded for localization
                await countryHelper.loadCountries()
            }
            .onChange(of: localizationService.currentLanguage) { oldValue, newValue in
                // Reload countries when language changes to update localized names
                Task {
                    await countryHelper.loadCountries()
                }
            }
        }
    }
}

struct TripDetailView: View {
    let trip: Trip
    @Binding var showBooking: Bool
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var viewModel = TripViewModel()
    @StateObject private var countryHelper = CountryLocalizationHelper.shared
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Photos Gallery with TabView
                    if !trip.photos.isEmpty {
                        TabView {
                            ForEach(trip.photos, id: \.self) { photoURL in
                                AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: photoURL) ?? "")) { image in
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Rectangle()
                                        .fill(Color.gray.opacity(0.3))
                                        .overlay(
                                            ProgressView()
                                        )
                                }
                                .frame(height: 300)
                                .clipped()
                            }
                        }
                        .frame(height: 300)
                        .tabViewStyle(.page)
                        .padding(.vertical, 8)
                    }
                    
                    VStack(alignment: .leading, spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(countryHelper.getLocalizedCountryName(trip.country))
                                .font(.title)
                                .bold()
                            
                            if let region = trip.region, !region.isEmpty {
                                Text(countryHelper.getLocalizedRegionName(region, countryName: trip.country))
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                        }
                        
                        Text(trip.description)
                            .font(.body)
                            .foregroundColor(.secondary)
                        
                        Divider()
                        
                        // Hotel/Yacht Info
                        if trip.tripType == .daily, let hotelId = trip.hotelId {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(localizationService.localizedString("hotel", table: "trips"))
                                    .font(.headline)
                                
                                // Try to find hotel in viewModel
                                if let hotel = viewModel.hotels.first(where: { $0.id == hotelId }) {
                                    HStack {
                                        Image(systemName: "bed.double.fill")
                                            .foregroundColor(.blue)
                                        Text("\(localizationService.localizedString("hotel", table: "trips")): \(hotel.name)")
                                            .font(.subheadline)
                                        Spacer()
                                    }
                                    
                                } else {
                                    // Fallback: hotelId might be a name
                                    HStack {
                                        Image(systemName: "bed.double.fill")
                                            .foregroundColor(.blue)
                                        Text("\(localizationService.localizedString("hotel", table: "trips")): \(hotelId)")
                                            .font(.subheadline)
                                    }
                                }
                            }
                            
                            Divider()
                        } else if trip.tripType == .safari, let yachtId = trip.yachtId {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(localizationService.localizedString("yacht", table: "trips"))
                                    .font(.headline)
                                
                                // Try to find yacht in viewModel
                                if let yacht = viewModel.yachts.first(where: { $0.id == yachtId }) {
                                    HStack {
                                        Image(systemName: "sailboat.fill")
                                            .foregroundColor(.blue)
                                        Text("\(localizationService.localizedString("yacht", table: "trips")): \(yacht.name)")
                                            .font(.subheadline)
                                        Spacer()
                                    }
                                    
                                } else {
                                    // Fallback: yachtId might be a name
                                    HStack {
                                        Image(systemName: "sailboat.fill")
                                            .foregroundColor(.blue)
                                        Text("\(localizationService.localizedString("yacht", table: "trips")): \(yachtId)")
                                            .font(.subheadline)
                                    }
                                }
                            }
                            
                            Divider()
                        }
                        
                        // Trip Info
                        VStack(alignment: .leading, spacing: 8) {
                            InfoRow(icon: "calendar", text: "\(trip.startDate.formatted(date: .abbreviated, time: .omitted)) - \(trip.endDate.formatted(date: .abbreviated, time: .omitted))")
                            InfoRow(icon: "person.2", text: "\(trip.availableSpots) \(localizationService.localizedString("of", table: "trips")) \(trip.totalSpots) \(localizationService.localizedString("spots", table: "trips")) \(localizationService.localizedString("available", table: "trips"))")
                            InfoRow(icon: "certificate", text: "\(localizationService.localizedString("minimumCertification", table: "trips")): \(trip.minimumCertificationLevel ?? "N/A") (\(trip.minimumDives ?? 0) dives)")
                            
                            if trip.nitroxAvailable {
                                InfoRow(icon: "air.purifier", text: localizationService.localizedString("nitroxAvailable", table: "trips"))
                            }
                            
                            if trip.equipmentRentalAvailable {
                                InfoRow(icon: "wrench.and.screwdriver", text: localizationService.localizedString("equipmentRentalAvailable", table: "trips"))
                            }
                        }
                        
                        Divider()
                        
                        // Program
                        if !trip.program.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text(localizationService.localizedString("program", table: "trips"))
                                    .font(.headline)
                                
                                ForEach(trip.program) { day in
                                    VStack(alignment: .leading, spacing: 8) {
                                        HStack {
                                            Text(day.date, style: .date)
                                                .font(.subheadline)
                                                .bold()
                                            Spacer()
                                        }
                                        
                                        if let description = day.description, !description.isEmpty {
                                            Text(description)
                                                .font(.body)
                                                .foregroundColor(.secondary)
                                                .padding(.bottom, 4)
                                        }
                                        
                                        if !day.activities.isEmpty {
                                            ForEach(day.activities) { activity in
                                                HStack(alignment: .top, spacing: 8) {
                                                    Text(activity.time)
                                                        .font(.caption)
                                                        .foregroundColor(.secondary)
                                                        .frame(width: 50, alignment: .leading)
                                                    VStack(alignment: .leading, spacing: 2) {
                                                        Text(activity.activity)
                                                            .font(.caption)
                                                        if let notes = activity.notes, !notes.isEmpty {
                                                            Text(notes)
                                                                .font(.caption2)
                                                                .foregroundColor(.secondary)
                                                        }
                                                    }
                                                    Spacer()
                                                }
                                                .padding(.vertical, 2)
                                            }
                                        }
                                    }
                                    .padding()
                                    .background(Color.diveBackground)
                                    .cornerRadius(8)
                                }
                            }
                        }
                        
                        Divider()
                        
                        // Price Details
                        VStack(alignment: .leading, spacing: 12) {
                            Text(localizationService.localizedString("priceDetails", table: "trips"))
                                .font(.headline)
                            
                            if trip.tripType == .daily, let roomPrices = trip.priceDetails.roomPrices, !roomPrices.isEmpty {
                                Text(localizationService.localizedString("roomPrices", table: "trips"))
                                    .font(.subheadline)
                                    .fontWeight(.semibold)
                                
                                ForEach(roomPrices) { roomPrice in
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(roomPrice.roomType)
                                            .font(.subheadline)
                                            .fontWeight(.semibold)
                                        HStack {
                                            Text("\(localizationService.localizedString("divingPrice", table: "trips")): \(roomPrice.divingPrice, format: .currency(code: trip.priceDetails.currency))")
                                            Spacer()
                                            Text("\(localizationService.localizedString("nonDivingPrice", table: "trips")): \(roomPrice.nonDivingPrice, format: .currency(code: trip.priceDetails.currency))")
                                        }
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                    }
                                    .padding(.vertical, 4)
                                }
                            } else if trip.tripType == .safari, let yachtPrices = trip.priceDetails.yachtPrices, !yachtPrices.isEmpty {
                                Text(localizationService.localizedString("cabinPrices", table: "trips"))
                                    .font(.subheadline)
                                    .fontWeight(.semibold)
                                
                                ForEach(yachtPrices) { yachtPrice in
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(yachtPrice.cabinType)
                                            .font(.subheadline)
                                            .fontWeight(.semibold)
                                        HStack {
                                            Text("\(localizationService.localizedString("divingPrice", table: "trips")): \(yachtPrice.divingPrice, format: .currency(code: trip.priceDetails.currency))")
                                            Spacer()
                                            Text("\(localizationService.localizedString("nonDivingPrice", table: "trips")): \(yachtPrice.nonDivingPrice, format: .currency(code: trip.priceDetails.currency))")
                                        }
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                    }
                                    .padding(.vertical, 4)
                                }
                            }
                        }
                        
                        Divider()
                        
                        // Additional Expenses
                        if !trip.additionalExpenses.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text(localizationService.localizedString("additionalExpenses", table: "trips"))
                                    .font(.headline)
                                
                                ForEach(trip.additionalExpenses) { expense in
                                    VStack(alignment: .leading, spacing: 4) {
                                        HStack {
                                            Text(expense.expenseType.rawValue.capitalized)
                                                .font(.subheadline)
                                                .fontWeight(.semibold)
                                            Spacer()
                                            Text("\(expense.cost, format: .currency(code: expense.currency))")
                                                .font(.subheadline)
                                                .fontWeight(.semibold)
                                        }
                                        if !expense.description.isEmpty {
                                            Text(expense.description)
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                    }
                                    .padding(.vertical, 4)
                                }
                            }
                        }
                        
                        // Participants
                        if !trip.participants.isEmpty {
                            Divider()
                            
                            VStack(alignment: .leading, spacing: 12) {
                                Text(localizationService.localizedString("participants", table: "trips"))
                                    .font(.headline)
                                
                                ForEach(trip.participants) { participant in
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(participant.name)
                                                .font(.subheadline)
                                            if let email = participant.email {
                                                Text(email)
                                                    .font(.caption)
                                                    .foregroundColor(.secondary)
                                            }
                                        }
                                        Spacer()
                                        if participant.isDiving {
                                            Text(localizationService.localizedString("diving", table: "trips"))
                                                .font(.caption)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(Color.divePrimary.opacity(0.2))
                                                .foregroundColor(.divePrimary)
                                                .cornerRadius(4)
                                        } else {
                                            Text(localizationService.localizedString("nonDiving", table: "trips"))
                                                .font(.caption)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(Color.gray.opacity(0.2))
                                                .foregroundColor(.secondary)
                                                .cornerRadius(4)
                                        }
                                    }
                                    .padding(.vertical, 4)
                                }
                            }
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle(localizationService.localizedString("tripDetails", table: "trips"))
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("book", table: "trips")) {
                        showBooking = true
                    }
                    .disabled(trip.isFullyBooked)
                }
            }
            .task {
                await viewModel.loadHotels()
                await viewModel.loadYachts()
                // Ensure countries are loaded for localization
                await countryHelper.loadCountries()
            }
            .onChange(of: localizationService.currentLanguage) { oldValue, newValue in
                // Reload countries when language changes to update localized names
                Task {
                    await countryHelper.loadCountries()
                }
            }
            .id(localizationService.currentLanguage) // Force view update when language changes
        }
    }
}

struct InfoRow: View {
    let icon: String
    let text: String
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(.blue)
                .frame(width: 20)
            Text(text)
                .font(.subheadline)
        }
    }
}
