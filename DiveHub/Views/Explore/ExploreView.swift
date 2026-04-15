//
//  ExploreView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import MapKit

struct ExploreView: View {
    @StateObject private var viewModel = GenericExploreViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var authService = AuthenticationService.shared
    @State private var showFilters = false
    @State private var selectedItem: (any ExploreItem)?
    @State private var mapRegion = MapRegion(
        center: CLLocationCoordinate2D(latitude: 25.7617, longitude: -80.1918),
        span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)
    )
    @State private var didCenterExploreMapOnUser = false
    /// Пока `Date() < значения`, баннер «завершите профиль» скрыт (см. `UserDefaults`).
    @State private var completeProfileBannerHiddenUntil: Date?
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let u = authService.currentUser,
                   u.diverProfile?.onboardingCompleted == true,
                   u.profileCompletionFraction() < 0.7,
                   !isCompleteProfileBannerCurrentlySuppressed
                {
                    HStack(alignment: .top, spacing: 10) {
                        NavigationLink {
                            EditProfileView()
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(localizationService.localizedString("completeProfileBannerTitle", table: "onboarding"))
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.primary)
                                Text(localizationService.localizedString("completeProfileBannerBody", table: "onboarding"))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityElement(children: .combine)
                        .accessibilityAddTraits(.isButton)
                        
                        Button {
                            suppressCompleteProfileBanner(forUserId: u.id)
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title3)
                                .symbolRenderingMode(.hierarchical)
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(localizationService.localizedString("completeProfileBannerDismiss", table: "onboarding"))
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.divePrimary.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .padding(.horizontal)
                    .padding(.top, 4)
                }
                // Segmented Control
                categorySegmentedControl
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                
                // Search Bar
                searchBar
                    .padding(.horizontal)
                    .padding(.bottom, 8)
                
                // Quick Filter Chips
                quickFilterChips
                    .padding(.horizontal)
                    .padding(.bottom, 8)
                
                // Content
                contentView
            }
            .navigationTitle(localizationService.localizedString("explore"))
            .diveHubNavigationChrome()
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 16) {
                        sortButton
                        filterButton
                    }
                }
            }
            .sheet(isPresented: $showFilters) {
                filterSheet
            }
            .sheet(item: Binding(
                get: { selectedItem as? DiveSite },
                set: { selectedItem = $0 }
            )) { site in
                NavigationStack {
                    DiveSiteDetailView(site: site)
                }
            }
            .sheet(item: Binding(
                get: { selectedItem as? DiveCenter },
                set: { selectedItem = $0 }
            )) { center in
                NavigationStack {
                    DiveCenterPublicView(center: center)
                }
            }
            .sheet(item: Binding(
                get: { selectedItem as? Shop },
                set: { selectedItem = $0 }
            )) { shop in
                NavigationStack {
                    ShopDetailView(shop: shop)
                }
            }
            .onChange(of: viewModel.userLocation) { _, location in
                applyInitialMapRegionIfNeeded(location: location)
            }
            .task {
                await viewModel.loadData()
            }
            .onAppear {
                refreshCompleteProfileBannerSuppressionState()
            }
            .onChange(of: authService.currentUser?.id) { _, _ in
                refreshCompleteProfileBannerSuppressionState()
            }
        }
    }
    
    private var isCompleteProfileBannerCurrentlySuppressed: Bool {
        guard let until = completeProfileBannerHiddenUntil else { return false }
        return Date() < until
    }
    
    private static let completeProfileBannerSuppressionSeconds: TimeInterval = 2 * 24 * 60 * 60
    
    private func completeProfileBannerUserDefaultsKey(userId: String) -> String {
        "explore_complete_profile_banner_hidden_until_\(userId)"
    }
    
    private func refreshCompleteProfileBannerSuppressionState() {
        guard let id = authService.currentUser?.id else {
            completeProfileBannerHiddenUntil = nil
            return
        }
        let raw = UserDefaults.standard.double(forKey: completeProfileBannerUserDefaultsKey(userId: id))
        guard raw > 0 else {
            completeProfileBannerHiddenUntil = nil
            return
        }
        let until = Date(timeIntervalSince1970: raw)
        if Date() >= until {
            UserDefaults.standard.removeObject(forKey: completeProfileBannerUserDefaultsKey(userId: id))
            completeProfileBannerHiddenUntil = nil
        } else {
            completeProfileBannerHiddenUntil = until
        }
    }
    
    private func suppressCompleteProfileBanner(forUserId userId: String) {
        let until = Date().addingTimeInterval(Self.completeProfileBannerSuppressionSeconds)
        UserDefaults.standard.set(until.timeIntervalSince1970, forKey: completeProfileBannerUserDefaultsKey(userId: userId))
        completeProfileBannerHiddenUntil = until
    }

    private func applyInitialMapRegionIfNeeded(location: CLLocation?) {
        guard !didCenterExploreMapOnUser, viewModel.currentViewMode == .map, let location else { return }
        didCenterExploreMapOnUser = true
        mapRegion = MapRegion(
            center: location.coordinate,
            span: MKCoordinateSpan(latitudeDelta: 0.35, longitudeDelta: 0.35)
        )
    }
    
    private var categorySegmentedControl: some View {
        Picker("ui_profile_category".localized, selection: $viewModel.selectedCategory) {
            ForEach(ExploreCategory.allCases) { category in
                // Один `Label` на сегмент: `HStack(Image+Text)` даёт в `.segmented` отдельную «пилюлю»
                // под иконку и обрезанный текст — выглядит как пустой «Dive Sites».
                Label(category.displayName, systemImage: category.iconName)
                    .labelStyle(.titleAndIcon)
                    .tag(category)
            }
        }
        .pickerStyle(.segmented)
        .onChange(of: viewModel.selectedCategory) { oldValue, newValue in
            viewModel.switchCategory(newValue)
        }
    }
    
    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
            TextField(
                viewModel.selectedCategory.searchPlaceholder,
                text: Binding(
                    get: { viewModel.currentSearchQuery },
                    set: { viewModel.search(query: $0) }
                )
            )
            .textFieldStyle(.plain)
            .onSubmit {
                Task {
                    await viewModel.loadData(refresh: true)
                }
            }
            
            if !viewModel.currentSearchQuery.isEmpty {
                Button(action: {
                    viewModel.search(query: "")
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(12)
        .background(Color(.systemGray6))
        .cornerRadius(10)
    }
    
    private var quickFilterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(quickFilters, id: \.self) { filter in
                    QuickFilterChip(
                        title: filter.title,
                        isActive: filter.isActive,
                        action: filter.action
                    )
                }
            }
            .padding(.horizontal)
        }
    }
    
    private var quickFilters: [QuickFilter] {
        switch viewModel.selectedCategory {
        case .diveSites:
            return [
                QuickFilter(
                    title: localizationService.localizedString("beginner", table: "explore"),
                    isActive: viewModel.diveSiteFilters.difficulty == .beginner,
                    action: {
                        if viewModel.diveSiteFilters.difficulty == .beginner {
                            viewModel.diveSiteFilters.difficulty = nil
                        } else {
                            viewModel.diveSiteFilters.difficulty = .beginner
                        }
                        Task {
                            await viewModel.loadData(refresh: true)
                        }
                    }
                ),
                QuickFilter(
                    title: localizationService.localizedString("reef", table: "explore"),
                    isActive: viewModel.diveSiteFilters.siteType == .reef,
                    action: {
                        if viewModel.diveSiteFilters.siteType == .reef {
                            viewModel.diveSiteFilters.siteType = nil
                        } else {
                            viewModel.diveSiteFilters.siteType = .reef
                        }
                        Task {
                            await viewModel.loadData(refresh: true)
                        }
                    }
                ),
                QuickFilter(
                    title: localizationService.localizedString("wreck", table: "explore"),
                    isActive: viewModel.diveSiteFilters.siteType == .wreck,
                    action: {
                        if viewModel.diveSiteFilters.siteType == .wreck {
                            viewModel.diveSiteFilters.siteType = nil
                        } else {
                            viewModel.diveSiteFilters.siteType = .wreck
                        }
                        Task {
                            await viewModel.loadData(refresh: true)
                        }
                    }
                )
            ]
        case .diveCenters:
            return [
                QuickFilter(
                    title: localizationService.localizedString("nitrox", table: "explore"),
                    isActive: viewModel.diveCenterFilters.nitroxAvailable == true,
                    action: {
                        if viewModel.diveCenterFilters.nitroxAvailable == true {
                            viewModel.diveCenterFilters.nitroxAvailable = nil
                        } else {
                            viewModel.diveCenterFilters.nitroxAvailable = true
                        }
                        Task {
                            await viewModel.loadData(refresh: true)
                        }
                    }
                ),
                QuickFilter(
                    title: localizationService.localizedString("padi", table: "explore"),
                    isActive: viewModel.diveCenterFilters.certificationAgency == "PADI",
                    action: {
                        if viewModel.diveCenterFilters.certificationAgency == "PADI" {
                            viewModel.diveCenterFilters.certificationAgency = nil
                        } else {
                            viewModel.diveCenterFilters.certificationAgency = "PADI"
                        }
                        Task {
                            await viewModel.loadData(refresh: true)
                        }
                    }
                )
            ]
        case .shops:
            return [
                QuickFilter(
                    title: localizationService.localizedString("online", table: "explore"),
                    isActive: viewModel.shopFilters.shopType == .online,
                    action: {
                        if viewModel.shopFilters.shopType == .online {
                            viewModel.shopFilters.shopType = nil
                        } else {
                            viewModel.shopFilters.shopType = .online
                        }
                        Task {
                            await viewModel.loadData(refresh: true)
                        }
                    }
                ),
                QuickFilter(
                    title: localizationService.localizedString("serviceAvailable", table: "explore"),
                    isActive: viewModel.shopFilters.serviceAvailable == true,
                    action: {
                        if viewModel.shopFilters.serviceAvailable == true {
                            viewModel.shopFilters.serviceAvailable = nil
                        } else {
                            viewModel.shopFilters.serviceAvailable = true
                        }
                        Task {
                            await viewModel.loadData(refresh: true)
                        }
                    }
                )
            ]
        }
    }
    
    private var sortButton: some View {
        Menu {
            ForEach(ExploreSortOption.allCases) { option in
                Button(action: {
                    viewModel.changeSortOption(option)
                }) {
                    HStack {
                        Label(option.displayName, systemImage: option.iconName)
                        if viewModel.currentSortOption == option {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            Image(systemName: "arrow.up.arrow.down")
                .font(.title3)
        }
    }
    
    private var filterButton: some View {
        Button(action: { showFilters = true }) {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "line.3.horizontal.decrease.circle")
                    .font(.title3)
                
                if viewModel.activeFilterCount > 0 {
                    Text("ui_explore_value_7".localized)
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(4)
                        .background(Color.red)
                        .clipShape(Circle())
                        .offset(x: 8, y: -8)
                }
            }
        }
    }
    
    @ViewBuilder
    private var filterSheet: some View {
        switch viewModel.selectedCategory {
        case .diveSites:
            FilterView(filters: $viewModel.diveSiteFilters, diveSites: viewModel.diveSites)
                .onDisappear {
                    Task {
                        await viewModel.loadData(refresh: true)
                    }
                }
        case .diveCenters:
            DiveCenterFilterView(filters: $viewModel.diveCenterFilters, diveCenters: viewModel.diveCenters)
                .onDisappear {
                    Task {
                        await viewModel.loadData(refresh: true)
                    }
                }
        case .shops:
            ShopFilterView(filters: $viewModel.shopFilters)
                .onDisappear {
                    Task {
                        await viewModel.loadData(refresh: true)
                    }
                }
        }
    }
    
    @ViewBuilder
    private var contentView: some View {
        if viewModel.isLoading && viewModel.diveSites.isEmpty && viewModel.diveCenters.isEmpty && viewModel.shops.isEmpty {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error = viewModel.error {
            errorView(error)
        } else {
            VStack(spacing: 0) {
                // View Mode Toggle
                viewModeToggle
                
                // List or Map
                if viewModel.currentViewMode == .list {
                    listView
                } else {
                    mapView
                }
            }
        }
    }
    
    private var viewModeToggle: some View {
        Picker(localizationService.localizedString("viewMode"), selection: $viewModel.currentViewMode) {
            Label(localizationService.localizedString("list"), systemImage: "list.bullet").tag(ExploreViewMode.list)
            Label(localizationService.localizedString("map"), systemImage: "map").tag(ExploreViewMode.map)
        }
        .pickerStyle(.segmented)
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
    
    @ViewBuilder
    private var listView: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                switch viewModel.selectedCategory {
                case .diveSites:
                    ForEach(viewModel.diveSites, id: \.id) { site in
                        ListCard(
                            item: site,
                            category: .diveSites,
                            onTap: {
                                selectedItem = site
                            },
                            onAddToTrip: {
                                // TODO: Implement add to trip
                            },
                            friendsVisited: friendsVisitedCount(for: site),
                            isRecommended: isRecommended(item: site),
                            distanceInMeters: distanceToItem(site)
                        )
                        .padding(.horizontal)
                        .onAppear {
                            if shouldLoadMore(for: site) {
                                Task {
                                    await viewModel.loadMore()
                                }
                            }
                        }
                    }
                case .diveCenters:
                    ForEach(viewModel.diveCenters, id: \.id) { center in
                        ListCard(
                            item: center,
                            category: .diveCenters,
                            onTap: {
                                selectedItem = center
                            },
                            onAddToTrip: {
                                // TODO: Implement add to trip
                            },
                            friendsVisited: friendsVisitedCount(for: center),
                            isRecommended: isRecommended(item: center),
                            distanceInMeters: distanceToItem(center)
                        )
                        .padding(.horizontal)
                        .onAppear {
                            if shouldLoadMore(for: center) {
                                Task {
                                    await viewModel.loadMore()
                                }
                            }
                        }
                    }
                case .shops:
                    ForEach(viewModel.shops, id: \.id) { shop in
                        ListCard(
                            item: shop,
                            category: .shops,
                            onTap: {
                                selectedItem = shop
                            },
                            onAddToTrip: {
                                // TODO: Implement add to trip
                            },
                            friendsVisited: friendsVisitedCount(for: shop),
                            isRecommended: isRecommended(item: shop),
                            distanceInMeters: distanceToItem(shop)
                        )
                        .padding(.horizontal)
                        .onAppear {
                            if shouldLoadMore(for: shop) {
                                Task {
                                    await viewModel.loadMore()
                                }
                            }
                        }
                    }
                }
                
                if viewModel.isLoading {
                    ProgressView()
                        .padding()
                }
            }
            .padding(.vertical)
        }
        .refreshable {
            await viewModel.loadData(refresh: true)
        }
    }
    
    private var mapView: some View {
        GeometryReader { geo in
            // `geo.safeAreaInsets` учитывает системный таб-бар; запас — над картой с `ignoresSafeArea`.
            let mapChromeBottom = max(geo.safeAreaInsets.bottom + 12, 64)
            ZStack {
                ExploreMapView(
                    category: viewModel.selectedCategory,
                    diveSites: viewModel.diveSites,
                    diveCenters: viewModel.diveCenters,
                    shops: viewModel.shops,
                    onItemTapped: { item in
                        selectedItem = item
                    },
                    region: $mapRegion,
                    showsUserLocation: .constant(viewModel.userLocation != nil)
                )

                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        VStack(spacing: 10) {
                            MapChromeZoomCluster(onZoomIn: zoomIn, onZoomOut: zoomOut)
                            MapChromeLocateButton(action: centerOnUserLocation)
                        }
                        .padding(.trailing, 16)
                        .padding(.bottom, mapChromeBottom)
                    }
                }
                .onAppear {
                    applyInitialMapRegionIfNeeded(location: viewModel.userLocation)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
    
    private func zoomIn() {
        let currentSpan = mapRegion.span
        let newSpan = MKCoordinateSpan(
            latitudeDelta: currentSpan.latitudeDelta * 0.5,
            longitudeDelta: currentSpan.longitudeDelta * 0.5
        )
        mapRegion = MapRegion(
            center: mapRegion.center,
            span: newSpan
        )
    }
    
    private func zoomOut() {
        let currentSpan = mapRegion.span
        let newSpan = MKCoordinateSpan(
            latitudeDelta: min(currentSpan.latitudeDelta * 2.0, 180.0),
            longitudeDelta: min(currentSpan.longitudeDelta * 2.0, 360.0)
        )
        mapRegion = MapRegion(
            center: mapRegion.center,
            span: newSpan
        )
    }
    
    private func centerOnUserLocation() {
        guard let location = viewModel.userLocation else {
            return
        }
        mapRegion = MapRegion(
            center: location.coordinate,
            span: MKCoordinateSpan(latitudeDelta: 0.1, longitudeDelta: 0.1)
        )
    }
    
    private func shouldLoadMore(for item: any ExploreItem) -> Bool {
        guard viewModel.hasMorePages[viewModel.selectedCategory] == true, !viewModel.isLoading else {
            return false
        }
        
        switch viewModel.selectedCategory {
        case .diveSites:
            guard let site = item as? DiveSite,
                  let index = viewModel.diveSites.firstIndex(where: { $0.id == site.id }) else {
                return false
            }
            return index >= viewModel.diveSites.count - 3
        case .diveCenters:
            guard let center = item as? DiveCenter,
                  let index = viewModel.diveCenters.firstIndex(where: { $0.id == center.id }) else {
                return false
            }
            return index >= viewModel.diveCenters.count - 3
        case .shops:
            guard let shop = item as? Shop,
                  let index = viewModel.shops.firstIndex(where: { $0.id == shop.id }) else {
                return false
            }
            return index >= viewModel.shops.count - 3
        }
    }
    
    private func friendsVisitedCount(for item: any ExploreItem) -> Int? {
        // TODO: Implement friends visited logic
        return nil
    }
    
    private func isRecommended(item: any ExploreItem) -> Bool {
        // TODO: Implement recommendation logic based on user certification
        return false
    }
    
    private func distanceToItem(_ item: any ExploreItem) -> Double? {
        guard let userLocation = viewModel.userLocation else { return nil }
        let itemLocation = CLLocation(
            latitude: item.exploreLocation.latitude,
            longitude: item.exploreLocation.longitude
        )
        return userLocation.distance(from: itemLocation)
    }
    
    private func errorView(_ error: Error) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 50))
                .foregroundColor(.orange)
            Text(localizationService.localizedString("errorLoadingData"))
                .font(.headline)
            Text(error.localizedDescription)
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            Button(localizationService.localizedString("retry")) {
                Task {
                    await viewModel.loadData(refresh: true)
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct QuickFilter: Hashable {
    let title: String
    let isActive: Bool
    let action: () -> Void
    
    // Hashable conformance - use title as the hash value
    func hash(into hasher: inout Hasher) {
        hasher.combine(title)
        hasher.combine(isActive)
    }
    
    static func == (lhs: QuickFilter, rhs: QuickFilter) -> Bool {
        lhs.title == rhs.title && lhs.isActive == rhs.isActive
    }
}

struct QuickFilterChip: View {
    let title: String
    let isActive: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.caption)
                .fontWeight(.medium)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isActive ? Color.blue : Color(.systemGray5))
                .foregroundColor(isActive ? .white : .primary)
                .cornerRadius(16)
        }
    }
}

