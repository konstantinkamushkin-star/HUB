//
//  PartnerShellTab.swift
//  DiveHub
//
//  Стабильные ключи вкладок дайв-центра (нижняя панель + быстрые действия), JSON в diver_profile.adminDashboardLayout.
//

import SwiftUI

enum PartnerShellTab {
    static let orderedKeys = [
        "dashboard", "explore", "feed", "courses", "trips", "photo", "services", "chats", "profile",
    ]

    /// Вкладки, которые можно скрыть из нижней панели (главная всегда остаётся).
    static let bottomBarToggleableKeys = orderedKeys.filter { $0 != "dashboard" }

    /// Полный порядок ключей нижней панели (включая скрытые) — источник правды для перестановки.
    static func fullBottomBarOrder(from layout: AdminDashboardLayoutPayload) -> [String] {
        let baseOrder = (layout.bottomBarOrder ?? orderedKeys).map { $0.lowercased() }.filter { orderedKeys.contains($0) }
        var merged: [String] = []
        for k in baseOrder where !merged.contains(k) {
            merged.append(k)
        }
        for k in orderedKeys where !merged.contains(k) {
            // New keys (e.g. `chats`) should sit before `profile`, not after persisted order.
            if k == "chats", let profileIndex = merged.firstIndex(of: "profile") {
                merged.insert(k, at: profileIndex)
            } else {
                merged.append(k)
            }
        }
        return merged
    }

    /// Видимые вкладки снизу: порядок из [bottomBarOrder] или по умолчанию, минус [bottomBarHiddenTabs].
    static func visibleKeys(from layout: AdminDashboardLayoutPayload) -> [String] {
        let hidden = Set((layout.bottomBarHiddenTabs ?? []).map { $0.lowercased() }).subtracting(["dashboard"])
        return fullBottomBarOrder(from: layout).filter { !hidden.contains($0) }
    }

    static func carouselItem(id: Int, key: String, localization: LocalizationService) -> CarouselTabItem {
        let k = key.lowercased()
        switch k {
        case "dashboard":
            return CarouselTabItem(
                id: id,
                title: localization.localizedString("tabDashboard", table: "admin"),
                systemImage: "house.fill",
                accessibilityLabel: localization.localizedString("dashboard", table: "admin")
            )
        case "explore":
            return CarouselTabItem(
                id: id,
                title: localization.localizedString("explore", table: "common"),
                systemImage: "magnifyingglass",
                accessibilityLabel: localization.localizedString("explore", table: "common")
            )
        case "feed":
            return CarouselTabItem(
                id: id,
                title: localization.localizedString("tabFeed", table: "admin"),
                systemImage: "newspaper",
                accessibilityLabel: localization.localizedString("feed", table: "feed")
            )
        case "courses":
            return CarouselTabItem(
                id: id,
                title: localization.localizedString("tabCourses", table: "admin"),
                systemImage: "book.closed",
                accessibilityLabel: localization.localizedString("courses", table: "courses")
            )
        case "trips":
            return CarouselTabItem(
                id: id,
                title: localization.localizedString("tabTrips", table: "admin"),
                systemImage: "airplane.departure",
                accessibilityLabel: localization.localizedString("trips", table: "trips")
            )
        case "photo":
            return CarouselTabItem(
                id: id,
                title: localization.localizedString("tabPhotoAI", table: "admin"),
                systemImage: "wand.and.stars",
                accessibilityLabel: localization.localizedString("photoProcessing")
            )
        case "services":
            return CarouselTabItem(
                id: id,
                title: localization.localizedString("tabServices", table: "admin"),
                systemImage: "tag",
                accessibilityLabel: localization.localizedString("services")
            )
        case "chats":
            return CarouselTabItem(
                id: id,
                title: localization.localizedString("messages", table: "common"),
                systemImage: "message",
                accessibilityLabel: localization.localizedString("messages", table: "common")
            )
        case "profile":
            return CarouselTabItem(
                id: id,
                title: localization.localizedString("tabProfile", table: "admin"),
                systemImage: "person.circle",
                accessibilityLabel: localization.localizedString("profile", table: "common")
            )
        default:
            return CarouselTabItem(
                id: id,
                title: k,
                systemImage: "square.grid.2x2",
                accessibilityLabel: nil
            )
        }
    }

    static func quickActionIcon(for target: String) -> String {
        switch target.lowercased() {
        case "dashboard": return "house.fill"
        case "explore": return "magnifyingglass"
        case "feed": return "newspaper"
        case "courses": return "book.closed"
        case "trips": return "airplane.departure"
        case "photo": return "wand.and.stars"
        case "services": return "tag"
        case "chats": return "message"
        case "profile": return "person.circle"
        case "instructors": return "person.2"
        default: return "square.grid.2x2"
        }
    }
}
