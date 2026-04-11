//
//  FeedPage.swift
//  DiveHub
//

import Foundation

struct FeedPage: Codable {
    let items: [FeedPost]
    let hasMore: Bool
    let nextCursor: String?
}

struct ChatMessagesPage: Codable {
    let messages: [ChatMessage]
    let hasMore: Bool
    let nextBefore: String?
}

struct MediaUploadResponse: Codable {
    let path: String
    let url: String
}

struct ChatRealtimeEvent: Codable {
    let type: String
    let message: ChatMessage
}
