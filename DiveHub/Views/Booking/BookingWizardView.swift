//
//  BookingWizardView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct BookingWizardView: View {
    var diveSiteId: String?
    var diveCenterId: String?
    var instructorId: String?

    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = BookingWizardViewModel()
    @State private var currentStep = 0
    @State private var completedBooking: Booking?
    @State private var chatConversation: ChatConversation?
    @State private var showChatAfterConfirmation = false

    var body: some View {
        NavigationView {
            VStack {
                ProgressView(value: Double(currentStep), total: Double(viewModel.totalSteps - 1))
                    .padding(.horizontal)

                TabView(selection: $currentStep) {
                    SelectBookingTypeAndCenterStep(viewModel: viewModel)
                        .tag(0)
                    ScheduleAndGroupStep(viewModel: viewModel)
                        .tag(1)
                    PreferencesStep(viewModel: viewModel)
                        .tag(2)
                    ParticipantsStep(viewModel: viewModel)
                        .tag(3)
                    ReviewAndPaymentStep(viewModel: viewModel)
                        .tag(4)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))

                HStack {
                    if currentStep > 0 {
                        Button("ui_text_2".localized) {
                            withAnimation { currentStep -= 1 }
                        }
                    }

                    Spacer()

                    if currentStep < viewModel.totalSteps - 1 {
                        Button("ui_text".localized) {
                            withAnimation { currentStep += 1 }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(!viewModel.canProceed(step: currentStep))
                    } else {
                        Button("ui_nn2_nn_n2on".localized) {
                            Task {
                                if let result = await viewModel.confirmBooking() {
                                    completedBooking = result.booking
                                    chatConversation = result.chatConversation
                                }
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.isLoading || !viewModel.canProceed(step: currentStep))
                    }
                }
                .padding()
            }
            .navigationTitle("ui_booking_n3412_n34212".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("ui_onnnn".localized) { dismiss() }
                }
            }
            .fullScreenCover(item: $completedBooking) { booking in
                NavigationView {
                    BookingConfirmationView(
                        booking: booking,
                        onOpenChat: chatConversation == nil ? nil : {
                            showChatAfterConfirmation = true
                        }
                    )
                }
            }
            .sheet(isPresented: $showChatAfterConfirmation) {
                if let conversation = chatConversation {
                    NavigationStack {
                        ChatDetailView(conversation: conversation)
                    }
                }
            }
        }
        .onAppear {
            if let centerId = diveCenterId {
                viewModel.selectedCenterId = centerId
            }
            if let siteId = diveSiteId {
                viewModel.selectedDiveSiteId = siteId
            }
            if let instructorId = instructorId {
                viewModel.selectedInstructorId = instructorId
            }
            Task {
                await viewModel.loadServicesForSelectedCenter()
            }
        }
    }
}

struct SelectBookingTypeAndCenterStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel
    @StateObject private var exploreViewModel = ExploreViewModel()

    var body: some View {
        List {
            Section("ui_n3412_n34212_n".localized) {
                Picker("ui_booking_n12n_1".localized, selection: $viewModel.bookingType) {
                    ForEach(BookingWizardViewModel.BookingType.allCases, id: \.self) { type in
                        Text(type.displayName).tag(type)
                    }
                }
                .pickerStyle(.segmented)
            }

            Section("ui_12n12nn".localized) {
                if exploreViewModel.isLoading {
                    ProgressView()
                } else {
                    ForEach(exploreViewModel.diveCenters) { center in
                        Button {
                            viewModel.selectedCenterId = center.id
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(center.name)
                                        .foregroundColor(.primary)
                                    Text(center.location.city)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                if viewModel.selectedCenterId == center.id {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(.divePrimary)
                                }
                            }
                        }
                    }
                }
            }

            Section("ui_nn3".localized) {
                if viewModel.isLoadingServices {
                    ProgressView()
                } else if viewModel.availableServices.isEmpty {
                    Text("ui_booking_services_not_configured_warning".localized)
                        .font(.caption)
                        .foregroundColor(.secondary)
                } else {
                    ForEach(viewModel.availableServices) { service in
                        Button {
                            viewModel.selectedServiceId = service.id
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(service.name)
                                        .foregroundColor(.primary)
                                    Text(String(format: "%.0f %@", service.price.amount, service.price.currency))
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                if viewModel.selectedServiceId == service.id {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(.divePrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
        .task {
            await exploreViewModel.loadData()
            await viewModel.loadServicesForSelectedCenter()
        }
        .onChange(of: viewModel.selectedCenterId) { _, _ in
            Task {
                await viewModel.loadServicesForSelectedCenter()
            }
        }
    }
}

struct ScheduleAndGroupStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel

    var body: some View {
        Form {
            if viewModel.bookingType == .openWater {
                Section("ui_34_o".localized) {
                    DatePicker("ui_booking_start_date".localized, selection: $viewModel.startDate, in: Date()..., displayedComponents: .date)
                    DatePicker("ui_booking_end_date".localized, selection: $viewModel.endDate, in: viewModel.startDate..., displayedComponents: .date)
                }
            } else {
                Section("ui_nn_n_2_nn112".localized) {
                    DatePicker("ui_booking_n".localized, selection: $viewModel.poolDate, in: Date()..., displayedComponents: .date)
                    Picker("ui_booking_n14n".localized, selection: $viewModel.poolTime) {
                        ForEach(BookingWizardViewModel.poolTimeSlots, id: \.self) { slot in
                            Text(slot).tag(slot)
                        }
                    }
                }
            }

            Section("ui_nn".localized) {
                Stepper("Участников: \(viewModel.participantsCount)", value: $viewModel.participantsCount, in: 1...20)
            }
        }
    }
}

struct PreferencesStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel

    var body: some View {
        Form {
            if viewModel.bookingType == .openWater {
                Section("ui_3412_n_34_12nnnnon34nn".localized) {
                    Picker("ui_booking_no".localized, selection: $viewModel.preferredInstructorLanguage) {
                        Text("ui_booking_value_3".localized).tag("")
                        Text("ui_booking_language_russian".localized).tag("ru")
                        Text("ui_booking_english".localized).tag("en")
                        Text("ui_booking_espaaol".localized).tag("es")
                        Text("ui_booking_franaais".localized).tag("fr")
                    }
                    Toggle("ui_booking_needs_private_instructor".localized, isOn: $viewModel.needsPrivateInstructor)
                    TextEditor(text: $viewModel.instructorNotes)
                        .frame(minHeight: 110)
                }
            } else {
                Section("ui_12nn12".localized) {
                    Toggle("ui_booking_n12_n12_n12nn12_n".localized, isOn: $viewModel.needsEquipmentRental)
                }
                Section("ui_343412_nn1234".localized) {
                    TextEditor(text: $viewModel.poolPreferences)
                        .frame(minHeight: 110)
                }
            }
        }
    }
}

struct ParticipantsStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel

    var body: some View {
        List {
            Section("ui_nnn12_o".localized) {
                ForEach($viewModel.participants) { $participant in
                    VStack(alignment: .leading, spacing: 8) {
                        TextField("ui_booking_name".localized, text: $participant.name)
                        TextField("ui_booking_email_optional".localized, text: Binding(
                            get: { participant.email ?? "" },
                            set: { participant.email = $0.isEmpty ? nil : $0 }
                        ))
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .onAppear {
            viewModel.syncParticipantsWithCount()
        }
        .onChange(of: viewModel.participantsCount) { _, _ in
            viewModel.syncParticipantsWithCount()
        }
    }
}

struct ReviewAndPaymentStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel

    var body: some View {
        Form {
            Section("ui_n343".localized) {
                Text("ui_booking_value".localized)
                Text("ui_booking_participants_count".localized)
                if viewModel.selectedService != nil {
                    Text("ui_booking_service_selected".localized)
                }
                if viewModel.bookingType == .openWater {
                    Text("ui_booking_dates_range".localized)
                } else {
                    Text("ui_booking_pool_slot_summary".localized)
                    Text(viewModel.needsEquipmentRental ? "Аренда: нужна" : "Аренда: не нужна")
                }
                if let estimate = viewModel.estimatedTotalAmount {
                    Text("Предварительная цена: \(String(format: "%.0f %@", estimate, viewModel.estimateCurrency))")
                        .fontWeight(.semibold)
                    Text("ui_booking_final_amount_confirmed_manually".localized)
                        .font(.caption)
                        .foregroundColor(.secondary)
                } else {
                    Text("ui_booking_price_confirmed_by_center".localized)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Section("ui_n".localized) {
                Picker("ui_booking_method".localized, selection: $viewModel.paymentMethod) {
                    Text("ui_booking_value_2".localized).tag(Booking.Payment.PaymentMethod.online)
                    Text("ui_booking_on_site".localized).tag(Booking.Payment.PaymentMethod.onSite)
                    Text("ui_booking_apple_pay".localized).tag(Booking.Payment.PaymentMethod.applePay)
                    Text("ui_booking_google_pay".localized).tag(Booking.Payment.PaymentMethod.googlePay)
                }
            }

            Section("ui_34141412nn_1".localized) {
                TextEditor(text: $viewModel.generalNotes)
                    .frame(minHeight: 100)
            }

            if let error = viewModel.errorMessage, !error.isEmpty {
                Section {
                    Text(error)
                        .foregroundColor(.red)
                }
            }
        }
    }
}

@MainActor
final class BookingWizardViewModel: ObservableObject {
    enum BookingType: String, CaseIterable {
        case openWater = "open_water"
        case pool = "pool"

        var displayName: String {
            switch self {
            case .openWater: return "Дайвинг / поездка"
            case .pool: return "Бассейн"
            }
        }
    }

    struct ConfirmationResult {
        let booking: Booking
        let chatConversation: ChatConversation?
    }

    static let poolTimeSlots = ["08:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00"]

    @Published var bookingType: BookingType = .openWater
    @Published var selectedCenterId: String?
    @Published var selectedDiveSiteId: String?
    @Published var selectedInstructorId: String?
    @Published var selectedServiceId: String?
    @Published var availableServices: [DiveCenterService] = []
    @Published var isLoadingServices = false

    @Published var startDate = Date()
    @Published var endDate = Calendar.current.date(byAdding: .day, value: 3, to: Date()) ?? Date()
    @Published var poolDate = Date()
    @Published var poolTime = "10:00"

    @Published var participantsCount = 1
    @Published var participants: [Booking.Participant] = []

    @Published var preferredInstructorLanguage = ""
    @Published var instructorNotes = ""
    @Published var poolPreferences = ""
    @Published var needsEquipmentRental = false
    @Published var needsPrivateInstructor = false
    @Published var generalNotes = ""

    @Published var paymentMethod: Booking.Payment.PaymentMethod = .online
    @Published var isLoading = false
    @Published var errorMessage: String?

    let totalSteps = 5

    var selectedService: DiveCenterService? {
        guard let selectedServiceId else { return nil }
        return availableServices.first(where: { $0.id == selectedServiceId })
    }

    var estimateCurrency: String {
        selectedService?.price.currency ?? "USD"
    }

    var estimatedTotalAmount: Double? {
        guard let service = selectedService else { return nil }

        let base = service.price.amount
        let unit = service.pricingUnit.lowercased()
        let days = max(1, Calendar.current.dateComponents([.day], from: startDate, to: endDate).day ?? 0)

        var total: Double
        switch unit {
        case "per_person":
            total = base * Double(participantsCount)
        case "per_group":
            total = base
        case "per_day":
            total = base * Double(days)
        case "per_dive":
            total = base * Double(participantsCount)
        default:
            total = base
        }

        if !needsEquipmentRental, let ownGearDiscountPercent = service.ownGearDiscountPercent {
            total -= total * (ownGearDiscountPercent / 100)
        }

        if let threshold = service.groupDiscountThreshold,
           participantsCount >= threshold,
           let groupDiscountPercent = service.groupDiscountPercent {
            total -= total * (groupDiscountPercent / 100)
        }

        if service.type == .nightDive, let nightSurcharge = service.nightDiveSurchargeAmount {
            total += nightSurcharge
        }

        if needsPrivateInstructor, let privateSurcharge = service.privateInstructorSurchargeAmount {
            total += privateSurcharge
        }

        return max(0, total)
    }

    func canProceed(step: Int) -> Bool {
        switch step {
        case 0:
            guard selectedCenterId != nil else { return false }
            if availableServices.isEmpty { return true }
            return selectedServiceId != nil
        case 1:
            if bookingType == .openWater {
                return endDate >= startDate && participantsCount > 0
            }
            return !poolTime.isEmpty && participantsCount > 0
        case 2, 3:
            return participantsCount > 0
        case 4:
            return selectedCenterId != nil && participantsCount > 0
        default:
            return true
        }
    }

    func syncParticipantsWithCount() {
        if participants.count < participantsCount {
            let missing = participantsCount - participants.count
            for _ in 0..<missing {
                participants.append(
                    Booking.Participant(
                        id: UUID().uuidString,
                        name: "",
                        email: nil,
                        phoneNumber: nil,
                        certificationLevel: nil,
                        isFriend: false,
                        friendUserId: nil
                    )
                )
            }
        } else if participants.count > participantsCount {
            participants = Array(participants.prefix(participantsCount))
        }
    }

    func confirmBooking() async -> ConfirmationResult? {
        guard let userId = AuthenticationService.shared.currentUser?.id,
              let centerId = selectedCenterId else {
            errorMessage = "Не удалось определить пользователя или дайвцентр."
            return nil
        }

        syncParticipantsWithCount()
        errorMessage = nil
        isLoading = true

        let normalizedParticipants = normalizedParticipantsPayload()
        let bookingDate = bookingType == .openWater ? startDate : poolDate
        let startTime = bookingType == .openWater ? "09:00" : poolTime
        let serviceId = selectedServiceId ?? (bookingType == .openWater ? "open_water_request" : "pool_session_request")
        let estimatedAmount = estimatedTotalAmount
        let trimmedGeneralNotes = generalNotes.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedPoolPreferences = poolPreferences.trimmingCharacters(in: .whitespacesAndNewlines)
        let mergedNotes: String? = {
            var lines: [String] = []
            if let estimatedAmount {
                lines.append("client_price_estimate=\(String(format: "%.2f %@", estimatedAmount, estimateCurrency))")
                lines.append("price_verification=manual_by_dive_center")
            }
            if bookingType == .pool {
                if !trimmedGeneralNotes.isEmpty && !trimmedPoolPreferences.isEmpty {
                    lines.append(trimmedGeneralNotes)
                    lines.append("Pool preferences: \(trimmedPoolPreferences)")
                    return lines.joined(separator: "\n")
                }
                if !trimmedGeneralNotes.isEmpty {
                    lines.append(trimmedGeneralNotes)
                    return lines.joined(separator: "\n")
                }
                if !trimmedPoolPreferences.isEmpty {
                    lines.append("Pool preferences: \(trimmedPoolPreferences)")
                    return lines.joined(separator: "\n")
                }
                return lines.isEmpty ? nil : lines.joined(separator: "\n")
            }
            if !trimmedGeneralNotes.isEmpty {
                lines.append(trimmedGeneralNotes)
            }
            return lines.isEmpty ? nil : lines.joined(separator: "\n")
        }()
        let gearPayload = needsEquipmentRental ? defaultGearRentalPayload() : nil
        let requestMode: Booking.RequestMode = .manualApproval
        let instructorPrefs = Booking.InstructorPreferences(
            language: preferredInstructorLanguage.isEmpty ? nil : preferredInstructorLanguage,
            notes: instructorNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : instructorNotes.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        let equipmentRequest = Booking.EquipmentRentalRequest(
            required: needsEquipmentRental,
            items: nil
        )

        let booking = Booking(
            id: UUID().uuidString,
            userId: userId,
            diveCenterId: centerId,
            serviceId: serviceId,
            diveSiteId: selectedDiveSiteId,
            instructorId: selectedInstructorId,
            date: bookingDate,
            startTime: startTime,
            participants: normalizedParticipants,
            gearRental: gearPayload,
            payment: Booking.Payment(
                method: paymentMethod,
                amount: estimatedAmount ?? 0,
                currency: estimateCurrency,
                status: .pending,
                transactionId: nil,
                paidAt: nil
            ),
            status: .pending,
            notes: mergedNotes,
            bookingType: bookingType == .openWater ? .openWater : .pool,
            requestMode: requestMode,
            dateEnd: bookingType == .openWater ? endDate : nil,
            sessionId: bookingType == .pool ? "\(poolDate.formatted(date: .numeric, time: .omitted))-\(poolTime)" : nil,
            participantsCount: participantsCount,
            instructorPreferences: bookingType == .openWater ? instructorPrefs : nil,
            equipmentRental: bookingType == .pool ? equipmentRequest : nil,
            createdAt: Date(),
            updatedAt: Date()
        )

        do {
            let createdBooking = try await NetworkService.shared.createBooking(booking)
            let conversation = await openBookingConversation(centerId: centerId)
            isLoading = false
            return ConfirmationResult(booking: createdBooking, chatConversation: conversation)
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
            return nil
        }
    }

    private func normalizedParticipantsPayload() -> [Booking.Participant] {
        participants.enumerated().map { index, participant in
            let trimmedName = participant.name.trimmingCharacters(in: .whitespacesAndNewlines)
            let safeName = trimmedName.isEmpty ? "Participant \(index + 1)" : trimmedName
            let safeEmail: String? = {
                let raw = participant.email?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                return raw.isEmpty ? nil : raw
            }()
            return Booking.Participant(
                id: participant.id,
                name: safeName,
                email: safeEmail,
                phoneNumber: participant.phoneNumber,
                certificationLevel: participant.certificationLevel,
                isFriend: participant.isFriend,
                friendUserId: participant.friendUserId
            )
        }
    }

    private func defaultGearRentalPayload() -> [Booking.GearRental] {
        [
            Booking.GearRental(
                id: UUID().uuidString,
                gearItemId: "rental-request",
                gearName: "Rental requested",
                size: "TBD",
                quantity: participantsCount,
                price: 0
            )
        ]
    }

    private func isoDate(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        return formatter.string(from: date)
    }

    private func openBookingConversation(centerId: String) async -> ChatConversation? {
        do {
            let conversation = try await NetworkService.shared.openChatConversation(
                peerType: "dive_center",
                peerId: centerId
            )
            let intro = makeBookingIntroMessage()
            _ = try await NetworkService.shared.sendChatMessage(
                conversationId: conversation.id,
                content: intro
            )
            return conversation
        } catch {
            return nil
        }
    }

    private func makeBookingIntroMessage() -> String {
        let selectedServiceLine = selectedService?.name ?? "без конкретной услуги"
        let estimateLine = estimatedTotalAmount.map { String(format: "%.0f %@", $0, estimateCurrency) } ?? "уточняется"
        if bookingType == .openWater {
            let lang = preferredInstructorLanguage.isEmpty ? "не указан" : preferredInstructorLanguage
            return """
            Здравствуйте! Отправил заявку на дайвинг.
            Услуга: \(selectedServiceLine).
            Формат: поездка (\(isoDate(startDate)) - \(isoDate(endDate))).
            Участников: \(participantsCount).
            Язык инструктора: \(lang).
            Пожелания: \(instructorNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "нет" : instructorNotes.trimmingCharacters(in: .whitespacesAndNewlines)).
            Предварительная цена: \(estimateLine) (ручная верификация).
            """
        }

        return """
        Здравствуйте! Отправил заявку на бассейн.
        Услуга: \(selectedServiceLine).
        Слот: \(isoDate(poolDate)) \(poolTime).
        Участников: \(participantsCount).
        Аренда: \(needsEquipmentRental ? "нужна" : "не нужна").
        Комментарий: \(poolPreferences.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "нет" : poolPreferences.trimmingCharacters(in: .whitespacesAndNewlines)).
        Предварительная цена: \(estimateLine) (ручная верификация).
        """
    }

    func loadServicesForSelectedCenter() async {
        guard let selectedCenterId else {
            availableServices = []
            selectedServiceId = nil
            return
        }
        isLoadingServices = true
        defer { isLoadingServices = false }
        do {
            let loaded = try await NetworkService.shared.getCenterServices(diveCenterId: selectedCenterId)
            availableServices = loaded.filter { $0.isActive }
            if let selectedServiceId, availableServices.contains(where: { $0.id == selectedServiceId }) {
                return
            }
            selectedServiceId = availableServices.first?.id
        } catch {
            availableServices = []
            selectedServiceId = nil
        }
    }
}

#Preview {
    BookingWizardView()
}
