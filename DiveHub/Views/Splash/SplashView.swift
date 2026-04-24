//
//  SplashView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct SplashView: View {
    @EnvironmentObject private var authService: AuthenticationService
    @State private var isActive = false
    @State private var size = 0.8
    @State private var opacity = 0.5

    #if DEBUG
    /// Симулятор: `xcrun simctl launch --env DH_APPSTORE_SCREENSHOTS=1 booted Dive-Hub.ru` — главные вкладки без логина (только DEBUG).
    private var appStoreScreenshotBypass: Bool {
        ProcessInfo.processInfo.environment["DH_APPSTORE_SCREENSHOTS"] == "1"
    }

    /// Заморозить сплэш для скриншота: `DH_APPSTORE_FREEZE_SPLASH=1` (вместе с обычным запуском, без SCREENSHOTS).
    private var appStoreFreezeSplash: Bool {
        ProcessInfo.processInfo.environment["DH_APPSTORE_FREEZE_SPLASH"] == "1"
    }
    #endif

    var body: some View {
        if isActive {
            postSplashContent
        } else {
            ZStack {
                Color.divePrimary
                    .ignoresSafeArea()
                
                VStack(spacing: 20) {
                    // Opaque raster flattened on divePrimary — avoids white fringe from alpha × scaling on the splash.
                    Image("BrandLogoSplash")
                        .resizable()
                        .interpolation(.high)
                        .scaledToFit()
                        .aspectRatio(1, contentMode: .fit)
                        .frame(width: 120, height: 120)
                        .scaleEffect(size)
                        .opacity(opacity)
                        .accessibilityLabel("DiveHub")
                    
                    Text("ui_splash_divehub".localized)
                        .font(.system(size: 40, weight: .bold))
                        .foregroundColor(.white)
                        .opacity(opacity)
                }
            }
            .onAppear {
                #if DEBUG
                if appStoreFreezeSplash {
                    withAnimation(.easeIn(duration: 0.35)) {
                        self.size = 0.9
                        self.opacity = 1.0
                    }
                    return
                }
                if appStoreScreenshotBypass {
                    self.size = 0.9
                    self.opacity = 1.0
                    self.isActive = true
                    return
                }
                #endif
                withAnimation(.easeIn(duration: 1.2)) {
                    self.size = 0.9
                    self.opacity = 1.0
                }
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    withAnimation {
                        self.isActive = true
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var postSplashContent: some View {
        #if DEBUG
        if appStoreScreenshotBypass {
            MainTabView()
        } else {
            standardPostSplashFlow
        }
        #else
        standardPostSplashFlow
        #endif
    }

    @ViewBuilder
    private var standardPostSplashFlow: some View {
        if authService.isAuthenticated {
            Group {
                if authService.requiresPasswordReset {
                    ForcePasswordChangeView()
                } else if authService.currentUser?.needsDiverProfileOnboarding == true {
                    ProfileOnboardingView()
                } else {
                    MainTabView()
                }
            }
            .onAppear {
                authService.presentPostRegistrationProWelcomeIfPending()
            }
            .fullScreenCover(
                isPresented: Binding(
                    get: { authService.showPostRegistrationProWelcome },
                    set: { newValue in
                        if newValue {
                            authService.showPostRegistrationProWelcome = true
                        } else {
                            authService.dismissPostRegistrationProWelcome()
                        }
                    }
                ),
                content: {
                    PostRegistrationProWelcomeView()
                        .environmentObject(authService)
                }
            )
        } else if UserDefaults.standard.bool(forKey: "has_completed_onboarding") {
            LoginView()
        } else {
            OnboardingView()
        }
    }
}

#Preview {
    SplashView()
        .environmentObject(AuthenticationService.shared)
        .environmentObject(LocalizationService.shared)
        .environmentObject(SettingsService.shared)
}
