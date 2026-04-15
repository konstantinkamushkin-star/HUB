//
//  DiveHubCarouselTabBar.swift
//  DiveHub
//
//  Общая нижняя панель (карусель) в стиле вкладок дайвера.
//

import SwiftUI
import UIKit

struct CarouselTabItem: Identifiable {
    let id: Int
    let title: String
    let systemImage: String
    let accessibilityLabel: String?
}

enum DiveHubTabBarStyle {
    static let rowHeight: CGFloat = 62
    static let selectionCorner: CGFloat = 18
    static let iconSize: CGFloat = 26
    static let iconSquare: CGFloat = 28
    static let iconLabelSpacing: CGFloat = 4
    static let selectionPaddingH: CGFloat = 6
    static let selectionPaddingV: CGFloat = 5
    static let hStackSpacing: CGFloat = 6
    static let rowHorizontalPadding: CGFloat = 16
    static let scrollContentMarginH: CGFloat = 12
    static let slotEdgePadding: CGFloat = 16
    static let slotInterItem: CGFloat = 6
    static let slotMin: CGFloat = 54
    static let slotMax: CGFloat = 70
    static let topVignetteHeight: CGFloat = 28
    static let shadowRadius: CGFloat = 18
    static let shadowYOffset: CGFloat = -10
    static let contentBottomExtra: CGFloat = 18
}

private struct SystemChromeTabBarBlur: UIViewRepresentable {
    func makeUIView(context: Context) -> UIVisualEffectView {
        let view = UIVisualEffectView(effect: UIBlurEffect(style: .systemChromeMaterial))
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIVisualEffectView, context: Context) {}
}

func diveHubReferenceTabBarWidth() -> CGFloat {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first
    if let w = scene?.windows.first(where: \.isKeyWindow)?.bounds.width ?? scene?.windows.first?.bounds.width {
        return w
    }
    return UIScreen.main.bounds.width
}

/// Полноширинная нижняя карусель: blur, линия сверху, скролл, капсула выбора.
struct DiveHubCarouselTabBar: View {
    let items: [CarouselTabItem]
    @Binding var selectedTab: Int
    /// Если `nil`, используется `min(5, max(2, items.count))` — как у дайверов при нескольких вкладках.
    var visibleColumnBasis: CGFloat? = nil
    /// Измените значение (например при смене набора вкладок), чтобы снова проскроллить к выбранной.
    var scrollAnchorNonce: Int = 0

    @Environment(\.displayScale) private var displayScale
    @Environment(\.colorScheme) private var colorScheme

    static func contentBottomInset(displayScale: CGFloat) -> CGFloat {
        let hairline = 1 / max(displayScale, 1)
        return hairline + DiveHubTabBarStyle.rowHeight + DiveHubTabBarStyle.contentBottomExtra
    }

    private var hairlineHeight: CGFloat {
        1 / max(displayScale, 1)
    }

    private var tabBarChromeHeight: CGFloat {
        hairlineHeight + DiveHubTabBarStyle.rowHeight
    }

    private var resolvedColumnBasis: CGFloat {
        if let explicit = visibleColumnBasis { return explicit }
        return min(5, CGFloat(max(2, items.count)))
    }

