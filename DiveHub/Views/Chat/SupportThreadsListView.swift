//
//  SupportThreadsListView.swift
//  DiveHub — список тем обращений в поддержку (один общий пункт в ленте чатов).
//

import SwiftUI

struct SupportThreadsListView: View {
    let threads: [ChatConversation]
    @StateObject private var localizationService = LocalizationService.shared

    var body: some View {
        List {
            ForEach(threads) { conversation in
                NavigationLink(destination: ChatDetailView(conversation: conversation)) {
                    SupportTopicRow(conversation: conversation)
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(localizationService.localizedString("supportTopicsNavTitle", table: "help"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct SupportTopicRow: View {
    let conversation: ChatConversation

    var body: some View {
        HStack {
            Circle()
                .fill(Color.divePrimary.opacity(0.3))
                .frame(width: 44, height: 44)
                .overlay(
                    Image(systemName: "bubble.left.and.bubble.right.fill")
                        .font(.body)
                        .foregroundColor(.divePrimary)
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(topicTitle)
                    .font(.headline)
                    .lineLimit(2)
                if let lastMessage = conversation.lastMessage {
                    Text(lastMessage.content ?? "")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
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

    private var topicTitle: String {
        let raw = conversation.lastMessage?.content?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if let first = raw.split(separator: "\n", omittingEmptySubsequences: true).first {
            let s = String(first).trimmingCharacters(in: .whitespacesAndNewlines)
            if !s.isEmpty {
                return String(s.prefix(120))
            }
        }
        return LocalizationService.shared.localizedString("supportTopicEmptyPreview", table: "help")
    }
}
