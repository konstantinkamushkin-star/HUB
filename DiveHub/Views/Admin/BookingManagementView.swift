//
//  BookingManagementView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct BookingManagementView: View {
    @StateObject private var viewModel = AdminViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedStatus: String = "all"
    @State private var selectedBooking: Booking?
    
    var filteredBookings: [Booking] {
        if selectedStatus == "all" {
            return viewModel.bookings
        }
        if let status = Booking.BookingStatus(rawValue: selectedStatus) {
            return viewModel.bookings.filter { $0.status == status }
        }
        return viewModel.bookings
    }
    
    var body: some View {
        List {
            Section {
                Picker(localizationService.localizedString("filterByStatus", table: "admin"), selection: $selectedStatus) {
                    Text(localizationService.localizedString("all", table: "common")).tag("all")
                    ForEach([Booking.BookingStatus.pending, .confirmed, .completed, .cancelled], id: \.self) { status in
                        Text(status.rawValue.capitalized).tag(status.rawValue)
                    }
                }
            }
            
            Section {
                ForEach(filteredBookings) { booking in
                    BookingRowView(booking: booking, onTap: {
                        selectedBooking = booking
                    })
                }
            }
        }
        .navigationTitle(localizationService.localizedString("bookings", table: "admin"))
        .sheet(item: $selectedBooking) { booking in
            BookingDetailView(booking: booking, viewModel: viewModel)
        }
        .task {
            await viewModel.loadBookings()
        }
    }
}

struct BookingRowView: View {
    let booking: Booking
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(formatBookingDate(booking.date))
                        .font(.headline)
                        .foregroundColor(.primary)
                    Text(formatParticipantsCount(booking.participants.count))
                        .font(.caption)
                        .foregroundColor(.secondary)
                    if !booking.serviceId.isEmpty {
                        Text("Service: \(booking.serviceId)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text(formatPrice(booking.payment.amount))
                        .font(.headline)
                        .foregroundColor(.primary)
                    Text(booking.status.rawValue.capitalized)
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(getColorForStatus(booking.status))
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
            }
        }
        .buttonStyle(.plain)
    }
    
    private func formatBookingDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
    
    private func formatParticipantsCount(_ count: Int) -> String {
        if count == 1 {
            return "1 participant"
        } else {
            return "\(count) participants"
        }
    }
    
    private func formatPrice(_ amount: Double) -> String {
        return String(format: "$%.2f", amount)
    }
    
    private func getColorForStatus(_ status: Booking.BookingStatus) -> Color {
        switch status {
        case .pending:
            return Color.orange
        case .confirmed:
            return Color.blue
        case .completed:
            return Color.green
        case .cancelled:
            return Color.red
        case .refunded:
            return Color.gray
        }
    }
}

struct BookingDetailView: View {
    let booking: Booking
    @ObservedObject var viewModel: AdminViewModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text(LocalizationService.shared.localizedString("bookingDetails", table: "admin"))) {
                    AdminDetailRow(
                        label: LocalizationService.shared.localizedString("date", table: "admin"),
                        value: formatDateOnly(booking.date)
                    )
                    AdminDetailRow(
                        label: LocalizationService.shared.localizedString("time", table: "admin"),
                        value: booking.startTime
                    )
                    AdminDetailRow(
                        label: LocalizationService.shared.localizedString("status", table: "admin"),
                        value: booking.status.rawValue.capitalized
                    )
                    AdminDetailRow(
                        label: LocalizationService.shared.localizedString("amount", table: "admin"),
                        value: formatPrice(booking.payment.amount)
                    )
                }
                
                Section(header: Text(LocalizationService.shared.localizedString("participants", table: "admin"))) {
                    ForEach(booking.participants) { participant in
                        ParticipantRowView(participant: participant)
                    }
                }
                
                Section(header: Text(LocalizationService.shared.localizedString("actions", table: "admin"))) {
                    if booking.status == .pending {
                        ActionButton(
                            title: LocalizationService.shared.localizedString("confirmBooking", table: "booking"),
                            action: {
                                handleConfirm()
                            }
                        )
                        ActionButton(
                            title: LocalizationService.shared.localizedString("cancelBooking", table: "admin"),
                            isDestructive: true,
                            action: {
                                handleCancel()
                            }
                        )
                    }
                }
            }
            .navigationTitle(LocalizationService.shared.localizedString("bookingDetails", table: "admin"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(LocalizationService.shared.localizedString("done", table: "common")) {
                        dismiss()
                    }
                }
            }
        }
    }
    
    private func formatDateOnly(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        return formatter.string(from: date)
    }
    
    private func formatPrice(_ amount: Double) -> String {
        return String(format: "$%.2f", amount)
    }
    
    private func handleConfirm() {
        Task {
            try? await viewModel.updateBookingStatus(booking.id, status: .confirmed)
            dismiss()
        }
    }
    
    private func handleCancel() {
        Task {
            try? await viewModel.updateBookingStatus(booking.id, status: .cancelled)
            dismiss()
        }
    }
}

struct AdminDetailRow: View {
    let label: String
    let value: String
    
    var body: some View {
        HStack {
            Text(label)
            Spacer()
            Text(value)
        }
    }
}

struct ParticipantRowView: View {
    let participant: Booking.Participant
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(participant.name)
                .font(.headline)
            if let email = participant.email {
                Text(email)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }
}

struct ActionButton: View {
    let title: String
    var isDestructive: Bool = false
    let action: () -> Void
    
    var body: some View {
        if isDestructive {
            Button(role: .destructive, action: action) {
                Text(title)
            }
        } else {
            Button(action: action) {
                Text(title)
            }
        }
    }
}

#Preview {
    BookingManagementView()
}
