//
//  OpenStreetMapView.swift
//  DiveHub
//
//  OpenStreetMap integration using MapKit with custom tile overlay
//

import SwiftUI
import MapKit
import CoreLocation

struct OpenStreetMapView: UIViewRepresentable {
    @Binding var region: MapRegion
    @Binding var annotations: [DiveMapAnnotation]
    @Binding var showsUserLocation: Bool
    var onAnnotationTapped: ((DiveMapAnnotation) -> Void)?
    var onMapTapped: ((CLLocationCoordinate2D) -> Void)?
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView(frame: .zero)
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = showsUserLocation
        mapView.userTrackingMode = .none
        mapView.mapType = .standard
        
        // Enable gestures
        mapView.isZoomEnabled = true
        mapView.isScrollEnabled = true
        mapView.isRotateEnabled = true
        mapView.isPitchEnabled = true
        
        context.coordinator.syncRasterBasemap(mapView: mapView)

        // Set initial region
        let mkRegion = MKCoordinateRegion(
            center: region.center,
            span: region.span
        )
        mapView.setRegion(mkRegion, animated: false)
        
        // Store initial region in coordinator to prevent unnecessary updates
        context.coordinator.lastSetRegion = mkRegion
        
        return mapView
    }
    
    func updateUIView(_ mapView: MKMapView, context: Context) {
        context.coordinator.syncRasterBasemap(mapView: mapView)

        // Only update region if user is not interacting with the map
        // and the region has actually changed
        if !context.coordinator.isUserInteracting {
            let mkRegion = MKCoordinateRegion(
                center: region.center,
                span: region.span
            )
            
            // Check if region has actually changed (with some tolerance)
            let currentCenter = mapView.region.center
            let currentSpan = mapView.region.span
            let centerDistance = abs(currentCenter.latitude - mkRegion.center.latitude) + abs(currentCenter.longitude - mkRegion.center.longitude)
            let spanDifference = abs(currentSpan.latitudeDelta - mkRegion.span.latitudeDelta) + abs(currentSpan.longitudeDelta - mkRegion.span.longitudeDelta)
            
            if centerDistance > 0.001 || spanDifference > 0.001 {
                mapView.setRegion(mkRegion, animated: true)
                context.coordinator.lastSetRegion = mkRegion
            }
        }
        
        // Update user location
        mapView.showsUserLocation = showsUserLocation
        
        // Update annotations only if they changed
        let currentAnnotationIds = Set(mapView.annotations.compactMap { ($0 as? DiveMapAnnotationMK)?.annotation.id })
        let newAnnotationIds = Set(annotations.map { $0.id })
        
        if currentAnnotationIds != newAnnotationIds {
            mapView.removeAnnotations(mapView.annotations.filter { !($0 is MKUserLocation) })
            
            for annotation in annotations {
                let mkAnnotation = DiveMapAnnotationMK(annotation: annotation)
                mapView.addAnnotation(mkAnnotation)
            }
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: OpenStreetMapView
        var isUserInteracting = false
        var lastSetRegion: MKCoordinateRegion?
        /// Tracks which basemap is applied (`nil` = not yet).
        private var appliedDarkBasemap: Bool?
        private var pendingRegionUpdate: MapRegion?
        private var updateTimer: Timer?
        
        init(_ parent: OpenStreetMapView) {
            self.parent = parent
        }
        
        deinit {
            updateTimer?.invalidate()
        }
        
        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            guard let annotation = view.annotation as? DiveMapAnnotationMK else { return }
            parent.onAnnotationTapped?(annotation.annotation)
        }
        
        func mapView(_ mapView: MKMapView, didDeselect view: MKAnnotationView) {
            // Handle deselection if needed
        }
        
        func mapView(_ mapView: MKMapView, regionWillChangeAnimated animated: Bool) {
            isUserInteracting = true
        }
        
        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            // Only update if this change was from user interaction
            guard isUserInteracting else { return }
            
            let center = mapView.region.center
            let span = mapView.region.span
            let newRegion = MapRegion(center: center, span: span)
            
            // Store the update
            pendingRegionUpdate = newRegion
            
            // Cancel any pending timer
            updateTimer?.invalidate()
            
            // Schedule update using RunLoop to ensure it happens outside view update cycle
            // This is the most reliable way to avoid SwiftUI warnings
            updateRegionBinding()
            
            // Reset interaction flag after a short delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
                self?.isUserInteracting = false
            }
        }
        
        private func updateRegionBinding() {
            guard let update = pendingRegionUpdate else { return }
            
            // Use RunLoop to schedule the update on the next iteration
            // This completely avoids the view update cycle
            let timer = Timer(timeInterval: 0.0, repeats: false) { [weak self] _ in
                guard let self = self else { return }
                // Update binding - this happens on the next run loop iteration
                self.parent.region = update
                self.pendingRegionUpdate = nil
            }
            RunLoop.main.add(timer, forMode: .common)
            updateTimer = timer
        }
        
        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            // Don't customize user location annotation
            if annotation is MKUserLocation {
                return nil
            }
            
            guard let diveAnnotation = annotation as? DiveMapAnnotationMK else {
                return nil
            }

            // Raster brand JPEG does not render inside MKMarkerAnnotationView glyph (often a white square).
            if diveAnnotation.annotation.iconName == "divehub.logo" {
                let id = DiveHubLogoMapPin.reuseIdentifier
                var view = mapView.dequeueReusableAnnotationView(withIdentifier: id)
                if view == nil {
                    view = MKAnnotationView(annotation: annotation, reuseIdentifier: id)
                }
                view?.annotation = annotation
                guard let logoView = view else { return nil }
                if let img = DiveHubLogoMapPin.pinImage() {
                    logoView.image = img
                    logoView.centerOffset = CGPoint(x: 0, y: -img.size.height / 2)
                }
                logoView.canShowCallout = true
                logoView.rightCalloutAccessoryView = UIButton(type: .detailDisclosure)
                return logoView
            }

            let identifier = "DiveAnnotation"
            var annotationView = mapView.dequeueReusableAnnotationView(withIdentifier: identifier)
            
            if annotationView == nil {
                annotationView = MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: identifier)
                annotationView?.canShowCallout = true
            } else {
                annotationView?.annotation = annotation
            }
            
            if let markerView = annotationView as? MKMarkerAnnotationView {
                markerView.markerTintColor = UIColor(diveAnnotation.annotation.color)
                Self.applyGlyph(to: markerView, iconName: diveAnnotation.annotation.iconName)
            }
            
            let button = UIButton(type: .detailDisclosure)
            annotationView?.rightCalloutAccessoryView = button
            
            return annotationView
        }
        
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tileOverlay = overlay as? MKTileOverlay {
                return MKTileOverlayRenderer(tileOverlay: tileOverlay)
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        /// Swap raster tiles when light/dark mode changes (Carto Dark vs OSM).
        func syncRasterBasemap(mapView: MKMapView) {
            let dark = mapView.traitCollection.userInterfaceStyle == .dark
            if appliedDarkBasemap == dark { return }
            appliedDarkBasemap = dark
            let existing = mapView.overlays.compactMap { $0 as? MKTileOverlay }
            mapView.removeOverlays(existing)
            let overlay = DiveHubRasterTileOverlay(dark: dark)
            overlay.canReplaceMapContent = true
            mapView.addOverlay(overlay, level: .aboveLabels)
        }
        
        func mapView(_ mapView: MKMapView, annotationView view: MKAnnotationView, calloutAccessoryControlTapped control: UIControl) {
            guard let annotation = view.annotation as? DiveMapAnnotationMK else { return }
            parent.onAnnotationTapped?(annotation.annotation)
        }

        private static func applyGlyph(to markerView: MKMarkerAnnotationView, iconName: String) {
            markerView.glyphImage = UIImage(systemName: iconName)?.withRenderingMode(.alwaysTemplate)
            markerView.glyphTintColor = .white
        }
    }
}

/// Light: OpenStreetMap raster. Dark: Carto Dark Matter (readable at night).
final class DiveHubRasterTileOverlay: MKTileOverlay {
    private let dark: Bool

    init(dark: Bool) {
        self.dark = dark
        let template = dark
            ? "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
            : "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        super.init(urlTemplate: template)
        minimumZ = 0
        maximumZ = 19
        canReplaceMapContent = true
    }

    override func url(forTilePath path: MKTileOverlayPath) -> URL {
        if dark {
            return URL(string: String(
                format: "https://basemaps.cartocdn.com/dark_all/%d/%d/%d.png",
                path.z, path.x, path.y
            ))!
        }
        let subdomain = ["a", "b", "c"][Int(path.x + path.y) % 3]
        let urlString = String(
            format: "https://%@.tile.openstreetmap.org/%d/%d/%d.png",
            subdomain, path.z, path.x, path.y
        )
        return URL(string: urlString)!
    }
}

// DiveMapAnnotationMK is now defined in Models/DiveMapAnnotationMK.swift
