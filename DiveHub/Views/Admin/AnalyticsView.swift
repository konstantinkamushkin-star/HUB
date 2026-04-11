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
            Section("Error Statistics") {
                HStack {
                    Text("Total Errors")
                    Spacer()
                    Text("\(viewModel.errorStats.totals.allErrors)")
                        .font(.headline)
                }
                HStack {
                    Text("HTTP Errors")
                    Spacer()
                    Text("\(viewModel.errorStats.totals.httpErrors)")
                }
                HStack {
                    Text("Uncaught Exceptions")
                    Spacer()
                    Text("\(viewModel.errorStats.totals.uncaughtExceptions)")
                }
                HStack {
                    Text("Unhandled Rejections")
                    Spacer()
                    Text("\(viewModel.errorStats.totals.unhandledRejections)")
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
