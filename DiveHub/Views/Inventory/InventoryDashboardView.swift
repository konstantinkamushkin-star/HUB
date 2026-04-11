//
//  InventoryDashboardView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Charts

struct InventoryDashboardView: View {
    @StateObject private var viewModel = InventoryViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedLocationId: String?
    @State private var showWarnings = true
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header with location selector
                headerSection
                
                // KPI Cards
                if let kpis = viewModel.kpis {
                    kpiSection(kpis: kpis)
                } else if viewModel.isLoading {
                    kpiLoadingSection
                }
                
                // Warnings Section
                if !viewModel.warnings.isEmpty {
                    warningsSection
                }
                
                // Quick Actions
                quickActionsSection
                
                // Charts Section
                chartsSection
            }
            .padding()
        }
        .navigationTitle("Inventory Dashboard")
        .refreshable {
            await viewModel.loadAllData()
        }
        .task {
            await viewModel.loadAllData()
        }
        .alert("Error", isPresented: .constant(viewModel.error != nil)) {
            Button("OK") {
                viewModel.error = nil
            }
        } message: {
            if let error = viewModel.error {
                Text(error.localizedDescription)
            }
        }
    }
    
    // MARK: - Header Section
    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Dive Center Inventory")
                .font(.largeTitle)
                .fontWeight(.bold)
            
            // Location Picker
            if !viewModel.locations.isEmpty {
                Picker("Location", selection: $selectedLocationId) {
                    Text("All Locations").tag(nil as String?)
                    ForEach(viewModel.locations.filter { $0.isActive }) { location in
                        Text(location.name).tag(location.id as String?)
                    }
                }
                .pickerStyle(.menu)
                .onChange(of: selectedLocationId) { oldValue, newValue in
                    viewModel.selectedLocationId = newValue
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    
    // MARK: - KPI Section
    private func kpiSection(kpis: InventoryKPIs) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Key Metrics")
                .font(.title2)
                .fontWeight(.semibold)
            
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 16) {
                KPICard(
                    title: "Total Items",
                    value: "\(kpis.totalItems)",
                    subtitle: "\(kpis.uniqueSKUs) unique SKUs",
                    color: .blue,
                    action: {
                        // Navigate to filtered list
                    }
                )
                
                KPICard(
                    title: "Available Now",
                    value: "\(kpis.availableNow)",
                    subtitle: "Ready to issue",
                    color: .green,
                    action: {
                        viewModel.selectedStatus = .available
                    }
                )
                
                KPICard(
                    title: "In Maintenance",
                    value: "\(kpis.inMaintenance)",
                    subtitle: "Being serviced",
                    color: .orange,
                    action: {
                        viewModel.selectedStatus = .maintenance
                    }
                )
                
                KPICard(
                    title: "Issued Now",
                    value: "\(kpis.issuedNow)",
                    subtitle: "With clients",
                    color: .purple,
                    action: {
                        viewModel.selectedStatus = .issued
                    }
                )
                
                KPICard(
                    title: "Inspection Overdue",
                    value: "\(kpis.inspectionOverdue)",
                    subtitle: "Requires attention",
                    color: .red,
                    action: {
                        // Filter by overdue
                    }
                )
                
                KPICard(
                    title: "Needs Service",
                    value: "\(kpis.needsService)",
                    subtitle: "Service required",
                    color: .red,
                    action: {
                        viewModel.selectedCondition = .needsService
                    }
                )
            }
        }
    }
    
    private var kpiLoadingSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Key Metrics")
                .font(.title2)
                .fontWeight(.semibold)
            
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 16) {
                ForEach(0..<6) { _ in
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.gray.opacity(0.2))
                        .frame(height: 100)
                        .shimmer()
                }
            }
        }
    }
    
    // MARK: - Warnings Section
    private var warningsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Warnings & Alerts")
                    .font(.title2)
                    .fontWeight(.semibold)
                
                Spacer()
                
                Button(action: { showWarnings.toggle() }) {
                    Image(systemName: showWarnings ? "chevron.up" : "chevron.down")
                }
            }
            
            if showWarnings {
                ForEach(viewModel.warnings.prefix(5)) { warning in
                    WarningRow(warning: warning)
                }
                
                if viewModel.warnings.count > 5 {
                    NavigationLink("View All Warnings") {
                        WarningsListView(warnings: viewModel.warnings)
                    }
                    .font(.subheadline)
                    .foregroundColor(.blue)
                }
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.1), radius: 5, x: 0, y: 2)
    }
    
    // MARK: - Quick Actions
    private var quickActionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Quick Actions")
                .font(.title2)
                .fontWeight(.semibold)
            
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 16) {
                InventoryQuickActionButton(
                    title: "Scan QR Code",
                    icon: "qrcode.viewfinder",
                    color: .blue
                ) {
                    // Open QR scanner
                }
                
                InventoryQuickActionButton(
                    title: "Create Item",
                    icon: "plus.circle.fill",
                    color: .green
                ) {
                    // Navigate to create item
                }
                
                InventoryQuickActionButton(
                    title: "Start Audit",
                    icon: "checklist",
                    color: .orange
                ) {
                    // Start inventory audit
                }
                
                InventoryQuickActionButton(
                    title: "New Inspection",
                    icon: "checkmark.shield.fill",
                    color: .purple
                ) {
                    // Start new inspection
                }
            }
        }
    }
    
    // MARK: - Charts Section
    private var chartsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Trends")
                .font(.title2)
                .fontWeight(.semibold)
            
            if let kpis = viewModel.kpis, !kpis.checkoutsLast30Days.isEmpty {
                Chart {
                    ForEach(kpis.checkoutsLast30Days, id: \.date) { dataPoint in
                        LineMark(
                            x: .value("Date", dataPoint.date, unit: .day),
                            y: .value("Count", dataPoint.count)
                        )
                        .foregroundStyle(.blue)
                    }
                }
                .frame(height: 200)
                .padding()
                .background(Color(.systemBackground))
                .cornerRadius(12)
            } else {
                Text("No data available")
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            }
        }
    }
}

