//
//  Review.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

enum ReviewableType: String, Codable, CaseIterable {
    case diveSite = "dive_site"
    case diveCenter = "dive_center"
    case instructor = "instructor"
    case shop = "shop"
}

/// Request body for creating a new review (rating + text).
struct CreateReviewRequest: Codable {
    var reviewableType: ReviewableType
    var reviewableId: String
    var rating: Int
    var text: String
    var language: String?
}

struct Review: Identifiable, Codable {
    let id: String
    var userId: String
    var userName: String
    var userAvatarURL: String?
    var reviewableType: ReviewableType
    var reviewableId: String // ID of the site/center/instructor
    var rating: Int // 1-5
    var text: String
    var categories: [CategoryRating]? // For AI analysis
    var language: String // Original language of the review
    var createdAt: Date
    var updatedAt: Date
    
    struct CategoryRating: Codable {
        var category: String // e.g., "knowledge", "safety", "fun"
        var score: Double // 0.0-5.0
    }
}

struct AISummary: Codable {
    var reviewableType: ReviewableType
    var reviewableId: String
    var summary: String // 2-3 sentence summary
    var sentiment: Sentiment
    var keyPhrases: [String]
    var categoryScores: [String: Double] // e.g., ["safety": 4.8, "knowledge": 4.5]
    var lastUpdated: Date
    
    enum Sentiment: String, Codable {
        case veryPositive = "very_positive"
        case positive = "positive"
        case neutral = "neutral"
        case negative = "negative"
        case veryNegative = "very_negative"
    }
}
