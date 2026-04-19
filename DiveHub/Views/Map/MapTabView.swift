//
//  MapTabView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import CoreLocation

struct MapTabView: View {
    @StateObject private var viewModel = MapViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showFilters = false
    @State private var showAddLog = false
    @State private var selectedSite: DiveSite?
    
    var body: some View {
        NavigationStack {
            mainContent
        }
    }
    
    private var mainContent: some View {
        mapContainer
            .navigationTitle(localizationService.localizedString("diveMap"))
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showFilters) {
                filterSheet
            }
            .sheet(isPresented: $showAddLog) {
                AddDiveLogView()
            }
            .sheet(item: $selectedSite) { site in
                NavigationStack {
                    DiveSiteDetailView(site: site)
                }
            }
            .onChange(of: viewModel.filters) { oldValue, newValue in
                handleFiltersChange(oldValue: oldValue, newValue: newValue)
            }
    }
    
    private var mapContainer: some View {
        ZStack {
            mapView
            overlayControls
        }
    }
    
    // MARK: - Subviews
    
    private var mapView: some View {
        OpenStreetMapView(
            region: $viewModel.region,
            annotations: $viewModel.annotations,
            showsUserLocation: $viewModel.showsUserLocation,
            onAnnotationTapped: { annotation in
                selectedSite = annotation.site
            }
        )
        .onChange(of: viewModel.region.center.latitude) { _, _ in
            viewModel.scheduleBoundsReload()
        }
        .onChange(of: viewModel.region.center.longitude) { _, _ in
            viewModel.scheduleBoundsReload()
        }
        .onChange(of: viewModel.region.zoom) { _, _ in
            viewModel.scheduleBoundsReload()
        }
        .ignoresSafeArea()
    }
    
    private var overlayControls: some View {
        VStack {
            HStack {
                Spacer()
                filterButton
                    .padding(.trailing, 16)
                    .padding(.top, 16)
            }
            Spacer()
            bottomRightControls
        }
        .allowsHitTesting(true)
        .contentShape(Rectangle())
    }
    
    private var filterButton: some View {
        Button(action: { showFilters.toggle() }) {
            Image(systemName: "line.3.horizontal.decrease.circle.fill")
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(Color.divePrimary)
                .frame(width: 44, height: 44)
                .background(.ultraThinMaterial, in: Circle())
                .overlay(
                    Circle()
                        .strokeBorder(Color.primary.opacity(0.10), lineWidth: 0.5)
                )
        }
        .buttonStyle(.plain)
    }
    
    private var bottomRightControls: some View {
        HStack {
            Spacer()
            locationAndAddButtons
            .padding(.trailing, 16)
            .padding(.bottom, 20)
        }
    }
    
    private var locationAndAddButtons: some View {
        VStack(spacing: 10) {
            MapChromeZoomCluster(onZoomIn: { viewModel.zoomIn() }, onZoomOut: { viewModel.zoomOut() })
            MapChromeLocateButton(action: { viewModel.centerOnUserLocation() })
            if shouldShowAddButton {
                addLogButton
            }
        }
    }
    
    private var addLogButton: some View {
        Button(action: { showAddLog.toggle() }) {
            Image(systemName: "plus.circle.fill")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(Color.divePrimary, in: Circle())
                .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
        }
        .buttonStyle(.plain)
    }
    
    private var shouldShowAddButton: Bool {
        AuthenticationService.shared.currentUser?.role.canAccessProFeatures == true
    }
    
    private var filterSheet: some View {
        FilterView(filters: $viewModel.filters, diveSites: availableDiveSites)
            .task {
                if viewModel.allDiveSites.isEmpty {
                    await viewModel.loadAllDiveSitesForFilters()
                }
            }
    }
    
    private var availableDiveSites: [DiveSite] {
        viewModel.allDiveSites.isEmpty ? viewModel.diveSites : viewModel.allDiveSites
    }
    
    // MARK: - Helper Methods
    
    private func handleFiltersChange(oldValue: DiveSiteFilters, newValue: DiveSiteFilters) {
        viewModel.loadDiveSites()
    }
}


#Preview {
    MapTabView()
}
