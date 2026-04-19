//
//  GearManagementView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct GearManagementView: View {
    @StateObject private var viewModel = AdminViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedStatus: String = "all"
    @State private var showAddGear = false
    
    var filteredGear: [GearItem] {
        if selectedStatus == "all" {
            return viewModel.gearItems
        }
        if let status = GearItem.GearStatus(rawValue: selectedStatus) {
            return viewModel.gearItems.filter { $0.status == status }
        }
        return viewModel.gearItems
    }
    
    var body: some View {
        VStack(spacing: 0) {
            List {
                // Filter Picker
                Section {
                    Picker(localizationService.localizedString("filterByStatus", table: "admin"), selection: $selectedStatus) {
                        Text(localizationService.localizedString("all", table: "common")).tag("all")
                        ForEach([GearItem.GearStatus.available, .issued, .maintenance, .scrapped], id: \.self) { status in
                            Text(status.rawValue.capitalized).tag(status.rawValue)
                        }
                    }
                }

                // Gear List
                Section {
                    ForEach(filteredGear) { gear in
                        GearManagementRow(gear: gear, viewModel: viewModel)
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("gear", table: "admin"))
        .diveHubNavigationChrome()
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showAddGear = true }) {
                    Image(systemName: "plus.circle.fill")
                        .font(.title3)
                }
            }
        }
        .sheet(isPresented: $showAddGear) {
            // TODO: Add gear creation view
            Text("ui_admin_add_gear_form".localized)
        }
        .task {
            await viewModel.loadGear()
        }
    }
}

struct GearManagementRow: View {
    let gear: GearItem
    @ObservedObject var viewModel: AdminViewModel
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(gear.name)
                    .font(.headline)
                Text(gear.category.rawValue.capitalized)
                    .font(.caption)
                    .foregroundColor(.secondary)
                if let manufacturer = gear.manufacturer {
                    Text(manufacturer)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            Spacer()
            VStack(alignment: .trailing) {
                Text(gear.status.rawValue.capitalized)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(statusColor(for: gear.status))
                    .foregroundColor(.white)
                    .cornerRadius(8)
                Menu {
                    ForEach([GearItem.GearStatus.available, .issued, .maintenance, .scrapped], id: \.self) { status in
                        Button(status.rawValue.capitalized) {
                            Task {
                                try? await viewModel.updateGearStatus(gear.id, status: status)
                            }
                        }
                    }
                } label: {
                    Text(LocalizationService.shared.localizedString("changeStatus", table: "admin"))
                        .font(.caption)
                }
            }
        }
    }
    
    private func statusColor(for status: GearItem.GearStatus) -> Color {
        switch status {
        case .available: return .green
        case .issued: return .blue
        case .maintenance: return .orange
        case .lost: return .purple
        case .retired: return .gray
        case .scrapped: return .red
        }
    }
}

#Preview {
    GearManagementView()
}
