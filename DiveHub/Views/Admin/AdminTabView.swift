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

    private var diveCenterLayout: AdminDashboardLayoutPayload {
        authService.currentUser?.diverProfile?.adminDashboardLayout ?? AdminDashboardLayoutPayload()
    }

    private var visibleDiveCenterTabKeys: [String] {
        PartnerShellTab.visibleKeys(from: diveCenterLayout)
    }

    private var bottomBarScrollNonce: Int {
        var hasher = Hasher()
        hasher.combine(visibleDiveCenterTabKeys)
        hasher.combine(diveCenterLayout.bottomBarHiddenTabs ?? [])
        hasher.combine(diveCenterLayout.bottomBarOrder ?? [])
        return hasher.finalize()
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
        .onChange(of: bottomBarScrollNonce) { _, _ in
            if selectedTab >= visibleDiveCenterTabKeys.count {
                selectedTab = max(0, visibleDiveCenterTabKeys.count - 1)
            }
        }
        .onChange(of: visibleDiveCenterTabKeys.count) { _, newCount in
            if selectedTab >= newCount {
                selectedTab = max(0, newCount - 1)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .diveHubNavigateToExploreDiveSitesMap)) { _ in
            guard !isSuperAdmin else { return }
            if let idx = visibleDiveCenterTabKeys.firstIndex(of: "explore") {
                selectedTab = idx
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                    NotificationCenter.default.post(name: .diveHubExploreApplyDiveSitesMap, object: nil)
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .diveHubNavigateToSocial)) { _ in
            guard !isSuperAdmin else { return }
            if let idx = visibleDiveCenterTabKeys.firstIndex(of: "chats") {
                selectedTab = idx
            }
        }
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
            ),
        ]
    }

    private func diveCenterCarouselItems() -> [CarouselTabItem] {
        visibleDiveCenterTabKeys.enumerated().map { idx, key in
            PartnerShellTab.carouselItem(id: idx, key: key, localization: localizationService)
        }
    }

    private func navigateDiveCenter(to target: String) {
        let t = target.lowercased()
        if t == "instructors" {
            if let idx = visibleDiveCenterTabKeys.firstIndex(of: "profile") {
                selectedTab = idx
            }
            return
        }
        if let idx = visibleDiveCenterTabKeys.firstIndex(of: t) {
            selectedTab = idx
        }
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
                scrollAnchorNonce: bottomBarScrollNonce
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
        let keys = visibleDiveCenterTabKeys
        if keys.isEmpty {
            AdminDashboardView(onNavigateToTabKey: { _ in })
        } else {
            let idx = min(max(0, selectedTab), keys.count - 1)
            diveCenterPage(for: keys[idx])
        }
    }

    @ViewBuilder
    private func diveCenterPage(for key: String) -> some View {
        switch key {
        case "dashboard":
            AdminDashboardView(onNavigateToTabKey: navigateDiveCenter)
        case "explore":
            ExploreView()
        case "feed":
            FeedView()
        case "courses":
            CoursesManagementView()
                .environmentObject(authService)
        case "trips":
            TripsManagementView()
                .environmentObject(authService)
        case "photo":
            PhotoProcessingView()
        case "services":
            ServicesManagementView()
                .environmentObject(authService)
        case "chats":
            NavigationStack {
                ChatHubView()
            }
        case "profile":
            ProfileTabView()
        default:
            AdminDashboardView(onNavigateToTabKey: navigateDiveCenter)
        }
    }
}

#Preview {
    AdminTabView()
}
