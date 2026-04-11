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
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel = BookingWizardViewModel()
    @State private var currentStep = 0
    @State private var completedBooking: Booking?
    
    var body: some View {
        NavigationView {
            VStack {
                // Progress Indicator
                ProgressView(value: Double(currentStep), total: Double(viewModel.totalSteps - 1))
                    .padding()
                
                // Step Content
                TabView(selection: $currentStep) {
                    SelectCenterStep(viewModel: viewModel)
                        .tag(0)
                    
                    SelectServiceStep(viewModel: viewModel)
                        .tag(1)
                    
                    SelectDateTimeStep(viewModel: viewModel)
                        .tag(2)
                    
                    SelectInstructorStep(viewModel: viewModel)
                        .tag(3)
                    
                    SelectDiveSiteStep(viewModel: viewModel)
                        .tag(4)
                    
                    SelectGearStep(viewModel: viewModel)
                        .tag(5)
                    
                    AddParticipantsStep(viewModel: viewModel)
                        .tag(6)
                    
                    PaymentStep(viewModel: viewModel)
                        .tag(7)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                
                // Navigation Buttons
                HStack {
                    if currentStep > 0 {
                        Button(LocalizationService.shared.localizedString("back", table: "booking")) {
                            withAnimation {
                                currentStep -= 1
                            }
                        }
                    }
                    
                    Spacer()
                    
                    if currentStep < viewModel.totalSteps - 1 {
                        Button(LocalizationService.shared.localizedString("next", table: "booking")) {
                            withAnimation {
                                currentStep += 1
                            }
                        }
                        .buttonStyle(.borderedProminent)
                    } else {
                        Button(LocalizationService.shared.localizedString("confirmBooking", table: "booking")) {
                            Task {
                                if let booking = await viewModel.confirmBooking() {
                                    completedBooking = booking
                                }
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.isLoading)
                    }
                }
                .padding()
            }
            .navigationTitle(LocalizationService.shared.localizedString("bookDive", table: "booking"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(LocalizationService.shared.localizedString("cancel", table: "booking")) {
                        dismiss()
                    }
                }
            }
            .fullScreenCover(item: $completedBooking) { booking in
                NavigationView {
                    BookingConfirmationView(booking: booking)
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
        }
    }
}

// Step views with full functionality
struct SelectCenterStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel
    @StateObject private var exploreViewModel = ExploreViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        List {
            ForEach(exploreViewModel.diveCenters) { center in
                Button(action: {
                    viewModel.selectedCenterId = center.id
                }) {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(center.name)
                                .font(.headline)
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
        .task {
            await exploreViewModel.loadData()
        }
    }
}

struct SelectServiceStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel
    @StateObject private var localizationService = LocalizationService.shared
    @State private var services: [Service] = []
    
    var body: some View {
        List {
            ForEach(services, id: \.id) { service in
                Button(action: {
                    viewModel.selectedServiceId = service.id
                }) {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(service.name)
                                .font(.headline)
                                .foregroundColor(.primary)
                            Text("\(service.price.currency) \(String(format: "%.2f", service.price.amount))")
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
        .task {
            if viewModel.selectedCenterId != nil {
                // Load services for selected center
                // TODO: Implement API call to get services
                services = [
                    Service(
                        id: "1",
                        name: "Fun Dive",
                        description: "Recreational dive for certified divers",
                        type: .funDive,
                        price: Service.Price(amount: 50, currency: "USD"),
                        duration: 120,
                        maxParticipants: 8
                    ),
                    Service(
                        id: "2",
                        name: "Discover Scuba",
                        description: "Introduction to scuba diving",
                        type: .course,
                        price: Service.Price(amount: 100, currency: "USD"),
                        duration: 180,
                        maxParticipants: 4
                    ),
                    Service(
                        id: "3",
                        name: "Open Water Course",
                        description: "PADI Open Water Diver certification course",
                        type: .course,
                        price: Service.Price(amount: 400, currency: "USD"),
                        duration: 1440,
                        maxParticipants: 6
                    )
                ]
            }
        }
    }
}

struct SelectDateTimeStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedDate = Date()
    @State private var selectedTime = "09:00"
    let timeSlots = ["09:00", "11:00", "13:00", "15:00"]
    
    var body: some View {
        Form {
            Section {
                DatePicker("Date", selection: $selectedDate, in: Date()..., displayedComponents: .date)
                    .onChange(of: selectedDate) { oldValue, newDate in
                        viewModel.selectedDate = newDate
                    }
            }
            
            Section("Time") {
                Picker("Time", selection: $selectedTime) {
                    ForEach(timeSlots, id: \.self) { time in
                        Text(time).tag(time)
                    }
                }
                .onChange(of: selectedTime) { oldValue, newTime in
                    viewModel.selectedTime = newTime
                }
            }
        }
        .onAppear {
            if let date = viewModel.selectedDate {
                selectedDate = date
            }
            if let time = viewModel.selectedTime {
                selectedTime = time
            }
        }
    }
}

struct SelectInstructorStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel
    @StateObject private var localizationService = LocalizationService.shared
    @State private var instructors: [User] = []
    
    var body: some View {
        List {
            ForEach(instructors) { instructor in
                Button(action: {
                    viewModel.selectedInstructorId = instructor.id
                }) {
                    HStack {
                        AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: instructor.avatarURL) ?? "")) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Image(systemName: "person.circle.fill")
                        }
                        .frame(width: 50, height: 50)
                        .clipShape(Circle())
                        
                        VStack(alignment: .leading) {
                            Text(instructor.displayName)
                                .font(.headline)
                                .foregroundColor(.primary)
                            Text("Instructor")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        if viewModel.selectedInstructorId == instructor.id {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.divePrimary)
                        }
                    }
                }
            }
        }
        .task {
            // TODO: Load instructors from API
        }
    }
}

