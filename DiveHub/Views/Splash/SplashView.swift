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
    
    var body: some View {
        if isActive {
            if authService.isAuthenticated {
                if authService.requiresPasswordReset {
                    ForcePasswordChangeView()
                } else if authService.currentUser?.needsDiverProfileOnboarding == true {
                    ProfileOnboardingView()
                } else {
                    MainTabView()
                }
            } else if UserDefaults.standard.bool(forKey: "has_completed_onboarding") {
                LoginView()
            } else {
                OnboardingView()
            }
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
}

#Preview {
    SplashView()
        .environmentObject(AuthenticationService.shared)
        .environmentObject(LocalizationService.shared)
        .environmentObject(SettingsService.shared)
}
