//
//  ExploreMapView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import MapKit
import CoreLocation

struct ExploreMapView: View {
    let category: ExploreCategory
    let diveSites: [DiveSite]
    let diveCenters: [DiveCenter]
    let shops: [Shop]
    let onItemTapped: (any ExploreItem) -> Void
    @Binding var region: MapRegion
    @Binding var showsUserLocation: Bool
    
    @State private var annotations: [DiveMapAnnotation] = []
    
    var body: some View {
        OpenStreetMapView(
            region: $region,
            annotations: $annotations,
            showsUserLocation: $showsUserLocation,
            onAnnotationTapped: { annotation in
                // Convert DiveMapAnnotation back to ExploreItem
                if let site = annotation.site {
                    onItemTapped(site)
                } else if let center = annotation.center {
                    onItemTapped(center)
                } else if let shop = annotation.shop {
                    onItemTapped(shop)
                }
            }
        )
        .ignoresSafeArea()
        .onAppear {
            updateAnnotations()
        }
        .onChange(of: category) { _, _ in
            updateAnnotations()
        }
        .onChange(of: diveSites) { _, _ in
            if category == .diveSites {
                updateAnnotations()
            }
        }
        .onChange(of: diveCenters) { _, _ in
            if category == .diveCenters {
                updateAnnotations()
            }
        }
        .onChange(of: shops) { _, _ in
            if category == .shops {
                updateAnnotations()
            }
        }
    }
    
    private func updateAnnotations() {
        switch category {
        case .diveSites:
            annotations = diveSites.map { site in
                DiveMapAnnotation(
                    id: site.id,
                    coordinate: site.location.coordinate,
                    title: site.exploreName,
                    iconName: "divehub.logo",
                    color: .blue,
                    site: site,
                    center: nil,
                    shop: nil
                )
            }
        case .diveCenters:
            annotations = diveCenters.map { center in
                DiveMapAnnotation(
                    id: center.id,
                    coordinate: center.location.coordinate,
                    title: center.exploreName,
                    iconName: "building.2",
                    color: .green,
                    site: nil,
                    center: center,
                    shop: nil
                )
            }
        case .shops:
            annotations = shops.map { shop in
                DiveMapAnnotation(
                    id: shop.id,
                    coordinate: shop.location.coordinate,
                    title: shop.exploreName,
                    iconName: "bag",
                    color: .orange,
                    site: nil,
                    center: nil,
                    shop: shop
                )
            }
        }
    }
}