struct SelectDiveSiteStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel
    @StateObject private var exploreViewModel = ExploreViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedCenter: DiveCenter?
    
    var availableSites: [DiveSite] {
        guard let center = selectedCenter, !center.affiliatedSites.isEmpty else {
            // If no center selected or no affiliated sites, show all sites
            return exploreViewModel.diveSites
        }
        // Filter sites to only show those affiliated with the selected center
        return exploreViewModel.diveSites.filter { center.affiliatedSites.contains($0.id) }
    }
    
    var body: some View {
        List {
            if let center = selectedCenter, !center.affiliatedSites.isEmpty {
                Section {
                    Text("Showing dive sites available at \(center.name)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            
            if availableSites.isEmpty {
                Section {
                    Text("No dive sites available")
                        .foregroundColor(.secondary)
                }
            } else {
                ForEach(availableSites) { site in
                    Button(action: {
                        viewModel.selectedDiveSiteId = site.id
                    }) {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(site.displayName)
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                Text(site.location.address ?? "")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            if viewModel.selectedDiveSiteId == site.id {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.divePrimary)
                            }
                        }
                    }
                }
            }
        }
        .task {
            await exploreViewModel.loadData()
            // Load selected center to filter sites
            if let centerId = viewModel.selectedCenterId {
                let centers = try? await NetworkService.shared.getDiveCenters()
                selectedCenter = centers?.first { $0.id == centerId }
            }
        }
    }
}

struct SelectGearStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel
    @StateObject private var localizationService = LocalizationService.shared
    @State private var availableGear: [GearItem] = []
    
    var body: some View {
        List {
            ForEach(availableGear) { item in
                HStack {
                    VStack(alignment: .leading) {
                        Text(String(item.name))
                            .font(.headline)
                        Text(String(item.description))
                            .font(.caption)
                            .foregroundColor(.secondary)
                        if let price = item.rentalPrice {
                            Text("\(price.currency) \(String(format: "%.2f", price.amount)) / \(price.period.rawValue)")
                                .font(.caption)
                                .foregroundColor(.divePrimary)
                        }
                    }
                    Spacer()
                    Toggle("", isOn: Binding(
                        get: { viewModel.selectedGear.contains(where: { $0.gearItemId == item.id }) },
                        set: { isOn in
                            if isOn {
                                if let price = item.rentalPrice {
                                    viewModel.selectedGear.append(Booking.GearRental(
                                        id: UUID().uuidString,
                                        gearItemId: item.id,
                                        gearName: item.name,
                                        size: item.sizes.first ?? "",
                                        quantity: 1,
                                        price: price.amount
                                    ))
                                }
                            } else {
                                viewModel.selectedGear.removeAll(where: { $0.gearItemId == item.id })
                            }
                        }
                    ))
                }
            }
        }
        .task {
            // TODO: Load available gear from API
        }
    }
}

