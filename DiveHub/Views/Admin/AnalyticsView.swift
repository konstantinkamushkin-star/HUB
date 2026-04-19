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
        VStack(spacing: 0) {
            List {
                Section("ui_error_statistics".localized) {
                    HStack {
                        Text("ui_admin_total_errors".localized)
                        Spacer()
                        Text("\(viewModel.errorStats.totals.allErrors)")
                            .font(.headline)
                    }
                    HStack {
                        Text("ui_admin_http_errors".localized)
                        Spacer()
                        Text("\(viewModel.errorStats.totals.httpErrors)")
                    }
                    HStack {
                        Text("ui_admin_uncaught_exceptions".localized)
                        Spacer()
                        Text("\(viewModel.errorStats.totals.uncaughtExceptions)")
                    }
                    HStack {
                        Text("ui_admin_unhandled_rejections".localized)
                        Spacer()
                        Text("\(viewModel.errorStats.totals.unhandledRejections)")
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("analytics", table: "admin"))
        .diveHubNavigationChrome()
        .task {
            await viewModel.loadErrorStats()
        }
    }
}

#Preview {
    AnalyticsView()
}
