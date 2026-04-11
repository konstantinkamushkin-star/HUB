//
//  ItemDetailView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct ItemDetailView: View {
    let item: GearItem
    @ObservedObject var viewModel: InventoryViewModel
    @State private var selectedTab: DetailTab = .overview
    @State private var showEdit = false
    @State private var showCheckout = false
    @State private var showInspection = false
    @Environment(\.dismiss) var dismiss
    
    enum DetailTab: String, CaseIterable {
        case overview = "Overview"
        case history = "History"
        case maintenance = "Maintenance"
        case rentals = "Rentals"
        case documents = "Documents"
        case inspections = "Inspections"
        case related = "Related"
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Header with photo carousel
                headerSection
                
                // Tab selector
                tabSelector
                
                // Tab content
                tabContent
            }
            .navigationTitle(item.displayName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button(action: { showCheckout = true }) {
                            Label("Check Out", systemImage: "arrow.up.circle")
                        }
                        
                        Button(action: { showInspection = true }) {
                            Label("Inspection", systemImage: "checkmark.shield")
                        }
                        
                        Button(action: { showEdit = true }) {
                            Label("Edit", systemImage: "pencil")
                        }
                        
                        Divider()
                        
                        Button(role: .destructive, action: {
                            Task {
                                try? await viewModel.deleteGearItem(item.id)
                                dismiss()
                            }
                        }) {
                            Label("Delete", systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .sheet(isPresented: $showEdit) {
                AddEditItemView(item: item, viewModel: viewModel)
            }
            .sheet(isPresented: $showCheckout) {
                CheckoutView(items: [item], viewModel: viewModel)
            }
            .sheet(isPresented: $showInspection) {
                InspectionView(item: item, viewModel: viewModel)
            }
        }
    }
    
    // MARK: - Header Section
    private var headerSection: some View {
        VStack(spacing: 0) {
            // Photo carousel
            if !item.photos.isEmpty {
                TabView {
                    ForEach(item.photos, id: \.self) { photoUrl in
                        AsyncImage(url: URL(string: photoUrl)) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Color.gray.opacity(0.3)
                        }
                    }
                }
                .tabViewStyle(.page)
                .frame(height: 250)
            } else {
                Color.gray.opacity(0.3)
                    .frame(height: 250)
                    .overlay(
                        Image(systemName: "photo")
                            .font(.system(size: 50))
                            .foregroundColor(.secondary)
                    )
            }
            
            // Status and condition badges
            HStack {
                StatusBadge(status: item.status)
                ConditionBadge(condition: item.condition)
                
                if item.isInspectionOverdue {
                    Label("Inspection Overdue", systemImage: "exclamationmark.triangle.fill")
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.red.opacity(0.1))
                        .cornerRadius(8)
                }
                
                Spacer()
            }
            .padding()
            .background(Color(.systemBackground))
        }
    }
    
    // MARK: - Tab Selector
    private var tabSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(DetailTab.allCases, id: \.self) { tab in
                    Button(action: { selectedTab = tab }) {
                        VStack(spacing: 4) {
                            Text(tab.rawValue)
                                .font(.subheadline)
                                .fontWeight(selectedTab == tab ? .semibold : .regular)
                            
                            Rectangle()
                                .fill(selectedTab == tab ? Color.blue : Color.clear)
                                .frame(height: 2)
                        }
                        .foregroundColor(selectedTab == tab ? .blue : .secondary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                    }
                }
            }
        }
        .background(Color(.systemGray6))
    }
    
    // MARK: - Tab Content
    @ViewBuilder
    private var tabContent: some View {
        ScrollView {
            switch selectedTab {
            case .overview:
                overviewTab
            case .history:
                historyTab
            case .maintenance:
                maintenanceTab
            case .rentals:
                rentalsTab
            case .documents:
                documentsTab
            case .inspections:
                inspectionsTab
            case .related:
                relatedTab
            }
        }
    }
    
    // MARK: - Overview Tab
    private var overviewTab: some View {
        VStack(alignment: .leading, spacing: 20) {
            // Basic Information
            SectionView(title: "Basic Information") {
                ItemInfoRow(label: "Category", value: item.category.displayName)
                ItemInfoRow(label: "Manufacturer", value: item.manufacturer ?? "N/A")
                ItemInfoRow(label: "Model", value: item.model ?? "N/A")
                if let size = item.size {
                    ItemInfoRow(label: "Size", value: size)
                }
                ItemInfoRow(label: "Serial Number", value: item.serialNumber ?? "N/A")
                if let barcode = item.barcode {
                    ItemInfoRow(label: "Barcode", value: barcode)
                }
                if let qrCode = item.qrCode {
                    ItemInfoRow(label: "QR Code", value: qrCode)
                }
            }
            
            // Location & Responsibility
            SectionView(title: "Location & Responsibility") {
                ItemInfoRow(label: "Location", value: item.locationName ?? "Not assigned")
                ItemInfoRow(label: "Responsible", value: item.responsibleUserName ?? "Not assigned")
            }
            
            // Purchase Information
            if item.purchaseDate != nil || item.supplier != nil {
                SectionView(title: "Purchase Information") {
                    if let purchaseDate = item.purchaseDate {
                        ItemInfoRow(label: "Purchase Date", value: formatDate(purchaseDate))
                    }
                    ItemInfoRow(label: "Supplier", value: item.supplier ?? "N/A")
                    if let price = item.purchasePrice {
                        ItemInfoRow(label: "Purchase Price", value: String(format: "%.2f", price))
                    }
                    if let warranty = item.warrantyExpiresAt {
                        ItemInfoRow(label: "Warranty Expires", value: formatDate(warranty))
                    }
                }
            }
            
            // Technical Details
            if item.productionYear != nil || item.maxPressure != nil || item.material != nil {
                SectionView(title: "Technical Details") {
                    if let year = item.productionYear {
                        ItemInfoRow(label: "Production Year", value: "\(year)")
                    }
                    if let pressure = item.maxPressure {
                        ItemInfoRow(label: "Max Pressure", value: "\(pressure) bar")
                    }
                    ItemInfoRow(label: "Material", value: item.material ?? "N/A")
                }
            }
            
            // Inspection Schedule
            SectionView(title: "Inspection Schedule") {
                if let lastInspection = item.lastInspectionDate {
                    ItemInfoRow(label: "Last Inspection", value: formatDate(lastInspection))
                } else {
                    ItemInfoRow(label: "Last Inspection", value: "Never")
                }
                
                if let nextInspection = item.nextInspectionDate {
                    HStack {
                        Text("Next Inspection")
                            .foregroundColor(.secondary)
                        Spacer()
                        VStack(alignment: .trailing, spacing: 4) {
                            Text(formatDate(nextInspection))
                            if item.isInspectionOverdue {
                                Text("OVERDUE")
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .foregroundColor(.red)
                            } else if let days = item.daysUntilInspection {
                                Text("\(days) days")
                                    .font(.caption)
                                    .foregroundColor(days <= 30 ? .orange : .secondary)
                            }
                        }
                    }
                } else {
                    ItemInfoRow(label: "Next Inspection", value: "Not scheduled")
                }
                
                if let interval = item.inspectionIntervalDays {
                    ItemInfoRow(label: "Inspection Interval", value: "\(interval) days")
                }
            }
            
            // Notes
            if let notes = item.notes, !notes.isEmpty {
                SectionView(title: "Notes") {
                    Text(notes)
                        .font(.body)
                }
            }
            
            // Tags
            if !item.tags.isEmpty {
                SectionView(title: "Tags") {
                    FlowLayout(items: item.tags) { tag in
                        Text(tag)
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.blue.opacity(0.1))
                            .foregroundColor(.blue)
                            .cornerRadius(8)
                    }
                }
            }
        }
        .padding()
    }
    
    // MARK: - History Tab
    private var historyTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            let itemLogs = viewModel.eventLogs.filter { $0.gearItemId == item.id }
            
            if itemLogs.isEmpty {
                Text("No history available")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            } else {
                ForEach(itemLogs.sorted(by: { $0.createdAt > $1.createdAt })) { log in
                    HistoryRow(log: log)
                }
            }
        }
        .padding()
    }
    
    // MARK: - Maintenance Tab
    private var maintenanceTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            let itemTickets = viewModel.maintenanceTickets.filter { $0.gearItemId == item.id }
            
            if itemTickets.isEmpty {
                VStack(spacing: 16) {
                    Text("No maintenance tickets")
                        .foregroundColor(.secondary)
                    
                    Button(action: {}) {
                        Label("Create Maintenance Ticket", systemImage: "wrench.and.screwdriver")
                            .padding()
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding()
            } else {
                ForEach(itemTickets) { ticket in
                    MaintenanceTicketRow(ticket: ticket)
                }
            }
        }
        .padding()
    }
    
    // MARK: - Rentals Tab
    private var rentalsTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            let itemCheckouts = viewModel.checkouts.filter { $0.gearItemIds.contains(item.id) }
            
            if itemCheckouts.isEmpty {
                Text("No rental history")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            } else {
                ForEach(itemCheckouts.sorted(by: { $0.createdAt > $1.createdAt })) { checkout in
                    CheckoutRow(checkout: checkout)
                }
            }
        }
        .padding()
    }
    
    // MARK: - Documents Tab
    private var documentsTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            if item.documents.isEmpty {
                Text("No documents")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            } else {
                ForEach(item.documents) { document in
                    DocumentRow(document: document)
                }
            }
        }
        .padding()
    }
    
    // MARK: - Inspections Tab
    private var inspectionsTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            let itemInspections = viewModel.inspections.filter { $0.gearItemId == item.id }
            
            if itemInspections.isEmpty {
                VStack(spacing: 16) {
                    Text("No inspections yet")
                        .foregroundColor(.secondary)
                    
                    Button(action: { showInspection = true }) {
                        Label("Start Inspection", systemImage: "checkmark.shield")
                            .padding()
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding()
            } else {
                ForEach(itemInspections.sorted(by: { $0.date > $1.date })) { inspection in
                    InspectionRow(inspection: inspection)
                }
            }
        }
        .padding()
    }
    
    // MARK: - Related Tab
    private var relatedTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            let relatedItems = viewModel.gearItems.filter { item.relatedItemIds.contains($0.id) }
            
            if relatedItems.isEmpty {
                Text("No related items")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            } else {
                ForEach(relatedItems) { relatedItem in
                    NavigationLink(destination: ItemDetailView(item: relatedItem, viewModel: viewModel)) {
                        InventoryListItemRow(
                            item: relatedItem,
                            isSelected: false,
                            onSelect: {},
                            onTap: {}
                        )
                    }
                }
            }
        }
        .padding()
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

