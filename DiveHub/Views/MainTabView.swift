//
//  MainTabView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct MainTabView: View {
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedTab = 0
    @AppStorage("instructorModeEnabled") private var instructorModeEnabled = true

    private func shouldUseShopInterface(for user: User) -> Bool {
        if let dc = user.diveCenterId, !dc.isEmpty {
            return false
        }
        if user.role == .shopAdmin {
            return true
        }
        if let sid = user.shopId, !sid.isEmpty {
            return true
        }
        return false
    }

    var body: some View {
        Group {
            if let user = authService.currentUser {
                if shouldUseShopInterface(for: user) {
                    ShopTabView()
                } else if user.role.canManageCenter {
                    AdminTabView()
                } else if user.role == .instructor && instructorModeEnabled {
                    InstructorTabView()
                } else {
                    DiverTabView()
                }
            } else {
                DiverTabView()
            }
        }
        .task {
            PushNotificationBootstrap.requestAndRegister()
        }
        .onReceive(NotificationCenter.default.publisher(for: .instructorModeChanged)) { _ in
            // Force view refresh when instructor mode changes
        }
    }
}

// MARK: - Diver tabs (carousel, full-width bar)
// IA: Поездки · Бадди · Сообщения · Редактор · Профиль

struct DiverTabView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @AppStorage(FeatureFlags.underwaterEditorKey) private var diveEditorEnabled = true
    @State private var selectedTab = 1 // default: Buddies
    @Environment(\.displayScale) private var displayScale

    private var isRussian: Bool {
        localizationService.currentLanguage == .russian
    }

    private var tabContentBottomPad: CGFloat {
        DiveHubCarouselTabBar.contentBottomInset(displayScale: displayScale)
    }

    private func carouselItems() -> [CarouselTabItem] {
        var items: [CarouselTabItem] = [
            CarouselTabItem(
                id: 0,
                title: isRussian ? "Поездки" : "Trips",
                systemImage: "airplane",
                accessibilityLabel: nil
            ),
            CarouselTabItem(
                id: 1,
                title: isRussian ? "Бадди" : "Buddies",
                systemImage: "person.2",
                accessibilityLabel: nil
            ),
            CarouselTabItem(
                id: 2,
                title: localizationService.localizedString("messages", table: "common"),
                systemImage: "message",
                accessibilityLabel: nil
            ),
        ]
        if diveEditorEnabled {
            items.append(
                CarouselTabItem(
                    id: 3,
                    title: localizationService.localizedString("diveEditorTabShort", table: "imageEditing"),
                    systemImage: "camera.filters",
                    accessibilityLabel: localizationService.localizedString("diveEditorTabTitle", table: "imageEditing")
                )
            )
            items.append(
                CarouselTabItem(
                    id: 4,
                    title: localizationService.localizedString("profile", table: "common"),
                    systemImage: "person.circle",
                    accessibilityLabel: nil
                )
            )
        } else {
            items.append(
                CarouselTabItem(
                    id: 3,
                    title: localizationService.localizedString("profile", table: "common"),
                    systemImage: "person.circle",
                    accessibilityLabel: nil
                )
            )
        }
        return items
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            tabContent
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.bottom, tabContentBottomPad)

            DiveHubCarouselTabBar(
                items: carouselItems(),
                selectedTab: $selectedTab,
                visibleColumnBasis: 5,
                scrollAnchorNonce: carouselItems().count + (diveEditorEnabled ? 1 : 0)
            )
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea(edges: .bottom)
        .tint(.divePrimary)
        .sensoryFeedback(.selection, trigger: selectedTab)
        .id(localizationService.currentLanguage)
        .onChange(of: diveEditorEnabled) { _, on in
            if on {
                if selectedTab == 3 { selectedTab = 4 }
            } else {
                if selectedTab == 4 { selectedTab = 3 }
                else if selectedTab == 3 { selectedTab = 1 }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .diveHubNavigateToExploreDiveSitesMap)) { _ in
            // Explore removed from primary bar — open trips catalog as closest entry.
            selectedTab = 0
        }
        .onReceive(NotificationCenter.default.publisher(for: .diveHubNavigateToSocial)) { _ in
            selectedTab = 1
        }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case 0:
            TripsListView()
        case 1:
            BuddyFindView()
        case 2:
            NavigationStack {
                ChatHubView()
            }
        case 3:
            if diveEditorEnabled {
                NavigationStack {
                    DiveEditorTabView()
                }
            } else {
                ProfileTabView()
            }
        case 4:
            ProfileTabView()
        default:
            BuddyFindView()
        }
    }
}

#Preview {
    MainTabView()
}
