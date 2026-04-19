import SwiftUI

struct MyBookingsView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @State private var bookings: [Booking] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var selectedBooking: Booking?
    @State private var selectedStatusFilter: String = "all"
    @State private var chatSheetConversation: ChatConversation?
    @State private var openChatAlertMessage: String?

    private var filteredBookings: [Booking] {
        if selectedStatusFilter == "all" {
            return bookings
        }
        guard let status = Booking.BookingStatus(rawValue: selectedStatusFilter) else {
            return bookings
        }
        return bookings.filter { $0.status == status }
    }

    var body: some View {
        List {
            Section {
                Picker("ui_profile_status".localized, selection: $selectedStatusFilter) {
                    Text(localizationService.localizedString("all", table: "common")).tag("all")
                    ForEach([
                        Booking.BookingStatus.pending,
                        .quoted,
                        .confirmed,
                        .completed,
                        .cancelled
                    ], id: \.self) { status in
                        Text(status.rawValue.capitalized).tag(status.rawValue)
                    }
                }
            } header: {
                Text(localizationService.localizedString("filterByStatus", table: "admin"))
            }

            if isLoading {
                Section {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                }
            } else if let errorMessage, !errorMessage.isEmpty {
                Section {
                    Text(errorMessage)
                        .foregroundColor(.red)
                }
            } else if filteredBookings.isEmpty {
                Section {
                    Text(bookings.isEmpty ? "No bookings yet" : "No bookings for selected status")
                        .foregroundColor(.secondary)
                }
            } else {
                Section {
                    ForEach(filteredBookings) { booking in
                        BookingListRow(
                            booking: booking,
                            onOpenChat: {
                                Task { await openChatWithDiveCenter(for: booking) }
                            }
                        )
                        .contentShape(Rectangle())
                        .onTapGesture {
                            selectedBooking = booking
                        }
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("bookings", table: "admin"))
        .sheet(item: $selectedBooking) { booking in
            NavigationStack {
                BookingConfirmationView(booking: booking)
            }
        }
        .sheet(item: $chatSheetConversation) { conversation in
            NavigationStack {
                ChatDetailView(conversation: conversation)
            }
        }
        .alert(
            localizationService.localizedString("error", table: "common"),
            isPresented: Binding(
                get: { openChatAlertMessage != nil },
                set: { if !$0 { openChatAlertMessage = nil } }
            ),
            actions: {
                Button("ok".localized, role: .cancel) { openChatAlertMessage = nil }
            },
            message: { Text(openChatAlertMessage ?? "") }
        )
        .task {
            await loadBookings()
        }
        .refreshable {
            await loadBookings()
        }
    }

    private func loadBookings() async {
        isLoading = true
        defer { isLoading = false }
        do {
            bookings = try await NetworkService.shared.getBookings()
                .sorted { $0.createdAt > $1.createdAt }
            errorMessage = nil
        } catch {
            bookings = []
            errorMessage = error.localizedDescription
        }
    }

    private func openChatWithDiveCenter(for booking: Booking) async {
        do {
            let conv = try await NetworkService.shared.openChatConversation(
                peerType: "dive_center",
                peerId: booking.diveCenterId
            )
            chatSheetConversation = conv
        } catch {
            openChatAlertMessage = error.localizedDescription
        }
    }
}

private struct BookingListRow: View {
    let booking: Booking
    let onOpenChat: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(booking.date.formatted(date: .abbreviated, time: .omitted))
                    .font(.headline)
                    .foregroundColor(.primary)
                Spacer()
                Text(booking.status.rawValue.capitalized)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(statusColor.opacity(0.2))
                    .foregroundColor(statusColor)
                    .cornerRadius(8)
            }

            HStack {
                Button(action: onOpenChat) {
                    Label("ui_profile_open_chat".localized, systemImage: "message")
                        .font(.caption)
                }
                .buttonStyle(.bordered)
                .tint(.divePrimary)
                Spacer()
            }

            if booking.isPriceVerifiedByDiveCenter {
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundColor(.green)
                    Text("ui_booking_verified_by_dive_center".localized)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(.green)
                }
            }

            if let verifiedPrice = booking.manualVerifiedPriceText {
                Text("\("ui_booking_detail_final_verified_price".localized): \(verifiedPrice)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(.primary)
            } else {
                Text(String(format: "\("ui_booking_detail_amount".localized): %.2f %@", booking.payment.amount, booking.payment.currency))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private var statusColor: Color {
        switch booking.status {
        case .pending: return .orange
        case .quoted: return .purple
        case .confirmed: return .blue
        case .completed: return .green
        case .cancelled: return .red
        case .refunded: return .gray
        }
    }
}

#Preview {
    NavigationStack {
        MyBookingsView()
    }
}
