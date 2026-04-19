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
    @StateObject private var localizationService = LocalizationService.shared
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
                
                Text(booking.status == .pending ? "ui_booking_sent_title".localized : "ui_booking_confirmed_title".localized)
                    .font(.title)
                    .fontWeight(.bold)
                
                Text(
                    booking.status == .pending
                    ? "ui_booking_sent_message".localized
                    : "ui_booking_confirmed_message".localized
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
                    
                    DetailRow(label: "ui_booking_detail_id".localized, value: booking.id.prefix(8).uppercased())
                    DetailRow(label: "ui_booking_detail_date".localized, value: booking.date.formatted(date: .long, time: .none))
                    DetailRow(label: "ui_booking_detail_time".localized, value: booking.startTime)
                    DetailRow(label: "ui_booking_detail_participants".localized, value: "\(booking.participants.count)")
                    DetailRow(label: "ui_booking_detail_status".localized, value: localizedBookingStatus(booking.status))
                    
                    Divider()
                    if let verifiedPrice = booking.manualVerifiedPriceText {
                        DetailRow(label: "ui_booking_detail_final_verified_price".localized, value: verifiedPrice)
                    }
                    DetailRow(label: "ui_booking_detail_amount".localized, value: String(format: "%.2f %@", booking.payment.amount, booking.payment.currency))
                    DetailRow(label: "ui_booking_detail_payment_method".localized, value: localizedPaymentMethod(booking.payment.method))
                    if let verificationNote = booking.manualVerificationNote {
                        DetailRow(label: "ui_booking_detail_center_note".localized, value: verificationNote)
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
                            event.title = "ui_booking_calendar_event_title".localized
                            event.startDate = booking.date
                            event.endDate = Calendar.current.date(byAdding: .hour, value: 3, to: booking.date) ?? booking.date
                            event.notes = "\("ui_booking_detail_id".localized): \(booking.id)"
                            event.calendar = eventStore.defaultCalendarForNewEvents
                            
                            do {
                                try eventStore.save(event, span: .thisEvent)
                                calendarAlertMessage = "ui_booking_calendar_added_success".localized
                            } catch {
                                calendarAlertMessage = "\("ui_booking_calendar_add_failed".localized): \(error.localizedDescription)"
                            }
                        } else {
                            calendarAlertMessage = "ui_booking_calendar_access_denied".localized
                        }
                        showCalendarAlert = true
                    }
                } catch {
                    await MainActor.run {
                        calendarAlertMessage = "\("ui_booking_calendar_access_request_failed".localized): \(error.localizedDescription)"
                        showCalendarAlert = true
                    }
                }
            }
        } else {
            eventStore.requestAccess(to: .event) { granted, error in
                DispatchQueue.main.async {
                    if granted {
                        let event = EKEvent(eventStore: eventStore)
                        event.title = "ui_booking_calendar_event_title".localized
                        event.startDate = self.booking.date
                        event.endDate = Calendar.current.date(byAdding: .hour, value: 3, to: self.booking.date) ?? self.booking.date
                        event.notes = "\("ui_booking_detail_id".localized): \(self.booking.id)"
                        event.calendar = eventStore.defaultCalendarForNewEvents
                        
                        do {
                            try eventStore.save(event, span: .thisEvent)
                            self.calendarAlertMessage = "ui_booking_calendar_added_success".localized
                        } catch {
                            self.calendarAlertMessage = "\("ui_booking_calendar_add_failed".localized): \(error.localizedDescription)"
                        }
                    } else {
                        self.calendarAlertMessage = "ui_booking_calendar_access_denied".localized
                    }
                    self.showCalendarAlert = true
                }
            }
        }
    }

    private func localizedBookingStatus(_ status: Booking.BookingStatus) -> String {
        switch status {
        case .pending:
            return localizationService.localizedString("bookingStatusPending", table: "admin")
        case .quoted:
            return localizationService.localizedString("bookingStatusQuoted", table: "admin")
        case .confirmed:
            return localizationService.localizedString("bookingStatusConfirmed", table: "admin")
        case .completed:
            return localizationService.localizedString("bookingStatusCompleted", table: "admin")
        case .cancelled:
            return localizationService.localizedString("bookingStatusCancelled", table: "admin")
        case .refunded:
            return localizationService.localizedString("bookingStatusRefunded", table: "admin")
        }
    }

    private func localizedPaymentMethod(_ method: Booking.Payment.PaymentMethod) -> String {
        switch method {
        case .online:
            return localizationService.localizedString("ui_booking_value_2", table: "ui")
        case .onSite:
            return localizationService.localizedString("ui_booking_on_site", table: "ui")
        case .applePay:
            return localizationService.localizedString("ui_booking_apple_pay", table: "ui")
        case .googlePay:
            return localizationService.localizedString("ui_booking_google_pay", table: "ui")
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
