//
//  ShopTabView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct ShopTabView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var authService = AuthenticationService.shared
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            ShopDashboardView()
                .tabItem {
                    Label("ui_shop_dashboard".localized, systemImage: "house.fill")
                }
                .tag(0)
            
            ShopsManagementView()
                .tabItem {
                    Label("ui_shop_my_shop".localized, systemImage: "storefront")
                }
                .tag(1)
            
            ShopProductsView()
                .tabItem {
                    Label("ui_shop_products".localized, systemImage: "cube.box")
                }
                .tag(2)
            
            ShopOrdersView()
                .tabItem {
                    Label("ui_shop_orders".localized, systemImage: "cart")
                }
                .tag(3)
            
            ShopAnalyticsView()
                .tabItem {
                    Label("ui_shop_analytics".localized, systemImage: "chart.bar")
                }
                .tag(4)
            
            ProfileTabView()
                .tabItem {
                    Label(localizationService.localizedString("profile"), systemImage: "person.circle")
                }
                .tag(5)
        }
        .accentColor(.divePrimary)
    }
}

struct ShopDashboardView: View {
    @StateObject private var viewModel = ShopDashboardViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Stats Cards
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                        ShopStatCard(
                            title: "ui_shop_total_orders".localized,
                            value: "\(viewModel.stats.totalOrders)",
                            icon: "cart",
                            color: .blue
                        )
                        ShopStatCard(
                            title: "ui_shop_pending_orders".localized,
                            value: "\(viewModel.stats.pendingOrders)",
                            icon: "clock",
                            color: .orange
                        )
                        ShopStatCard(
                            title: "ui_shop_today_revenue".localized,
                            value: String(format: "$%.0f", viewModel.stats.todayRevenue),
                            icon: "dollarsign.circle",
                            color: .green
                        )
                        ShopStatCard(
                            title: "ui_shop_total_products".localized,
                            value: "\(viewModel.stats.totalProducts)",
                            icon: "cube.box",
                            color: .purple
                        )
                    }
                    .padding()
                    
                    // Quick Actions
                    Section(header: Text("ui_shop_quick_actions".localized)
                        .font(.headline)
                        .padding(.horizontal)) {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                            QuickActionButton(
                                title: "ui_shop_my_shop".localized,
                                icon: "storefront",
                                destination: AnyView(ShopsManagementView())
                            )
                            QuickActionButton(
                                title: "ui_shop_products".localized,
                                icon: "cube.box",
                                destination: AnyView(ShopProductsView())
                            )
                            QuickActionButton(
                                title: "ui_shop_orders".localized,
                                icon: "cart",
                                destination: AnyView(ShopOrdersView())
                            )
                            QuickActionButton(
                                title: "ui_shop_analytics".localized,
                                icon: "chart.bar",
                                destination: AnyView(ShopAnalyticsView())
                            )
                        }
                        .padding(.horizontal)
                    }
                }
            }
            .navigationTitle("ui_shop_shop_dashboard".localized)
            .onAppear {
                Task {
                    await viewModel.loadStats()
                }
            }
        }
    }
}

struct ShopStatCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(color)
                Spacer()
            }
            Text(value)
                .font(.title)
                .fontWeight(.bold)
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}

struct QuickActionButton: View {
    let title: String
    let icon: String
    let destination: AnyView
    
    var body: some View {
        NavigationLink(destination: destination) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(.divePrimary)
                
                Text(title)
                    .font(.subheadline)
                    .foregroundColor(.primary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, minHeight: 90)
            .padding(.horizontal, 8)
            .background(Color(.systemGray6))
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
    }
}

struct ShopProductsView: View {
    @StateObject private var viewModel = ShopProductsViewModel()
    
    var body: some View {
        NavigationView {
            VStack {
                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if viewModel.products.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "cube.box")
                            .font(.system(size: 60))
                            .foregroundColor(.gray)
                        Text("ui_shop_no_products_yet".localized)
                            .font(.headline)
                            .foregroundColor(.gray)
                        Button("ui_add_first_product".localized) {
                            // TODO: Add product
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(viewModel.products) { product in
                        ProductRowView(product: product)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("ui_shop_products".localized)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { /* Add product */ }) {
                        Image(systemName: "plus")
                    }
                }
            }
            .onAppear {
                Task {
                    await viewModel.loadProducts()
                }
            }
        }
    }
}

