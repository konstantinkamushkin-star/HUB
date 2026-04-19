import SwiftUI

struct ServicesManagementView: View {
    @EnvironmentObject private var authService: AuthenticationService
    @StateObject private var localizationService = LocalizationService.shared
    @State private var services: [DiveCenterService] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var selectedService: DiveCenterService?
    @State private var showCreate = false
    @State private var includeInactive = false
    @State private var resolvedDiveCenterId: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Toggle("ui_admin_show_inactive".localized, isOn: $includeInactive)
                        .onChange(of: includeInactive) { _, _ in
                            Task { await loadServices() }
                        }
                }

                if let errorMessage, !errorMessage.isEmpty {
                    Section {
                        Text(errorMessage)
                            .foregroundColor(.red)
                    }
                }

                Section("ui_admin_services_pricing".localized) {
                    if isLoading && services.isEmpty {
                        HStack {
                            Spacer()
                            ProgressView()
                            Spacer()
                        }
                    } else if services.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("ui_admin_no_services_yet_add_your_first_package".localized)
                                .foregroundColor(.secondary)
                            Button("ui_admin_new_service".localized) {
                                showCreate = true
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .padding(.vertical, 6)
                    } else {
                        ForEach(services) { service in
                            Button {
                                selectedService = service
                            } label: {
                                AdminServiceRow(service: service)
                            }
                            .buttonStyle(.plain)
                        }
                        .onDelete(perform: delete)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle(localizationService.localizedString("services"))
            .diveHubNavigationChrome()
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showCreate = true }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title3)
                    }
                }
            }
            .refreshable {
                await loadServices()
            }
        }
        .sheet(isPresented: $showCreate) {
            if let centerId = resolvedDiveCenterId ?? authService.currentUser?.diveCenterId {
                ServiceEditorView(
                    mode: .create(centerId: centerId),
                    onSaved: {
                        Task { await loadServices() }
                    }
                )
            } else {
                Text("ui_admin_dive_center_not_found_for_current_user".localized)
                    .padding()
            }
        }
        .sheet(item: $selectedService) { service in
            ServiceEditorView(
                mode: .edit(service: service),
                onSaved: {
                    Task { await loadServices() }
                }
            )
        }
        .task {
            await loadServices()
        }
    }

    private func loadServices() async {
        let centerId = await resolveDiveCenterId()
        guard let centerId else {
            services = []
            errorMessage = "ui_admin_dive_center_not_found_for_current_user".localized
            return
        }
        resolvedDiveCenterId = centerId
        isLoading = true
        defer { isLoading = false }
        do {
            services = try await NetworkService.shared.getCenterServices(
                diveCenterId: centerId,
                includeInactive: includeInactive
            )
            errorMessage = nil
        } catch {
            services = []
            errorMessage = userFacingServicesError(error)
        }
    }

    private func delete(at offsets: IndexSet) {
        let selected = offsets.map { services[$0] }
        Task {
            for service in selected {
                do {
                    try await NetworkService.shared.deleteCenterService(serviceId: service.id)
                } catch {
                    errorMessage = userFacingServicesError(error)
                    return
                }
            }
            await loadServices()
        }
    }

    private func resolveDiveCenterId() async -> String? {
        if let id = authService.currentUser?.diveCenterId, !id.isEmpty {
            return id
        }
        guard authService.currentUser?.role == .diveCenterAdmin,
              let email = authService.currentUser?.email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !email.isEmpty else {
            return nil
        }
        do {
            let centers = try await NetworkService.shared.getDiveCenters()
            if let matched = centers.first(where: { $0.contactInfo.email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == email }) {
                if var current = authService.currentUser {
                    current.diveCenterId = matched.id
                    authService.updateUser(current)
                }
                return matched.id
            }
            return nil
        } catch {
            return nil
        }
    }

    private func userFacingServicesError(_ error: Error) -> String {
        let raw = error.localizedDescription
        let lowered = raw.lowercased()
        if lowered.contains("cannot get /api/center-services")
            || lowered.contains("cannot post /api/center-services")
            || lowered.contains("cannot patch /api/center-services")
            || lowered.contains("cannot delete /api/center-services")
            || lowered.contains("center_services")
            || lowered.contains("relation") && lowered.contains("does not exist") {
            return "ui_admin_services_backend_not_deployed".localized
        }
        return raw
    }
}

