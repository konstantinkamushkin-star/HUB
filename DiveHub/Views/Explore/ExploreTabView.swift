//
//  ExploreTabView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import MapKit

enum ViewMode {
    case list
    case map
}

struct ExploreTabView: View {
    @StateObject private var viewModel = ExploreViewModel()
    @StateObject private var mapViewModel = MapViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var searchText = ""
    @State private var showFilters = false
    @State private var viewMode: ViewMode = .list
    @State private var selectedSite: DiveSite?
    @State private var selectedCenter: DiveCenter?
    @State private var showOnMapSite: DiveSite?
    @State private var showOnMapCenter: DiveCenter?
    @State private var showSuggestNewDiveSite = false
    
    var body: some View {
        NavigationView {
            mainContent
                .navigationTitle(localizationService.localizedString("explore"))
                .toolbar {
                    toolbarContent
                }
                .sheet(isPresented: $showFilters) {
                    filterSheet
                }
                .sheet(item: $selectedSite) { site in
                    siteDetailSheet(site: site)
                }
                .sheet(item: $selectedCenter) { center in
                    centerDetailSheet(center: center)
                }
                .sheet(isPresented: $showSuggestNewDiveSite) {
                    DiveSiteContributionSheet(mode: .newSite)
                }
                .onChange(of: showOnMapSite) { oldValue, site in
                    handleShowOnMapSite(site)
                }
                .onChange(of: showOnMapCenter) { oldValue, center in
                    handleShowOnMapCenter(center)
                }
                .task {
                    await viewModel.loadData()
                    // Ensure all dive sites are loaded for filter options
                    if viewModel.allDiveSites.isEmpty {
                        await viewModel.loadAllDiveSitesForFilters()
                    }
                    mapViewModel.filters = viewModel.filters
                    mapViewModel.loadDiveSites()
                }
                .onChange(of: viewModel.filters) { oldValue, newValue in
                    mapViewModel.filters = newValue
                    mapViewModel.loadDiveSites()
                }
        }
    }
    
    private var mainContent: some View {
        VStack(spacing: 0) {
            // Search Bar
            SearchBar(text: $searchText, onSearchButtonClicked: {
                viewModel.search(query: searchText)
            })
            
            // View Mode Toggle
            viewModePicker
            
            // Content
            contentView
        }
    }
    
