//
//  AdminTabView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct AdminTabView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var authService = AuthenticationService.shared
    @State private var selectedTab = 0
    
    private var isSuperAdmin: Bool {
        authService.currentUser?.role == .superAdmin
    }
    
    var body: some View {
        TabView(selection: $selectedTab) {
            AdminDashboardView()
                .tabItem {
                    Label(localizationService.localizedString("dashboard", table: "admin"), systemImage: "house.fill")
                }
                .tag(0)
            
            if !isSuperAdmin {
                CoursesManagementView()
                    .environmentObject(authService)
                    .tabItem {
                        Label(localizationService.localizedString("courses", table: "courses"), systemImage: "book.closed")
                    }
                    .tag(1)
                
                TripsManagementView()
                    .environmentObject(authService)
                    .tabItem {
                        Label(localizationService.localizedString("trips", table: "trips"), systemImage: "airplane.departure")
                    }
                    .tag(2)
                
                PhotoProcessingView()
                    .tabItem {
                        Label(localizationService.localizedString("photoProcessing"), systemImage: "wand.and.stars")
                    }
                    .tag(3)
            }
            
            AnalyticsView()
                .tabItem {
                    Label(localizationService.localizedString("analytics", table: "admin"), systemImage: "chart.bar")
                }
                .tag(isSuperAdmin ? 1 : 4)
            
            if isSuperAdmin {
                SuperAdminControlCenterView()
                    .tabItem {
                        Label("Control", systemImage: "lock.shield")
                    }
                    .tag(2)
            }
            
            ProfileTabView()
                .tabItem {
                    Label(localizationService.localizedString("profile"), systemImage: "person.circle")
                }
                .tag(isSuperAdmin ? 3 : 5)
        }
        .accentColor(.divePrimary)
    }
}

struct SuperAdminControlCenterView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Super Admin") {
                    NavigationLink {
                        SuperAdminSectionDetailView(
                            routeKey: "moderation_verification",
                            title: "Moderation & verification",
                            detail: "Reports, content moderation, and verification workflows. API: /api/admin/reports, /api/admin/verification."
                        )
                    } label: {
                        Label("Global moderation and verification", systemImage: "exclamationmark.shield")
                    }

                    NavigationLink {
                        SuperAdminSectionDetailView(
                            routeKey: "roles_permissions",
                            title: "Roles & permissions",
                            detail: "Admin role matrix. API: GET /api/admin/roles."
                        )
                    } label: {
                        Label("Roles & permissions governance", systemImage: "person.3")
                    }

                    NavigationLink {
                        SuperAdminSectionDetailView(
                            routeKey: "settings_flags",
                            title: "Settings & feature flags",
                            detail: "System settings and toggles. API: /api/admin/system-settings, /api/admin/feature-flags."
                        )
                    } label: {
                        Label("System settings and feature flags", systemImage: "slider.horizontal.3")
                    }

                    NavigationLink {
                        SuperAdminSectionDetailView(
                            routeKey: "audit_compliance",
                            title: "Audit & compliance",
                            detail: "Immutable audit trail and data requests. API: /api/admin/audit-logs, /api/admin/compliance."
                        )
                    } label: {
                        Label("Audit logs and compliance controls", systemImage: "doc.text.magnifyingglass")
                    }
                }
            }
            .navigationTitle("Control Center")
        }
    }
}

/// Detail screen after tapping a Control Center row (proves navigation works; full CRUD UI can layer on these routes).
struct SuperAdminSectionDetailView: View {
    let routeKey: String
    let title: String
    let detail: String

    var body: some View {
        List {
            Section {
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .id(routeKey)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    AdminTabView()
}
