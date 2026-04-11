//
//  Chat.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

struct ChatConversation: Identifiable, Codable, Hashable {
    let id: String
    var participants: [String] // Peer user / center / shop IDs (other side)
    var diveCenterId: String?
    var shopId: String?
    var bookingId: String?
    var lastMessage: ChatMessage?
    var unreadCount: Int
    var createdAt: Date
    var updatedAt: Date
    /// Server-provided title for list and navigation (friends use display name).
    var peerDisplayName: String?
    
    enum CodingKeys: String, CodingKey {
        case id, participants, diveCenterId, shopId, bookingId, lastMessage, unreadCount, createdAt, updatedAt, peerDisplayName
    }
    
    static func == (lhs: ChatConversation, rhs: ChatConversation) -> Bool {
        lhs.id == rhs.id
    }
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
    
    var displayTitle: String {
        peerDisplayName ?? participants.first ?? "Chat"
    }
}

struct ChatMessage: Identifiable, Codable {
    let id: String
    var conversationId: String
    var senderId: String
    var senderName: String
    var content: String?
    var messageType: MessageType
    var attachments: [Attachment]?
    var location: LocationAttachment?
    var isRead: Bool
    var createdAt: Date
    
    enum CodingKeys: String, CodingKey {
        case id, conversationId, senderId, senderName, content, messageType, attachments, location, isRead, createdAt
    }
    
    enum MessageType: String, Codable {
        case text = "text"
        case photo = "photo"
        case voice = "voice"
        case location = "location"
        case system = "system"
    }
    
    struct Attachment: Codable {
        var type: AttachmentType
        var url: String
        var thumbnailURL: String?
        var duration: Int? // For voice messages, in seconds
        
        enum AttachmentType: String, Codable {
            case photo = "photo"
            case video = "video"
            case voice = "voice"
        }
    }
    
    struct LocationAttachment: Codable {
        var latitude: Double
        var longitude: Double
        var name: String?
    }
    
    init(
        id: String,
        conversationId: String,
        senderId: String,
        senderName: String,
        content: String?,
        messageType: MessageType,
        attachments: [Attachment]?,
        location: LocationAttachment?,
        isRead: Bool,
        createdAt: Date
    ) {
        self.id = id
        self.conversationId = conversationId
        self.senderId = senderId
        self.senderName = senderName
        self.content = content
        self.messageType = messageType
        self.attachments = attachments
        self.location = location
        self.isRead = isRead
        self.createdAt = createdAt
    }
    
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        conversationId = try c.decode(String.self, forKey: .conversationId)
        senderId = try c.decode(String.self, forKey: .senderId)
        senderName = try c.decodeIfPresent(String.self, forKey: .senderName) ?? ""
        content = try c.decodeIfPresent(String.self, forKey: .content)
        let typeRaw = try c.decodeIfPresent(String.self, forKey: .messageType) ?? "text"
        messageType = MessageType(rawValue: typeRaw.lowercased()) ?? .text
        attachments = try c.decodeIfPresent([Attachment].self, forKey: .attachments)
        location = try c.decodeIfPresent(LocationAttachment.self, forKey: .location)
        isRead = try c.decodeIfPresent(Bool.self, forKey: .isRead) ?? false
        createdAt = try c.decode(Date.self, forKey: .createdAt)
    }
    
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(conversationId, forKey: .conversationId)
        try c.encode(senderId, forKey: .senderId)
        try c.encode(senderName, forKey: .senderName)
        try c.encodeIfPresent(content, forKey: .content)
        try c.encode(messageType.rawValue, forKey: .messageType)
        try c.encodeIfPresent(attachments, forKey: .attachments)
        try c.encodeIfPresent(location, forKey: .location)
        try c.encode(isRead, forKey: .isRead)
        try c.encode(createdAt, forKey: .createdAt)
    }
}

struct GroupTrip: Identifiable, Codable {
    let id: String
    var name: String
    var description: String?
    var organizerId: String
    var participants: [String] // User IDs
    var itinerary: [ItineraryItem]
    var chatId: String
    var startDate: Date
    var endDate: Date
    var destination: String
    var createdAt: Date
    var updatedAt: Date
    
    struct ItineraryItem: Identifiable, Codable {
        let id: String
        var date: Date
        var time: String
        var activity: String
        var diveSiteId: String?
        var diveCenterId: String?
        var bookingId: String?
    }
}

struct FriendTracking: Codable {
    var userId: String
    var isTrackingEnabled: Bool
    var geofence: Geofence?
    var lastLocation: Location?
    var expiresAt: Date?
    
    struct Geofence: Codable {
        var centerLatitude: Double
        var centerLongitude: Double
        var radius: Double // in meters
    }
    
    struct Location: Codable {
        var latitude: Double
        var longitude: Double
        var timestamp: Date
    }
}
