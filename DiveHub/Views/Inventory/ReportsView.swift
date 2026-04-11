//
//  ReportsView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct ReportsView: View {
    @StateObject private var viewModel = InventoryViewModel()
    @State private var selectedReport: ReportType?
    
    enum ReportType: String, CaseIterable {
        case usage = "Usage Report"
        case maintenance = "Maintenance Costs"
        case inspections = "Inspection Compliance"
        case roi = "ROI Analysis"
        
        var icon: String {
            switch self {
            case .usage: return "chart.line.uptrend.xyaxis"
            case .maintenance: return "dollarsign.circle"
            case .inspections: return "checkmark.shield"
            case .roi: return "chart.bar"
            }
        }
    }
    
    var body: some View {
        List {
            Section("Available Reports") {
                ForEach(ReportType.allCases, id: \.self) { reportType in
                    NavigationLink(destination: reportDetailView(for: reportType)) {
                        HStack {
                            Image(systemName: reportType.icon)
                                .foregroundColor(.blue)
                                .frame(width: 30)
                            
                            Text(reportType.rawValue)
                        }
                    }
                }
            }
            
            Section("Export Options") {
                Button(action: {}) {
                    Label("Export All Data (CSV)", systemImage: "square.and.arrow.up")
                }
                
                Button(action: {}) {
                    Label("Export All Data (PDF)", systemImage: "doc.fill")
                }
            }
        }
        .navigationTitle("Reports")
    }
    
    @ViewBuilder
    private func reportDetailView(for type: ReportType) -> some View {
        switch type {
        case .usage:
            UsageReportView(viewModel: viewModel)
        case .maintenance:
            MaintenanceCostReportView(viewModel: viewModel)
        case .inspections:
            InspectionComplianceReportView(viewModel: viewModel)
        case .roi:
            ROIReportView(viewModel: viewModel)
        }
    }
}

struct UsageReportView: View {
    @ObservedObject var viewModel: InventoryViewModel
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Usage Report")
                    .font(.title)
                    .padding()
                
                // TODO: Implement usage report
                Text("Usage report coming soon...")
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
        .navigationTitle("Usage Report")
    }
}

struct MaintenanceCostReportView: View {
    @ObservedObject var viewModel: InventoryViewModel
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Maintenance Costs")
                    .font(.title)
                    .padding()
                
                // TODO: Implement maintenance cost report
                Text("Maintenance cost report coming soon...")
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
        .navigationTitle("Maintenance Costs")
    }
}

struct InspectionComplianceReportView: View {
    @ObservedObject var viewModel: InventoryViewModel
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Inspection Compliance")
                    .font(.title)
                    .padding()
                
                // TODO: Implement inspection compliance report
                Text("Inspection compliance report coming soon...")
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
        .navigationTitle("Inspection Compliance")
    }
}

struct ROIReportView: View {
    @ObservedObject var viewModel: InventoryViewModel
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("ROI Analysis")
                    .font(.title)
                    .padding()
                
                // TODO: Implement ROI report
                Text("ROI analysis report coming soon...")
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
        .navigationTitle("ROI Analysis")
    }
}

#Preview {
    NavigationView {
        ReportsView()
    }
}