    private var viewModePicker: some View {
        Picker(localizationService.localizedString("viewMode"), selection: $viewMode) {
            Label(localizationService.localizedString("list"), systemImage: "list.bullet").tag(ViewMode.list)
            Label(localizationService.localizedString("map"), systemImage: "map").tag(ViewMode.map)
        }
        .pickerStyle(.segmented)
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
    
    @ViewBuilder
    private var contentView: some View {
        if viewModel.isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error = viewModel.error {
            errorView(error)
        } else {
            if viewMode == .list {
                listView
            } else {
                mapView
            }
        }
    }
    
    private func errorView(_ error: Error) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 50))
                .foregroundColor(.orange)
            Text(localizationService.localizedString("errorLoadingData", table: "errors"))
                .font(.headline)
            Text(error.localizedDescription)
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            Button(localizationService.localizedString("retry")) {
                Task {
                    await viewModel.loadData()
                    mapViewModel.loadDiveSites()
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            NavigationLink(destination: SearchView()) {
                Image(systemName: "magnifyingglass")
            }
        }
        ToolbarItem(placement: .navigationBarTrailing) {
            if AuthenticationService.shared.isAuthenticated {
                Button(action: { showSuggestNewDiveSite = true }) {
                    Image(systemName: "plus.rectangle.on.folder")
                }
                .accessibilityLabel(localizationService.localizedString("suggestNewDiveSite", table: "diveSite"))
            }
        }
        ToolbarItem(placement: .navigationBarTrailing) {
            Button(action: { showFilters.toggle() }) {
                Image(systemName: "line.3.horizontal.decrease.circle")
            }
        }
    }
    
    private var filterSheet: some View {
        FilterView(filters: $viewModel.filters, diveSites: viewModel.allDiveSites.isEmpty ? viewModel.diveSites : viewModel.allDiveSites)
            .task {
                // Load all dive sites for filter options if not already loaded
                if viewModel.allDiveSites.isEmpty {
                    await viewModel.loadAllDiveSitesForFilters()
                }
            }
            .onDisappear {
                Task {
                    mapViewModel.filters = viewModel.filters
                    await viewModel.loadData()
                    mapViewModel.loadDiveSites()
                }
            }
    }
    
    private func siteDetailSheet(site: DiveSite) -> some View {
        NavigationView {
            DiveSiteDetailView(site: site, onShowOnMap: {
                showOnMapSite = site
                viewMode = .map
            })
        }
    }
    
    private func centerDetailSheet(center: DiveCenter) -> some View {
        NavigationView {
            DiveCenterPublicView(center: center, onShowOnMap: {
                showOnMapCenter = center
                viewMode = .map
            })
        }
    }
    
    private func handleShowOnMapSite(_ site: DiveSite?) {
        guard let site = site else { return }
        let span = MKCoordinateSpan(latitudeDelta: 0.1, longitudeDelta: 0.1)
        mapViewModel.region = MapRegion(
            center: site.location.coordinate,
            span: span
        )
    }
    
    private func handleShowOnMapCenter(_ center: DiveCenter?) {
        guard let center = center else { return }
        let span = MKCoordinateSpan(latitudeDelta: 0.1, longitudeDelta: 0.1)
        mapViewModel.region = MapRegion(
            center: center.location.coordinate,
            span: span
        )
    }
    
    private var listView: some View {
        List {
            Section(localizationService.localizedString("diveSites", table: "explore")) {
                if viewModel.diveSites.isEmpty {
                    Text(localizationService.localizedString("noDiveSitesFound", table: "explore"))
                        .foregroundColor(.secondary)
                        .font(.caption)
                } else {
                    ForEach(viewModel.diveSites) { site in
                        Button(action: {
                            selectedSite = site
                        }) {
                            DiveSiteRow(site: site)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            
            Section(localizationService.localizedString("diveCenters", table: "explore")) {
                if viewModel.diveCenters.isEmpty {
                    Text(localizationService.localizedString("noDiveCentersFound", table: "explore"))
                        .foregroundColor(.secondary)
                        .font(.caption)
                } else {
                    ForEach(viewModel.diveCenters) { center in
                        Button(action: {
                            selectedCenter = center
                        }) {
                            DiveCenterRow(center: center)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
    
    private var mapView: some View {
        ZStack {
            OpenStreetMapView(
                region: $mapViewModel.region,
                annotations: $mapViewModel.annotations,
                showsUserLocation: $mapViewModel.showsUserLocation,
                onAnnotationTapped: { annotation in
                    if let site = annotation.site {
                        selectedSite = site
                    } else if let center = annotation.center {
                        selectedCenter = center
                    }
                }
            )
            .onChange(of: mapViewModel.region.center.latitude) { _, _ in
                if viewMode == .map { mapViewModel.scheduleBoundsReload() }
            }
            .onChange(of: mapViewModel.region.center.longitude) { _, _ in
                if viewMode == .map { mapViewModel.scheduleBoundsReload() }
            }
            .onChange(of: mapViewModel.region.zoom) { _, _ in
                if viewMode == .map { mapViewModel.scheduleBoundsReload() }
            }
            .ignoresSafeArea()
            
            VStack {
                HStack {
                    Spacer()
                }
                .padding()
                
                Spacer()
                
                HStack {
                    Button(action: { mapViewModel.centerOnUserLocation() }) {
                        Image(systemName: "location.fill")
                            .font(.title2)
                            .foregroundColor(.divePrimary)
                            .padding()
                            .background(Color.white)
                            .clipShape(Circle())
                            .shadow(radius: 5)
                    }
                    .padding()
                    
                    if AuthenticationService.shared.currentUser?.role.canAccessProFeatures == true {
                        NavigationLink(destination: AddDiveLogView()) {
                            Image(systemName: "plus.circle.fill")
                                .font(.title)
                                .foregroundColor(.white)
                                .padding()
                                .background(Color.divePrimary)
                                .clipShape(Circle())
                                .shadow(radius: 5)
                        }
                        .padding()
                    }
                }
            }
        }
    }
}

struct DiveSiteRow: View {
    let site: DiveSite
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(site.displayName)
                    .font(.headline)
                Text(site.siteType.displayName)
                    .font(.caption)
                    .foregroundColor(.secondary)
                HStack {
                    Label(String(format: "%.1f", site.averageRating), systemImage: "star.fill")
                        .font(.caption)
                    Text("(\(site.reviewCount))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            Spacer()
            VStack(alignment: .trailing) {
                Text("\(Int(site.maxDepth))m")
                    .font(.headline)
                Text(site.difficulty.displayName)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

struct DiveCenterRow: View {
    let center: DiveCenter
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(center.name)
                    .font(.headline)
                Text(center.location.city)
                    .font(.caption)
                    .foregroundColor(.secondary)
                HStack {
                    Label(String(format: "%.1f", center.averageRating), systemImage: "star.fill")
                        .font(.caption)
                    Text("(\(center.reviewCount))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            Spacer()
        }
        .padding(.vertical, 4)
    }
}

struct SearchBar: View {
    @Binding var text: String
    var onSearchButtonClicked: () -> Void
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        HStack {
            TextField(localizationService.localizedString("searchDiveSitesCenters", table: "explore"), text: $text)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .onSubmit {
                    onSearchButtonClicked()
                }
            Button(action: onSearchButtonClicked) {
                Image(systemName: "magnifyingglass")
            }
        }
        .padding()
    }
}

#Preview {
    ExploreTabView()
}
