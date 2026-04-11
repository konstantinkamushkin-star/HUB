//
//  NewChatWithFriendView.swift
//  DiveHub
//

import SwiftUI

struct NewChatWithFriendView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var friends: [User] = []
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var path = NavigationPath()
    
    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let err = errorMessage {
                    Text(err)
                        .foregroundColor(.red)
                        .padding()
                } else if friends.isEmpty {
                    ContentUnavailableView(
                        "No friends yet",
                        systemImage: "person.2.slash",
                        description: Text("Add friends in the Social tab to start a chat.")
                    )
                } else {
                    List(friends) { friend in
                        Button {
                            Task {
                                await openChat(with: friend)
                            }
                        } label: {
                            HStack {
                                AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: friend.avatarURL) ?? "")) { image in
                                    image.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Image(systemName: "person.circle.fill")
                                        .foregroundColor(.secondary)
                                }
                                .frame(width: 44, height: 44)
                                .clipShape(Circle())
                                VStack(alignment: .leading) {
                                    Text(friend.displayName)
                                        .font(.headline)
                                        .foregroundColor(.primary)
                                    Text(friend.role.displayName)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                            }
                        }
                    }
                }
            }
            .navigationTitle("New message")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .navigationDestination(for: ChatConversation.self) { conversation in
                ChatDetailView(conversation: conversation)
            }
            .task {
                await loadFriends()
            }
        }
    }
    
    private func loadFriends() async {
        isLoading = true
        errorMessage = nil
        do {
            friends = try await NetworkService.shared.getFriends()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func openChat(with friend: User) async {
        do {
            let conversation = try await NetworkService.shared.openChatConversation(
                peerType: "user",
                peerId: friend.id
            )
            path.append(conversation)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

#Preview {
    NewChatWithFriendView()
}
