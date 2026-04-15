//
//  NotificationsView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct NotificationsView: View {
    @StateObject private var viewModel = NotificationsViewModel()
    
    var body: some View {
        List {
            ForEach(viewModel.notifications) { notification in
                NotificationRow(notification: notification) { notification in
                    viewModel.handleNotificationTap(notification)
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive, action: {
                        Task {
                            await viewModel.deleteNotification(notification.id)
                        }
                    }) {
                        Label("ui_inventory_delete".localized, systemImage: "trash")
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("ui_notifications_notifications".localized)
        .diveHubNavigationChrome()
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("ui_mark_all_read".localized) {
                    Task {
                        await viewModel.markAllAsRead()
                    }
                }
                .disabled(viewModel.notifications.isEmpty)
            }
        }
        .task {
            await viewModel.loadNotifications()
        }
    }
}

struct NotificationRow: View {
    let notification: AppNotification
    var onTap: (AppNotification) -> Void = { _ in }
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: notification.icon)
                .foregroundColor(notification.isRead ? .secondary : .divePrimary)
                .font(.title3)
                .frame(width: 40)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(notification.title)
                    .font(.headline)
                    .foregroundColor(notification.isRead ? .secondary : .primary)
                
                Text(notification.message)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .lineLimit(2)
                
                Text(notification.createdAt.formatted(date: .medium, time: .short))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            if !notification.isRead {
                Circle()
                    .fill(Color.divePrimary)
                    .frame(width: 8, height: 8)
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture {
            onTap(notification)
        }
    }
}

struct AppNotification: Identifiable {
    let id: String
    let type: NotificationType
    let title: String
    let message: String
    let icon: String
    let isRead: Bool
    let createdAt: Date
    let actionURL: String?
    
    enum NotificationType {
        case booking
        case message
        case review
        case achievement
        case system
    }
}

@MainActor
class NotificationsViewModel: ObservableObject {
    @Published var notifications: [AppNotification] = []
    @Published var isLoading = false
    @Published var error: Error?
    
    func loadNotifications() async {
        isLoading = true
        error = nil
        
        do {
            struct NotificationsResponse: Codable {
                let notifications: [AppNotification]
            }
            
            let response: NotificationsResponse = try await NetworkService.shared.request(
                endpoint: "/api/notifications",
                method: .get
            )
            
            notifications = response.notifications
        } catch {
            self.error = error
        }
        
        isLoading = false
    }
    
    func deleteNotification(_ id: String) async {
        do {
            _ = try await NetworkService.shared.request(
                endpoint: "/api/notifications/\(id)",
                method: .delete,
                body: Optional<String>.none
            ) as EmptyResponse
            
            notifications.removeAll { $0.id == id }
        } catch {
            self.error = error
        }
    }
    
    func markAllAsRead() async {
        do {
            _ = try await NetworkService.shared.request(
                endpoint: "/api/notifications/read-all",
                method: .post,
                body: Optional<String>.none
            ) as EmptyResponse
            
            notifications = notifications.map { notification in
                // Mark as read locally
                return AppNotification(
                    id: notification.id,
                    type: notification.type,
                    title: notification.title,
                    message: notification.message,
                    icon: notification.icon,
                    isRead: true,
                    createdAt: notification.createdAt,
                    actionURL: notification.actionURL
                )
            }
        } catch {
            self.error = error
        }
    }
    
    func handleNotificationTap(_ notification: AppNotification) {
        // Navigate based on notification type
        guard notification.actionURL != nil else { return }
        
        // Parse action URL and navigate accordingly
        // For now, this would be handled by a navigation coordinator
        // Example: booking://123, message://456, etc.
    }
}

extension AppNotification: Codable {
    enum CodingKeys: String, CodingKey {
        case id
        case type
        case title
        case message
        case icon
        case isRead
        case createdAt
        case actionURL
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        let typeString = try container.decode(String.self, forKey: .type)
        type = NotificationType(rawValue: typeString) ?? .system
        title = try container.decode(String.self, forKey: .title)
        message = try container.decode(String.self, forKey: .message)
        icon = try container.decode(String.self, forKey: .icon)
        isRead = try container.decode(Bool.self, forKey: .isRead)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
        actionURL = try container.decodeIfPresent(String.self, forKey: .actionURL)
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(type.rawValue, forKey: .type)
        try container.encode(title, forKey: .title)
        try container.encode(message, forKey: .message)
        try container.encode(icon, forKey: .icon)
        try container.encode(isRead, forKey: .isRead)
        try container.encode(createdAt, forKey: .createdAt)
        try container.encodeIfPresent(actionURL, forKey: .actionURL)
    }
}

extension AppNotification.NotificationType: Codable {
    var rawValue: String {
        switch self {
        case .booking: return "booking"
        case .message: return "message"
        case .review: return "review"
        case .achievement: return "achievement"
        case .system: return "system"
        }
    }
    
    init?(rawValue: String) {
        switch rawValue {
        case "booking": self = .booking
        case "message": self = .message
        case "review": self = .review
        case "achievement": self = .achievement
        case "system": self = .system
        default: return nil
        }
    }
}

#Preview {
    NotificationsView()
}
