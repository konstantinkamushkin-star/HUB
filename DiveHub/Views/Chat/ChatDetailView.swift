//
//  ChatDetailView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine
import UIKit

struct ChatDetailView: View {
    let conversation: ChatConversation
    @StateObject private var viewModel = ChatDetailViewModel()
    @State private var messageText = ""
    @State private var showImagePicker = false
    @State private var showVoiceRecorder = false
    @FocusState private var isInputFocused: Bool
    
    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 12) {
                        if viewModel.hasMoreMessages {
                            HStack {
                                Spacer()
                                Button("ui_load_older_messages".localized) {
                                    Task {
                                        await viewModel.loadOlderMessages(conversationId: conversation.id)
                                    }
                                }
                                .font(.caption)
                                Spacer()
                            }
                            .padding(.vertical, 4)
                        }
                        if viewModel.isLoadingOlder {
                            ProgressView()
                                .padding(.vertical, 8)
                        }
                        ForEach(viewModel.messages) { message in
                            MessageBubble(
                                message: message,
                                isCurrentUser: isSameChatUser(message.senderId, AuthenticationService.shared.currentUser?.id)
                            )
                                .id(message.id)
                        }
                    }
                    .padding()
                }
                .onChange(of: viewModel.messages.last?.id) { _, newId in
                    if let newId {
                        withAnimation {
                            proxy.scrollTo(newId, anchor: .bottom)
                        }
                    }
                }
            }
            
            HStack(spacing: 12) {
                Button(action: {
                    showImagePicker = true
                }) {
                    Image(systemName: "camera.fill")
                        .foregroundColor(.divePrimary)
                }
                
                TextField("ui_chat_type_a_message".localized, text: $messageText, axis: .vertical)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .focused($isInputFocused)
                    .lineLimit(1...5)
                    .onSubmit {
                        sendMessage()
                    }
                
                if !messageText.isEmpty {
                    Button(action: sendMessage) {
                        Image(systemName: "paperplane.fill")
                            .foregroundColor(.white)
                            .padding(10)
                            .background(Color.divePrimary)
                            .clipShape(Circle())
                    }
                } else {
                    Button(action: {
                        showVoiceRecorder = true
                    }) {
                        Image(systemName: "mic.fill")
                            .foregroundColor(.divePrimary)
                    }
                }
            }
            .padding()
            .background(Color(.systemBackground))
        }
        .navigationTitle(conversation.displayTitle)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showImagePicker) {
            ImagePicker(selectedImage: Binding(
                get: { nil },
                set: { image in
                    if let image {
                        Task {
                            await viewModel.sendImage(image: image, conversationId: conversation.id)
                        }
                    }
                }
            ))
        }
        .alert("ui_chat_voice_messages".localized, isPresented: $showVoiceRecorder) {
            Button("ok".localized, role: .cancel) {}
        } message: {
            Text("ui_chat_voice_message_recording_will_be_available_in_a_future_up".localized)
        }
        .alert("ui_chat_chat".localized, isPresented: Binding(
            get: { viewModel.error != nil },
            set: { if !$0 { viewModel.error = nil } }
        ), actions: {
            Button("ok".localized, role: .cancel) { viewModel.error = nil }
        }, message: {
            Text(viewModel.error?.localizedDescription ?? "")
        })
        .task(id: conversation.id) {
            await viewModel.loadMessages(conversationId: conversation.id)
            if Task.isCancelled { return }
            viewModel.startRealtime(conversationId: conversation.id)
        }
        .onDisappear {
            viewModel.stopRealtime()
        }
    }
    
    private func sendMessage() {
        guard !messageText.isEmpty else { return }
        Task {
            await viewModel.sendMessage(text: messageText, conversationId: conversation.id)
            messageText = ""
        }
    }
}

private func isSameChatUser(_ a: String?, _ b: String?) -> Bool {
    guard let a, let b, !a.isEmpty, !b.isEmpty else { return false }
    return a.caseInsensitiveCompare(b) == .orderedSame
}

struct MessageBubble: View {
    let message: ChatMessage
    let isCurrentUser: Bool
    
    var body: some View {
        HStack {
            if isCurrentUser {
                Spacer()
            }
            
            VStack(alignment: isCurrentUser ? .trailing : .leading, spacing: 4) {
                if !isCurrentUser {
                    Text(message.senderName)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                if message.messageType == .photo,
                   let attachments = message.attachments {
                    ForEach(Array(attachments.enumerated()), id: \.offset) { _, att in
                        let urlStr = NetworkService.shared.fullImageURL(from: att.url) ?? att.url
                        if let u = URL(string: urlStr) {
                            AsyncImage(url: u) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .scaledToFit()
                                        .frame(maxWidth: 220, maxHeight: 220)
                                        .cornerRadius(12)
                                case .failure:
                                    Image(systemName: "photo")
                                        .foregroundColor(.secondary)
                                default:
                                    ProgressView()
                                }
                            }
                        }
                    }
                }
                
                if message.messageType == .text {
                    let trimmed = message.content?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                    let display = trimmed.isEmpty ? " " : trimmed
                    Text(display)
                        .font(.body)
                        .foregroundColor(isCurrentUser ? .white : .primary)
                        .padding(12)
                        .background(isCurrentUser ? Color.divePrimary : Color(.systemGray5))
                        .cornerRadius(16)
                }
                
                Text(message.createdAt.formatted(date: .none, time: .short))
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: 300, alignment: isCurrentUser ? .trailing : .leading)
            
            if !isCurrentUser {
                Spacer()
            }
        }
    }
}

