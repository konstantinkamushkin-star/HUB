//
//  MapViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import MapKit
import Combine
import CoreLocation
import SwiftUI

private let mapBoundsReloadDebounceNs: UInt64 = 400_000_000

struct DiveMapAnnotation: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let title: String
    let iconName: String
    let color: Color
    let site: DiveSite?
    let center: DiveCenter?
    let shop: Shop?
}

class LocationManagerDelegate: NSObject, CLLocationManagerDelegate {
    let lastLocationSubject = PassthroughSubject<CLLocation?, Never>()
    let authorizationStatusSubject = CurrentValueSubject<CLAuthorizationStatus, Never>(.notDetermined)
    
    var lastLocation: CLLocation? {
        didSet {
            lastLocationSubject.send(lastLocation)
        }
    }
    
    var authorizationStatus: CLAuthorizationStatus = .notDetermined {
        didSet {
            authorizationStatusSubject.send(authorizationStatus)
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        lastLocation = locations.last
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Handle location errors if needed
    }
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
    }
}

@MainActor
class MapViewModel: ObservableObject {
    // Initial map region - centered on a popular diving area (Caribbean)
    // Will be updated when dive sites are loaded or user location is available
    @Published var region = MapRegion(
        center: CLLocationCoordinate2D(latitude: 20.0, longitude: -80.0),
        span: MKCoordinateSpan(latitudeDelta: 50.0, longitudeDelta: 50.0) // Wider span to show more sites
    )
    @Published var annotations: [DiveMapAnnotation] = []
    @Published var diveSites: [DiveSite] = []
    @Published var allDiveSites: [DiveSite] = [] // All dive sites without filters, for filter options
    @Published var filters = DiveSiteFilters()
    @Published var isLoading = false
    @Published var error: Error?
    @Published var userLocation: CLLocation?
    @Published var showsUserLocation = false
    
    private let locationManager = CLLocationManager()
    private let locationDelegate = LocationManagerDelegate()
    private var cancellables = Set<AnyCancellable>()
    private var didApplyInitialUserRegion = false
    private var boundsReloadTask: Task<Void, Never>?
    
    init() {
        setupLocationManager()
        loadDiveSites()
        observeLocationUpdates()
        // Load all dive sites for filter options
        Task {
            await loadAllDiveSitesForFilters()
        }
    }
    
