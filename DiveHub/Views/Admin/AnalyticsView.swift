//
//  AnalyticsView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct AnalyticsView: View {
    @StateObject private var viewModel = AdminViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        List {
            Section("ui_error_statistics".localized) {
                HStack {
                    Text("ui_admin_total_errors".localized)
                    Spacer()
                    Text("ui_admin_value_4".localized)
                        .font(.headline)
                }
                HStack {
                    Text("ui_admin_http_errors".localized)
                    Spacer()
                    Text("ui_admin_value_5".localized)
                }
                HStack {
                    Text("ui_admin_uncaught_exceptions".localized)
                    Spacer()
                    Text("ui_admin_value_6".localized)
                }
                HStack {
                    Text("ui_admin_unhandled_rejections".localized)
                    Spacer()
                    Text("ui_admin_value_7".localized)
                }
            }
        }
        .navigationTitle(localizationService.localizedString("analytics", table: "admin"))
        .task {
            await viewModel.loadErrorStats()
        }
    }
}

#Preview {
    AnalyticsView()
}
