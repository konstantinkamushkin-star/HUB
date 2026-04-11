//
//  SettingsService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine
import SwiftUI

/// Тема интерфейса: системная или принудительно светлая/тёмная.
enum AppThemePreference: String, Codable, CaseIterable {
    case system
    case light
    case dark

    /// Для `.preferredColorScheme`: `nil` = как на устройстве.
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

enum MeasurementUnit: String, Codable, CaseIterable {
    case metric = "metric"
    case imperial = "imperial"
    
    var displayName: String {
        switch self {
        case .metric: return "Metric (m, °C)"
        case .imperial: return "Imperial (ft, °F)"
        }
    }
}

enum DepthUnit: String, Codable {
    case meters = "meters"
    case feet = "feet"
}

enum TemperatureUnit: String, Codable {
    case celsius = "celsius"
    case fahrenheit = "fahrenheit"
}

struct MeasurementUnits: Codable {
    var depth: DepthUnit = .meters
    var temperature: TemperatureUnit = .celsius
    
    static var metric: MeasurementUnits {
        MeasurementUnits(depth: .meters, temperature: .celsius)
    }
    
    static var imperial: MeasurementUnits {
        MeasurementUnits(depth: .feet, temperature: .fahrenheit)
    }
}

class SettingsService: ObservableObject {
    static let shared = SettingsService()
    
    @Published var notificationSettings = NotificationSettings()
    @Published var privacySettings = PrivacySettings()
    @Published var measurementUnits = MeasurementUnits.metric
    @Published var themePreference: AppThemePreference = .system

    private let notificationSettingsKey = "notification_settings"
    private let privacySettingsKey = "privacy_settings"
    private let measurementUnitsKey = "measurement_units"
    private let themePreferenceKey = "app_theme_preference"
    
    private init() {
        loadSettings()
    }
    
    func loadSettings() {
        // Load from UserDefaults
        if let data = UserDefaults.standard.data(forKey: notificationSettingsKey),
           let settings = try? JSONDecoder().decode(NotificationSettings.self, from: data) {
            notificationSettings = settings
        }
        
        if let data = UserDefaults.standard.data(forKey: privacySettingsKey),
           let settings = try? JSONDecoder().decode(PrivacySettings.self, from: data) {
            privacySettings = settings
        }
        
        if let data = UserDefaults.standard.data(forKey: measurementUnitsKey),
           let units = try? JSONDecoder().decode(MeasurementUnits.self, from: data) {
            measurementUnits = units
        }

        if let raw = UserDefaults.standard.string(forKey: themePreferenceKey),
           let pref = AppThemePreference(rawValue: raw) {
            themePreference = pref
        }
    }

    func saveThemePreference(_ value: AppThemePreference) {
        themePreference = value
        UserDefaults.standard.set(value.rawValue, forKey: themePreferenceKey)
    }
    
    func saveNotificationSettings() {
        if let data = try? JSONEncoder().encode(notificationSettings) {
            UserDefaults.standard.set(data, forKey: notificationSettingsKey)
        }
        
        // Also save to API
        Task {
            await syncNotificationSettingsToAPI()
        }
    }
    
    func savePrivacySettings() {
        if let data = try? JSONEncoder().encode(privacySettings) {
            UserDefaults.standard.set(data, forKey: privacySettingsKey)
        }
        
        // Also save to API
        Task {
            await syncPrivacySettingsToAPI()
        }
    }
    
    private func syncNotificationSettingsToAPI() async {
        do {
            struct SettingsRequest: Codable {
                let pushNotifications: Bool
                let bookingReminders: Bool
                let friendActivity: Bool
                let newMessages: Bool
            }
            
            let request = SettingsRequest(
                pushNotifications: notificationSettings.pushNotifications,
                bookingReminders: notificationSettings.bookingReminders,
                friendActivity: notificationSettings.friendActivity,
                newMessages: notificationSettings.newMessages
            )
            
            _ = try await NetworkService.shared.request(
                endpoint: "/api/users/me/settings/notifications",
                method: .patch,
                body: request
            ) as EmptyResponse
        } catch {
            // Silently fail - settings are saved locally
        }
    }
    
    private func syncPrivacySettingsToAPI() async {
        do {
            struct PrivacyRequest: Codable {
                let shareLocation: Bool
                let publicProfile: Bool
                let showInFriendSearch: Bool
                let shareLogbook: Bool
            }
            
            let request = PrivacyRequest(
                shareLocation: privacySettings.shareLocation,
                publicProfile: privacySettings.publicProfile,
                showInFriendSearch: privacySettings.showInFriendSearch,
                shareLogbook: privacySettings.shareLogbook
            )
            
            _ = try await NetworkService.shared.request(
                endpoint: "/api/users/me/settings/privacy",
                method: .patch,
                body: request
            ) as EmptyResponse
        } catch {
            // Silently fail - settings are saved locally
        }
    }
    
    func saveMeasurementUnits(_ units: MeasurementUnits) {
        measurementUnits = units
        if let data = try? JSONEncoder().encode(units) {
            UserDefaults.standard.set(data, forKey: measurementUnitsKey)
        }
        
        // Recalculate all dive logs
        Task {
            await recalculateDiveLogs()
        }
    }
    
    private func recalculateDiveLogs() async {
        // This will be called when units change
        // The actual recalculation happens in the views when displaying
        // But we can trigger a reload notification
        NotificationCenter.default.post(name: .measurementUnitsChanged, object: nil)
    }
}

extension Notification.Name {
    static let measurementUnitsChanged = Notification.Name("measurementUnitsChanged")
}

struct NotificationSettings: Codable {
    var pushNotifications: Bool = true
    var bookingReminders: Bool = true
    var friendActivity: Bool = true
    var newMessages: Bool = true
}

struct PrivacySettings: Codable {
    var shareLocation: Bool = false
    var publicProfile: Bool = false
    var showInFriendSearch: Bool = true
    var shareLogbook: Bool = false // Allow others to see dives at dive sites
}
