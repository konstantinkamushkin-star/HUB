//
//  DashboardView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct AdminDashboardView: View {
    @StateObject private var tripViewModel = TripViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @ObservedObject private var auth = AuthenticationService.shared
    @State private var selectedDate = Date()
    @State private var customizePresented = false
    /// When embedded inside an existing navigation stack (e.g. opened from Dive Center profile),
    /// avoid nesting another navigation container — it causes double chrome and confusing layouts.
    private let embedNavigationChrome: Bool
    let onNavigateToTabKey: (String) -> Void

    init(
        embedNavigationChrome: Bool = true,
        onNavigateToTabKey: @escaping (String) -> Void = { _ in }
    ) {
        self.embedNavigationChrome = embedNavigationChrome
        self.onNavigateToTabKey = onNavigateToTabKey
    }

    private var adminLayout: AdminDashboardLayoutPayload {
        auth.currentUser?.diverProfile?.adminDashboardLayout ?? AdminDashboardLayoutPayload()
    }

    private var showQuickActions: Bool { adminLayout.quick ?? true }
    private var showCalendarSection: Bool { adminLayout.cal ?? true }

    private var visibleShellTabKeys: [String] {
        PartnerShellTab.visibleKeys(from: adminLayout)
    }

    /// Варианты для быстрых действий: видимые вкладки нижней панели + «Инструкторы», если профиль в панели.
    private var quickActionPickerOptions: [String] {
        var o = visibleShellTabKeys
        if o.contains("profile") {
            o.append("instructors")
        }
        return o
    }

    /// Порядок блоков на главной: `quick` (сетка быстрых действий), `cal` (календарь и поездки).
    private func iosBlockOrder() -> [String] {
        let known = ["quick", "cal"]
        let raw = adminLayout.sectionOrder ?? []
        var order = raw.map { $0.lowercased() }.filter { known.contains($0) }
        for k in known where !order.contains(k) {
            order.append(k)
        }
        return order
    }

    private func iosBlockTitle(_ id: String) -> String {
        switch id {
        case "quick":
            return localizationService.localizedString("quickActions", table: "admin")
        case "cal":
            return localizationService.localizedString("calendar", table: "admin")
        default:
            return id
        }
    }

    private func resolvedQuickActionTargets() -> [String] {
        let layout = adminLayout
        let raw: [String]
        if let q = layout.quickActionTargets, !q.isEmpty {
            raw = q.map { $0.lowercased() }
        } else {
            raw = Self.migratedLegacyQuickTargets(layout)
        }
        let allowed = Set(quickActionPickerOptions.map { $0.lowercased() })
        return raw.filter { allowed.contains($0) }
    }

    private func quickActionLabel(_ target: String) -> String {
        switch target.lowercased() {
        case "dashboard":
            return localizationService.localizedString("tabDashboard", table: "admin")
        case "explore":
            return localizationService.localizedString("explore", table: "common")
        case "feed":
            return localizationService.localizedString("tabFeed", table: "admin")
        case "courses":
            return localizationService.localizedString("tabCourses", table: "admin")
        case "trips":
            return localizationService.localizedString("tabTrips", table: "admin")
        case "photo":
            return localizationService.localizedString("tabPhotoAI", table: "admin")
        case "services":
            return localizationService.localizedString("tabServices", table: "admin")
        case "profile":
            return localizationService.localizedString("tabProfile", table: "admin")
        case "instructors":
            return localizationService.localizedString("instructors", table: "admin")
        default:
            return target
        }
    }

    private func isBottomTabHidden(_ key: String) -> Bool {
        let k = key.lowercased()
        return (adminLayout.bottomBarHiddenTabs ?? []).map { $0.lowercased() }.contains(k)
    }

    var tripsForSelectedDate: [Trip] {
        tripViewModel.trips.filter { trip in
            selectedDate >= trip.startDate && selectedDate <= trip.endDate
        }
    }

    var body: some View {
        Group {
            if embedNavigationChrome {
                NavigationStack { dashboardRoot }
            } else {
                dashboardRoot
            }
        }
        .task {
            await tripViewModel.loadTrips()
        }
    }

    private var dashboardRoot: some View {
        ScrollView {
            VStack(spacing: 20) {
                ForEach(iosBlockOrder(), id: \.self) { block in
                    Group {
                        if block == "quick" && showQuickActions {
                            Section(header: Text(localizationService.localizedString("quickActions", table: "admin"))
                                .font(.headline)
                                .padding(.horizontal)) {
                                let actions = resolvedQuickActionTargets()
                                if actions.isEmpty {
                                    Text(localizationService.localizedString("dashboardNoQuickActions", table: "admin"))
                                        .font(.subheadline)
                                        .foregroundColor(.secondary)
                                        .padding(.horizontal)
                                } else {
                                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                                        ForEach(Array(actions.enumerated()), id: \.offset) { _, target in
                                            QuickActionTile(
                                                title: quickActionLabel(target),
                                                icon: PartnerShellTab.quickActionIcon(for: target),
                                                action: {
                                                    onNavigateToTabKey(target.lowercased())
                                                }
                                            )
                                        }
                                    }
                                    .padding(.horizontal)
                                }
                            }
                        }
                        if block == "cal" && showCalendarSection {
                            Section(header: Text(localizationService.localizedString("calendar", table: "admin"))
                                .font(.headline)
                                .padding(.horizontal)) {
                                VStack(spacing: 16) {
                                    CustomCalendarView(
                                        selectedDate: $selectedDate,
                                        bookings: []
                                    )
                                    .padding()

                                    if !tripsForSelectedDate.isEmpty {
                                        VStack(alignment: .leading, spacing: 8) {
                                            Text(localizationService.localizedString("trips", table: "trips"))
                                                .font(.headline)
                                                .padding(.horizontal)
                                            ForEach(tripsForSelectedDate.prefix(3)) { trip in
                                                TripRow(trip: trip)
                                            }
                                            if tripsForSelectedDate.count > 3 {
                                                NavigationLink(destination: TripsManagementView().environmentObject(AuthenticationService.shared)) {
                                                    Text("ui_admin_view_all_trips".localized)
                                                        .font(.caption)
                                                        .foregroundColor(.divePrimary)
                                                        .padding(.horizontal)
                                                }
                                            }
                                        }
                                    }

                                    if tripsForSelectedDate.isEmpty {
                                        Text(localizationService.localizedString("noTripsAvailable", table: "trips"))
                                            .foregroundColor(.secondary)
                                            .padding()
                                    }
                                }
                                .padding(.vertical, 8)
                            }
                        }
                    }
                }
            }
            .padding(.vertical)
        }
        .navigationTitle(localizationService.localizedString("dashboard", table: "admin"))
        .diveHubNavigationChrome()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    customizePresented = true
                } label: {
                    Image(systemName: "slider.horizontal.3")
                        .font(.title3)
                }
                .accessibilityLabel(localizationService.localizedString("dashboardCustomize", table: "admin"))
            }
        }
        .sheet(isPresented: $customizePresented) {
            NavigationStack {
                List {
                    Section {
                        Text(localizationService.localizedString("dashboardCustomizeIntro", table: "admin"))
                            .font(.footnote)
                            .foregroundColor(.secondary)
                            .listRowBackground(Color.clear)
                    }
                    Section(header: Text(localizationService.localizedString("dashboardBottomBarTitle", table: "admin"))) {
                        ForEach(PartnerShellTab.bottomBarToggleableKeys, id: \.self) { key in
                            Toggle(
                                quickActionLabel(key),
                                isOn: Binding(
                                    get: { !isBottomTabHidden(key) },
                                    set: { visible in
                                        Task { await setBottomBarHidden(key, hidden: !visible) }
                                    }
                                )
                            )
                        }
                        Text(localizationService.localizedString("dashboardBottomBarFooter", table: "admin"))
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    if visibleShellTabKeys.count > 1 {
                        Section(
                            header: Text(localizationService.localizedString("dashboardBottomBarOrderTitle", table: "admin")),
                            footer: Text(localizationService.localizedString("dashboardDragToReorder", table: "admin"))
                                .font(.footnote)
                        ) {
                            ForEach(visibleShellTabKeys, id: \.self) { key in
                                Text(quickActionLabel(key))
                                    .font(.body)
                            }
                            .onMove { indices, newOffset in
                                Task { await moveBottomBarTabs(from: indices, to: newOffset) }
                            }
                        }
                    }
                    Section(header: Text(localizationService.localizedString("dashboardQuickActionsConfigureTitle", table: "admin"))) {
                        ForEach(0..<resolvedQuickActionTargets().count, id: \.self) { index in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Picker(
                                        localizationService.localizedString("dashboardQuickActionDestination", table: "admin"),
                                        selection: Binding(
                                            get: {
                                                let arr = resolvedQuickActionTargets()
                                                guard index >= 0, index < arr.count else { return "dashboard" }
                                                return arr[index]
                                            },
                                            set: { new in Task { await setQuickTarget(at: index, to: new) } }
                                        )
                                    ) {
                                        ForEach(quickActionPickerOptions, id: \.self) { opt in
                                            Text(quickActionLabel(opt)).tag(opt)
                                        }
                                    }
                                    .pickerStyle(.menu)
                                    Button(role: .destructive) {
                                        Task { await removeQuickTarget(at: index) }
                                    } label: {
                                        Image(systemName: "minus.circle.fill")
                                            .accessibilityLabel(localizationService.localizedString("dashboardQuickActionsRemove", table: "admin"))
                                    }
                                }
                            }
                        }
                        .onMove { indices, newOffset in
                            Task { await moveQuickActionTargets(from: indices, to: newOffset) }
                        }
                        Button {
                            Task { await addQuickTargetSlot() }
                        } label: {
                            Label(localizationService.localizedString("dashboardQuickActionsAdd", table: "admin"), systemImage: "plus.circle.fill")
                        }
                    }
                    Section(
                        header: Text(localizationService.localizedString("dashboardSectionOrderTitle", table: "admin")),
                        footer: Text(localizationService.localizedString("dashboardDragToReorder", table: "admin"))
                            .font(.footnote)
                    ) {
                        ForEach(iosBlockOrder(), id: \.self) { sid in
                            Text(iosBlockTitle(sid))
                                .font(.body)
                        }
                        .onMove { indices, newOffset in
                            Task { await moveIosBlocks(from: indices, to: newOffset) }
                        }
                    }
                    Section(header: Text(localizationService.localizedString("dashboardHelpTitle", table: "admin"))) {
                        Text(localizationService.localizedString("dashboardHelpBottomBar", table: "admin"))
                            .font(.footnote)
                            .foregroundColor(.secondary)
                        Text(localizationService.localizedString("dashboardHelpQuickTabs", table: "admin"))
                            .font(.footnote)
                            .foregroundColor(.secondary)
                        Text(localizationService.localizedString("dashboardHelpCalendarBlock", table: "admin"))
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    Section(header: Text(localizationService.localizedString("dashboardBlocksVisibility", table: "admin"))) {
                        Toggle(
                            localizationService.localizedString("dashboardShowQuickActions", table: "admin"),
                            isOn: Binding(
                                get: { adminLayout.quick ?? true },
                                set: { newValue in
                                    Task { await patchAdminLayout(quick: newValue, calendar: nil) }
                                }
                            )
                        )
                        Toggle(
                            localizationService.localizedString("dashboardShowCalendar", table: "admin"),
                            isOn: Binding(
                                get: { adminLayout.cal ?? true },
                                set: { newValue in
                                    Task { await patchAdminLayout(quick: nil, calendar: newValue) }
                                }
                            )
                        )
                    }
                    Section {
                        Button(role: .destructive) {
                            Task {
                                await resetAccountLayout()
                                await MainActor.run { customizePresented = false }
                            }
                        } label: {
                            Text(localizationService.localizedString("dashboardResetLayout", table: "admin"))
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .environment(\.editMode, .constant(.active))
                .navigationTitle(localizationService.localizedString("dashboardCustomize", table: "admin"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(localizationService.localizedString("done", table: "common")) {
                            customizePresented = false
                        }
                    }
                }
            }
        }
    }

    private func patchAdminLayout(quick: Bool?, calendar: Bool?) async {
        await patchLayout { layout in
            if let quick {
                layout.quick = quick
            }
            if let calendar {
                layout.cal = calendar
            }
        }
    }

    private func resetAccountLayout() async {
        var merged = auth.currentUser?.diverProfile ?? DiverProfilePayload()
        merged.adminDashboardLayout = .accountDefaults
        do {
            _ = try await auth.patchAuthenticatedProfile(AuthMePatchBody(diverProfile: merged))
        } catch {}
    }

    private func moveIosBlocks(from: IndexSet, to: Int) async {
        var arr = iosBlockOrder()
        arr.move(fromOffsets: from, toOffset: to)
        await patchSectionOrder(arr)
    }

    private func patchSectionOrder(_ order: [String]) async {
        await patchLayout { layout in
            layout.sectionOrder = order
        }
    }

    private func moveBottomBarTabs(from: IndexSet, to: Int) async {
        var visible = visibleShellTabKeys
        visible.move(fromOffsets: from, toOffset: to)
        let layout = adminLayout
        let hidden = Set((layout.bottomBarHiddenTabs ?? []).map { $0.lowercased() }).subtracting(["dashboard"])
        let hiddenOrdered = PartnerShellTab.fullBottomBarOrder(from: layout).filter { hidden.contains($0) }
        let newFull = visible + hiddenOrdered
        await patchLayout { $0.bottomBarOrder = newFull }
    }

    private func moveQuickActionTargets(from: IndexSet, to: Int) async {
        var arr = resolvedQuickActionTargets()
        arr.move(fromOffsets: from, toOffset: to)
        await patchLayout { layout in
            layout.quickActionTargets = arr
        }
    }

    private func setBottomBarHidden(_ key: String, hidden: Bool) async {
        let k = key.lowercased()
        guard k != "dashboard" else { return }
        await patchLayout { layout in
            var h = Set((layout.bottomBarHiddenTabs ?? []).map { $0.lowercased() })
            if hidden { h.insert(k) } else { h.remove(k) }
            h.remove("dashboard")
            layout.bottomBarHiddenTabs = Array(h).sorted()
        }
    }

    private func setQuickTarget(at index: Int, to newKey: String) async {
        var arr = resolvedQuickActionTargets()
        guard index >= 0, index < arr.count else { return }
        arr[index] = newKey.lowercased()
        await patchLayout { layout in
            layout.quickActionTargets = arr
        }
    }

    private func removeQuickTarget(at index: Int) async {
        var arr = resolvedQuickActionTargets()
        guard index >= 0, index < arr.count else { return }
        arr.remove(at: index)
        await patchLayout { layout in
            layout.quickActionTargets = arr
        }
    }

    private func addQuickTargetSlot() async {
        let opts = quickActionPickerOptions
        let fallback = opts.first ?? "dashboard"
        await patchLayout { layout in
            var arr = layout.quickActionTargets
            if arr == nil || arr?.isEmpty == true {
                arr = Self.migratedLegacyQuickTargets(layout)
            }
            arr?.append(fallback)
            layout.quickActionTargets = arr ?? [fallback]
        }
    }

    private func patchLayout(_ mutate: (inout AdminDashboardLayoutPayload) -> Void) async {
        var merged = auth.currentUser?.diverProfile ?? DiverProfilePayload()
        var layout = merged.adminDashboardLayout ?? AdminDashboardLayoutPayload()
        mutate(&layout)
        Self.sanitize(&layout)
        merged.adminDashboardLayout = layout
        do {
            _ = try await auth.patchAuthenticatedProfile(AuthMePatchBody(diverProfile: merged))
        } catch {
            // Keep local UI; user can retry when online.
        }
    }

    /// Старые поля `quickActionInstructors` / `quickActionOrder` → список целей, пока не сохранён `quickActionTargets`.
    private static func migratedLegacyQuickTargets(_ layout: AdminDashboardLayoutPayload) -> [String] {
        var legacy: [String] = []
        let order = (layout.quickActionOrder ?? ["instructors", "services"]).map { $0.lowercased() }
        for k in order {
            switch k {
            case "instructors":
                if layout.quickActionInstructors ?? true { legacy.append("instructors") }
            case "services":
                if layout.quickActionServices ?? true { legacy.append("services") }
            default:
                break
            }
        }
        if legacy.isEmpty {
            if layout.quickActionInstructors ?? true { legacy.append("instructors") }
            if layout.quickActionServices ?? true { legacy.append("services") }
        }
        if legacy.isEmpty {
            return ["instructors", "services"]
        }
        return legacy
    }

    private static func sanitize(_ layout: inout AdminDashboardLayoutPayload) {
        layout.bottomBarHiddenTabs = (layout.bottomBarHiddenTabs ?? [])
            .map { $0.lowercased() }
            .filter { $0 != "dashboard" }
        let visible = Set(PartnerShellTab.visibleKeys(from: layout))
        var q = layout.quickActionTargets
        if q == nil || q?.isEmpty == true {
            q = migratedLegacyQuickTargets(layout)
        }
        layout.quickActionTargets = q?.map { $0.lowercased() }.filter { target in
            if target == "instructors" { return visible.contains("profile") }
            return visible.contains(target)
        }
    }
}

struct QuickActionTile: View {
    let title: String
    let icon: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.title)
                    .foregroundColor(.divePrimary)
                Text(title)
                    .font(.caption)
                    .foregroundColor(.primary)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.diveCard)
            .cornerRadius(12)
            .shadow(radius: 2)
        }
        .buttonStyle(.plain)
    }
}

struct TripRow: View {
    let trip: Trip
    @StateObject private var localizationService = LocalizationService.shared

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(trip.country)
                    .font(.headline)
                if let region = trip.region, !region.isEmpty {
                    Text(region)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Text("\(trip.startDate.formatted(date: .abbreviated, time: .omitted)) - \(trip.endDate.formatted(date: .abbreviated, time: .omitted))")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing) {
                Text("\(trip.bookedSpots)/\(trip.totalSpots)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(localizationService.localizedString("spots", table: "trips"))
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color.diveCard)
        .cornerRadius(8)
        .padding(.horizontal)
    }
}

#Preview {
    AdminDashboardView()
}
