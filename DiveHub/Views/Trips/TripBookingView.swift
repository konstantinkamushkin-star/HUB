//
//  TripBookingView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

enum ParticipantStatus: String, CaseIterable {
    case certified = "certified"
    case discoverScuba = "discover_scuba"
    case wantCourse = "want_course"
    
    var displayName: String {
        switch self {
        case .certified:
            return "Сертифицированный дайвер"
        case .discoverScuba:
            return "Хочу попробовать (Discover Scuba)"
        case .wantCourse:
            return "Хочу пройти курс"
        }
    }
}

struct TripBookingView: View {
    let trip: Trip
    @StateObject private var viewModel = TripViewModel()
    @Environment(\.dismiss) var dismiss
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    
    @State private var participantStatus: ParticipantStatus = .certified
    @State private var participants: [TripParticipantForm] = []
    @State private var selectedCourses: Set<String> = []
    @State private var needsEquipmentRental: Bool = false
    @State private var notes: String = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showSuccess = false
    
    init(trip: Trip) {
        self.trip = trip
    }
    
    var body: some View {
        NavigationView {
            formContent
                .navigationTitle(localizationService.localizedString("bookTrip", table: "trips"))
                .toolbar {
                    toolbarContent
                }
                .onAppear {
                    initializeParticipants()
                }
                .alert("Success", isPresented: $showSuccess) {
                    Button(localizationService.localizedString("ok", table: "common")) {
                        dismiss()
                    }
                } message: {
                    Text(localizationService.localizedString("tripBooked", table: "trips"))
                }
                .alert("Error", isPresented: Binding(
                    get: { errorMessage != nil },
                    set: { if !$0 { errorMessage = nil } }
                )) {
                    Button("OK") {
                        errorMessage = nil
                    }
                } message: {
                    if let error = errorMessage {
                        Text(error)
                    }
                }
        }
    }
    
    @ViewBuilder
    private var formContent: some View {
        Form {
            statusSection
            participantSections
            equipmentSection
            notesSection
            priceSection
                    }
                }
                
    @ViewBuilder
    private var statusSection: some View {
                Section("1️⃣ \(localizationService.localizedString("selectStatus", table: "trips"))") {
                    Picker(localizationService.localizedString("status", table: "trips"), selection: $participantStatus) {
                        ForEach(ParticipantStatus.allCases, id: \.self) { status in
                            Text(status.displayName).tag(status)
                }
                        }
                    }
                }
                
    @ViewBuilder
    private var participantSections: some View {
        if !participants.isEmpty {
                if participantStatus == .certified {
                    certifiedDiverSection
                } else if participantStatus == .discoverScuba {
                    discoverScubaSection
                } else if participantStatus == .wantCourse {
                    wantCourseSection
            }
        }
                }
                
    @ViewBuilder
    private var equipmentSection: some View {
                if trip.equipmentRentalAvailable && participantStatus == .certified {
                    Section("Equipment") {
                        Toggle(localizationService.localizedString("rentEquipment", table: "trips"), isOn: $needsEquipmentRental)
            }
                    }
                }
                
    @ViewBuilder
    private var notesSection: some View {
                Section(localizationService.localizedString("notes", table: "trips")) {
                    TextEditor(text: $notes)
                        .frame(height: 100)
        }
                }
                
