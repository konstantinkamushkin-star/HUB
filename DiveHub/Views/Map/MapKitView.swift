//
//  MapKitView.swift
//  DiveHub
//
//  Apple MapKit SwiftUI wrapper (replaces Google Maps)
//

import SwiftUI
import MapKit
import CoreLocation

struct MapKitView: UIViewRepresentable {
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
        
        return mapView
    }
    
    func updateUIView(_ mapView: MKMapView, context: Context) {
        // Update region
        let mkRegion = MKCoordinateRegion(
            center: region.center,
            span: region.span
        )
        mapView.setRegion(mkRegion, animated: true)
        
        // Update user location
        mapView.showsUserLocation = showsUserLocation
        
        // Update annotations
        mapView.removeAnnotations(mapView.annotations.filter { !($0 is MKUserLocation) })
        
        for annotation in annotations {
            let mkAnnotation = DiveMapAnnotationMK(annotation: annotation)
            mapView.addAnnotation(mkAnnotation)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: MapKitView
        
        init(_ parent: MapKitView) {
            self.parent = parent
        }
        
        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            guard let annotation = view.annotation as? DiveMapAnnotationMK else { return }
            parent.onAnnotationTapped?(annotation.annotation)
        }
        
        func mapView(_ mapView: MKMapView, didDeselect view: MKAnnotationView) {
            // Handle deselection if needed
        }
        
        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            let center = mapView.region.center
            let span = mapView.region.span
            parent.region = MapRegion(center: center, span: span)
        }
        
        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            // Don't customize user location annotation
            if annotation is MKUserLocation {
                return nil
            }
            
            guard let diveAnnotation = annotation as? DiveMapAnnotationMK else {
                return nil
            }

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

// DiveMapAnnotationMK is now defined in Models/DiveMapAnnotationMK.swift

// MapRegion is now defined in Models/MapRegion.swift
