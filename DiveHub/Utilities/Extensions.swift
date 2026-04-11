//
//  Extensions.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import SwiftUI
import CoreLocation

// MARK: - Date Extensions

extension Date {
    func formatted(style: DateFormatter.Style = .medium) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = style
        formatter.timeStyle = .none
        return formatter.string(from: self)
    }
    
    func formatted(date: DateFormatter.Style = .medium, time: DateFormatter.Style = .short) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = date
        formatter.timeStyle = time
        return formatter.string(from: self)
    }
}

// MARK: - Double Extensions

extension Double {
    func metersToFeet() -> Double {
        return self * 3.28084
    }
    
    func feetToMeters() -> Double {
        return self / 3.28084
    }
    
    func celsiusToFahrenheit() -> Double {
        return (self * 9/5) + 32
    }
    
    func fahrenheitToCelsius() -> Double {
        return (self - 32) * 5/9
    }
}

// MARK: - Color Extensions

extension Color {
    static let divePrimary = Color(red: 0.0, green: 0.5, blue: 0.8)
    static let diveSecondary = Color(red: 0.0, green: 0.7, blue: 0.9)
    static let diveAccent = Color(red: 1.0, green: 0.6, blue: 0.0)
    static let diveBackground = Color(red: 0.95, green: 0.95, blue: 0.97)
    static let diveCard = Color.white
    static let diveText = Color(red: 0.1, green: 0.1, blue: 0.1)
    static let diveTextSecondary = Color(red: 0.5, green: 0.5, blue: 0.5)
}

// MARK: - View Extensions

extension View {
    func cardStyle() -> some View {
        self
            .background(Color.diveCard)
            .cornerRadius(12)
            .shadow(color: Color.black.opacity(0.1), radius: 5, x: 0, y: 2)
    }
    
    func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

// MARK: - UserRole Extensions

extension UserRole {
    var canAccessProFeatures: Bool {
        return self == .diverPro || self == .instructor || self == .diveCenterAdmin || self == .shopAdmin || self == .superAdmin
    }
    
    var canManageBookings: Bool {
        return self == .instructor || self == .diveCenterAdmin || self == .shopAdmin || self == .superAdmin
    }
    
    var canManageCenter: Bool {
        return self == .diveCenterAdmin || self == .superAdmin
    }
}
