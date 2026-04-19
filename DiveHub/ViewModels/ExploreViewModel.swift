//
//  ExploreViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine
import CoreLocation

@MainActor
class ExploreViewModel: ObservableObject {
    @Published var diveSites: [DiveSite] = []
    @Published var allDiveSites: [DiveSite] = []
    @Published var diveCenters: [DiveCenter] = []
    @Published var filters = DiveSiteFilters()
    @Published var isLoading = false
    @Published var error: Error?
    
    private let locationManager = CLLocationManager()
    private let locationDelegate = LocationManagerDelegate()
    private var cancellables = Set<AnyCancellable>()
    
    /// Used for map / distance sort only; no longer written into `filters`.
    @Published var userLocation: CLLocation?
    
    init() {
        setupLocationManager()
    }
    
    private func setupLocationManager() {
        locationManager.delegate = locationDelegate
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
        locationManager.distanceFilter = 1000
        
        let status = locationManager.authorizationStatus
        
        if status == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        } else if status == .authorizedWhenInUse || status == .authorizedAlways {
            locationManager.startUpdatingLocation()
        }
        
        locationDelegate.lastLocationSubject
            .compactMap { $0 }
            .debounce(for: .seconds(1), scheduler: DispatchQueue.main)
            .assign(to: &$userLocation)
        
        locationDelegate.authorizationStatusSubject
            .sink { [weak self] status in
                guard let self = self else { return }
                if status == .authorizedWhenInUse || status == .authorizedAlways {
                    self.locationManager.startUpdatingLocation()
                } else {
                    self.locationManager.stopUpdatingLocation()
                }
            }
            .store(in: &cancellables)
    }
    
    func loadData() async {
        isLoading = true
        error = nil
        
        GeoCacheService.shared.clearExpired()
        
        do {
            let loadedSites: [DiveSite]
            do {
                loadedSites = try await NetworkService.shared.getDiveSites(filters: filters, page: 1, limit: 100)
            } catch {
                loadedSites = try await NetworkService.shared.getPopularDiveSites(
                    country: filters.country,
                    limit: 100
                )
            }
            
            let loadedCenters = try await NetworkService.shared.getPopularDiveCenters(
                country: filters.country,
                limit: 100
            )
            
            diveSites = loadedSites
            diveCenters = loadedCenters
            
            if allDiveSites.isEmpty {
                let emptyFilters = DiveSiteFilters()
                do {
                    allDiveSites = try await NetworkService.shared.getDiveSites(filters: emptyFilters, page: 1, limit: 200)
                } catch {
                    allDiveSites = try await NetworkService.shared.getPopularDiveSites(limit: 200)
                }
            }
            
            isLoading = false
        } catch {
            self.error = error
            isLoading = false
        }
    }
    
    func loadAllDiveSitesForFilters() async {
        do {
            let emptyFilters = DiveSiteFilters()
            allDiveSites = try await NetworkService.shared.getDiveSites(filters: emptyFilters, page: 1, limit: 200)
        } catch {
            do {
                allDiveSites = try await NetworkService.shared.getPopularDiveSites(limit: 200)
            } catch {
                print("Error loading all dive sites for filters: \(error)")
            }
        }
    }
    
    func search(query: String) {
        Task {
            await loadData()
        }
    }
}