// MARK: - Supporting Views
struct SectionView<Content: View>: View {
    let title: String
    let content: Content
    
    init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundColor(.primary)
            
            VStack(alignment: .leading, spacing: 8) {
                content
            }
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(10)
        }
    }
}

struct ItemInfoRow: View {
    let label: String
    let value: String
    
    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.medium)
        }
    }
}

struct HistoryRow: View {
    let log: EventLog
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Circle()
                .fill(Color.blue)
                .frame(width: 8, height: 8)
                .padding(.top, 6)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(log.title)
                    .font(.headline)
                
                if let description = log.description {
                    Text(description)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                
                HStack {
                    if let userName = log.userName {
                        Text(userName)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    
                    Text(formatDate(log.createdAt))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

struct MaintenanceTicketRow: View {
    let ticket: MaintenanceTicket
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(ticket.title)
                    .font(.headline)
                Spacer()
                Text(ticket.status.displayName)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(statusColor.opacity(0.2))
                    .foregroundColor(statusColor)
                    .cornerRadius(8)
            }
            
            Text(ticket.description)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .lineLimit(2)
            
            HStack {
                Label(ticket.priority.displayName, systemImage: "exclamationmark.circle")
                    .font(.caption)
                Spacer()
                Text(formatDate(ticket.openedAt))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
    
    private var statusColor: Color {
        switch ticket.status {
        case .open: return .blue
        case .inProgress: return .orange
        case .awaitingParts: return .yellow
        case .completed, .closed: return .green
        case .cancelled: return .gray
        }
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        return formatter.string(from: date)
    }
}

struct CheckoutRow: View {
    let checkout: Checkout
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(checkout.issuedToName ?? "Unknown")
                    .font(.headline)
                Spacer()
                Text(checkout.status.displayName)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(statusColor.opacity(0.2))
                    .foregroundColor(statusColor)
                    .cornerRadius(8)
            }
            
            HStack {
                Text("Due: \(formatDate(checkout.dueDate))")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                
                if checkout.isOverdue {
                    Text("OVERDUE")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.red)
                }
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
    
    private var statusColor: Color {
        switch checkout.status {
        case .open: return checkout.isOverdue ? .red : .blue
        case .returned: return .green
        case .overdue: return .red
        case .lost: return .red
        case .cancelled: return .gray
        }
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        return formatter.string(from: date)
    }
}

struct DocumentRow: View {
    let document: GearItem.Document
    
    var body: some View {
        HStack {
            Image(systemName: "doc.fill")
                .foregroundColor(.blue)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(document.name)
                    .font(.headline)
                
                Text(document.type.rawValue.capitalized)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .foregroundColor(.secondary)
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
}

struct InspectionRow: View {
    let inspection: Inspection
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(formatDate(inspection.date))
                    .font(.headline)
                Spacer()
                Text(inspection.result.rawValue.capitalized)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(resultColor.opacity(0.2))
                    .foregroundColor(resultColor)
                    .cornerRadius(8)
            }
            
            if let performedBy = inspection.performedByName {
                Text("By: \(performedBy)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            if let notes = inspection.notes {
                Text(notes)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
    
    private var resultColor: Color {
        switch inspection.result {
        case .passed: return .green
        case .failed: return .red
        case .conditional: return .orange
        }
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        return formatter.string(from: date)
    }
}

// MARK: - Flow Layout
struct FlowLayout: View {
    let items: [String]
    let content: (String) -> AnyView
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            GeometryReader { geometry in
                self.generateContent(in: geometry)
            }
        }
    }
    
    private func generateContent(in geometry: GeometryProxy) -> some View {
        var width = CGFloat.zero
        var height = CGFloat.zero
        
        return ZStack(alignment: .topLeading) {
            ForEach(items, id: \.self) { item in
                content(item)
                    .padding(.trailing, 4)
                    .padding(.bottom, 4)
                    .alignmentGuide(.leading, computeValue: { d in
                        if abs(width - d.width) > geometry.size.width {
                            width = 0
                            height -= d.height
                        }
                        let result = width
                        if item == items.last {
                            width = 0
                        } else {
                            width -= d.width
                        }
                        return result
                    })
                    .alignmentGuide(.top, computeValue: { _ in
                        let result = height
                        if item == items.last {
                            height = 0
                        }
                        return result
                    })
            }
        }
    }
}

extension FlowLayout {
    init(items: [String], @ViewBuilder content: @escaping (String) -> some View) {
        self.items = items
        self.content = { AnyView(content($0)) }
    }
}

#Preview {
    let viewModel = InventoryViewModel()
    let sampleItem = GearItem(
        id: "1",
        diveCenterId: "dc1",
        name: "Regulator Set",
        category: .regulator,
        manufacturer: "Scubapro",
        model: "MK25",
        status: .available,
        condition: .good
    )
    return ItemDetailView(item: sampleItem, viewModel: viewModel)
}
