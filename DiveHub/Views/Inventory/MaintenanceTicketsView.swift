//
//  MaintenanceTicketsView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct MaintenanceTicketsView: View {
    @StateObject private var viewModel = InventoryViewModel()
    @State private var selectedStatus: MaintenanceTicket.TicketStatus?
    @State private var showCreateTicket = false
    
    var filteredTickets: [MaintenanceTicket] {
        var tickets = viewModel.maintenanceTickets
        
        if let status = selectedStatus {
            tickets = tickets.filter { $0.status == status }
        }
        
        return tickets.sorted { $0.openedAt > $1.openedAt }
    }
    
    var body: some View {
        List {
            // Filter
            Section {
                Picker("ui_profile_status".localized, selection: $selectedStatus) {
                    Text("ui_inventory_all".localized).tag(nil as MaintenanceTicket.TicketStatus?)
                    ForEach(MaintenanceTicket.TicketStatus.allCases, id: \.self) { status in
                        Text(status.displayName).tag(status as MaintenanceTicket.TicketStatus?)
                    }
                }
            }
            
            // Tickets list
            Section {
                ForEach(filteredTickets) { ticket in
                    NavigationLink(destination: MaintenanceTicketDetailView(ticket: ticket, viewModel: viewModel)) {
                        MaintenanceTicketRow(ticket: ticket)
                    }
                }
            }
        }
        .navigationTitle("ui_inventory_maintenance_tickets".localized)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showCreateTicket = true }) {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(isPresented: $showCreateTicket) {
            CreateMaintenanceTicketView(viewModel: viewModel)
        }
        .task {
            await viewModel.loadAllData()
        }
    }
}

struct MaintenanceTicketDetailView: View {
    let ticket: MaintenanceTicket
    @ObservedObject var viewModel: InventoryViewModel
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Header
                VStack(alignment: .leading, spacing: 8) {
                    Text(ticket.title)
                        .font(.title)
                        .fontWeight(.bold)
                    
                    HStack {
                        Text(ticket.status.displayName)
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(statusColor.opacity(0.2))
                            .foregroundColor(statusColor)
                            .cornerRadius(8)
                        
                        Text(ticket.priority.displayName)
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(priorityColor.opacity(0.2))
                            .foregroundColor(priorityColor)
                            .cornerRadius(8)
                    }
                }
                
                // Description
                SectionView(title: "Description") {
                    Text(ticket.description)
                }
                
                // Details
                SectionView(title: "Details") {
                    ItemInfoRow(label: "Opened", value: formatDate(ticket.openedAt))
                    if let startedAt = ticket.startedAt {
                        ItemInfoRow(label: "Started", value: formatDate(startedAt))
                    }
                    if let completedAt = ticket.completedAt {
                        ItemInfoRow(label: "Completed", value: formatDate(completedAt))
                    }
                    ItemInfoRow(label: "Days Open", value: "\(ticket.daysOpen)")
                    
                    if let assignedTo = ticket.assignedToName {
                        ItemInfoRow(label: "Assigned To", value: assignedTo)
                    }
                }
                
                // Costs
                if ticket.estimatedCost != nil || ticket.actualCost != nil {
                    SectionView(title: "Costs") {
                        if let estimated = ticket.estimatedCost {
                            ItemInfoRow(label: "Estimated", value: String(format: "%.2f %@", estimated, ticket.currency))
                        }
                        if let actual = ticket.actualCost {
                            ItemInfoRow(label: "Actual", value: String(format: "%.2f %@", actual, ticket.currency))
                        }
                    }
                }
                
                // Parts Used
                if !ticket.partsUsed.isEmpty {
                    SectionView(title: "Parts Used") {
                        ForEach(ticket.partsUsed) { part in
                            HStack {
                                Text(part.name)
                                Spacer()
                                Text("\(part.quantity) x \(String(format: "%.2f", part.unitPrice))")
                            }
                        }
                    }
                }
            }
            .padding()
        }
        .navigationTitle("ui_inventory_ticket_details".localized)
        .navigationBarTitleDisplayMode(.inline)
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
    
    private var priorityColor: Color {
        switch ticket.priority {
        case .low: return .green
        case .medium: return .yellow
        case .high: return .orange
        case .urgent: return .red
        }
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

struct CreateMaintenanceTicketView: View {
    @ObservedObject var viewModel: InventoryViewModel
    @Environment(\.dismiss) var dismiss
    
    @State private var gearItemId: String = ""
    @State private var title: String = ""
    @State private var description: String = ""
    @State private var priority: MaintenanceTicket.Priority = .medium
    
    var body: some View {
        NavigationView {
            Form {
                Section("ui_equipment".localized) {
                    Picker("ui_inventory_item".localized, selection: $gearItemId) {
                        Text("ui_inventory_select_item".localized).tag("")
                        ForEach(viewModel.gearItems.filter { !$0.isDeleted }) { item in
                            Text(item.displayName).tag(item.id)
                        }
                    }
                }
                
                Section("ui_inventory_ticket_details".localized) {
                    TextField("ui_inventory_title".localized, text: $title)
                    TextEditor(text: $description)
                        .frame(height: 100)
                    
                    Picker("ui_inventory_priority".localized, selection: $priority) {
                        ForEach(MaintenanceTicket.Priority.allCases, id: \.self) { prio in
                            Text(prio.displayName).tag(prio)
                        }
                    }
                }
            }
            .navigationTitle("ui_inventory_new_ticket".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("ui_cancel".localized) {
                        dismiss()
                    }
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("ui_create".localized) {
                        createTicket()
                    }
                    .disabled(title.isEmpty || gearItemId.isEmpty)
                }
            }
        }
    }
    
    private func createTicket() {
        // TODO: Create ticket
        dismiss()
    }
}

#Preview {
    NavigationView {
        MaintenanceTicketsView()
    }
}