private struct AdminServiceRow: View {
    let service: DiveCenterService

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(service.name)
                    .font(.headline)
                Spacer()
                Text(service.isActive ? "ui_status_active".localized : "ui_status_inactive".localized)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(service.isActive ? Color.green.opacity(0.2) : Color.gray.opacity(0.2))
                    .foregroundColor(service.isActive ? .green : .secondary)
                    .cornerRadius(8)
            }

            Text(service.type.displayName)
                .font(.subheadline)
                .foregroundColor(.secondary)

            HStack {
                Text(String(format: "%.0f %@", service.price.amount, service.price.currency))
                    .fontWeight(.semibold)
                Spacer()
                Text(localizedPricingUnit(service.pricingUnit))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private func localizedPricingUnit(_ rawValue: String) -> String {
        switch rawValue {
        case "per_person":
            return "ui_pricing_unit_per_person".localized
        default:
            return rawValue.replacingOccurrences(of: "_", with: " ")
        }
    }
}

private struct ServiceEditorView: View {
    enum Mode {
        case create(centerId: String)
        case edit(service: DiveCenterService)
    }

    let mode: Mode
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var id: String?
    @State private var diveCenterId = ""
    @State private var name = ""
    @State private var description = ""
    @State private var type: DiveCenterService.ServiceType = .funDive
    @State private var priceAmount = ""
    @State private var currency = "USD"
    @State private var pricingUnit = "per_person"
    @State private var durationMinutes = ""
    @State private var maxParticipants = ""
    @State private var requirementsText = ""
    @State private var includedText = ""
    @State private var ownGearDiscountPercent = ""
    @State private var nightDiveSurchargeAmount = ""
    @State private var privateInstructorSurchargeAmount = ""
    @State private var groupDiscountThreshold = ""
    @State private var groupDiscountPercent = ""
    @State private var isActive = true
    @State private var isSaving = false
    @State private var errorMessage: String?
    
    private struct PricingUnitOption: Identifiable, Hashable {
        let value: String
        let title: String
        var id: String { value }
    }
    
    private let pricingUnitOptions: [PricingUnitOption] = [
        .init(value: "per_person", title: "ui_pricing_unit_per_person".localized),
        .init(value: "per_group", title: "Per group"),
        .init(value: "per_day", title: "Per day"),
        .init(value: "per_session", title: "Per session")
    ]
    
    private let currencyOptions: [String] = ["USD", "EUR", "RUB", "EGP"]

