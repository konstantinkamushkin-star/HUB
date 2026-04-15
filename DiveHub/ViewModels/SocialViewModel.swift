//
//  SocialViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine

struct FriendRequest: Identifiable {
    let id: String
    let user: User
    let createdAt: Date
}

@MainActor
class SocialViewModel: ObservableObject {
    @Published var friends: [User] = []
    @Published var groupTrips: [GroupTrip] = []
    @Published var friendRequests: [User] = []
    @Published var sentFriendRequests: [FriendRequest] = []
    @Published var receivedFriendRequests: [FriendRequest] = []
    @Published var isLoading = false
    @Published var error: Error?
    
    func loadFriends() async {
        // Validate authentication state first
        AuthenticationService.shared.validateAuthentication()
        
        // Check if user is authenticated AND has valid tokens
        let isAuth = AuthenticationService.shared.isAuthenticated
        let hasUser = AuthenticationService.shared.currentUser != nil
        let hasAccessToken = KeychainService.shared.getAccessToken() != nil
        let hasRefreshToken = KeychainService.shared.getRefreshToken() != nil
        let hasValidTokens = hasAccessToken || hasRefreshToken
        guard isAuth, hasUser, hasValidTokens else {
            let msg = LocalizationService.shared.localizedString("pleaseSignIn", table: "errors")
            error = NSError(domain: "AuthError", code: 401, userInfo: [NSLocalizedDescriptionKey: msg])
            isLoading = false
            return
        }
        
        isLoading = true
        error = nil
        
        do {
            friends = try await NetworkService.shared.getFriends()
            isLoading = false
        } catch {
            self.error = error
            isLoading = false
        }
    }
    
    func loadGroupTrips() async {
        isLoading = true
        error = nil
        
        // TODO: Replace with actual API call when backend is ready
        // groupTrips = try await NetworkService.shared.getGroupTrips()
        
        // Mock data for now
        groupTrips = []
        isLoading = false
    }
    
    func sendFriendRequest(userId: String) async throws {
        try await NetworkService.shared.sendFriendRequest(userId: userId)
        // Reload friends to update the list
        await loadFriends()
    }
    
    func acceptFriendRequest(userId: String) async throws {
        try await NetworkService.shared.acceptFriendRequest(userId: userId)
        // Reload friends to update the list
        await loadFriends()
        // Also reload received requests to remove the accepted one
        await loadReceivedFriendRequests()
    }
    
    func createGroupTrip(name: String, description: String, startDate: Date, destination: String) async throws -> GroupTrip {
        // TODO: Implement API call
        // return try await NetworkService.shared.createGroupTrip(...)
        throw NSError(domain: "NotImplemented", code: -1)
    }
    
    func loadSentFriendRequests() async {
        isLoading = true
        error = nil
        
        do {
            let responses = try await NetworkService.shared.getSentFriendRequests()
            sentFriendRequests = responses.map { FriendRequest(id: $0.id, user: $0.user, createdAt: $0.createdAt) }
            isLoading = false
        } catch {
            self.error = error
            isLoading = false
        }
    }
    
    func loadReceivedFriendRequests() async {
        isLoading = true
        error = nil
        
        do {
            let responses = try await NetworkService.shared.getReceivedFriendRequests()
            receivedFriendRequests = responses.map { FriendRequest(id: $0.id, user: $0.user, createdAt: $0.createdAt) }
            isLoading = false
        } catch {
            self.error = error
            isLoading = false
        }
    }
    
    func declineFriendRequest(friendshipId: String) async throws {
        try await NetworkService.shared.declineFriendRequest(friendshipId: friendshipId)
        await loadReceivedFriendRequests()
    }
    
    func shareDiveToFeed(diveLog: DiveLog, message: String) async throws {
        _ = try await NetworkService.shared.createFeedPost(
            type: .dive,
            content: message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : message,
            diveLogId: diveLog.id,
            photos: []
        )
    }
}
