//
//  DiveHubNavigationAppearance.swift
//  DiveHub
//

import SwiftUI
import UIKit

/// Shared navigation bar look: larger inline title, consistent with toolbar actions on one row.
enum DiveHubNavigationAppearance {
    static func apply() {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithDefaultBackground()

        let titleFont = UIFontMetrics(forTextStyle: .title2)
            .scaledFont(for: UIFont.systemFont(ofSize: 22, weight: .semibold))
        appearance.titleTextAttributes = [
            .font: titleFont,
            .foregroundColor: UIColor.label,
        ]
        appearance.largeTitleTextAttributes = [
            .font: UIFontMetrics(forTextStyle: .largeTitle)
                .scaledFont(for: UIFont.systemFont(ofSize: 34, weight: .bold)),
            .foregroundColor: UIColor.label,
        ]

        let back = UIBarButtonItemAppearance()
        back.normal.titleTextAttributes = [
            .font: UIFont.systemFont(ofSize: 17, weight: .regular),
        ]
        appearance.backButtonAppearance = back

        let plain = UIBarButtonItemAppearance()
        plain.normal.titleTextAttributes = [
            .font: UIFont.systemFont(ofSize: 17, weight: .regular),
        ]
        appearance.buttonAppearance = plain
        appearance.doneButtonAppearance.normal.titleTextAttributes = [
            .font: UIFont.systemFont(ofSize: 17, weight: .semibold),
        ]

        let nav = UINavigationBar.appearance()
        nav.standardAppearance = appearance
        nav.compactAppearance = appearance
        nav.scrollEdgeAppearance = appearance
        nav.compactScrollEdgeAppearance = appearance
    }
}

extension View {
    /// Title and `toolbar` items on a single navigation bar row (no large collapsing title).
    func diveHubNavigationChrome() -> some View {
        navigationBarTitleDisplayMode(.inline)
    }
}