// MARK: - KPI Card
struct KPICard: View {
    let title: String
    let value: String
    let subtitle: String
    let color: Color
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                Text(title)
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Text(value)
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.primary)
                
                Text(subtitle)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(color.opacity(0.1))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(color.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Warning Row
struct WarningRow: View {
    let warning: WarningItem
    
    var body: some View {
        HStack(spacing: 12) {
            // Severity indicator
            Circle()
                .fill(severityColor)
                .frame(width: 8, height: 8)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(warning.title)
                    .font(.headline)
                
                Text(warning.message)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .foregroundColor(.secondary)
                .font(.caption)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background(Color(.systemGray6))
        .cornerRadius(8)
    }
    
    private var severityColor: Color {
        switch warning.severity {
        case .critical: return .red
        case .high: return .orange
        case .medium: return .yellow
        case .low: return .blue
        }
    }
}

// MARK: - Quick Action Button
struct InventoryQuickActionButton: View {
    let title: String
    let icon: String
    let color: Color
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.title)
                    .foregroundColor(color)
                
                Text(title)
                    .font(.caption)
                    .foregroundColor(.primary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color(.systemBackground))
            .cornerRadius(12)
            .shadow(color: .black.opacity(0.1), radius: 5, x: 0, y: 2)
        }
    }
}

// MARK: - Shimmer Effect
extension View {
    func shimmer() -> some View {
        self.modifier(ShimmerModifier())
    }
}

struct ShimmerModifier: ViewModifier {
    @State private var phase: CGFloat = 0
    
    func body(content: Content) -> some View {
        content
            .overlay(
                GeometryReader { geometry in
                    LinearGradient(
                        gradient: Gradient(colors: [
                            Color.clear,
                            Color.white.opacity(0.3),
                            Color.clear
                        ]),
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: geometry.size.width * 2)
                    .offset(x: -geometry.size.width + phase * geometry.size.width * 2)
                }
            )
            .onAppear {
                withAnimation(.linear(duration: 1.5).repeatForever(autoreverses: false)) {
                    phase = 1
                }
            }
    }
}

// MARK: - Warnings List View
struct WarningsListView: View {
    let warnings: [WarningItem]
    
    var body: some View {
        List {
            ForEach(warnings) { warning in
                WarningRow(warning: warning)
            }
        }
        .navigationTitle("All Warnings")
    }
}

#Preview {
    NavigationView {
        InventoryDashboardView()
    }
}
