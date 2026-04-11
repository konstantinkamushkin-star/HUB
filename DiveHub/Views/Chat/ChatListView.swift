//
//  ChatListView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct ChatListView: View {
    @StateObject private var viewModel = ChatListViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showNewChat = false
    
    var body: some View {
        List {
            ForEach(viewModel.conversations) { conversation in
                NavigationLink(destination: ChatDetailView(conversation: conversation)) {
                    ChatRow(conversation: conversation)
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

@MainActor
class ChatListViewModel: ObservableObject {
    @Published var conversations: [ChatConversation] = []
    @Published var isLoading = false
    @Published var error: Error?
    
    func loadConversations() async {
        isLoading = true
        error = nil
        
        do {
            conversations = try await NetworkService.shared.getChatConversations()
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
