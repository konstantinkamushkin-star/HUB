//
//  FeedPost.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

struct FeedPost: Identifiable, Codable {
    let id: String
    var userId: String
    var user: User? // User info for display
    var type: PostType
    var content: String? // Text content for text posts
    var diveLogId: String? // For dive log posts
    var diveLog: DiveLog? // Dive log data for dive posts
    var photos: [String] // Photo URLs
    var likes: Int
    var comments: Int
    var isLiked: Bool? // Whether current user liked this post
    var createdAt: Date
    var updatedAt: Date
    
    enum PostType: String, Codable {
        case dive = "dive"
        case text = "text"
        case photo = "photo"
    }
    
    enum CodingKeys: String, CodingKey {
        case id, userId, user, type, content, diveLogId, diveLog, photos, likes, comments, isLiked, createdAt, updatedAt
    }
}

struct FeedComment: Identifiable, Codable {
    let id: String
    var userId: String
    var user: User?
    var postId: String
    var content: String
    var createdAt: Date
    var updatedAt: Date
}
