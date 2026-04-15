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
    @Environment(\.displayScale) private var displayScale

    private var isSuperAdmin: Bool {
        authService.currentUser?.role == .superAdmin
    }

    private var tabContentBottomPad: CGFloat {
        DiveHubCarouselTabBar.contentBottomInset(displayScale: displayScale)
    }

    var body: some View {
        Group {
            if isSuperAdmin {
                superAdminShell
            } else {
                diveCenterAdminShell
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea(edges: .bottom)
        .tint(.divePrimary)
        .sensoryFeedback(.selection, trigger: selectedTab)
    }

    private func superAdminCarouselItems() -> [CarouselTabItem] {
        [
            CarouselTabItem(
                id: 0,
                title: localizationService.localizedString("webPanel", table: "admin"),
                systemImage: "globe",
                accessibilityLabel: nil
            ),
            CarouselTabItem(
                id: 1,
                title: localizationService.localizedString("profile", table: "common"),
                systemImage: "person.circle",
                accessibilityLabel: nil
            )
        ]
    }

    private func diveCenterCarouselItems() -> [CarouselTabItem] {
        [
            CarouselTabItem(
                id: 0,
                title: localizationService.localizedString("dashboard", table: "admin"),
                systemImage: "house.fill",
                accessibilityLabel: nil
            ),
            CarouselTabItem(
                id: 1,
                title: localizationService.localizedString("feed", table: "feed"),
                systemImage: "newspaper",
                accessibilityLabel: nil
            ),
            CarouselTabItem(
                id: 2,
                title: localizationService.localizedString("courses", table: "courses"),
                systemImage: "book.closed",
                accessibilityLabel: nil
            ),
            CarouselTabItem(
                id: 3,
                title: localizationService.localizedString("trips", table: "trips"),
                systemImage: "airplane.departure",
                accessibilityLabel: nil
            ),
            CarouselTabItem(
                id: 4,
                title: localizationService.localizedString("photoProcessing"),
                systemImage: "wand.and.stars",
                accessibilityLabel: nil
            ),
            CarouselTabItem(
                id: 5,
                title: localizationService.localizedString("profile", table: "common"),
                systemImage: "person.circle",
                accessibilityLabel: nil
            )
        ]
    }

    private var superAdminShell: some View {
        ZStack(alignment: .bottom) {
            superAdminTabContent
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.bottom, tabContentBottomPad)

            DiveHubCarouselTabBar(
                items: superAdminCarouselItems(),
                selectedTab: $selectedTab,
                visibleColumnBasis: nil,
                scrollAnchorNonce: 0
            )
        }
    }

    private var diveCenterAdminShell: some View {
        ZStack(alignment: .bottom) {
            diveCenterTabContent
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.bottom, tabContentBottomPad)

            DiveHubCarouselTabBar(
                items: diveCenterCarouselItems(),
                selectedTab: $selectedTab,
                visibleColumnBasis: nil,
                scrollAnchorNonce: 0
            )
        }
    }

    @ViewBuilder
    private var superAdminTabContent: some View {
        switch selectedTab {
        case 0:
            AdminWebPanelView()
        case 1:
            ProfileTabView()
        default:
            AdminWebPanelView()
        }
    }

    @ViewBuilder
    private var diveCenterTabContent: some View {
        switch selectedTab {
        case 0:
            AdminDashboardView()
        case 1:
            FeedView()
        case 2:
            CoursesManagementView()
                .environmentObject(authService)
        case 3:
            TripsManagementView()
                .environmentObject(authService)
        case 4:
            PhotoProcessingView()
        case 5:
            ProfileTabView()
        default:
            AdminDashboardView()
        }
    }
}

#Preview {
    AdminTabView()
}
