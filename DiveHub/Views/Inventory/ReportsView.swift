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
            Section("ui_available_reports".localized) {
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
            
            Section("ui_export_options".localized) {
                Button(action: {}) {
                    Label("ui_inventory_export_all_data_csv".localized, systemImage: "square.and.arrow.up")
                }
                
                Button(action: {}) {
                    Label("ui_inventory_export_all_data_pdf".localized, systemImage: "doc.fill")
                }
            }
        }
        .navigationTitle("ui_inventory_reports".localized)
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
                Text("ui_inventory_usage_report".localized)
                    .font(.title)
                    .padding()
                
                // TODO: Implement usage report
                Text("ui_inventory_usage_report_coming_soon".localized)
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
        .navigationTitle("ui_inventory_usage_report".localized)
    }
}

struct MaintenanceCostReportView: View {
    @ObservedObject var viewModel: InventoryViewModel
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("ui_inventory_maintenance_costs".localized)
                    .font(.title)
                    .padding()
                
                // TODO: Implement maintenance cost report
                Text("ui_inventory_maintenance_cost_report_coming_soon".localized)
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
        .navigationTitle("ui_inventory_maintenance_costs".localized)
    }
}

struct InspectionComplianceReportView: View {
    @ObservedObject var viewModel: InventoryViewModel
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("ui_inventory_inspection_compliance".localized)
                    .font(.title)
                    .padding()
                
                // TODO: Implement inspection compliance report
                Text("ui_inventory_inspection_compliance_report_coming_soon".localized)
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
        .navigationTitle("ui_inventory_inspection_compliance".localized)
    }
}

struct ROIReportView: View {
    @ObservedObject var viewModel: InventoryViewModel
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("ui_inventory_roi_analysis".localized)
                    .font(.title)
                    .padding()
                
                // TODO: Implement ROI report
                Text("ui_inventory_roi_analysis_report_coming_soon".localized)
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
        .navigationTitle("ui_inventory_roi_analysis".localized)
    }
}

#Preview {
    NavigationView {
        ReportsView()
    }
}
