//
//  DiveMapAnnotationMK.swift
//  DiveHub
//
//  MapKit annotation wrapper for DiveMapAnnotation
//

import Foundation
import MapKit
import CoreLocation

// Custom MKAnnotation class for dive sites and centers
class DiveMapAnnotationMK: NSObject, MKAnnotation {
    let annotation: DiveMapAnnotation
    var coordinate: CLLocationCoordinate2D {
        annotation.coordinate
    }
    var title: String? {
        annotation.title
    }
    
    init(annotation: DiveMapAnnotation) {
        self.annotation = annotation
        super.init()
    }
}
