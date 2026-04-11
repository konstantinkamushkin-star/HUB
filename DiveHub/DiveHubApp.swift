//
//  DiveHubApp.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

@main
struct DiveHubApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var settingsService = SettingsService.shared

    init() {
        DiveHubNavigationAppearance.apply()
    }

    var body: some Scene {
        WindowGroup {
            SplashView()
                .environmentObject(authService)
                .environmentObject(localizationService)
                .environmentObject(settingsService)
                .preferredColorScheme(settingsService.themePreference.colorScheme)
        }
    }
}
