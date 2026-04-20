//
//  ChatListView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

/// Messages + in-app / push notification inbox (same tab).
struct ChatHubView: View {
    private enum Segment: Int, CaseIterable {
        case messages = 0
        case notifications = 1
    }

    @StateObject private var localizationService = LocalizationService.shared
    @State private var segment: Segment = .messages

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $segment) {
                Text(localizationService.localizedString("messages"))
                    .tag(Segment.messages)
                Text("ui_notifications_notifications".localized)
                    .tag(Segment.notifications)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.vertical, 8)

            Group {
                switch segment {
                case .messages:
                    ChatListView()
                case .notifications:
                    NotificationsView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

struct ChatListView: View {
    @StateObject private var viewModel = ChatListViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showNewChat = false
    
    var body: some View {
        List {
            ForEach(viewModel.rows) { row in
                switch row {
                case .supportHub(let threads):
                    NavigationLink(destination: SupportThreadsListView(threads: threads)) {
                        SupportHubRow(threads: threads)
                    }
                case .direct(let conversation):
                    NavigationLink(destination: ChatDetailView(conversation: conversation)) {
                        ChatRow(conversation: conversation)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(localizationService.localizedString("messages"))
        .diveHubNavigationChrome()
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showNewChat = true }) {
                    Image(systemName: "square.and.pencil")
                        .font(.title3)
                }
            }
        }
        .sheet(isPresented: $showNewChat, onDismiss: {
            Task { await viewModel.loadConversations() }
        }) {
            NewChatWithFriendView()
        }
        .task {
            await viewModel.loadConversations()
        }
    }
}

/// Один пункт в ленте для всех обращений в поддержку (`APP_SUPPORT_TOPIC`).
struct SupportHubRow: View {
    let threads: [ChatConversation]
    @StateObject private var localizationService = LocalizationService.shared

    private var latest: ChatConversation? {
        threads.max(by: { $0.updatedAt < $1.updatedAt })
    }

    private var totalUnread: Int {
        threads.reduce(0) { $0 + $1.unreadCount }
    }

    var body: some View {
        HStack {
            Circle()
                .fill(Color.divePrimary.opacity(0.3))
                .frame(width: 50, height: 50)
                .overlay(
                    Image(systemName: "headphones")
                        .font(.title3)
                        .foregroundColor(.divePrimary)
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(localizationService.localizedString("supportInboxHubTitle", table: "help"))
                    .font(.headline)
                if let lastMessage = latest?.lastMessage {
                    Text(lastMessage.content ?? "")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                if let t = latest, let lastMessage = t.lastMessage {
                    Text(lastMessage.createdAt.formatted(date: .none, time: .short))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                if totalUnread > 0 {
                    Text(totalUnread > 99 ? "99+" : "\(totalUnread)")
                        .font(.caption)
                        .foregroundColor(.white)
                        .padding(6)
                        .background(Color.divePrimary)
                        .clipShape(Circle())
                }
            }
        }
        .padding(.vertical, 4)
    }
}

struct ChatRow: View {
    let conversation: ChatConversation
    
    var body: some View {
        HStack {
            // Avatar
            Circle()
                .fill(Color.divePrimary.opacity(0.3))
                .frame(width: 50, height: 50)
                .overlay(
                    Text(conversation.displayTitle.prefix(1).uppercased())
                        .font(.headline)
                        .foregroundColor(.divePrimary)
                )
            
            VStack(alignment: .leading, spacing: 4) {
                Text(conversation.displayTitle)
                    .font(.headline)
                
                if let lastMessage = conversation.lastMessage {
                    Text(lastMessage.content ?? "")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 4) {
                if let lastMessage = conversation.lastMessage {
                    Text(lastMessage.createdAt.formatted(date: .none, time: .short))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                if conversation.unreadCount > 0 {
                    Text("\(conversation.unreadCount)")
                        .font(.caption)
                        .foregroundColor(.white)
                        .padding(6)
                        .background(Color.divePrimary)
                        .clipShape(Circle())
                }
            }
        }
        .padding(.vertical, 4)
    }
}

enum ChatListRow: Identifiable {
    /// Все треды `APP_SUPPORT_TOPIC` — один пункт, детали в `SupportThreadsListView`.
    case supportHub(threads: [ChatConversation])
    case direct(ChatConversation)

    var id: String {
        switch self {
        case .supportHub:
            return "__divehub_support_hub__"
        case .direct(let c):
            return c.id
        }
    }
}

@MainActor
class ChatListViewModel: ObservableObject {
    @Published var rows: [ChatListRow] = []
    @Published var isLoading = false
    @Published var error: Error?

    private func isSupportThread(_ conversation: ChatConversation) -> Bool {
        if conversation.kind == "APP_SUPPORT_TOPIC" { return true }
        if conversation.topicId != nil { return true }

        // Fallback for older backend payloads where `kind` was not returned yet.
        let normalizedTitle = conversation.peerDisplayName?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        return normalizedTitle == "divehub support"
    }

    func loadConversations() async {
        isLoading = true
        error = nil

        do {
            let all = try await NetworkService.shared.getChatConversations()
            let supportThreads = all
                .filter { isSupportThread($0) }
                .sorted { $0.updatedAt > $1.updatedAt }
            let rest = all.filter { !isSupportThread($0) }

            var next: [ChatListRow] = []
            if !supportThreads.isEmpty {
                next.append(.supportHub(threads: supportThreads))
            }
            next.append(contentsOf: rest.map { ChatListRow.direct($0) })
            rows = next
        } catch {
            self.error = error
        }

        isLoading = false
    }
}

#Preview {
    NavigationStack {
        ChatListView()
    }
}
