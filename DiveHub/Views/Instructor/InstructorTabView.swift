//
//  InstructorTabView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct InstructorTabView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            InstructorDashboardView()
                .tabItem {
                    Label(localizationService.localizedString("dashboard", table: "instructor"), systemImage: "house.fill")
                }
                .tag(0)
            
            InstructorScheduleView()
                .tabItem {
                    Label(localizationService.localizedString("schedule", table: "instructor"), systemImage: "calendar")
                }
                .tag(1)
            
            PhotoProcessingView()
                .tabItem {
                    Label(localizationService.localizedString("photoProcessing"), systemImage: "wand.and.stars")
                }
                .tag(2)
            
            ProfileTabView()
                .tabItem {
                    Label(localizationService.localizedString("profile"), systemImage: "person.circle")
                }
                .tag(3)
        }
        .accentColor(.divePrimary)
    }
}

#Preview {
    InstructorTabView()
}
