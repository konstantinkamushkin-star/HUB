//
//  InventoryTabView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct InventoryTabView: View {
    @StateObject private var viewModel = InventoryViewModel()
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationView {
                InventoryDashboardView()
            }
            .tabItem {
                Label("Dashboard", systemImage: "chart.bar.fill")
            }
            .tag(0)
            
            NavigationView {
                InventoryListView()
            }
            .tabItem {
                Label("Inventory", systemImage: "tray.fill")
            }
            .tag(1)
            
            NavigationView {
                MaintenanceTicketsView()
            }
            .tabItem {
                Label("Maintenance", systemImage: "wrench.and.screwdriver.fill")
            }
            .tag(2)
            
            NavigationView {
                ReportsView()
            }
            .tabItem {
                Label("Reports", systemImage: "doc.text.fill")
            }
            .tag(3)
        }
        .environmentObject(viewModel)
    }
}

#Preview {
    InventoryTabView()
}