    private func setupLocationManager() {
        locationManager.delegate = locationDelegate
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = 10 // Update every 10 meters
        
        let status = locationManager.authorizationStatus
        
        if status == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        } else if status == .authorizedWhenInUse || status == .authorizedAlways {
            locationManager.startUpdatingLocation()
            showsUserLocation = true
        }
    }
    
    private func observeLocationUpdates() {
        locationDelegate.lastLocationSubject
            .compactMap { $0 }
            .debounce(for: .seconds(2), scheduler: DispatchQueue.main) // Debounce location updates
            .sink { [weak self] location in
                guard let self = self else { return }
                self.userLocation = location

                if !self.didApplyInitialUserRegion {
                    self.didApplyInitialUserRegion = true
                    self.region = MapRegion(
                        center: location.coordinate,
                        span: MKCoordinateSpan(latitudeDelta: 0.45, longitudeDelta: 0.45)
                    )
                }
                
                #if DEBUG
                print("📍 [MapViewModel] Location updated: \(location.coordinate.latitude), \(location.coordinate.longitude)")
                #endif
                
                // Reload sites when location changes significantly (optional)
                // Uncomment if you want auto-reload on location change
                // self.loadDiveSites()
            }
            .store(in: &cancellables)
        
        locationDelegate.authorizationStatusSubject
            .sink { [weak self] status in
                guard let self = self else { return }
                if status == .authorizedWhenInUse || status == .authorizedAlways {
                    self.locationManager.startUpdatingLocation()
                    self.showsUserLocation = true
                } else {
                    self.locationManager.stopUpdatingLocation()
                    self.showsUserLocation = false
                }
            }
            .store(in: &cancellables)
    }
    
    func centerOnUserLocation() {
        guard let location = userLocation ?? locationManager.location else {
            return
        }
        
        region = MapRegion(
            center: location.coordinate,
            span: MKCoordinateSpan(latitudeDelta: 0.1, longitudeDelta: 0.1)
        )
    }
    
    // Center map on all annotations (dive sites and centers)
    func centerMapOnAnnotations(_ annotations: [DiveMapAnnotation]) {
        guard !annotations.isEmpty else { return }
        
        // Calculate bounding box
        var minLat = annotations[0].coordinate.latitude
        var maxLat = annotations[0].coordinate.latitude
        var minLng = annotations[0].coordinate.longitude
        var maxLng = annotations[0].coordinate.longitude
        
        for annotation in annotations {
            let lat = annotation.coordinate.latitude
            let lng = annotation.coordinate.longitude
            
            minLat = min(minLat, lat)
            maxLat = max(maxLat, lat)
            minLng = min(minLng, lng)
            maxLng = max(maxLng, lng)
        }
        
        // Calculate center
        let centerLat = (minLat + maxLat) / 2
        let centerLng = (minLng + maxLng) / 2
        
        // Calculate span with padding
        let latDelta = max((maxLat - minLat) * 1.3, 0.1) // Add 30% padding, minimum 0.1
        let lngDelta = max((maxLng - minLng) * 1.3, 0.1)
        
        #if DEBUG
        print("🗺️ [MapViewModel] Centering map on \(annotations.count) annotations")
        print("   Center: lat=\(centerLat), lng=\(centerLng)")
        print("   Span: latDelta=\(latDelta), lngDelta=\(lngDelta)")
        print("   Bounds: lat=[\(minLat), \(maxLat)], lng=[\(minLng), \(maxLng)]")
        #endif
        
        region = MapRegion(
            center: CLLocationCoordinate2D(latitude: centerLat, longitude: centerLng),
            span: MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lngDelta)
        )
    }
    
    func zoomIn() {
        let currentSpan = region.span
        let newSpan = MKCoordinateSpan(
            latitudeDelta: currentSpan.latitudeDelta * 0.5,
            longitudeDelta: currentSpan.longitudeDelta * 0.5
        )
        region = MapRegion(
            center: region.center,
            span: newSpan
        )
    }
    
    func zoomOut() {
        let currentSpan = region.span
        let newSpan = MKCoordinateSpan(
            latitudeDelta: min(currentSpan.latitudeDelta * 2.0, 180.0),
            longitudeDelta: min(currentSpan.longitudeDelta * 2.0, 360.0)
        )
        region = MapRegion(
            center: region.center,
            span: newSpan
        )
    }
    
    func loadDiveSites() {
        isLoading = true
        error = nil
        
        Task {
            do {
                let sites: [DiveSite]
                let centers: [DiveCenter]
                
                // Calculate bounding box from current map region
                let center = region.center
                let span = region.span
                let north = center.latitude + span.latitudeDelta / 2
                let south = center.latitude - span.latitudeDelta / 2
                let east = center.longitude + span.longitudeDelta / 2
                let west = center.longitude - span.longitudeDelta / 2
                
                // Check cache first
                if let cached = GeoCacheService.shared.getCachedBounds(
                    north: north,
                    south: south,
                    east: east,
                    west: west,
                    filters: filters
                ) {
                    sites = cached
                    #if DEBUG
                    print("📍 [MapViewModel] Using cached bounds results: \(cached.count) sites")
                    #endif
                } else {
                    // Try optimized bounding box search for map
                    do {
                        sites = try await NetworkService.shared.searchDiveSitesInBounds(
                            north: north,
                            south: south,
                            east: east,
                            west: west,
                            filters: filters,
                            limit: 500
                        )
                        
                        #if DEBUG
                        print("📍 [MapViewModel] Bounds search successful: \(sites.count) sites")
                        #endif
                        
                        // Cache the result
                        GeoCacheService.shared.cacheBounds(
                            sites: sites,
                            north: north,
                            south: south,
                            east: east,
                            west: west,
                            filters: filters
                        )
                    } catch let error as NetworkError {
                        // If new API fails (404 = endpoint doesn't exist), fallback to legacy API
                        #if DEBUG
                        if case .serverError(404) = error {
                            print("⚠️ [MapViewModel] Bounds search API endpoint not found (404), using legacy API")
                        } else {
                            print("⚠️ [MapViewModel] Bounds search API failed, using legacy API: \(error.localizedDescription)")
                        }
                        #endif
                        sites = try await NetworkService.shared.getDiveSites(filters: filters)
                    } catch {
                        // Any other error - fallback to legacy API
                        #if DEBUG
                        print("⚠️ [MapViewModel] Bounds search API error, using legacy API: \(error.localizedDescription)")
                        #endif
                        sites = try await NetworkService.shared.getDiveSites(filters: filters)
                    }
                }
                
                // Load centers using new optimized bounding box search
                do {
                    centers = try await NetworkService.shared.searchDiveCentersInBounds(
                        north: north,
                        south: south,
                        east: east,
                        west: west,
                        filters: nil,
                        limit: 500
                    )
                    
                    #if DEBUG
                    print("📍 [MapViewModel] Dive centers bounds search successful: \(centers.count) centers")
                    #endif
                } catch {
                    // Fallback to legacy API
                    #if DEBUG
                    print("⚠️ [MapViewModel] Dive centers bounds search failed, using legacy API: \(error.localizedDescription)")
                    #endif
                    centers = try await NetworkService.shared.getDiveCenters()
                }
                
                var newAnnotations: [DiveMapAnnotation] = []
                
                // Add dive sites
                for site in sites {
                    let finalCoord = site.location.coordinate
                    
                    
                    // Debug: Log annotation creation
                    #if DEBUG
                    print("🗺️ [MapViewModel] Creating annotation: \(site.name)")
                    print("   Location: lat=\(site.location.latitude), lng=\(site.location.longitude)")
                    print("   Coordinate: lat=\(finalCoord.latitude), lng=\(finalCoord.longitude)")
                    print("   Country: \(site.location.address ?? "unknown")")
                    #endif
                    
                    newAnnotations.append(DiveMapAnnotation(
                        id: site.id,
                        coordinate: finalCoord,
                        title: site.displayName,
                        iconName: "divehub.logo",
                        color: .blue,
                        site: site,
                        center: nil,
                        shop: nil
                    ))
                }
                
                // Add dive centers
                for center in centers {
                    newAnnotations.append(DiveMapAnnotation(
                        id: center.id,
                        coordinate: center.location.coordinate,
                        title: center.name,
                        iconName: "building.2",
                        color: .orange,
                        site: nil,
                        center: center,
                        shop: nil
                    ))
                }
                
                annotations = newAnnotations
                diveSites = sites
                
                // Keep user-centered map when we already moved to GPS; otherwise fit annotations once.
                if !newAnnotations.isEmpty, !didApplyInitialUserRegion {
                    centerMapOnAnnotations(newAnnotations)
                }
                
                isLoading = false
            } catch {
                self.error = error
                isLoading = false
            }
        }
    }
    
    /// Call when the user pans/zooms the map; loads markers for the visible bounds (debounced, like Google Maps).
    func scheduleBoundsReload() {
        boundsReloadTask?.cancel()
        boundsReloadTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: mapBoundsReloadDebounceNs)
            guard !Task.isCancelled else { return }
            loadDiveSites()
        }
    }
    
    func loadAllDiveSitesForFilters() async {
        do {
            let emptyFilters = DiveSiteFilters()
            allDiveSites = try await NetworkService.shared.getDiveSites(filters: emptyFilters, page: 1, limit: 300)
        } catch {
            print("Error loading all dive sites for filters: \(error)")
        }
    }
}
