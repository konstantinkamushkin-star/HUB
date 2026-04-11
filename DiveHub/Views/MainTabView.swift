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
                    // Admin interface
                    AdminTabView()
                } else if user.role == .instructor && instructorModeEnabled {
                    // Instructor interface
                    InstructorTabView()
                } else {
                    // Diver interface
                    DiverTabView()
                }
            } else {
                // Fallback to diver interface
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

struct DiverTabView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @AppStorage(FeatureFlags.underwaterEditorKey) private var diveEditorEnabled = true
    @State private var selectedTab = 0

    /// Horizontal `ScrollView` otherwise expands vertically inside `safeAreaInset`, leaving a large empty band above the home indicator.
    private let tabBarContentHeight: CGFloat = 48

    private var tabItems: [(tag: Int, titleKey: String, table: String, systemImage: String)] {
        var items: [(Int, String, String, String)] = [
            (0, "explore", "common", "magnifyingglass"),
            (1, "feed", "feed", "newspaper"),
            (2, "logbook", "common", "book"),
            (3, "social", "common", "person.2"),
            (4, "messages", "common", "message"),
        ]
        if diveEditorEnabled {
            items.append((5, "diveEditorTabTitle", "imageEditing", "camera.filters"))
            items.append((6, "profile", "common", "person.circle"))
        } else {
            items.append((5, "profile", "common", "person.circle"))
        }
        return items
    }

    var body: some View {
        selectedRoot
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .tint(.divePrimary)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 0) {
                    Divider()
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 0) {
                            ForEach(tabItems, id: \.tag) { tab in
                                diverTabBarButton(
                                    tag: tab.tag,
                                    title: localizationService.localizedString(tab.titleKey, table: tab.table),
                                    systemImage: tab.systemImage
                                )
                            }
                        }
                        .padding(.horizontal, 6)
                        .frame(height: tabBarContentHeight, alignment: .center)
                    }
                    .frame(height: tabBarContentHeight)
                }
                .background(.regularMaterial)
            }
            .onChange(of: diveEditorEnabled) { _, on in
                if on {
                    if selectedTab == 5 { selectedTab = 6 }
                } else {
                    if selectedTab == 6 { selectedTab = 5 }
                    else if selectedTab == 5 { selectedTab = 0 }
                }
            }
    }

    @ViewBuilder
    private var selectedRoot: some View {
        switch selectedTab {
        case 0:
            ExploreView()
        case 1:
            FeedView()
        case 2:
            LogbookTabView()
        case 3:
            SocialTabView()
        case 4:
            NavigationStack {
                ChatListView()
            }
        case 5:
            if diveEditorEnabled {
                NavigationStack {
                    DiveEditorTabView()
                }
            } else {
                ProfileTabView()
            }
        case 6:
            ProfileTabView()
        default:
            ExploreView()
        }
    }

    private func diverTabBarButton(tag: Int, title: String, systemImage: String) -> some View {
        let selected = selectedTab == tag
        return Button {
            selectedTab = tag
        } label: {
            VStack(spacing: 2) {
                Image(systemName: systemImage)
                    .font(.system(size: 22))
                    .symbolRenderingMode(.monochrome)
                Text(title)
                    .font(.caption2)
                    .fontWeight(selected ? .semibold : .regular)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .foregroundStyle(selected ? Color.divePrimary : Color.primary.opacity(0.55))
            .frame(minWidth: 64)
            .padding(.horizontal, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

#Preview {
    MainTabView()
}
