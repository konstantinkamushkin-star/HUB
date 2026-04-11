//
//  ProfileTabView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct ProfileTabView: View {
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var settingsService = SettingsService.shared
    @State private var showSettings = false

    private var appearanceSummary: String {
        switch settingsService.themePreference {
        case .system:
            return localizationService.localizedString("appearanceSystem", table: "settings")
        case .light:
            return localizationService.localizedString("appearanceLight", table: "settings")
        case .dark:
            return localizationService.localizedString("appearanceDark", table: "settings")
        }
    }

    var body: some View {
        NavigationStack {
            List {
                if let user = authService.currentUser {
                    Section {
                        ProfileHeaderView(user: user)
                    }
                    
                    // Show different sections based on user role
                    if user.role == .diveCenterAdmin || user.role == .instructor {
                        Section(localizationService.localizedString("centerManagement")) {
                            NavigationLink(destination: DiveCenterAdminView()) {
                                Label("Dive Center Management", systemImage: "building.2")
                            }
                        }
                    }
                    
                    // Instructor mode toggle
                    if user.role == .instructor {
                        Section(localizationService.localizedString("mode", table: "settings")) {
                            InstructorModeToggleView()
                        }
                    }
                    
                    Section(localizationService.localizedString("account")) {
                        NavigationLink(destination: EditProfileView()) {
                            Label(localizationService.localizedString("editProfile"), systemImage: "pencil")
                        }
                        
                        if user.role == .diverBasic {
                            NavigationLink(destination: SubscriptionView()) {
                                Label(localizationService.localizedString("upgradeToPro"), systemImage: "star.fill")
                                    .foregroundColor(.diveAccent)
                            }
                        } else if user.role == .diverPro {
                            NavigationLink(destination: SubscriptionView()) {
                                Label(localizationService.localizedString("proSubscription"), systemImage: "checkmark.circle.fill")
                                    .foregroundColor(.green)
                            }
                        }
                        
                        NavigationLink(destination: CertificationsView()) {
                            Label(localizationService.localizedString("certifications"), systemImage: "doc.text")
                        }
                        
                        NavigationLink(destination: GearProfilesView()) {
                            Label(localizationService.localizedString("gearProfiles"), systemImage: "bag")
                        }
                        
                        NavigationLink(destination: StatisticsView()) {
                            Label(localizationService.localizedString("statistics"), systemImage: "chart.bar")
                        }
                        
                        NavigationLink(destination: AchievementsView()) {
                            Label(localizationService.localizedString("achievements"), systemImage: "trophy")
                        }
                    }
                    
                    Section(localizationService.localizedString("settings")) {
                        NavigationLink(destination: NotificationsView()) {
                            Label(localizationService.localizedString("notifications"), systemImage: "bell")
                        }
                        
                        NavigationLink(destination: LanguageSettingsView()) {
                            HStack {
                                Label(localizationService.localizedString("language"), systemImage: "globe")
                                Spacer()
                                Text(localizationService.currentLanguage.displayName)
                                    .foregroundColor(.secondary)
                            }
                        }

                        NavigationLink(destination: AppearanceSettingsView()) {
                            HStack {
                                Label(localizationService.localizedString("appearance", table: "settings"), systemImage: "circle.lefthalf.filled")
                                Spacer()
                                Text(appearanceSummary)
                                    .foregroundColor(.secondary)
                            }
                        }
                        
                        NavigationLink(destination: NotificationSettingsView()) {
                            Label(localizationService.localizedString("notificationSettings"), systemImage: "bell.badge")
                        }
                        
                        NavigationLink(destination: PrivacySettingsView()) {
                            Label(localizationService.localizedString("privacy"), systemImage: "lock")
                        }
                        
                        NavigationLink(destination: MeasurementUnitsSettingsView()) {
                            Label(localizationService.localizedString("measurementUnits", table: "settings"), systemImage: "ruler")
                        }
                        
                        NavigationLink(destination: HelpSupportView()) {
                            Label(localizationService.localizedString("helpSupport"), systemImage: "questionmark.circle")
                        }
                    }
                    
                    Section {
                        Button(role: .destructive, action: {
                            authService.signOut()
                        }) {
                            Label(localizationService.localizedString("signOut"), systemImage: "arrow.right.square")
                        }
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("profile"))
            .diveHubNavigationChrome()
        }
    }
}

struct ProfileHeaderView: View {
    let user: User
    
    private var avatarURL: URL? {
        guard let avatarURLString = user.avatarURL else { return nil }
        var urlString = avatarURLString
        // Convert relative URL to absolute URL if needed
        if urlString.hasPrefix("/") && !urlString.hasPrefix("http") {
            urlString = NetworkService.shared.baseURL + urlString
        }
        return URL(string: urlString)
    }
    
    var body: some View {
        HStack {
            AsyncImage(url: avatarURL) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                Image(systemName: "person.circle.fill")
                    .foregroundColor(.secondary)
            }
            .frame(width: 80, height: 80)
            .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 4) {
                Text(user.displayName)
                    .font(.title2)
                    .fontWeight(.semibold)
                Text(user.role.displayName)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                if let level = user.certificationLevel {
                    Text(level)
                        .font(.caption)
                        .foregroundColor(.divePrimary)
                }
            }
            .padding(.leading)
            
            Spacer()
        }
        .padding(.vertical, 8)
    }
}

#Preview {
    ProfileTabView()
}