@MainActor
class ChatDetailViewModel: ObservableObject {
    @Published var messages: [ChatMessage] = []
    @Published var isLoading = false
    @Published var isLoadingOlder = false
    @Published var hasMoreMessages = false
    @Published var error: Error?
    
    private var nextBefore: String?
    private var socketListenTask: Task<Void, Never>?
    
    func loadMessages(conversationId: String) async {
        isLoading = true
        error = nil
        
        do {
            let page = try await NetworkService.shared.getChatMessages(conversationId: conversationId)
            try Task.checkCancellation()
            messages = page.messages
            hasMoreMessages = page.hasMore
            nextBefore = page.nextBefore
        } catch is CancellationError {
            // Avoid wiping the list if a newer .task cancelled this load (e.g. during send).
        } catch {
            self.error = error
        }
        
        isLoading = false
    }
    
    func loadOlderMessages(conversationId: String) async {
        guard hasMoreMessages, let before = nextBefore, !isLoadingOlder else { return }
        isLoadingOlder = true
        defer { isLoadingOlder = false }
        do {
            let page = try await NetworkService.shared.getChatMessages(
                conversationId: conversationId,
                before: before
            )
            try Task.checkCancellation()
            messages.insert(contentsOf: page.messages, at: 0)
            hasMoreMessages = page.hasMore
            nextBefore = page.nextBefore
        } catch is CancellationError {
        } catch {
            self.error = error
        }
    }
    
    func startRealtime(conversationId: String) {
        stopRealtime()
        guard let token = KeychainService.shared.getAccessToken(),
              let url = NetworkService.shared.chatWebSocketURL(accessToken: token) else {
            return
        }
        let cid = conversationId
        socketListenTask = Task {
            let session = URLSession(configuration: .default)
            let ws = session.webSocketTask(with: url)
            ws.resume()
            while !Task.isCancelled {
                do {
                    let msg = try await ws.receive()
                    guard case .string(let text) = msg,
                          let data = text.data(using: .utf8),
                          let evt = try? NetworkService.apiJSONDecoder().decode(ChatRealtimeEvent.self, from: data),
                          evt.type == "chat.message" else {
                        continue
                    }
                    await MainActor.run {
                        handleRealtime(evt.message, conversationId: cid)
                    }
                } catch {
                    break
                }
            }
        }
    }
    
    func stopRealtime() {
        socketListenTask?.cancel()
        socketListenTask = nil
    }
    
    private func handleRealtime(_ m: ChatMessage, conversationId: String) {
        guard m.conversationId == conversationId else { return }
        if messages.contains(where: { $0.id == m.id }) { return }
        messages.append(m)
    }
    
    func sendMessage(text: String, conversationId: String) async {
        guard let userId = AuthenticationService.shared.currentUser?.id,
              let userName = AuthenticationService.shared.currentUser?.displayName else { return }
        
        let newMessage = ChatMessage(
            id: UUID().uuidString,
            conversationId: conversationId,
            senderId: userId,
            senderName: userName,
            content: text,
            messageType: .text,
            attachments: nil,
            location: nil,
            isRead: false,
            createdAt: Date()
        )
        
        messages.append(newMessage)
        
        do {
            let sentMessage = try await NetworkService.shared.sendChatMessage(
                conversationId: conversationId,
                content: text,
                messageType: "text"
            )
            var merged = sentMessage
            if merged.messageType == .text,
               (merged.content?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true) {
                merged.content = text
            }
            messages.removeAll { $0.id == newMessage.id }
            if let idx = messages.firstIndex(where: { $0.id == merged.id }) {
                messages[idx] = merged
            } else {
                messages.append(merged)
            }
            messages.sort { $0.createdAt < $1.createdAt }
        } catch {
            self.error = error
            await loadMessages(conversationId: conversationId)
        }
    }
    
    func sendImage(image: UIImage, conversationId: String) async {
        guard let userId = AuthenticationService.shared.currentUser?.id,
              let userName = AuthenticationService.shared.currentUser?.displayName,
              let data = image.jpegData(compressionQuality: 0.85) else { return }
        
        let tempId = UUID().uuidString
        do {
            let url = try await NetworkService.shared.uploadMediaImage(data)
            let att = ChatMessage.Attachment(type: .photo, url: url, thumbnailURL: nil, duration: nil)
            let optimistic = ChatMessage(
                id: tempId,
                conversationId: conversationId,
                senderId: userId,
                senderName: userName,
                content: " ",
                messageType: .photo,
                attachments: [att],
                location: nil,
                isRead: false,
                createdAt: Date()
            )
            messages.append(optimistic)
            let sent = try await NetworkService.shared.sendChatMessage(
                conversationId: conversationId,
                content: " ",
                messageType: "photo",
                attachments: [att]
            )
            messages.removeAll { $0.id == tempId }
            if let idx = messages.firstIndex(where: { $0.id == sent.id }) {
                messages[idx] = sent
            } else {
                messages.append(sent)
            }
            messages.sort { $0.createdAt < $1.createdAt }
        } catch {
            self.error = error
            await loadMessages(conversationId: conversationId)
        }
    }
}

#Preview {
    NavigationView {
        ChatDetailView(conversation: ChatConversation(
            id: "1",
            participants: [],
            diveCenterId: nil,
            shopId: nil,
            bookingId: nil,
            lastMessage: nil,
            unreadCount: 0,
            createdAt: Date(),
            updatedAt: Date(),
            peerDisplayName: "Preview"
        ))
    }
}
