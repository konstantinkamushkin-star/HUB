//
//  SplashView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct SplashView: View {
    @StateObject private var authService = AuthenticationService.shared
    @State private var isActive = false
    @State private var size = 0.8
    @State private var opacity = 0.5
    
    var body: some View {
        if isActive {
            if authService.isAuthenticated {
                if authService.requiresPasswordReset {
                    ForcePasswordChangeView()
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
                    DiveHubLogoMark(color: .white)
                        .frame(width: 130, height: 100)
                        .scaleEffect(size)
                        .opacity(opacity)
                    
                    Text("DiveHub")
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
}