    private var tabSlotWidth: CGFloat {
        let w = diveHubReferenceTabBarWidth()
        let edgePadding = DiveHubTabBarStyle.slotEdgePadding
        let interItem = DiveHubTabBarStyle.slotInterItem
        let visibleColumns = resolvedColumnBasis
        let gaps = interItem * max(0, visibleColumns - 1)
        let usable = w - edgePadding * 2 - gaps
        return max(DiveHubTabBarStyle.slotMin, min(DiveHubTabBarStyle.slotMax, floor(usable / visibleColumns)))
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                LinearGradient(
                    colors: [
                        Color.divePrimary.opacity(colorScheme == .dark ? 0.22 : 0.14),
                        Color.divePrimary.opacity(0.04)
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(height: hairlineHeight)
                Rectangle()
                    .fill(Color.primary.opacity(colorScheme == .dark ? 0.35 : 0.12))
                    .frame(height: hairlineHeight)
                    .blendMode(.overlay)
            }
            .frame(maxWidth: .infinity)

            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .center, spacing: DiveHubTabBarStyle.hStackSpacing) {
                        ForEach(items) { item in
                            tabItemButton(item: item, isSelected: selectedTab == item.id)
                                .frame(width: tabSlotWidth)
                                .id(item.id)
                        }
                    }
                    .padding(.horizontal, DiveHubTabBarStyle.rowHorizontalPadding)
                    .frame(height: DiveHubTabBarStyle.rowHeight)
                }
                .scrollContentBackground(.hidden)
                .contentMargins(.horizontal, DiveHubTabBarStyle.scrollContentMarginH, for: .scrollContent)
                .frame(height: DiveHubTabBarStyle.rowHeight)
                .onAppear {
                    proxy.scrollTo(selectedTab, anchor: .center)
                }
                .onChange(of: selectedTab) { _, new in
                    withAnimation(.easeInOut(duration: 0.24)) {
                        proxy.scrollTo(new, anchor: .center)
                    }
                }
                .onChange(of: scrollAnchorNonce) { _, _ in
                    withAnimation(.easeInOut(duration: 0.24)) {
                        proxy.scrollTo(selectedTab, anchor: .center)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: tabBarChromeHeight)
        .background {
            ZStack(alignment: .top) {
                SystemChromeTabBarBlur()
                LinearGradient(
                    colors: [
                        Color.primary.opacity(colorScheme == .dark ? 0.2 : 0.06),
                        Color.clear
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: DiveHubTabBarStyle.topVignetteHeight)
                .allowsHitTesting(false)
            }
            .ignoresSafeArea(edges: .bottom)
        }
        .compositingGroup()
        .shadow(
            color: Color.black.opacity(colorScheme == .dark ? 0.45 : 0.12),
            radius: DiveHubTabBarStyle.shadowRadius,
            x: 0,
            y: DiveHubTabBarStyle.shadowYOffset
        )
    }

    private func tabItemButton(item: CarouselTabItem, isSelected: Bool) -> some View {
        Button {
            withAnimation(.spring(response: 0.4, dampingFraction: 0.78)) {
                selectedTab = item.id
            }
        } label: {
            VStack(spacing: DiveHubTabBarStyle.iconLabelSpacing) {
                Image(systemName: item.systemImage)
                    .font(.system(size: DiveHubTabBarStyle.iconSize, weight: isSelected ? .semibold : .medium))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(isSelected ? Color.divePrimary : Color.primary.opacity(0.42))
                    .frame(width: DiveHubTabBarStyle.iconSquare, height: DiveHubTabBarStyle.iconSquare, alignment: .center)
                Text(item.title)
                    .font(.caption2)
                    .fontWeight(isSelected ? .semibold : .medium)
                    .tracking(-0.15)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .foregroundStyle(isSelected ? Color.divePrimary : Color.primary.opacity(0.48))
            }
            .padding(.horizontal, DiveHubTabBarStyle.selectionPaddingH)
            .padding(.vertical, DiveHubTabBarStyle.selectionPaddingV)
            .background {
                if isSelected {
                    ZStack {
                        RoundedRectangle(cornerRadius: DiveHubTabBarStyle.selectionCorner, style: .continuous)
                            .fill(.ultraThinMaterial)
                        RoundedRectangle(cornerRadius: DiveHubTabBarStyle.selectionCorner, style: .continuous)
                            .fill(Color.divePrimary.opacity(colorScheme == .dark ? 0.22 : 0.14))
                        RoundedRectangle(cornerRadius: DiveHubTabBarStyle.selectionCorner, style: .continuous)
                            .strokeBorder(Color.divePrimary.opacity(0.38), lineWidth: 1)
                    }
                }
            }
            .frame(maxWidth: .infinity, minHeight: DiveHubTabBarStyle.rowHeight, maxHeight: DiveHubTabBarStyle.rowHeight, alignment: .center)
            .contentShape(Rectangle())
        }
        .buttonStyle(DiveHubTabBarButtonStyle())
        .accessibilityLabel(item.accessibilityLabel ?? item.title)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

private struct DiveHubTabBarButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.spring(response: 0.28, dampingFraction: 0.72), value: configuration.isPressed)
    }
}