struct AddParticipantsStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel
    @StateObject private var localizationService = LocalizationService.shared
    @State private var participantName = ""
    @State private var participantEmail = ""
    
    var body: some View {
        List {
            Section("Add Participant") {
                TextField("Name", text: $participantName)
                TextField("Email", text: $participantEmail)
                    .keyboardType(.emailAddress)
                
                Button("Add") {
                    let participant = Booking.Participant(
                        id: UUID().uuidString,
                        name: participantName,
                        email: participantEmail,
                        phoneNumber: nil,
                        certificationLevel: nil,
                        isFriend: false,
                        friendUserId: nil
                    )
                    viewModel.participants.append(participant)
                    participantName = ""
                    participantEmail = ""
                }
                .disabled(participantName.isEmpty || participantEmail.isEmpty)
            }
            
            Section("Participants") {
                ForEach(Array(viewModel.participants.enumerated()), id: \.offset) { index, participant in
                    VStack(alignment: .leading) {
                        Text(participant.name)
                            .font(.headline)
                        Text(participant.email ?? "")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .onDelete(perform: { indexSet in
                    viewModel.participants.remove(atOffsets: indexSet)
                })
            }
        }
    }
}

struct PaymentStep: View {
    @ObservedObject var viewModel: BookingWizardViewModel
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        Form {
            Section("Payment Method") {
                Picker("Method", selection: $viewModel.paymentMethod) {
                    Text("Online").tag(Booking.Payment.PaymentMethod.online)
                    Text("On Site").tag(Booking.Payment.PaymentMethod.onSite)
                }
            }
            
            Section("Summary") {
                HStack {
                    Text("Service")
                    Spacer()
                    Text("$0.00")
                        .foregroundColor(.secondary)
                }
                HStack {
                    Text("Gear Rental")
                    Spacer()
                    Text("$0.00")
                        .foregroundColor(.secondary)
                }
                Divider()
                HStack {
                    Text("Total")
                        .font(.headline)
                    Spacer()
                    Text("$0.00")
                        .font(.headline)
                        .foregroundColor(.divePrimary)
                }
            }
        }
    }
}

@MainActor
class BookingWizardViewModel: ObservableObject {
    @Published var selectedCenterId: String?
    @Published var selectedServiceId: String?
    @Published var selectedDate: Date?
    @Published var selectedTime: String?
    @Published var selectedInstructorId: String?
    @Published var selectedDiveSiteId: String?
    @Published var selectedGear: [Booking.GearRental] = []
    @Published var participants: [Booking.Participant] = []
    @Published var paymentMethod: Booking.Payment.PaymentMethod = .online
    @Published var isLoading = false
    
    let totalSteps = 8
    
    func confirmBooking() async -> Booking? {
        guard let userId = AuthenticationService.shared.currentUser?.id,
              let centerId = selectedCenterId,
              let serviceId = selectedServiceId,
              let date = selectedDate else { return nil }
        
        isLoading = true
        
        let booking = Booking(
            id: UUID().uuidString,
            userId: userId,
            diveCenterId: centerId,
            serviceId: serviceId,
            diveSiteId: selectedDiveSiteId,
            instructorId: selectedInstructorId,
            date: date,
            startTime: selectedTime ?? "09:00",
            participants: participants,
            gearRental: selectedGear.isEmpty ? nil : selectedGear,
            payment: Booking.Payment(
                method: paymentMethod,
                amount: 0, // TODO: Calculate from service and gear
                currency: "USD",
                status: .pending,
                transactionId: nil,
                paidAt: nil
            ),
            status: .pending,
            notes: nil,
            createdAt: Date(),
            updatedAt: Date()
        )
        
        do {
            let createdBooking = try await NetworkService.shared.createBooking(booking)
            isLoading = false
            return createdBooking
        } catch {
            isLoading = false
            return nil
        }
    }
}

#Preview {
    BookingWizardView()
}