// Placeholder views for filters and detail
struct DiveCenterFilterView: View {
    @Binding var filters: DiveCenterFilters
    let diveCenters: [DiveCenter]
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var countryHelper = CountryLocalizationHelper.shared
    
    private var availableCountries: [String] {
        let centerCountries = Set(diveCenters.map { $0.location.country }.filter { !$0.isEmpty })
        if countryHelper.countryNames.isEmpty {
            return centerCountries.sorted()
        }
        return countryHelper.countryNames.filter { centerCountries.contains($0) }
    }
    
    var body: some View {
        NavigationView {
            Form {
                Section(localizationService.localizedString("country", table: "explore")) {
                    if availableCountries.isEmpty {
                        Text(localizationService.localizedString("noOptionsAvailable", table: "common"))
                            .foregroundColor(.secondary)
                    } else {
                        Picker(localizationService.localizedString("country", table: "explore"), selection: Binding<String?>(
                            get: { filters.country },
                            set: { filters.country = $0 }
                        )) {
                            Text(localizationService.localizedString("all")).tag(String?.none)
                            ForEach(availableCountries, id: \.self) { country in
                                Text(countryHelper.getLocalizedCountryName(country)).tag(String?.some(country))
                            }
                        }
                    }
                }
                
                Section("ui_rating".localized) {
                    Stepper("Min Rating: \(filters.minRating ?? 0, specifier: "%.1f")", value: Binding(
                        get: { filters.minRating ?? 0 },
                        set: { filters.minRating = $0 }
                    ), in: 0...5, step: 0.5)
                }
                
                Section("ui_admin_services".localized) {
                    Toggle("ui_explore_nitrox_available".localized, isOn: Binding(
                        get: { filters.nitroxAvailable ?? false },
                        set: { filters.nitroxAvailable = $0 }
                    ))
                }
            }
            .onAppear {
                countryHelper.ensureLoaded()
            }
            .navigationTitle(localizationService.localizedString("filters", table: "explore"))
            .diveHubNavigationChrome()
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("reset")) {
                        filters = DiveCenterFilters()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("apply")) {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct ShopFilterView: View {
    @Binding var filters: ShopFilters
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        NavigationView {
            Form {
                Section(localizationService.localizedString("type")) {
                    Picker("ui_explore_shop_type".localized, selection: Binding(
                        get: { filters.shopType },
                        set: { filters.shopType = $0 }
                    )) {
                        Text(localizationService.localizedString("all")).tag(ShopType?.none)
                        ForEach(ShopType.allCases, id: \.self) { type in
                            Text(type.displayName).tag(ShopType?.some(type))
                        }
                    }
                }
                
                Section(localizationService.localizedString("services")) {
                    Toggle(localizationService.localizedString("serviceAvailable", table: "explore"), isOn: Binding(
                        get: { filters.serviceAvailable ?? false },
                        set: { filters.serviceAvailable = $0 }
                    ))
                }
            }
            .navigationTitle(localizationService.localizedString("filters", table: "explore"))
            .diveHubNavigationChrome()
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("done")) {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct ShopDetailView: View {
    let shop: Shop
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showMessage = false
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Title and rating
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(shop.displayName)
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        if let city = shop.location.city, let country = shop.location.country, !city.isEmpty || !country.isEmpty {
                            Text([city, country].filter { !$0.isEmpty }.joined(separator: ", "))
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill")
                                .foregroundColor(.yellow)
                            Text(String(format: "%.1f", shop.averageRating))
                                .fontWeight(.semibold)
                        }
                        Text("(\(shop.reviewCount) \(localizationService.localizedString("reviews", table: "common")))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                
                Text(shop.displayDescription)
                    .foregroundColor(.secondary)
                
                if !shop.brands.isEmpty {
                    Text("Brands: \(shop.brands.joined(separator: ", "))")
                        .font(.subheadline)
                }
                
                // Reviews
                ReviewsSection(reviewableType: .shop, reviewableId: shop.id)
            }
            .padding()
        }
        .navigationTitle(shop.displayName)
        .diveHubNavigationChrome()
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showMessage = true }) {
                    Image(systemName: "message.fill")
                }
            }
        }
        .sheet(isPresented: $showMessage) {
            NavigationStack {
                BusinessChatLaunchView(
                    peerType: "shop",
                    peerId: shop.id,
                    title: shop.displayName
                )
            }
        }
    }
}

#Preview {
    ExploreView()
}
