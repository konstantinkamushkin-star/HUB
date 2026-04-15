//
//  FishSpeciesPickerView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct FishSpeciesPickerView: View {
    @Binding var selectedSpecies: [String]
    @Environment(\.dismiss) var dismiss
    @State private var searchText = ""
    
    // Common fish species list
    private let allFishSpecies = [
        "Clownfish", "Angelfish", "Butterflyfish", "Parrotfish", "Triggerfish",
        "Surgeonfish", "Wrasse", "Grouper", "Snapper", "Barracuda",
        "Shark", "Ray", "Turtle", "Moray Eel", "Lionfish",
        "Pufferfish", "Seahorse", "Octopus", "Squid", "Cuttlefish",
        "Lobster", "Crab", "Shrimp", "Nudibranch", "Sea Star",
        "Sea Urchin", "Jellyfish", "Manta Ray", "Whale Shark", "Dolphin",
        "Tuna", "Mackerel", "Jackfish", "Trevally", "Emperor",
        "Sweetlips", "Goatfish", "Squirrelfish", "Cardinalfish", "Damselfish"
    ]
    
    private var filteredSpecies: [String] {
        if searchText.isEmpty {
            return allFishSpecies
        }
        return allFishSpecies.filter { $0.localizedCaseInsensitiveContains(searchText) }
    }
    
    var body: some View {
        NavigationView {
            List {
                ForEach(filteredSpecies, id: \.self) { species in
                    Button(action: {
                        if selectedSpecies.contains(species) {
                            selectedSpecies.removeAll { $0 == species }
                        } else {
                            selectedSpecies.append(species)
                        }
                    }) {
                        HStack {
                            Text(species)
                                .foregroundColor(.primary)
                            Spacer()
                            if selectedSpecies.contains(species) {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.divePrimary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("ui_logbook_select_fish_species".localized)
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $searchText, prompt: "Search fish species")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("ui_feed_done".localized) {
                        dismiss()
                    }
                }
            }
        }
    }
}