    @ViewBuilder
    private var priceSection: some View {
                Section(localizationService.localizedString("priceSummary", table: "trips")) {
                    let totalPrice = calculateTotalPrice()
            let currency = trip.priceDetails.currency.isEmpty ? "USD" : trip.priceDetails.currency
                    HStack {
                        Text(localizationService.localizedString("total", table: "trips"))
                            .font(.headline)
                        Spacer()
                Text("\(totalPrice, format: .currency(code: currency))")
                            .font(.headline)
                    }
                }
            }
    
    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("confirm", table: "trips")) {
                        Task {
                            await bookTrip()
                        }
                    }
                    .disabled(!isBookingValid || isLoading)
                }
            }
    
    private func initializeParticipants() {
                if let user = authService.currentUser {
            let userName = user.displayName.isEmpty ? (user.email.components(separatedBy: "@").first ?? "User") : user.displayName
            
            // Парсим сертификацию пользователя
            var userCert: Certification? = nil
            if let certLevel = user.certificationLevel {
                // Пытаемся распарсить сертификацию из строки
                userCert = parseCertificationFromString(certLevel)
            }
            
                    participants = [TripParticipantForm(
                        id: UUID().uuidString,
                name: userName,
                        email: user.email,
                        phoneNumber: user.phoneNumber,
                certification: userCert,
                        certificationLevel: user.certificationLevel,
                        isDiving: true,
                        status: participantStatus
                    )]
        } else {
            participants = []
                }
                
                Task {
                    await viewModel.loadCourses()
                }
            }
    
    // Парсинг сертификации из строки (например, "PADI - Divemaster" или "CMAS 3*")
    private func parseCertificationFromString(_ string: String) -> Certification? {
        let parts = string.components(separatedBy: " - ")
        guard parts.count >= 2 else {
            // Если формат не "Organization - Level", возвращаем nil
            return nil
        }
        
        let organization = parts[0].trimmingCharacters(in: .whitespaces)
        let level = parts[1].trimmingCharacters(in: .whitespaces)
        
        return Certification(
            id: UUID().uuidString,
            organization: organization,
            level: level,
            cardImageURL: nil,
            issueDate: nil,
            verificationStatus: .pending,
            instructorNumber: nil
        )
    }
    
    // MARK: - Certified Diver Section
    @ViewBuilder
    private var certifiedDiverSection: some View {
            Section("\(localizationService.localizedString("participantInfo", table: "trips"))") {
            ForEach(participants.indices, id: \.self) { index in
                    VStack(alignment: .leading, spacing: 8) {
                        TextField(localizationService.localizedString("name", table: "trips"), text: $participants[index].name)
                        TextField(localizationService.localizedString("email", table: "trips"), text: Binding(
                            get: { participants[index].email ?? "" },
                            set: { participants[index].email = $0.isEmpty ? nil : $0 }
                        ))
                        .keyboardType(.emailAddress)
                        TextField(localizationService.localizedString("phone", table: "trips"), text: Binding(
                            get: { participants[index].phoneNumber ?? "" },
                            set: { participants[index].phoneNumber = $0.isEmpty ? nil : $0 }
                        ))
                        .keyboardType(.phonePad)
                        
                        Picker(localizationService.localizedString("certificationLevel", table: "trips"), selection: $participants[index].certificationLevel) {
                            Text(localizationService.localizedString("none", table: "trips")).tag(nil as String?)
                            Text("Open Water").tag("Open Water" as String?)
                            Text("Advanced Open Water").tag("Advanced Open Water" as String?)
                            Text("Rescue Diver").tag("Rescue Diver" as String?)
                            Text("Divemaster").tag("Divemaster" as String?)
                        }
                        
                        HStack {
                            Text(localizationService.localizedString("numberOfDives", table: "trips"))
                            Spacer()
                            TextField("0", value: $participants[index].numberOfDives, format: .number)
                                .keyboardType(.numberPad)
                                .frame(width: 80)
                        }
                        
                        if let lastDive = participants[index].lastDiveDate {
                            DatePicker(localizationService.localizedString("lastDive", table: "trips"), selection: Binding(
                                get: { lastDive },
                                set: { participants[index].lastDiveDate = $0 }
                            ), displayedComponents: .date)
                        } else {
                            Button(action: {
                                participants[index].lastDiveDate = Date()
                            }) {
                                Text(localizationService.localizedString("setLastDive", table: "trips"))
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                Button(localizationService.localizedString("addParticipant", table: "trips")) {
                    participants.append(TripParticipantForm(
                        id: UUID().uuidString,
                        name: "",
                        email: nil,
                        phoneNumber: nil,
                        certificationLevel: nil,
                        isDiving: true,
                        status: participantStatus
                    ))
                }
                .disabled(participants.count >= trip.availableSpots)
        }
    }
    
    // MARK: - Discover Scuba Section
    @ViewBuilder
    private var discoverScubaSection: some View {
            Section("\(localizationService.localizedString("participantInfo", table: "trips"))") {
            ForEach(participants.indices, id: \.self) { index in
                    VStack(alignment: .leading, spacing: 8) {
                        TextField(localizationService.localizedString("name", table: "trips"), text: $participants[index].name)
                        TextField(localizationService.localizedString("email", table: "trips"), text: Binding(
                            get: { participants[index].email ?? "" },
                            set: { participants[index].email = $0.isEmpty ? nil : $0 }
                        ))
                        .keyboardType(.emailAddress)
                        TextField(localizationService.localizedString("phone", table: "trips"), text: Binding(
                            get: { participants[index].phoneNumber ?? "" },
                            set: { participants[index].phoneNumber = $0.isEmpty ? nil : $0 }
                        ))
                        .keyboardType(.phonePad)
                        
                        Toggle(localizationService.localizedString("canSwim", table: "trips"), isOn: Binding(
                            get: { participants[index].canSwim ?? false },
                            set: { participants[index].canSwim = $0 }
                        ))
                        
                        HStack {
                            Text(localizationService.localizedString("height", table: "trips"))
                            Spacer()
                            TextField("cm", value: $participants[index].height, format: .number)
                                .keyboardType(.decimalPad)
                                .frame(width: 80)
                            Text("cm")
                        }
                        
                        HStack {
                            Text(localizationService.localizedString("weight", table: "trips"))
                            Spacer()
                            TextField("kg", value: $participants[index].weight, format: .number)
                                .keyboardType(.decimalPad)
                                .frame(width: 80)
                            Text("kg")
                        }
                        
                        Picker(localizationService.localizedString("instructorLanguage", table: "trips"), selection: Binding(
                            get: { participants[index].instructorLanguage ?? "ru" },
                            set: { participants[index].instructorLanguage = $0 }
                        )) {
                            Text("Русский").tag("ru")
                            Text("English").tag("en")
                            Text("Español").tag("es")
                            Text("Français").tag("fr")
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                Button(localizationService.localizedString("addParticipant", table: "trips")) {
                    participants.append(TripParticipantForm(
                        id: UUID().uuidString,
                        name: "",
                        email: nil,
                        phoneNumber: nil,
                    certification: nil,
                        certificationLevel: nil,
                        isDiving: false,
                        status: participantStatus
                    ))
                }
                .disabled(participants.count >= trip.availableSpots)
        }
    }
    
    // MARK: - Want Course Section
    @ViewBuilder
    private var wantCourseSection: some View {
            if !trip.availableCourses.isEmpty {
            courseSelectionSection
        }
        participantInfoSection
    }
    
    @ViewBuilder
    private var courseSelectionSection: some View {
                Section("\(localizationService.localizedString("selectCourse", table: "trips"))") {
                    ForEach(trip.availableCourses, id: \.self) { courseId in
                        if let course = viewModel.courses.first(where: { $0.id == courseId }) {
                            Toggle(course.name, isOn: Binding(
                                get: { selectedCourses.contains(courseId) },
                                set: { isOn in
                                    if isOn {
                                        selectedCourses.insert(courseId)
                                    } else {
                                        selectedCourses.remove(courseId)
                                    }
                                }
                            ))
                        }
                    }
                }
            }
            
    @ViewBuilder
    private var participantInfoSection: some View {
            Section("\(localizationService.localizedString("participantInfo", table: "trips"))") {
            ForEach(participants.indices, id: \.self) { index in
                participantInfoRow(index: index)
            }
            
            Button(localizationService.localizedString("addParticipant", table: "trips")) {
                participants.append(TripParticipantForm(
                    id: UUID().uuidString,
                    name: "",
                    email: nil,
                    phoneNumber: nil,
                    certificationLevel: nil,
                    isDiving: false,
                    status: participantStatus
                ))
            }
            .disabled(participants.count >= trip.availableSpots)
        }
    }
    
    @ViewBuilder
    private func participantInfoRow(index: Int) -> some View {
                    VStack(alignment: .leading, spacing: 8) {
                        TextField(localizationService.localizedString("name", table: "trips"), text: $participants[index].name)
                        TextField(localizationService.localizedString("email", table: "trips"), text: Binding(
                            get: { participants[index].email ?? "" },
                            set: { participants[index].email = $0.isEmpty ? nil : $0 }
                        ))
                        .keyboardType(.emailAddress)
                        TextField(localizationService.localizedString("phone", table: "trips"), text: Binding(
                            get: { participants[index].phoneNumber ?? "" },
                            set: { participants[index].phoneNumber = $0.isEmpty ? nil : $0 }
                        ))
                        .keyboardType(.phonePad)
                        
                        Toggle(localizationService.localizedString("canSwim", table: "trips"), isOn: Binding(
                            get: { participants[index].canSwim ?? false },
                            set: { participants[index].canSwim = $0 }
                        ))
                        
                        HStack {
                            Text(localizationService.localizedString("height", table: "trips"))
                            Spacer()
                            TextField("cm", value: $participants[index].height, format: .number)
                                .keyboardType(.decimalPad)
                                .frame(width: 80)
                            Text("cm")
                        }
                        
                        HStack {
                            Text(localizationService.localizedString("weight", table: "trips"))
                            Spacer()
                            TextField("kg", value: $participants[index].weight, format: .number)
                                .keyboardType(.decimalPad)
                                .frame(width: 80)
                            Text("kg")
                        }
                        
                        Picker(localizationService.localizedString("instructorLanguage", table: "trips"), selection: Binding(
                            get: { participants[index].instructorLanguage ?? "ru" },
                            set: { participants[index].instructorLanguage = $0 }
                        )) {
                            Text("Русский").tag("ru")
                            Text("English").tag("en")
                            Text("Español").tag("es")
                            Text("Français").tag("fr")
                        }
                    }
                    .padding(.vertical, 4)
    }
    
    private var isBookingValid: Bool {
        let participantsEmpty = participants.isEmpty
        let allNamesNotEmpty = participants.allSatisfy { !$0.name.isEmpty }
        let participantsCountValid = participants.count <= trip.availableSpots
        let requiresCourseSelection = participantStatus == .wantCourse && !trip.availableCourses.isEmpty
        let hasRequiredCourses = !requiresCourseSelection || !selectedCourses.isEmpty
        let isValid = !participantsEmpty && allNamesNotEmpty && participantsCountValid && hasRequiredCourses
        return isValid
    }
    
    private func calculateTotalPrice() -> Double {
        var total: Double = 0
        
        for participant in participants {
            if participant.isDiving {
                total += trip.priceDetails.divingPrice ?? 0
            } else {
                total += trip.priceDetails.nonDivingPrice ?? 0
            }
        }
        
        // Course prices are not included in the Course model
        // If course pricing is needed, it should be added to the Course model or handled separately
        
        // Add additional expenses
        for expense in trip.additionalExpenses {
            total += expense.cost
        }
        
        return total
    }
    
    private func bookTrip() async {
        guard let user = authService.currentUser else {
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        let tripParticipants = participants.map { form in
            // Используем новую модель сертификации, если доступна, иначе старую строку
            let certLevelString: String?
            if let cert = form.certification {
                certLevelString = cert.displayName
            } else {
                certLevelString = form.certificationLevel
            }
            
            return Trip.TripParticipant(
                id: form.id,
                userId: user.id,
                name: form.name,
                email: form.email,
                phoneNumber: form.phoneNumber,
                certificationLevel: certLevelString,
                isDiving: form.isDiving,
                bookedAt: Date()
            )
        }
        
        do {
            let updatedTrip = try await viewModel.bookTrip(tripId: trip.id, participants: tripParticipants)
            
            // Create chat with dive center/instructor
            await createChatForBooking(booking: updatedTrip)
            
            showSuccess = true
        } catch {
            errorMessage = error.localizedDescription
        }
        
        isLoading = false
    }
    
    private func createChatForBooking(booking: Trip) async {
        guard authService.currentUser != nil else { return }
        
        do {
            let conv: ChatConversation
            switch booking.organizerType {
            case .diveCenter:
                conv = try await NetworkService.shared.openChatConversation(
                    peerType: "dive_center",
                    peerId: booking.organizerId
                )
            case .user:
                conv = try await NetworkService.shared.openChatConversation(
                    peerType: "user",
                    peerId: booking.organizerId
                )
            }
            _ = try await NetworkService.shared.sendChatMessage(
                conversationId: conv.id,
                content: "Здравствуйте! Я забронировал поездку \(booking.country). Хотел бы уточнить детали."
            )
        } catch {
            print("Failed to create chat: \(error.localizedDescription)")
        }
    }
}

struct TripParticipantForm: Identifiable {
    let id: String
    var name: String
    var email: String?
    var phoneNumber: String?
    var certification: Certification? // Новая модель сертификации
    var certificationLevel: String? // Для обратной совместимости
    var isDiving: Bool
    var status: ParticipantStatus = .certified
    
    // Выбранные курсы (специализации и авторские)
    var selectedSpecializations: Set<Specialization> = []
    var selectedCustomCourses: Set<String> = [] // ID авторских курсов
    
    // For certified divers
    var numberOfDives: Int?
    var lastDiveDate: Date?
    
    // For discover scuba / course
    var canSwim: Bool?
    var height: Double?
    var weight: Double?
    var instructorLanguage: String?
    
    // Computed property для обратной совместимости
    var certificationLevelString: String? {
        get {
            if let cert = certification {
                return cert.displayName
            }
            return certificationLevel
        }
        set {
            certificationLevel = newValue
        }
    }
}
