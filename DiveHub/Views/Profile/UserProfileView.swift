//
//  UserProfileView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct UserProfileView: View {
    let userId: String
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var feedViewModel = FeedViewModel()
    @State private var user: User?
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var friendIds: Set<String> = []
    @State private var chatSheetConversation: ChatConversation?
    @State private var openChatAlertMessage: String?
    
    private var canMessageFriend: Bool {
        guard let me = AuthenticationService.shared.currentUser?.id else { return false }
        return me != userId && friendIds.contains(userId)
    }
    
    var body: some View {
        ScrollView {
            if isLoading && user == nil {
                ProgressView()
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            } else if errorMessage != nil, user == nil {
                Text("ui_profile_error_value".localized)
                    .foregroundColor(.red)
                    .padding()
            } else if let user = user {
                VStack(alignment: .leading, spacing: 16) {
                    ProfileHeaderView(user: user)
                    
                    Divider()
                    
                    VStack(alignment: .leading, spacing: 12) {
                        Text(localizationService.localizedString("statistics"))
                            .font(.headline)
                        
                        if user.totalDives != nil {
                            HStack {
                                Text("ui_profile_total_dives".localized)
                                    .foregroundColor(.secondary)
                                Spacer()
                                Text("ui_profile_value_7".localized)
                                    .fontWeight(.semibold)
                            }
                        }
                    }
                    .padding(.horizontal)
                    
                    Divider()
                    
                    Text(localizationService.localizedString("feed", table: "feed"))
                        .font(.headline)
                        .padding(.horizontal)
                    
                    if feedViewModel.isLoading && feedViewModel.posts.isEmpty {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding()
                    } else if feedViewModel.posts.isEmpty {
                        Text(localizationService.localizedString("noPostsYet", table: "feed"))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .padding(.horizontal)
                    } else {
                        LazyVStack(spacing: 12) {
                            ForEach(Array(feedViewModel.posts.enumerated()), id: \.element.id) { index, post in
                                FeedPostRow(post: post, viewModel: feedViewModel)
                                    .onAppear {
                                        if index == feedViewModel.posts.count - 1 {
                                            Task { await feedViewModel.loadMoreProfileFeed() }
                                        }
                                    }
                            }
                            if feedViewModel.isLoadingMore {
                                ProgressView()
                                    .frame(maxWidth: .infinity)
                                    .padding()
                            }
                        }
                        .padding(.horizontal)
                    }
                }
            }
        }
        .navigationTitle(user?.displayName ?? "Profile")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if canMessageFriend {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        Task { await openChatWithUser() }
                    } label: {
                        Image(systemName: "message.fill")
                    }
                }
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
            await loadUser()
            if user != nil {
                await loadFriendIds()
                await feedViewModel.loadProfileFeed(userId: userId)
            }
        }
    }
    
    private func loadUser() async {
        isLoading = true
        errorMessage = nil
        do {
            user = try await NetworkService.shared.getUser(userId: userId)
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func loadFriendIds() async {
        do {
            let friends = try await NetworkService.shared.getFriends()
            friendIds = Set(friends.map(\.id))
        } catch {
            friendIds = []
        }
    }
    
    private func openChatWithUser() async {
        do {
            let conv = try await NetworkService.shared.openChatConversation(
                peerType: "user",
                peerId: userId
            )
            chatSheetConversation = conv
        } catch {
            openChatAlertMessage = error.localizedDescription
        }
    }
}
