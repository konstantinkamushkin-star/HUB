//
//  DiverProfileDetailsView.swift
//  DiveHub
//

import SwiftUI

struct DiverProfileDetailsView: View {
    private enum EditTab: Int, CaseIterable {
        case basics
        case diving
        case privacy
    }

    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var loc = LocalizationService.shared
    @Environment(\.dismiss) private var dismiss

    @State private var selectedTab: EditTab = .basics
    @State private var displayName = ""
    @State private var username = ""
    @State private var countryCode = ""
    @State private var city = ""
    @State private var certLevel = ""
    @State private var divesRange = ""
    @State private var selectedAgencies: Set<String> = []
    @State private var selectedInterests: Set<String> = []
    @State private var selectedEquipment: Set<String> = []
    @State private var privacyPhoto = true
    @State private var privacyCert = true
    @State private var privacyDives = true
    @State private var privacyLocation = true
    @State private var privacyLastDive = false
    @State private var privacyEquipment = false
    @State private var privacyBuddy = true
    @State private var privacyLogbook = false
    @State private var privacyContact = false
    @State private var showCountrySheet = false
    @State private var countryQuery = ""
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var regionDisplayLocale: Locale {
        Locale(identifier: loc.currentLanguage.rawValue)
    }

    private var regions: [(code: String, name: String)] {
        Array(Locale.Region.isoRegions).map(\.identifier)
            .map { code in
                (code, regionDisplayLocale.localizedString(forRegionCode: code) ?? code)
            }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private var filteredRegions: [(code: String, name: String)] {
        let q = countryQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return regions }
        let codePrefix = q.uppercased()
        return regions.filter { row in
            row.name.localizedCaseInsensitiveContains(q) || row.code.uppercased().hasPrefix(codePrefix)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $selectedTab) {
                Text(loc.localizedString("profileOnboardingBasics", table: "onboarding")).tag(EditTab.basics)
                Text(loc.localizedString("profileOnboardingDiving", table: "onboarding")).tag(EditTab.diving)
                Text(loc.localizedString("profileOnboardingPrivacy", table: "onboarding")).tag(EditTab.privacy)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.top, 8)

            Group {
                switch selectedTab {
                case .basics:
                    basicsForm
                case .diving:
                    divingForm
                case .privacy:
                    privacyForm
                }
            }
        }
        .navigationTitle(loc.localizedString("profileOnboardingTitle", table: "onboarding"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(loc.localizedString("cancel", table: "common")) { dismiss() }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(loc.localizedString("save", table: "common")) {
                    Task { await save() }
                }
                .disabled(isSaving)
            }
        }
        .onAppear(perform: prefill)
        .sheet(isPresented: $showCountrySheet) {
            NavigationStack {
                List(filteredRegions, id: \.code) { row in
                    Button {
                        countryCode = row.code
                        showCountrySheet = false
                    } label: {
                        HStack {
                            Text(row.name)
                            Spacer()
                            Text(row.code).foregroundStyle(.secondary)
                        }
                    }
                }
                .searchable(text: $countryQuery, prompt: loc.localizedString("profileOnboardingCountry", table: "onboarding"))
                .navigationTitle(loc.localizedString("profileOnboardingCountry", table: "onboarding"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(loc.localizedString("cancel", table: "common")) { showCountrySheet = false }
                    }
                }
            }
        }
    }

    private var basicsForm: some View {
        Form {
            Section {
                TextField(loc.localizedString("profileOnboardingDisplayName", table: "onboarding"), text: $displayName)
                    .textInputAutocapitalization(.words)
                TextField(loc.localizedString("profileOnboardingUsername", table: "onboarding"), text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                Button {
                    showCountrySheet = true
                } label: {
                    HStack {
                        Text(loc.localizedString("profileOnboardingCountry", table: "onboarding"))
                        Spacer()
                        Text(countryLabel)
                            .foregroundStyle(.secondary)
                    }
                }
                TextField(loc.localizedString("profileOnboardingCity", table: "onboarding"), text: $city)
            } header: {
                Text(loc.localizedString("profileOnboardingBasics", table: "onboarding"))
            }
            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
        }
    }

    private var divingForm: some View {
        Form {
            Section(loc.localizedString("profileOnboardingDiving", table: "onboarding")) {
                Picker(loc.localizedString("profileOnboardingCertLevel", table: "onboarding"), selection: $certLevel) {
                    Text("ui_auth_a".localized).tag("")
                    ForEach(DiverProfileCatalog.certificationLevels, id: \.self) { code in
                        Text(catalogLabel(prefix: "diverCert", code: code)).tag(code)
                    }
                }
                Picker(loc.localizedString("profileOnboardingDiveCount", table: "onboarding"), selection: $divesRange) {
                    Text("ui_auth_a".localized).tag("")
                    ForEach(DiverProfileCatalog.diveCountRanges, id: \.self) { code in
                        Text(catalogLabel(prefix: "diverRange", code: code)).tag(code)
                    }
                }
            }

            Section(loc.localizedString("profileOnboardingAgency", table: "onboarding")) {
                ForEach(DiverProfileCatalog.certifyingAgencies, id: \.self) { code in
                    Toggle(catalogLabel(prefix: "diverAgency", code: code), isOn: agencyBinding(code))
                }
            }

            Section(loc.localizedString("profileOnboardingInterests", table: "onboarding")) {
                ForEach(DiverProfileCatalog.diveInterests, id: \.self) { code in
                    Toggle(catalogLabel(prefix: "diverInterest", code: code), isOn: Binding(
                        get: { selectedInterests.contains(code) },
                        set: { on in
                            if on { selectedInterests.insert(code) } else { selectedInterests.remove(code) }
                        }
                    ))
                }
            }

            Section(loc.localizedString("profileOnboardingEquipment", table: "onboarding")) {
                ForEach(DiverProfileCatalog.equipmentKeys, id: \.self) { code in
                    Toggle(catalogLabel(prefix: "diverEquip", code: code), isOn: Binding(
                        get: { selectedEquipment.contains(code) },
                        set: { on in
                            if on { selectedEquipment.insert(code) } else { selectedEquipment.remove(code) }
                        }
                    ))
                }
            }
            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
        }
    }

    private var privacyForm: some View {
        Form {
            Section {
                Toggle(loc.localizedString("privacyShowProfilePhoto", table: "onboarding"), isOn: $privacyPhoto)
                Toggle(loc.localizedString("privacyShowCertificationLevel", table: "onboarding"), isOn: $privacyCert)
                Toggle(loc.localizedString("privacyShowNumberOfDives", table: "onboarding"), isOn: $privacyDives)
                Toggle(loc.localizedString("privacyShowLocation", table: "onboarding"), isOn: $privacyLocation)
                Toggle(loc.localizedString("privacyShowLastDive", table: "onboarding"), isOn: $privacyLastDive)
                Toggle(loc.localizedString("privacyShowEquipment", table: "onboarding"), isOn: $privacyEquipment)
                Toggle(loc.localizedString("privacyShowBuddySearchStatus", table: "onboarding"), isOn: $privacyBuddy)
                Toggle(loc.localizedString("privacyShowLogbook", table: "onboarding"), isOn: $privacyLogbook)
                Toggle(loc.localizedString("privacyShowContactOptions", table: "onboarding"), isOn: $privacyContact)
            } header: {
                Text(loc.localizedString("profileOnboardingPrivacy", table: "onboarding"))
            }
            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
        }
    }

    private var countryLabel: String {
        guard !countryCode.isEmpty else { return "—" }
        let name = regionDisplayLocale.localizedString(forRegionCode: countryCode) ?? countryCode
        return "\(name) (\(countryCode))"
    }

    private func prefill() {
        guard let user = authService.currentUser else { return }
        let profile = user.diverProfile
        displayName = profile?.displayName ?? user.displayName
        username = profile?.username ?? ""
        countryCode = user.countryCode ?? ""
        city = profile?.city ?? ""
        certLevel = profile?.certificationLevel ?? ""
        divesRange = profile?.totalDivesRange ?? ""
        if let agencies = profile?.certifyingAgencies, !agencies.isEmpty {
            selectedAgencies = Set(agencies)
        } else if let legacy = profile?.certifyingAgency {
            let parts = legacy
                .split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            selectedAgencies = Set(parts)
        }
        selectedInterests = Set(profile?.diveInterests ?? [])
        selectedEquipment = Set(profile?.ownEquipment ?? [])
        if let privacy = profile?.privacy {
            privacyPhoto = privacy.showProfilePhoto ?? true
            privacyCert = privacy.showCertificationLevel ?? true
            privacyDives = privacy.showNumberOfDives ?? true
            privacyLocation = privacy.showLocation ?? true
            privacyLastDive = privacy.showLastDive ?? false
            privacyEquipment = privacy.showEquipment ?? false
            privacyBuddy = privacy.showBuddySearchStatus ?? true
            privacyLogbook = privacy.showLogbook ?? false
            privacyContact = privacy.showContactOptions ?? false
        }
    }

    private func splitDisplayName(_ raw: String) -> (String, String) {
        let parts = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: " ", maxSplits: 1, omittingEmptySubsequences: true)
            .map(String.init)
        let first = parts.first ?? "Diver"
        let last = parts.count > 1 ? parts[1] : first
        return (first, last)
    }

    private func agencyBinding(_ code: String) -> Binding<Bool> {
        Binding(
            get: { selectedAgencies.contains(code) },
            set: { on in
                if code == "NONE_YET" {
                    if on { selectedAgencies = ["NONE_YET"] } else { selectedAgencies.remove("NONE_YET") }
                } else if on {
                    selectedAgencies.remove("NONE_YET")
                    selectedAgencies.insert(code)
                } else {
                    selectedAgencies.remove(code)
                }
            }
        )
    }

    private func catalogLabel(prefix: String, code: String) -> String {
        let key = "\(prefix)_\(code)"
        let value = loc.localizedString(key, table: "onboarding")
        return value == key ? code : value
    }

    @MainActor
    private func save() async {
        guard authService.currentUser != nil else {
            errorMessage = loc.localizedString("pleaseSignIn", table: "errors")
            return
        }
        errorMessage = nil
        isSaving = true
        defer { isSaving = false }

        let trimmedDisplayName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedDisplayName.isEmpty else {
            errorMessage = loc.localizedString("displayNameTooShort", table: "onboarding")
            return
        }

        let (fn, ln) = splitDisplayName(trimmedDisplayName)
        let noneOnly = selectedAgencies == Set(["NONE_YET"])
        let realAgencies = selectedAgencies.subtracting(["NONE_YET"]).sorted()
        let legacyAgency: String? = {
            if noneOnly { return "NONE_YET" }
            if realAgencies.isEmpty { return nil }
            return realAgencies.joined(separator: ",")
        }()

        var merged = authService.currentUser?.diverProfile ?? DiverProfilePayload()
        let privacy = DiverPrivacyPayload(
            showProfilePhoto: privacyPhoto,
            showCertificationLevel: privacyCert,
            showNumberOfDives: privacyDives,
            showLocation: privacyLocation,
            showLastDive: privacyLastDive,
            showEquipment: privacyEquipment,
            showBuddySearchStatus: privacyBuddy,
            showLogbook: privacyLogbook,
            showContactOptions: privacyContact
        )
        let normalizedUsername = username
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "@", with: "")

        merged.displayName = trimmedDisplayName
        merged.username = normalizedUsername.isEmpty ? nil : normalizedUsername
        merged.city = city.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : city.trimmingCharacters(in: .whitespacesAndNewlines)
        merged.certificationLevel = certLevel.isEmpty ? nil : certLevel
        merged.certifyingAgency = legacyAgency
        merged.certifyingAgencies = selectedAgencies.isEmpty ? nil : Array(selectedAgencies).sorted()
        merged.noCertYet = noneOnly
        merged.totalDivesRange = divesRange.isEmpty ? nil : divesRange
        merged.diveInterests = selectedInterests.isEmpty ? nil : Array(selectedInterests).sorted()
        merged.ownEquipment = selectedEquipment.isEmpty ? nil : Array(selectedEquipment).sorted()
        merged.privacy = privacy
        merged.onboardingCompleted = true

        do {
            var user = try await authService.patchAuthenticatedProfile(
                AuthMePatchBody(
                    firstName: fn,
                    lastName: ln,
                    phone: nil,
                    bio: nil,
                    language: nil,
                    avatarUrl: nil,
                    countryCode: countryCode.isEmpty ? nil : countryCode,
                    diverProfile: merged,
                    email: nil
                )
            )
            user.diverProfile = merged
            authService.updateUser(user)
            dismiss()
        } catch let authError as AuthError {
            errorMessage = authError.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack {
        DiverProfileDetailsView()
    }
}