struct ProductRowView: View {
    let product: ShopProduct
    
    var body: some View {
        HStack(spacing: 12) {
            if let firstPhoto = product.photos.first, !firstPhoto.isEmpty {
                AsyncImage(url: URL(string: firstPhoto)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                }
                .frame(width: 60, height: 60)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: 60, height: 60)
                    .overlay(
                        Image(systemName: "cube.box")
                            .foregroundColor(.gray)
                    )
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(product.name)
                    .font(.headline)
                Text(product.category)
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(String(format: "$%.2f", product.price))
                    .font(.subheadline)
                    .fontWeight(.semibold)
            }
            
            Spacer()
        }
        .padding(.vertical, 4)
    }
}

struct ShopOrdersView: View {
    @StateObject private var viewModel = ShopOrdersViewModel()
    
    var body: some View {
        NavigationView {
            VStack {
                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if viewModel.orders.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "cart")
                            .font(.system(size: 60))
                            .foregroundColor(.gray)
                        Text("ui_shop_no_orders_yet".localized)
                            .font(.headline)
                            .foregroundColor(.gray)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(viewModel.orders) { order in
                        OrderRowView(order: order)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("ui_shop_orders".localized)
            .onAppear {
                Task {
                    await viewModel.loadOrders()
                }
            }
        }
    }
}

struct OrderRowView: View {
    let order: ShopOrder
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("\(localizationService.localizedString("ui_shop_order_prefix", table: "ui"))\(order.id.prefix(8))")
                    .font(.headline)
                Spacer()
                Text(localizedOrderStatus(order.status))
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(order.status == .pending ? Color.orange.opacity(0.2) : Color.green.opacity(0.2))
                    .cornerRadius(8)
            }
            Text(order.customerName)
                .font(.subheadline)
                .foregroundColor(.secondary)
            Text(String(format: "$%.2f", order.total))
                .font(.headline)
        }
        .padding(.vertical, 4)
    }

    private func localizedOrderStatus(_ status: ShopOrder.OrderStatus) -> String {
        switch status {
        case .pending:
            return localizationService.localizedString("ui_shop_order_status_pending", table: "ui")
        case .processing:
            return localizationService.localizedString("ui_shop_order_status_processing", table: "ui")
        case .shipped:
            return localizationService.localizedString("ui_shop_order_status_shipped", table: "ui")
        case .delivered:
            return localizationService.localizedString("ui_shop_order_status_delivered", table: "ui")
        case .cancelled:
            return localizationService.localizedString("ui_shop_order_status_cancelled", table: "ui")
        }
    }
}

struct ShopAnalyticsView: View {
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    Text("ui_shop_analytics_coming_soon".localized)
                        .font(.headline)
                        .foregroundColor(.secondary)
                        .padding()
                }
            }
            .navigationTitle("ui_shop_analytics".localized)
        }
    }
}

// ViewModels
@MainActor
class ShopDashboardViewModel: ObservableObject {
    @Published var stats = ShopStats()
    
    func loadStats() async {
        // TODO: Load from API
        stats = ShopStats(
            totalOrders: 0,
            pendingOrders: 0,
            todayRevenue: 0,
            totalProducts: 0
        )
    }
}

struct ShopStats {
    var totalOrders: Int = 0
    var pendingOrders: Int = 0
    var todayRevenue: Double = 0
    var totalProducts: Int = 0
}

@MainActor
class ShopProductsViewModel: ObservableObject {
    @Published var products: [ShopProduct] = []
    @Published var isLoading = false
    
    func loadProducts() async {
        isLoading = true
        // TODO: Load from API
        products = []
        isLoading = false
    }
}

struct ShopProduct: Identifiable {
    let id: String
    var name: String
    var description: String
    var category: String
    var price: Double
    var photos: [String]
}

@MainActor
class ShopOrdersViewModel: ObservableObject {
    @Published var orders: [ShopOrder] = []
    @Published var isLoading = false
    
    func loadOrders() async {
        isLoading = true
        // TODO: Load from API
        orders = []
        isLoading = false
    }
}

struct ShopOrder: Identifiable {
    let id: String
    var customerName: String
    var total: Double
    var status: OrderStatus
    var createdAt: Date
    
    enum OrderStatus: String {
        case pending
        case processing
        case shipped
        case delivered
        case cancelled
    }
}

#Preview {
    ShopTabView()
}
