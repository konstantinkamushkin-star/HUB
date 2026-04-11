//
//  FeedViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine

struct FeedRecommendation: Identifiable {
    let id: String
    let type: RecommendationType
    let title: String
    let description: String
    let action: () -> Void
    
    enum RecommendationType {
        case location
        case friend
        case diveSite
    }
}

@MainActor
class FeedViewModel: ObservableObject {
    @Published var posts: [FeedPost] = []
    @Published var comments: [FeedComment] = []
    @Published var recommendations: [FeedRecommendation] = []
    @Published var isLoading = false
    @Published var isLoadingMore = false
    @Published var feedHasMore = false
    @Published var profileHasMore = false
    @Published var error: Error?
    
    private let authService = AuthenticationService.shared
    private var feedNextCursor: String?
    private var profileNextCursor: String?
    private var profileFeedUserId: String?
    
    func loadFeed() async {
        guard authService.isAuthenticated else { return }
        
        isLoading = true
        error = nil
        profileFeedUserId = nil
        
        do {
            let page = try await NetworkService.shared.getFeedPosts()
            posts = page.items
            feedHasMore = page.hasMore
            feedNextCursor = page.nextCursor
            isLoading = false
        } catch {
            self.error = error
            isLoading = false
        }
    }
    
    func loadMoreFeed() async {
        guard authService.isAuthenticated, feedHasMore, let cursor = feedNextCursor, !isLoadingMore else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        do {
            let page = try await NetworkService.shared.getFeedPosts(cursor: cursor)
            posts.append(contentsOf: page.items)
            feedHasMore = page.hasMore
            feedNextCursor = page.nextCursor
        } catch {
            self.error = error
        }
    }
    
    /// Posts visible on a user's profile (self or accepted friend).
    func loadProfileFeed(userId: String) async {
        guard authService.isAuthenticated else { return }
        isLoading = true
        error = nil
        profileFeedUserId = userId
        do {
            let page = try await NetworkService.shared.getProfileFeedPosts(userId: userId)
            posts = page.items
            profileHasMore = page.hasMore
            profileNextCursor = page.nextCursor
            isLoading = false
        } catch {
            self.error = error
            isLoading = false
        }
    }
    
    func loadMoreProfileFeed() async {
        guard authService.isAuthenticated,
              profileHasMore,
              let uid = profileFeedUserId,
              let cursor = profileNextCursor,
              !isLoadingMore else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        do {
            let page = try await NetworkService.shared.getProfileFeedPosts(userId: uid, cursor: cursor)
            posts.append(contentsOf: page.items)
            profileHasMore = page.hasMore
            profileNextCursor = page.nextCursor
        } catch {
            self.error = error
        }
    }
    
    func toggleLike(postId: String) async {
        do {
            let updatedPost = try await NetworkService.shared.togglePostLike(postId: postId)
            if let index = posts.firstIndex(where: { $0.id == postId }) {
                posts[index] = updatedPost
            }
        } catch {
            self.error = error
        }
    }
    
    func loadComments(postId: String) async {
        do {
            comments = try await NetworkService.shared.getPostComments(postId: postId)
        } catch {
            self.error = error
        }
    }
    
    func addComment(postId: String, content: String) async {
        do {
            let comment = try await NetworkService.shared.addPostComment(postId: postId, content: content)
            comments.append(comment)
        } catch {
            self.error = error
        }
    }
    
    func loadRecommendations() async {
        // Load location-based recommendations (nearby dive sites, friends in same location)
        // Load friend recommendations (mutual friends, similar interests)
        // This is a simplified version - in production, this would call a backend endpoint
        
        var recs: [FeedRecommendation] = []
        
        // Example: Location-based recommendations
        // In production, this would be fetched from backend based on user's location
        let loc = LocalizationService.shared
        recs.append(FeedRecommendation(
            id: UUID().uuidString,
            type: .location,
            title: loc.localizedString("recNearbyTitle", table: "feed"),
            description: loc.localizedString("recNearbySubtitle", table: "feed"),
            action: {}
        ))
        
        // Example: Friend recommendations
        // In production, this would be fetched from backend based on mutual friends
        recs.append(FeedRecommendation(
            id: UUID().uuidString,
            type: .friend,
            title: loc.localizedString("recDiversTitle", table: "feed"),
            description: loc.localizedString("recDiversSubtitle", table: "feed"),
            action: {}
        ))
        
        recommendations = recs
    }
}