    var body: some View {
        NavigationStack {
            Form {
                Section("ui_basics".localized) {
                    TextField("ui_admin_name".localized, text: $name)
                    TextField("ui_admin_description".localized, text: $description, axis: .vertical)
                        .lineLimit(3...6)
                    Picker("ui_admin_type".localized, selection: $type) {
                        ForEach(DiveCenterService.ServiceType.allCases, id: \.self) { item in
                            Text(item.displayName).tag(item)
                        }
                    }
                }

                Section("ui_price".localized) {
                    TextField("ui_admin_base_price".localized, text: $priceAmount)
                        .keyboardType(.decimalPad)
                    Picker("ui_admin_currency".localized, selection: $currency) {
                        ForEach(currencyOptions, id: \.self) { item in
                            Text(item).tag(item)
                        }
                    }
                    Picker("ui_admin_pricing_unit".localized, selection: $pricingUnit) {
                        ForEach(pricingUnitOptions) { item in
                            Text(item.title).tag(item.value)
                        }
                    }
                }

                Section("ui_limits".localized) {
                    TextField("ui_admin_duration_minutes".localized, text: $durationMinutes)
                        .keyboardType(.numberPad)
                    TextField("ui_admin_max_participants".localized, text: $maxParticipants)
                        .keyboardType(.numberPad)
                    Toggle("ui_admin_active".localized, isOn: $isActive)
                }

                Section("ui_what_is_included".localized) {
                    TextEditor(text: $includedText)
                        .frame(minHeight: 80)
                    Text("ui_admin_one_item_per_line".localized)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Section("ui_requirements".localized) {
                    TextEditor(text: $requirementsText)
                        .frame(minHeight: 80)
                    Text("ui_admin_one_item_per_line".localized)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Section("ui_pricing_rules".localized) {
                    TextField("ui_admin_own_gear_discount".localized, text: $ownGearDiscountPercent)
                        .keyboardType(.decimalPad)
                    TextField("ui_admin_night_dive_surcharge".localized, text: $nightDiveSurchargeAmount)
                        .keyboardType(.decimalPad)
                    TextField("ui_admin_private_instructor_surcharge".localized, text: $privateInstructorSurchargeAmount)
                        .keyboardType(.decimalPad)
                    TextField("ui_admin_group_discount_threshold".localized, text: $groupDiscountThreshold)
                        .keyboardType(.numberPad)
                    TextField("ui_admin_group_discount".localized, text: $groupDiscountPercent)
                        .keyboardType(.decimalPad)
                }

                if let errorMessage, !errorMessage.isEmpty {
                    Section {
                        Text(errorMessage)
                            .foregroundColor(.red)
                    }
                }
            }
            .navigationTitle(id == nil ? "ui_admin_new_service".localized : "ui_admin_edit_service".localized)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("ui_cancel".localized) { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("ui_save".localized) {
                        Task { await save() }
                    }
                    .disabled(isSaving || !isFormValid)
                }
            }
            .onAppear(perform: bootstrap)
            .onChange(of: type) { _, newValue in
                applyTypeDefaultsIfNeeded(for: newValue)
            }
        }
    }

    private var isFormValid: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        Double(priceAmount.replacingOccurrences(of: ",", with: ".")) != nil &&
        !currency.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func bootstrap() {
        switch mode {
        case .create(let centerId):
            id = nil
            diveCenterId = centerId
            pricingUnit = "per_person"
            currency = "USD"
        case .edit(let service):
            id = service.id
            diveCenterId = service.diveCenterId
            name = service.name
            description = service.description
            type = service.type
            priceAmount = String(format: "%.2f", service.price.amount)
            currency = service.price.currency
            pricingUnit = service.pricingUnit
            durationMinutes = String(service.duration)
            maxParticipants = String(service.maxParticipants)
            requirementsText = service.requirements.joined(separator: "\n")
            includedText = service.includedItems.joined(separator: "\n")
            ownGearDiscountPercent = service.ownGearDiscountPercent.map { String($0) } ?? ""
            nightDiveSurchargeAmount = service.nightDiveSurchargeAmount.map { String($0) } ?? ""
            privateInstructorSurchargeAmount = service.privateInstructorSurchargeAmount.map { String($0) } ?? ""
            groupDiscountThreshold = service.groupDiscountThreshold.map { String($0) } ?? ""
            groupDiscountPercent = service.groupDiscountPercent.map { String($0) } ?? ""
            isActive = service.isActive
        }
    }
    
    private func applyTypeDefaultsIfNeeded(for newType: DiveCenterService.ServiceType) {
        // Keep this lightweight: only suggest defaults when user hasn't typed custom values yet.
        let normalizedPricingUnit = pricingUnit.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let hasCustomPricingUnit = !normalizedPricingUnit.isEmpty && normalizedPricingUnit != "per_person"
        if hasCustomPricingUnit {
            return
        }
        switch newType {
        case .package:
            pricingUnit = "per_group"
        case .equipmentRental:
            pricingUnit = "per_day"
        case .poolSession:
            pricingUnit = "per_session"
        default:
            pricingUnit = "per_person"
        }
    }

    private func save() async {
        guard let parsedAmount = Double(priceAmount.replacingOccurrences(of: ",", with: ".")) else { return }
        isSaving = true
        defer { isSaving = false }

        let service = DiveCenterService(
            id: id ?? UUID().uuidString,
            diveCenterId: diveCenterId,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            description: description.trimmingCharacters(in: .whitespacesAndNewlines),
            type: type,
            price: DiveCenterService.Price(amount: parsedAmount, currency: currency.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()),
            pricingUnit: pricingUnit.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "per_person" : pricingUnit.trimmingCharacters(in: .whitespacesAndNewlines),
            duration: Int(durationMinutes) ?? 0,
            maxParticipants: Int(maxParticipants) ?? 0,
            requirements: splitLines(requirementsText),
            includedItems: splitLines(includedText),
            pricingRules: nil,
            ownGearDiscountPercent: Double(ownGearDiscountPercent.replacingOccurrences(of: ",", with: ".")),
            groupDiscountThreshold: Int(groupDiscountThreshold),
            groupDiscountPercent: Double(groupDiscountPercent.replacingOccurrences(of: ",", with: ".")),
            nightDiveSurchargeAmount: Double(nightDiveSurchargeAmount.replacingOccurrences(of: ",", with: ".")),
            privateInstructorSurchargeAmount: Double(privateInstructorSurchargeAmount.replacingOccurrences(of: ",", with: ".")),
            isActive: isActive,
            createdAt: Date(),
            updatedAt: Date()
        )

        do {
            if id == nil {
                _ = try await NetworkService.shared.createCenterService(service)
            } else {
                _ = try await NetworkService.shared.updateCenterService(service)
            }
            onSaved()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func splitLines(_ value: String) -> [String] {
        value
            .split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
}

#Preview {
    NavigationStack {
        ServicesManagementView()
            .environmentObject(AuthenticationService.shared)
    }
}
