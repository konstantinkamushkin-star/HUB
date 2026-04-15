import SwiftUI

struct CourseBookingView: View {
    let course: Course
    @ObservedObject var courseViewModel: CourseViewModel
    @Environment(\.dismiss) private var dismiss
    
    @StateObject private var localizationService = LocalizationService.shared
    @State private var preferredDate = Date()
    @State private var paymentMethod: Booking.Payment.PaymentMethod = .online
    @State private var notes = ""
    @State private var participants: [Booking.Participant] = []
    @State private var participantName = ""
    @State private var participantEmail = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var completedBooking: Booking?
    @State private var centerInstructors: [Instructor] = []
    /// Выбранный инструктор (user id), если у курса несколько ведущих.
    @State private var selectedInstructorUserId: String = ""
    
    private var assignedInstructorIds: [String] {
        course.assignedInstructorUserIds
    }
    
    /// (userId, displayName) для выбора; если API не вернул карточки — показываем id.
    private var instructorPickerOptions: [(String, String)] {
        let set = Set(assignedInstructorIds)
        let fromApi = centerInstructors.filter { set.contains($0.id) }.map { ($0.id, $0.name) }
        if !fromApi.isEmpty { return fromApi }
        return assignedInstructorIds.map { ($0, $0) }
    }
    
    var body: some View {
        NavigationView {
            Form {
                Section {
                    Text(course.name)
                        .font(.headline)
                    Text(course.level.displayName)
                        .font(.caption)
                        .foregroundColor(.secondary)
                } header: {
                    Text(localizationService.localizedString("courseDetails", table: "courses"))
                }
                
                if assignedInstructorIds.count > 1 {
                    Section {
                        Picker(localizationService.localizedString("selectInstructorForEnrollment", table: "courses"), selection: $selectedInstructorUserId) {
                            ForEach(instructorPickerOptions, id: \.0) { pair in
                                Text(pair.1).tag(pair.0)
                            }
                        }
                    } footer: {
                        Text(localizationService.localizedString("mustSelectInstructor", table: "courses"))
                            .font(.caption)
                    }
                }
                
                Section {
                    DatePicker(
                        localizationService.localizedString("preferredDate", table: "courses"),
                        selection: $preferredDate,
                        in: Date()...,
                        displayedComponents: .date
                    )
                }
                
                Section {
                    TextField(localizationService.localizedString("name", table: "common"), text: $participantName)
                    TextField(localizationService.localizedString("email", table: "common"), text: $participantEmail)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                    
                    Button(localizationService.localizedString("addParticipant", table: "trips")) {
                        participants.append(
                            Booking.Participant(
                                id: UUID().uuidString,
                                name: participantName.trimmingCharacters(in: .whitespacesAndNewlines),
                                email: participantEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : participantEmail.trimmingCharacters(in: .whitespacesAndNewlines),
                                phoneNumber: nil,
                                certificationLevel: nil,
                                isFriend: false,
                                friendUserId: nil
                            )
                        )
                        participantName = ""
                        participantEmail = ""
                    }
                    .disabled(participantName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    
                    ForEach(Array(participants.enumerated()), id: \.element.id) { _, participant in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(participant.name)
                            if let email = participant.email, !email.isEmpty {
                                Text(email)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .onDelete { indexSet in
                        participants.remove(atOffsets: indexSet)
                    }
                } header: {
                    Text(localizationService.localizedString("participants", table: "trips"))
                }
                
                Section {
                    Picker(localizationService.localizedString("payment", table: "booking"), selection: $paymentMethod) {
                        Text(localizationService.localizedString("payOnline", table: "courses")).tag(Booking.Payment.PaymentMethod.online)
                        Text(localizationService.localizedString("payOnSite", table: "courses")).tag(Booking.Payment.PaymentMethod.onSite)
                        Text(localizationService.localizedString("payApplePay", table: "courses")).tag(Booking.Payment.PaymentMethod.applePay)
                        Text(localizationService.localizedString("payGooglePay", table: "courses")).tag(Booking.Payment.PaymentMethod.googlePay)
                    }
                }
                
                Section {
                    TextEditor(text: $notes)
                        .frame(minHeight: 90)
                } header: {
                    Text(localizationService.localizedString("notes", table: "trips"))
                }
                
                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundColor(.red)
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("bookCourse", table: "courses"))
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel", table: "common")) { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("confirm", table: "trips")) {
                        Task { await confirmBooking() }
                    }
                    .disabled(isLoading || participants.isEmpty)
                }
            }
            .fullScreenCover(item: $completedBooking) { booking in
                NavigationView {
                    BookingConfirmationView(booking: booking)
                }
            }
            .task {
                await loadInstructorsForCourse()
                let ids = assignedInstructorIds
                if ids.count > 1, let first = instructorPickerOptions.first?.0 {
                    selectedInstructorUserId = first
                } else if let only = ids.first {
                    selectedInstructorUserId = only
                }
            }
        }
    }
    
    private func loadInstructorsForCourse() async {
        guard let dc = course.diveCenterId else {
            centerInstructors = []
            return
        }
        do {
            centerInstructors = try await NetworkService.shared.getDiveCenterInstructors(diveCenterId: dc)
        } catch {
            centerInstructors = []
        }
    }
    
    private func confirmBooking() async {
        isLoading = true
        errorMessage = nil
        do {
            let instructorArg: String? = assignedInstructorIds.count > 1 ? selectedInstructorUserId : nil
            let booking = try await courseViewModel.bookCourse(
                course,
                preferredDate: preferredDate,
                participants: participants,
                paymentMethod: paymentMethod,
                notes: notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes,
                instructorUserId: instructorArg
            )
            completedBooking = booking
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

#Preview {
    CourseBookingView(
        course: Course(
            id: "demo-course",
            name: "Open Water Diver",
            level: .basic,
            description: "Learn fundamentals",
            trainingSystems: ["PADI"],
            program: [],
            duration: 4,
            prerequisites: nil,
            diveCenterId: "dc-1",
            instructorId: nil,
            instructorIds: [],
            photos: [],
            createdAt: Date(),
            updatedAt: Date()
        ),
        courseViewModel: CourseViewModel()
    )
}
