//
//  FilterView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct FilterView: View {
    @Binding var filters: DiveSiteFilters
    let diveSites: [DiveSite]
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var countryHelper = CountryLocalizationHelper.shared
    
    // Extract available options from dive sites
    private var availableSiteTypes: [DiveSiteType] {
        Array(Set(diveSites.map { $0.siteType }))
            .sorted { $0.rawValue < $1.rawValue }
    }
    
    private var availableDifficultyLevels: [DifficultyLevel] {
        Array(Set(diveSites.map { $0.difficulty }))
            .sorted { $0.rawValue < $1.rawValue }
    }
    
    private var availableCountries: [String] {
        let sitesCountries = Set(diveSites.map { $0.country }.filter { !$0.isEmpty })
        if countryHelper.countryNames.isEmpty {
            return sitesCountries.sorted()
        }
        // Show only countries that exist in loaded sites, ordered by backend list
        return countryHelper.countryNames.filter { sitesCountries.contains($0) }
    }
    
    var body: some View {
        NavigationView {
            Form {
                Section(localizationService.localizedString("siteType", table: "explore")) {
                    if availableSiteTypes.isEmpty {
                        Text(localizationService.localizedString("noOptionsAvailable", table: "common"))
                            .foregroundColor(.secondary)
                    } else {
                    Picker(localizationService.localizedString("type"), selection: Binding<DiveSiteType?>(
                        get: { filters.siteType },
                        set: { filters.siteType = $0 }
                    )) {
                        Text(localizationService.localizedString("all")).tag(DiveSiteType?.none)
                            ForEach(availableSiteTypes, id: \.self) { type in
                            Text(type.displayName).tag(DiveSiteType?.some(type))
                            }
                        }
                    }
                }
                
                Section(localizationService.localizedString("difficulty", table: "explore")) {
                    if availableDifficultyLevels.isEmpty {
                        Text(localizationService.localizedString("noOptionsAvailable", table: "common"))
                            .foregroundColor(.secondary)
                    } else {
                    Picker(localizationService.localizedString("difficulty", table: "explore"), selection: Binding<DifficultyLevel?>(
                        get: { filters.difficulty },
                        set: { filters.difficulty = $0 }
                    )) {
                        Text(localizationService.localizedString("all")).tag(DifficultyLevel?.none)
                            ForEach(availableDifficultyLevels, id: \.self) { level in
                            Text(level.displayName).tag(DifficultyLevel?.some(level))
                            }
                        }
                    }
                }
                
                Section(localizationService.localizedString("country", table: "explore")) {
                    if availableCountries.isEmpty {
                        Text(localizationService.localizedString("noOptionsAvailable", table: "common"))
                            .foregroundColor(.secondary)
                    } else {
                        Picker(localizationService.localizedString("country", table: "explore"), selection: Binding<String?>(
                            get: { filters.country },
                            set: { filters.country = $0 }
                        )) {
                            Text(localizationService.localizedString("all")).tag(String?.none)
                            ForEach(availableCountries, id: \.self) { country in
                                Text(countryHelper.getLocalizedCountryName(country)).tag(String?.some(country))
                            }
                        }
                    }
                }
                
                Section(localizationService.localizedString("depth", table: "explore")) {
                    HStack {
                        Text(localizationService.localizedString("minDepth", table: "explore"))
                        Spacer()
                        TextField("0", value: $filters.minDepth, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 80)
                        Text(localizationService.localizedString("meters", table: "explore"))
                    }
                    
                    HStack {
                        Text(localizationService.localizedString("maxDepth", table: "explore"))
                        Spacer()
                        TextField("ui_map_infinity_symbol".localized, value: $filters.maxDepth, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 80)
                        Text(localizationService.localizedString("meters", table: "explore"))
                    }
                }
                
                Section(localizationService.localizedString("rating", table: "explore")) {
                    HStack {
                        Text(localizationService.localizedString("minimumRating", table: "explore"))
                        Spacer()
                        TextField("0", value: $filters.minRating, format: .number)
                            .keyboardType(.decimalPad)
                            .frame(width: 80)
                    }
                }
            }
            .onAppear {
                countryHelper.ensureLoaded()
            }
            .navigationTitle(localizationService.localizedString("filters", table: "explore"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("reset")) {
                        filters = DiveSiteFilters()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("apply")) {
                        dismiss()
                    }
                }
            }
        }
    }
}

#Preview {
    FilterView(filters: .constant(DiveSiteFilters()), diveSites: [])
}
