import SwiftUI

struct CourseBookingView: View {
    let course: Course
    @ObservedObject var courseViewModel: CourseViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var preferredDate = Date()
    @State private var paymentMethod: Booking.Payment.PaymentMethod = .online
    @State private var notes = ""
    @State private var participants: [Booking.Participant] = []
    @State private var participantName = ""
    @State private var participantEmail = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var completedBooking: Booking?
    
    var body: some View {
        NavigationView {
            Form {
                Section("Course") {
                    Text(course.name)
                        .font(.headline)
                    Text(course.level.displayName)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Section("Preferred Date") {
                    DatePicker("Start date", selection: $preferredDate, in: Date()..., displayedComponents: .date)
                }
                
                Section("Participants") {
                    TextField("Name", text: $participantName)
                    TextField("Email", text: $participantEmail)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                    
                    Button("Add Participant") {
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
                }
                
                Section("Payment") {
                    Picker("Method", selection: $paymentMethod) {
                        Text("Online").tag(Booking.Payment.PaymentMethod.online)
                        Text("On Site").tag(Booking.Payment.PaymentMethod.onSite)
                        Text("Apple Pay").tag(Booking.Payment.PaymentMethod.applePay)
                        Text("Google Pay").tag(Booking.Payment.PaymentMethod.googlePay)
                    }
                }
                
                Section("Notes") {
                    TextEditor(text: $notes)
                        .frame(minHeight: 90)
                }
                
                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundColor(.red)
                    }
                }
            }
            .navigationTitle("Book Course")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Confirm") {
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
        }
    }
    
    private func confirmBooking() async {
        isLoading = true
        errorMessage = nil
        do {
            let booking = try await courseViewModel.bookCourse(
                course,
                preferredDate: preferredDate,
                participants: participants,
                paymentMethod: paymentMethod,
                notes: notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes
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
            photos: [],
            createdAt: Date(),
            updatedAt: Date()
        ),
        courseViewModel: CourseViewModel()
    )
}
