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
            GeometryReader { geo in
                let s = settingsService.interfaceScale
                SplashView()
                    .environmentObject(authService)
                    .environmentObject(localizationService)
                    .environmentObject(settingsService)
                    .preferredColorScheme(settingsService.themePreference.colorScheme)
                    .frame(width: geo.size.width / s, height: geo.size.height / s)
                    .scaleEffect(s, anchor: .center)
                    .frame(width: geo.size.width, height: geo.size.height)
            }
            .ignoresSafeArea()
        }
    }
}
