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

    private var diverInfoSectionTitle: String {
        localizationService.localizedString("profileOnboardingDiving", table: "onboarding")
    }

    private func isDiverRole(_ role: UserRole) -> Bool {
        role == .diverBasic || role == .diverPro
    }

    private func isDiveCenterProfileRole(_ user: User) -> Bool {
        (user.role == .diveCenterAdmin || user.role == .instructor || user.role == .superAdmin) &&
            user.diveCenterId != nil
    }

    private func onboardingCatalogLabel(prefix: String, code: String) -> String {
        let key = "\(prefix)_\(code)"
        let value = localizationService.localizedString(key, table: "onboarding")
        return value == key ? code : value
    }

    private func countryName(from code: String) -> String {
        let locale = Locale(identifier: localizationService.currentLanguage.rawValue)
        return locale.localizedString(forRegionCode: code) ?? code
    }

    private func diverProfileRows(for user: User) -> [(label: String, value: String)] {
        let profile = user.diverProfile
        var rows: [(String, String)] = []

        let handle = user.username ?? profile?.username
        if let username = handle, !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let display = username.hasPrefix("@") ? username : "@\(username)"
            rows.append((localizationService.localizedString("profileOnboardingUsername", table: "onboarding"), display))
        }
        if let city = profile?.city, !city.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            rows.append((localizationService.localizedString("profileOnboardingCity", table: "onboarding"), city))
        }
        if let countryCode = user.countryCode, !countryCode.isEmpty {
            rows.append((localizationService.localizedString("profileOnboardingCountry", table: "onboarding"), countryName(from: countryCode)))
        }

        if let certCode = profile?.certificationLevel ?? user.certificationLevel, !certCode.isEmpty {
            rows.append((localizationService.localizedString("profileOnboardingCertLevel", table: "onboarding"), onboardingCatalogLabel(prefix: "diverCert", code: certCode)))
        }

        let agencies = (profile?.certifyingAgencies ?? [])
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        if !agencies.isEmpty {
            let text = agencies.map { onboardingCatalogLabel(prefix: "diverAgency", code: $0) }.joined(separator: ", ")
            rows.append((localizationService.localizedString("profileOnboardingAgency", table: "onboarding"), text))
        } else if let legacy = profile?.certifyingAgency, !legacy.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let values = legacy
                .split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
                .map { onboardingCatalogLabel(prefix: "diverAgency", code: $0) }
                .joined(separator: ", ")
            if !values.isEmpty {
                rows.append((localizationService.localizedString("profileOnboardingAgency", table: "onboarding"), values))
            }
        }

        if let divesRange = profile?.totalDivesRange, !divesRange.isEmpty {
            rows.append((localizationService.localizedString("profileOnboardingDiveCount", table: "onboarding"), onboardingCatalogLabel(prefix: "diverRange", code: divesRange)))
        }

        let interests = (profile?.diveInterests ?? [])
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        if !interests.isEmpty {
            let text = interests.map { onboardingCatalogLabel(prefix: "diverInterest", code: $0) }.joined(separator: ", ")
            rows.append((localizationService.localizedString("profileOnboardingInterests", table: "onboarding"), text))
        }
        return rows
    }

    var body: some View {
        NavigationStack {
            List {
                if let user = authService.currentUser {
                    Section {
                        ProfileHeaderView(user: user)
                    }

                    let diverRows = diverProfileRows(for: user)
                    if isDiverRole(user.role) && !diverRows.isEmpty {
                        Section(diverInfoSectionTitle) {
                            ForEach(Array(diverRows.enumerated()), id: \.offset) { _, row in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(row.label)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Text(row.value)
                                        .font(.subheadline)
                                }
                                .padding(.vertical, 2)
                            }
                        }
                    }
                    
                    // Instructor mode toggle
                    if user.role == .instructor {
                        Section(localizationService.localizedString("mode", table: "settings")) {
                            InstructorModeToggleView()
                        }
                    }

                    // Dive center staff: center overview + profile editing on one destination screen.
                    if isDiveCenterProfileRole(user) {
                        Section(localizationService.localizedString("centerManagement")) {
                            NavigationLink(destination: DiveCenterProfileHubView()) {
                                Label("ui_profile_dive_center_profile".localized, systemImage: "building.2")
                            }
                        }
                    } else {
                        Section(localizationService.localizedString("account")) {
                            if isDiverRole(user.role) {
                                NavigationLink(destination: UnifiedProfileEditView()) {
                                    Label(localizationService.localizedString("editProfile"), systemImage: "pencil")
                                }
                            } else {
                                NavigationLink(destination: EditProfileView()) {
                                    Label(localizationService.localizedString("editProfile"), systemImage: "pencil")
                                }
                            }

                            if isDiverRole(user.role) {
                                NavigationLink(destination: CertificationsView()) {
                                    Label(localizationService.localizedString("certifications"), systemImage: "doc.text")
                                }

                                NavigationLink(destination: GearProfilesView()) {
                                    Label(localizationService.localizedString("gearProfiles"), systemImage: "bag")
                                }

                                NavigationLink(destination: StatisticsView()) {
                                    Label(localizationService.localizedString("statistics"), systemImage: "chart.bar")
                                }

                                NavigationLink(destination: MyBookingsView()) {
                                    Label(localizationService.localizedString("bookings", table: "admin"), systemImage: "calendar.badge.clock")
                                }

                                NavigationLink(destination: AchievementsView()) {
                                    Label(localizationService.localizedString("achievements"), systemImage: "trophy")
                                }
                            }
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

    private var isDiverRole: Bool {
        user.role == .diverBasic || user.role == .diverPro
    }
    
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
                if isDiverRole, let level = user.certificationLevel {
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
