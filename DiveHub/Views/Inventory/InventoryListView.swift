//
//  InventoryListView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct InventoryListView: View {
    @StateObject private var viewModel = InventoryViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var viewMode: ViewMode = .list
    @State private var showFilters = false
    @State private var showAddItem = false
    @State private var showBulkActions = false
    @State private var selectedItem: GearItem?

    private func inv(_ key: String) -> String {
        localizationService.localizedString(key, table: "inventory")
    }
    
    enum ViewMode: String, CaseIterable {
        case list = "list"
        case grouped = "grouped"
        case cards = "cards"
        
        var icon: String {
            switch self {
            case .list: return "list.bullet"
            case .grouped: return "square.grid.2x2"
            case .cards: return "rectangle.grid.1x2"
            }
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Search and View Mode
            searchAndToolbar
            
            // Content
            if viewModel.isLoading && viewModel.gearItems.isEmpty {
                loadingView
            } else if viewModel.filteredItems.isEmpty {
                emptyStateView
            } else {
                contentView
            }
        }
        .navigationTitle(inv("inventoryTitle"))
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                if !viewModel.selectedItems.isEmpty {
                    Button(inv("actions")) {
                        showBulkActions = true
                    }
                }
                
                Menu {
                    Button(action: { showFilters.toggle() }) {
                        Label(inv("filters"), systemImage: "line.3.horizontal.decrease.circle")
                    }
                    
                    Picker(inv("viewMode"), selection: $viewMode) {
                        ForEach(ViewMode.allCases, id: \.self) { mode in
                            Label(mode.rawValue.capitalized, systemImage: mode.icon)
                                .tag(mode)
                        }
                    }
                    
                    Button(action: { showAddItem = true }) {
                        Label(inv("addItem"), systemImage: "plus")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .sheet(isPresented: $showFilters) {
            FiltersView(viewModel: viewModel)
        }
        .sheet(isPresented: $showAddItem) {
            AddEditItemView(item: nil, viewModel: viewModel)
        }
        .sheet(item: $selectedItem) { item in
            ItemDetailView(item: item, viewModel: viewModel)
        }
        .actionSheet(isPresented: $showBulkActions) {
            ActionSheet(
                title: Text(inv("bulkActions")),
                message: Text("\(viewModel.selectedItems.count) \(inv("itemsSelected"))"),
                buttons: [
                    .default(Text(inv("changeLocation"))) {
                        // Show location picker
                    },
                    .default(Text(inv("changeStatus"))) {
                        // Show status picker
                    },
                    .default(Text(inv("export"))) {
                        // Export selected items
                    },
                    .cancel(Text(inv("cancel"))),
                    .destructive(Text(inv("deselectAll"))) {
                        viewModel.deselectAll()
                    }
                ]
            )
        }
        .task {
            await viewModel.loadAllData()
        }
    }
    
    // MARK: - Search and Toolbar
    private var searchAndToolbar: some View {
        VStack(spacing: 12) {
            // Search Bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.secondary)
                
                TextField(inv("searchPlaceholder"), text: $viewModel.searchText)
                    .textFieldStyle(.plain)
                
                if !viewModel.searchText.isEmpty {
                    Button(action: { viewModel.searchText = "" }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(10)
            
            // Active Filters
            if hasActiveFilters {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        if let category = viewModel.selectedCategory {
                            FilterChip(title: category.displayName) {
                                viewModel.selectedCategory = nil
                            }
                        }
                        
                        if let status = viewModel.selectedStatus {
                            FilterChip(title: status.displayName) {
                                viewModel.selectedStatus = nil
                            }
                        }
                        
                        if let condition = viewModel.selectedCondition {
                            FilterChip(title: condition.displayName) {
                                viewModel.selectedCondition = nil
                            }
                        }
                        
                        if let locationId = viewModel.selectedLocationId,
                           let location = viewModel.locations.first(where: { $0.id == locationId }) {
                            FilterChip(title: location.name) {
                                viewModel.selectedLocationId = nil
                            }
                        }
                    }
                    .padding(.horizontal)
                }
            }
        }
        .padding()
        .background(Color(.systemBackground))
    }
    
    private var hasActiveFilters: Bool {
        viewModel.selectedCategory != nil ||
        viewModel.selectedStatus != nil ||
        viewModel.selectedCondition != nil ||
        viewModel.selectedLocationId != nil
    }
    
    // MARK: - Content View
    @ViewBuilder
    private var contentView: some View {
        switch viewMode {
        case .list:
            listView
        case .grouped:
            groupedView
        case .cards:
            cardsView
        }
    }
    
    // MARK: - List View
    private var listView: some View {
        List {
            ForEach(viewModel.filteredItems) { item in
                InventoryListItemRow(
                    item: item,
                    isSelected: viewModel.selectedItems.contains(item.id),
                    onSelect: {
                        viewModel.toggleSelection(item.id)
                    },
                    onTap: {
                        selectedItem = item
                    }
                )
            }
        }
        .listStyle(.plain)
    }
    
    // MARK: - Grouped View
    private var groupedView: some View {
        List {
            ForEach(Array(viewModel.groupedByCategory.keys.sorted(by: { $0.rawValue < $1.rawValue })), id: \.self) { category in
                Section(header: Text(category.displayName)) {
                    ForEach(viewModel.groupedByCategory[category] ?? []) { item in
                        InventoryListItemRow(
                            item: item,
                            isSelected: viewModel.selectedItems.contains(item.id),
                            onSelect: {
                                viewModel.toggleSelection(item.id)
                            },
                            onTap: {
                                selectedItem = item
                            }
                        )
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
    
    // MARK: - Cards View
    private var cardsView: some View {
        ScrollView {
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 16) {
                ForEach(viewModel.filteredItems) { item in
                    InventoryCardView(
                        item: item,
                        isSelected: viewModel.selectedItems.contains(item.id),
                        onSelect: {
                            viewModel.toggleSelection(item.id)
                        },
                        onTap: {
                            selectedItem = item
                        }
                    )
                }
            }
            .padding()
        }
    }
    
    // MARK: - Loading View
    private var loadingView: some View {
        VStack(spacing: 16) {
            ProgressView()
            Text(inv("loadingEquipment"))
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    // MARK: - Empty State
    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Image(systemName: "tray")
                .font(.system(size: 60))
                .foregroundColor(.secondary)
            
            Text(hasActiveFilters ? inv("noItemsMatchFilters") : inv("noEquipmentYet"))
                .font(.title2)
                .fontWeight(.semibold)
            
            Text(hasActiveFilters ? inv("tryAdjustingFilters") : inv("addFirstEquipment"))
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            if !hasActiveFilters {
                Button(action: { showAddItem = true }) {
                    Label(inv("addEquipment"), systemImage: "plus.circle.fill")
                        .font(.headline)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Inventory List Item Row
struct InventoryListItemRow: View {
    let item: GearItem
    let isSelected: Bool
    let onSelect: () -> Void
    let onTap: () -> Void

    @StateObject private var localizationService = LocalizationService.shared

    private func inv(_ key: String) -> String {
        localizationService.localizedString(key, table: "inventory")
    }
    
    var body: some View {
        HStack(spacing: 12) {
            // Selection indicator
            Button(action: onSelect) {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(isSelected ? .blue : .secondary)
            }
            .buttonStyle(.plain)
            
            // Thumbnail
            if let firstPhoto = item.photos.first, !firstPhoto.isEmpty {
                AsyncImage(url: URL(string: firstPhoto)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.gray.opacity(0.3)
                }
                .frame(width: 60, height: 60)
                .cornerRadius(8)
            } else {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: 60, height: 60)
                    .overlay(
                        Image(systemName: "photo")
                            .foregroundColor(.secondary)
                    )
            }
            
            // Item Info
            VStack(alignment: .leading, spacing: 4) {
                Text(item.displayName)
                    .font(.headline)
                    .lineLimit(1)
                
                HStack(spacing: 8) {
                    // Status badge
                    StatusBadge(status: item.status)
                    
                    // Condition badge
                    if item.condition != .good {
                        ConditionBadge(condition: item.condition)
                    }
                }
                
                HStack(spacing: 12) {
                    if let location = item.locationName {
                        Label(location, systemImage: "location")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    
                    if let lastInspection = item.lastInspectionDate {
                        Label(formatDate(lastInspection), systemImage: "calendar")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            
            Spacer()
            
            // Quick actions
            Menu {
                Button(action: {}) {
                    Label(inv("checkOut"), systemImage: "arrow.up.circle")
                }
                
                Button(action: {}) {
                    Label(inv("inspection"), systemImage: "checkmark.shield")
                }
                
                Button(action: onTap) {
                    Label(inv("details"), systemImage: "info.circle")
                }
                
                Divider()
                
                Button(action: {}) {
                    Label(inv("edit"), systemImage: "pencil")
                }
                
                Button(role: .destructive, action: {}) {
                    Label(inv("delete"), systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .onTapGesture {
            onTap()
        }
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        return formatter.string(from: date)
    }
}

// MARK: - Inventory Card View
struct InventoryCardView: View {
    let item: GearItem
    let isSelected: Bool
    let onSelect: () -> Void
    let onTap: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Image
            ZStack(alignment: .topTrailing) {
                if let firstPhoto = item.photos.first, !firstPhoto.isEmpty {
                    AsyncImage(url: URL(string: firstPhoto)) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.gray.opacity(0.3)
                    }
                    .frame(height: 120)
                    .clipped()
                } else {
                    Color.gray.opacity(0.3)
                        .frame(height: 120)
                        .overlay(
                            Image(systemName: "photo")
                                .foregroundColor(.secondary)
                        )
                }
                
                Button(action: onSelect) {
                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                        .foregroundColor(isSelected ? .blue : .white)
                        .background(Circle().fill(Color.black.opacity(0.3)))
                }
                .padding(8)
            }
            
            // Info
            VStack(alignment: .leading, spacing: 4) {
                Text(item.displayName)
                    .font(.headline)
                    .lineLimit(2)
                
                HStack {
                    StatusBadge(status: item.status)
                    if item.condition != .good {
                        ConditionBadge(condition: item.condition)
                    }
                }
                
                if let location = item.locationName {
                    Text(location)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .padding(.horizontal, 8)
            .padding(.bottom, 8)
        }
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.1), radius: 5, x: 0, y: 2)
        .onTapGesture {
            onTap()
        }
    }
}

// MARK: - Status Badge
struct StatusBadge: View {
    let status: GearItem.GearStatus
    
    var body: some View {
        Text(status.displayName)
            .font(.caption2)
            .fontWeight(.semibold)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(statusColor.opacity(0.2))
            .foregroundColor(statusColor)
            .cornerRadius(4)
    }
    
    private var statusColor: Color {
        switch status {
        case .available: return .green
        case .issued: return .blue
        case .maintenance: return .orange
        case .lost: return .red
        case .retired: return .gray
        case .scrapped: return .black
        }
    }
}

// MARK: - Condition Badge
struct ConditionBadge: View {
    let condition: GearItem.GearCondition
    
    var body: some View {
        Text(condition.displayName)
            .font(.caption2)
            .fontWeight(.semibold)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(conditionColor.opacity(0.2))
            .foregroundColor(conditionColor)
            .cornerRadius(4)
    }
    
    private var conditionColor: Color {
        switch condition {
        case .new: return .green
        case .good: return .blue
        case .needsService: return .orange
        case .damaged: return .red
        case .lost: return .red
        case .retired: return .gray
        }
    }
}

// MARK: - Filter Chip
struct FilterChip: View {
    let title: String
    let onRemove: () -> Void
    
    var body: some View {
        HStack(spacing: 4) {
            Text(title)
                .font(.caption)
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.caption2)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.blue.opacity(0.1))
        .foregroundColor(.blue)
        .cornerRadius(8)
    }
}

// MARK: - Filters View
struct FiltersView: View {
    @ObservedObject var viewModel: InventoryViewModel
    @Environment(\.dismiss) var dismiss

    @StateObject private var localizationService = LocalizationService.shared

    private func inv(_ key: String) -> String {
        localizationService.localizedString(key, table: "inventory")
    }
    
    var body: some View {
        NavigationView {
            Form {
                Section(inv("category")) {
                    Picker(inv("category"), selection: $viewModel.selectedCategory) {
                        Text(inv("all")).tag(nil as GearItem.GearCategory?)
                        ForEach(GearItem.GearCategory.allCases, id: \.self) { category in
                            Text(category.displayName).tag(category as GearItem.GearCategory?)
                        }
                    }
                }
                
                Section(inv("status")) {
                    Picker(inv("status"), selection: $viewModel.selectedStatus) {
                        Text(inv("all")).tag(nil as GearItem.GearStatus?)
                        ForEach(GearItem.GearStatus.allCases, id: \.self) { status in
                            Text(status.displayName).tag(status as GearItem.GearStatus?)
                        }
                    }
                }
                
                Section(inv("condition")) {
                    Picker(inv("condition"), selection: $viewModel.selectedCondition) {
                        Text(inv("all")).tag(nil as GearItem.GearCondition?)
                        ForEach(GearItem.GearCondition.allCases, id: \.self) { condition in
                            Text(condition.displayName).tag(condition as GearItem.GearCondition?)
                        }
                    }
                }
                
                Section(inv("location")) {
                    Picker(inv("location"), selection: $viewModel.selectedLocationId) {
                        Text(inv("allLocations")).tag(nil as String?)
                        ForEach(viewModel.locations.filter { $0.isActive }) { location in
                            Text(location.name).tag(location.id as String?)
                        }
                    }
                }
                
                Section(inv("sortBy")) {
                    Picker(inv("sort"), selection: $viewModel.sortOption) {
                        ForEach(InventoryViewModel.SortOption.allCases, id: \.self) { option in
                            Text(option.displayName).tag(option)
                        }
                    }
                }
                
                Section {
                    Button(inv("clearAllFilters"), role: .destructive) {
                        viewModel.selectedCategory = nil
                        viewModel.selectedStatus = nil
                        viewModel.selectedCondition = nil
                        viewModel.selectedLocationId = nil
                    }
                }
            }
            .navigationTitle(inv("filters"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(inv("done")) {
                        dismiss()
                    }
                }
            }
        }
    }
}

#Preview {
    NavigationView {
        InventoryListView()
    }
}
