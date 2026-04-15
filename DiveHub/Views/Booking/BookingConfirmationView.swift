//
//  BookingConfirmationView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import UIKit
import EventKit

struct BookingConfirmationView: View {
    let booking: Booking
    var onOpenChat: (() -> Void)? = nil
    @Environment(\.dismiss) var dismiss
    @State private var showShareSheet = false
    @State private var showCalendarAlert = false
    @State private var calendarAlertMessage = ""
    
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Success Icon
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 80))
                    .foregroundColor(.green)
                    .padding(.top, 40)
                
                Text(booking.status == .pending ? "Booking Sent!" : "Booking Confirmed!")
                    .font(.title)
                    .fontWeight(.bold)
                
                Text(
                    booking.status == .pending
                    ? "Your request has been sent to the dive center. Continue in chat to confirm details."
                    : "Your dive booking has been confirmed. We'll send you a confirmation email shortly."
                )
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                
                // Booking Details Card
                VStack(alignment: .leading, spacing: 16) {
                    Text("ui_booking_booking_details".localized)
                        .font(.headline)

                    if booking.isPriceVerifiedByDiveCenter {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.seal.fill")
                                .foregroundColor(.green)
                            Text("ui_booking_verified_by_dive_center".localized)
                                .font(.subheadline)
                                .fontWeight(.semibold)
                                .foregroundColor(.green)
                        }
                    }
                    
                    DetailRow(label: "Booking ID", value: booking.id.prefix(8).uppercased())
                    DetailRow(label: "Date", value: booking.date.formatted(date: .long, time: .none))
                    DetailRow(label: "Time", value: booking.startTime)
                    DetailRow(label: "Participants", value: "\(booking.participants.count)")
                    DetailRow(label: "Status", value: booking.status.rawValue.capitalized)
                    
                    Divider()
                    if let verifiedPrice = booking.manualVerifiedPriceText {
                        DetailRow(label: "Final verified price", value: verifiedPrice)
                    }
                    DetailRow(label: "Amount", value: String(format: "%.2f %@", booking.payment.amount, booking.payment.currency))
                    DetailRow(label: "Payment Method", value: booking.payment.method.rawValue.capitalized)
                    if let verificationNote = booking.manualVerificationNote {
                        DetailRow(label: "Center note", value: verificationNote)
                    }
                }
                .padding()
                .cardStyle()
                .padding(.horizontal)
                
                // Action Buttons
                VStack(spacing: 12) {
                    if let onOpenChat {
                        Button(action: onOpenChat) {
                            Label("ui_booking_open_chat".localized, systemImage: "message.fill")
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color(.systemGray6))
                                .foregroundColor(.primary)
                                .cornerRadius(12)
                        }
                    }

                    Button(action: {
                        addToCalendar()
                    }) {
                        Label("ui_booking_add_to_calendar".localized, systemImage: "calendar")
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.divePrimary)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                    }
                    
                    Button(action: {
                        showShareSheet = true
                    }) {
                        Label("ui_booking_share_booking".localized, systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color(.systemGray6))
                            .foregroundColor(.primary)
                            .cornerRadius(12)
                    }
                    
                    Button(action: {
                        dismiss()
                    }) {
                        Text("ui_feed_done".localized)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.divePrimary.opacity(0.1))
                            .foregroundColor(.divePrimary)
                            .cornerRadius(12)
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 40)
            }
        }
        .navigationTitle("ui_booking_confirmation".localized)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showShareSheet) {
            ShareSheet(items: [booking.id])
        }
        .alert("ui_booking_calendar".localized, isPresented: $showCalendarAlert) {
            Button("ok".localized, role: .cancel) {}
        } message: {
            Text(calendarAlertMessage)
        }
    }
    
    private func addToCalendar() {
        let eventStore = EKEventStore()
        
        if #available(iOS 17.0, *) {
            Task {
                do {
                    let granted = try await eventStore.requestFullAccessToEvents()
                    await MainActor.run {
                        if granted {
                            let event = EKEvent(eventStore: eventStore)
                            event.title = "Dive Booking"
                            event.startDate = booking.date
                            event.endDate = Calendar.current.date(byAdding: .hour, value: 3, to: booking.date) ?? booking.date
                            event.notes = "Booking ID: \(booking.id)"
                            event.calendar = eventStore.defaultCalendarForNewEvents
                            
                            do {
                                try eventStore.save(event, span: .thisEvent)
                                calendarAlertMessage = "Booking added to calendar successfully!"
                            } catch {
                                calendarAlertMessage = "Failed to add to calendar: \(error.localizedDescription)"
                            }
                        } else {
                            calendarAlertMessage = "Calendar access denied. Please enable it in Settings."
                        }
                        showCalendarAlert = true
                    }
                } catch {
                    await MainActor.run {
                        calendarAlertMessage = "Failed to request calendar access: \(error.localizedDescription)"
                        showCalendarAlert = true
                    }
                }
            }
        } else {
            eventStore.requestAccess(to: .event) { granted, error in
                DispatchQueue.main.async {
                    if granted {
                        let event = EKEvent(eventStore: eventStore)
                        event.title = "Dive Booking"
                        event.startDate = self.booking.date
                        event.endDate = Calendar.current.date(byAdding: .hour, value: 3, to: self.booking.date) ?? self.booking.date
                        event.notes = "Booking ID: \(self.booking.id)"
                        event.calendar = eventStore.defaultCalendarForNewEvents
                        
                        do {
                            try eventStore.save(event, span: .thisEvent)
                            self.calendarAlertMessage = "Booking added to calendar successfully!"
                        } catch {
                            self.calendarAlertMessage = "Failed to add to calendar: \(error.localizedDescription)"
                        }
                    } else {
                        self.calendarAlertMessage = "Calendar access denied. Please enable it in Settings."
                    }
                    self.showCalendarAlert = true
                }
            }
        }
    }
}

struct DetailRow: View {
    let label: String
    let value: String
    
    var body: some View {
        HStack(alignment: .top) {
            Text(label)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.semibold)
                .multilineTextAlignment(.trailing)
        }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        return controller
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

#Preview {
    NavigationView {
        BookingConfirmationView(booking: Booking(
            id: UUID().uuidString,
            userId: "user1",
            diveCenterId: "center1",
            serviceId: "service1",
            diveSiteId: "site1",
            instructorId: "instructor1",
            date: Date(),
            startTime: "09:00",
            participants: [],
            gearRental: nil,
            payment: Booking.Payment(method: .online, amount: 100, currency: "USD", status: .paid, transactionId: nil, paidAt: Date()),
            status: .confirmed,
            notes: nil,
            createdAt: Date(),
            updatedAt: Date()
        ))
    }
}
