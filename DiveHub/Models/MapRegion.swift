//
//  MapRegion.swift
//  DiveHub
//
//  Shared MapRegion model for map views
//

import Foundation
import MapKit
import CoreLocation

// Helper struct to replace MKCoordinateRegion for compatibility
struct MapRegion {
    var center: CLLocationCoordinate2D
    var zoom: Float
    
    init(center: CLLocationCoordinate2D, zoom: Float = 10.0) {
        self.center = center
        self.zoom = zoom
    }
    
    init(center: CLLocationCoordinate2D, span: MKCoordinateSpan) {
        self.center = center
        // Convert span to zoom level (approximate)
        let latDelta = span.latitudeDelta
        let zoom = Float(log2(360.0 / latDelta))
        self.zoom = max(2.0, min(20.0, zoom))
    }
    
    var span: MKCoordinateSpan {
        let latDelta = 360.0 / pow(2.0, Double(zoom))
        return MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: latDelta)
    }
}
