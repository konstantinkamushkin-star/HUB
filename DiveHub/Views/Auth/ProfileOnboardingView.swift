//
//  ProfileOnboardingView.swift
//  DiveHub
//

import SwiftUI

struct ProfileOnboardingView: View {
    @EnvironmentObject private var authService: AuthenticationService
    @StateObject private var loc = LocalizationService.shared

    @State private var step = 0
    @State private var displayName = ""
    @State private var username = ""
    @State private var countryCode = ""
    @State private var city = ""
    @State private var showPhotoPicker = false
    @State private var pickedImage: UIImage?
    @State private var showCountrySheet = false
    @State private var countryQuery = ""

    @State private var certLevel = ""
    @State private var selectedAgencies: Set<String> = []
    @State private var divesRange = ""
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

    @State private var errorMessage: String?
    @State private var isSaving = false

    /// Названия стран — на языке приложения (не `Locale.current` устройства).
    private var regionDisplayLocale: Locale {
        Locale(identifier: loc.currentLanguage.rawValue)
    }

    private var regions: [(code: String, name: String)] {
        Array(Locale.Region.isoRegions).map(\.identifier)
            .map { code in
                let name = regionDisplayLocale.localizedString(forRegionCode: code) ?? code
                return (code, name)
            }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private var filteredRegions: [(code: String, name: String)] {
        let q = countryQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return regions }
        let locale = regionDisplayLocale
        let codePrefix = q.uppercased()
        return regions
            .filter { row in
                if row.name.range(of: q, options: [.anchored, .caseInsensitive], range: nil, locale: locale) != nil {
                    return true
                }
                if row.code.uppercased().hasPrefix(codePrefix) {
                    return true
                }
                return false
            }
            .sorted { lhs, rhs in
                lhs.name.compare(rhs.name, options: [.caseInsensitive], locale: locale) == .orderedAscending
            }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                stepIndicator
                Group {
                    switch step {
                    case 0: basicsStep
                    case 1: divingStep
                    default: privacyStep
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
            .navigationTitle(loc.localizedString("profileOnboardingTitle", table: "onboarding"))
            .navigationBarTitleDisplayMode(.inline)
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
            .sheet(isPresented: $showPhotoPicker) {
                ImagePicker(selectedImage: Binding(
                    get: { pickedImage },
                    set: { pickedImage = $0 }
                ))
            }
        }
    }

    private var stepIndicator: some View {
        HStack(spacing: 8) {
            stepChip(0, loc.localizedString("profileOnboardingBasics", table: "onboarding"))
            stepChip(1, loc.localizedString("profileOnboardingDiving", table: "onboarding"))
            stepChip(2, loc.localizedString("profileOnboardingPrivacy", table: "onboarding"))
        }
        .padding()
    }

    private func stepChip(_ index: Int, _ title: String) -> some View {
        let on = step == index
        return Text(title)
            .font(.caption.weight(on ? .semibold : .regular))
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(on ? Color.divePrimary.opacity(0.15) : Color(.systemGray5))
            .foregroundStyle(on ? Color.divePrimary : Color.primary)
            .clipShape(Capsule())
    }

    private var basicsStep: some View {
        Form {
            Section {
                Text(loc.localizedString("profileOnboardingDisplayName", table: "onboarding"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("", text: $displayName)
                    .textInputAutocapitalization(.words)
                Text(loc.localizedString("profileOnboardingUsername", table: "onboarding"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("ui_auth_username".localized, text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button {
                    showCountrySheet = true
                } label: {
                    HStack {
                        Text(loc.localizedString("profileOnboardingCountry", table: "onboarding"))
                        Spacer()
                        Text(countryLabel).foregroundStyle(.secondary)
                    }
                }
                Text(loc.localizedString("profileOnboardingCity", table: "onboarding"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("", text: $city)
                Button {
                    showPhotoPicker = true
                } label: {
                    HStack {
                        Text(loc.localizedString("profileOnboardingPhoto", table: "onboarding"))
                        Spacer()
                        if pickedImage != nil {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                        }
                    }
                }
            }
            if let err = errorMessage, step == 0 {
                Section { Text(err).foregroundStyle(.red).font(.caption) }
            }
            Section {
                Button(loc.localizedString("continue", table: "onboarding")) { goNextFromBasics() }
                    .disabled(isSaving)
            }
        }
    }

    private var countryLabel: String {
        guard !countryCode.isEmpty else { return "—" }
        let name = regionDisplayLocale.localizedString(forRegionCode: countryCode) ?? countryCode
        return "\(name) (\(countryCode))"
    }

    private var divingStep: some View {
        Form {
            Section {
                Picker(loc.localizedString("profileOnboardingCertLevel", table: "onboarding"), selection: $certLevel) {
                    Text("ui_auth_a".localized).tag("")
                    ForEach(DiverProfileCatalog.certificationLevels, id: \.self) { code in
                        Text(diverCertLabel(code)).tag(code)
                    }
                }
                Picker(loc.localizedString("profileOnboardingDiveCount", table: "onboarding"), selection: $divesRange) {
                    Text("ui_auth_a".localized).tag("")
                    ForEach(DiverProfileCatalog.diveCountRanges, id: \.self) { code in
                        Text(diverRangeLabel(code)).tag(code)
                    }
                }
            }
            Section {
                Text(loc.localizedString("agencyExclusiveHint", table: "onboarding"))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                ForEach(DiverProfileCatalog.certifyingAgencies, id: \.self) { code in
                    Toggle(isOn: agencyToggleBinding(code)) {
                        Text(diverAgencyLabel(code))
                    }
                }
            } header: {
                Text(loc.localizedString("profileOnboardingAgency", table: "onboarding"))
            }
            Section(loc.localizedString("profileOnboardingInterests", table: "onboarding")) {
                ForEach(DiverProfileCatalog.diveInterests, id: \.self) { key in
                    Toggle(diverInterestLabel(key), isOn: Binding(
                        get: { selectedInterests.contains(key) },
                        set: { on in
                            if on { selectedInterests.insert(key) } else { selectedInterests.remove(key) }
                        }
                    ))
                }
            }
            Section(loc.localizedString("profileOnboardingEquipment", table: "onboarding")) {
                ForEach(DiverProfileCatalog.equipmentKeys, id: \.self) { key in
                    Toggle(diverEquipLabel(key), isOn: Binding(
                        get: { selectedEquipment.contains(key) },
                        set: { on in
                            if on { selectedEquipment.insert(key) } else { selectedEquipment.remove(key) }
                        }
                    ))
                }
            }
            if let err = errorMessage, step == 1 {
                Section { Text(err).foregroundStyle(.red).font(.caption) }
            }
            Section {
                Button(loc.localizedString("continue", table: "onboarding")) { goNextFromDiving() }
                Button(loc.localizedString("profileOnboardingSkipOptional", table: "onboarding")) {
                    selectedInterests = []
                    selectedEquipment = []
                    step = 2
                }
                .foregroundStyle(.secondary)
            }
        }
    }

    private var privacyStep: some View {
        Form {
            Section {
                Text(loc.localizedString("profileOnboardingPrivacyIntro", table: "onboarding"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
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
            }
            if let err = errorMessage, step == 2 {
                Section { Text(err).foregroundStyle(.red).font(.caption) }
            }
            Section {
                Button {
                    Task { await saveAll() }
                } label: {
                    if isSaving { ProgressView() } else { Text(loc.localizedString("profileOnboardingFinish", table: "onboarding")) }
                }
                .disabled(isSaving)
            }
        }
    }

    private func prefill() {
        guard let u = authService.currentUser else { return }
        if displayName.isEmpty {
            let dn = u.diverProfile?.displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !dn.isEmpty {
                displayName = dn
            } else {
                displayName = u.displayName
            }
        }
        if username.isEmpty { username = u.diverProfile?.username ?? "" }
        if countryCode.isEmpty { countryCode = u.countryCode ?? "" }
        if city.isEmpty { city = u.diverProfile?.city ?? "" }
        certLevel = u.diverProfile?.certificationLevel ?? ""
        if selectedAgencies.isEmpty {
            if let arr = u.diverProfile?.certifyingAgencies, !arr.isEmpty {
                selectedAgencies = Set(arr)
            } else if let a = u.diverProfile?.certifyingAgency, !a.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                let parts = a.split(separator: ",")
                    .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
                if !parts.isEmpty { selectedAgencies = Set(parts) }
            }
        }
        divesRange = u.diverProfile?.totalDivesRange ?? ""
        if let ints = u.diverProfile?.diveInterests { selectedInterests = Set(ints) }
        if let eq = u.diverProfile?.ownEquipment { selectedEquipment = Set(eq) }
        if let p = u.diverProfile?.privacy {
            privacyPhoto = p.showProfilePhoto ?? true
            privacyCert = p.showCertificationLevel ?? true
            privacyDives = p.showNumberOfDives ?? true
            privacyLocation = p.showLocation ?? true
            privacyLastDive = p.showLastDive ?? false
            privacyEquipment = p.showEquipment ?? false
            privacyBuddy = p.showBuddySearchStatus ?? true
            privacyLogbook = p.showLogbook ?? false
            privacyContact = p.showContactOptions ?? false
        }
    }

    private func goNextFromBasics() {
        errorMessage = nil
        let dn = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        if dn.count < 2 {
            errorMessage = loc.localizedString("displayNameTooShort", table: "onboarding")
            return
        }
        if countryCode.isEmpty {
            errorMessage = loc.localizedString("countryRequired", table: "onboarding")
            return
        }
        step = 1
    }

    private func goNextFromDiving() {
        errorMessage = nil
        if certLevel.isEmpty {
            errorMessage = loc.localizedString("certRequired", table: "onboarding")
            return
        }
        if selectedAgencies.isEmpty {
            errorMessage = loc.localizedString("agencyRequired", table: "onboarding")
            return
        }
        if divesRange.isEmpty {
            errorMessage = loc.localizedString("divesRangeRequired", table: "onboarding")
            return
        }
        step = 2
    }

    private func splitDisplayName(_ raw: String) -> (String, String) {
        let parts = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: " ", maxSplits: 1, omittingEmptySubsequences: true)
            .map(String.init)
        let first = parts.first ?? "Diver"
        let last = parts.count > 1 ? parts[1] : first
        return (first, last)
    }

    @MainActor
    private func saveAll() async {
        errorMessage = nil
        isSaving = true
        defer { isSaving = false }
        do {
            var avatarUrl: String?
            if let img = pickedImage, let data = img.jpegData(compressionQuality: 0.85) {
                avatarUrl = try? await NetworkService.shared.uploadProfileImage(imageData: data)
            }
            let (fn, ln) = splitDisplayName(displayName)
            let noneOnly = selectedAgencies == Set(["NONE_YET"])
            let realAgencies = selectedAgencies.subtracting(["NONE_YET"]).sorted()
            let legacyAgency: String? = {
                if noneOnly { return "NONE_YET" }
                if realAgencies.isEmpty { return nil }
                return realAgencies.joined(separator: ",")
            }()
            let noCert = noneOnly
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
            var merged = authService.currentUser?.diverProfile ?? DiverProfilePayload()
            merged.displayName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
            let un = username.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "@", with: "")
            merged.username = un.isEmpty ? nil : un
            merged.city = city.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : city.trimmingCharacters(in: .whitespacesAndNewlines)
            merged.certificationLevel = certLevel
            merged.certifyingAgency = legacyAgency
            merged.certifyingAgencies = selectedAgencies.isEmpty ? nil : Array(selectedAgencies).sorted()
            merged.noCertYet = noCert
            merged.totalDivesRange = divesRange
            merged.diveInterests = selectedInterests.isEmpty ? nil : Array(selectedInterests).sorted()
            merged.ownEquipment = selectedEquipment.isEmpty ? nil : Array(selectedEquipment).sorted()
            merged.privacy = privacy
            merged.onboardingCompleted = true

            var user = try await authService.patchAuthenticatedProfile(
                AuthMePatchBody(
                    firstName: fn,
                    lastName: ln,
                    phone: nil,
                    bio: nil,
                    language: nil,
                    avatarUrl: avatarUrl,
                    countryCode: countryCode,
                    diverProfile: merged,
                    email: nil
                )
            )
            // Серверный JSON иногда не отдаёт `diverProfile` в том же виде — без этого
            // `needsDiverProfileOnboarding` остаётся true и экран «зависает» после сохранения.
            user.diverProfile = merged
            authService.updateUser(user)
        } catch {
            if let ae = error as? AuthError {
                errorMessage = ae.errorDescription
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func catalogLabel(prefix: String, code: String) -> String {
        let key = "\(prefix)_\(code)"
        let s = loc.localizedString(key, table: "onboarding")
        return s == key ? code : s
    }

    private func diverCertLabel(_ code: String) -> String { catalogLabel(prefix: "diverCert", code: code) }
    private func diverAgencyLabel(_ code: String) -> String { catalogLabel(prefix: "diverAgency", code: code) }
    private func diverRangeLabel(_ code: String) -> String { catalogLabel(prefix: "diverRange", code: code) }
    private func diverInterestLabel(_ code: String) -> String { catalogLabel(prefix: "diverInterest", code: code) }
    private func diverEquipLabel(_ code: String) -> String { catalogLabel(prefix: "diverEquip", code: code) }

    private func agencyToggleBinding(_ code: String) -> Binding<Bool> {
        Binding(
            get: { selectedAgencies.contains(code) },
            set: { on in
                if code == "NONE_YET" {
                    if on {
                        selectedAgencies = ["NONE_YET"]
                    } else {
                        selectedAgencies.remove("NONE_YET")
                    }
                } else if on {
                    selectedAgencies.remove("NONE_YET")
                    selectedAgencies.insert(code)
                } else {
                    selectedAgencies.remove(code)
                }
            }
        )
    }
}

#Preview {
    ProfileOnboardingView()
        .environmentObject(AuthenticationService.shared)
}
