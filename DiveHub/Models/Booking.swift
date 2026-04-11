//
//  Booking.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

struct Booking: Identifiable, Codable {
    let id: String
    var userId: String
    var diveCenterId: String
    var serviceId: String
    var diveSiteId: String?
    var instructorId: String?
    var date: Date
    var startTime: String
    var participants: [Participant]
    var gearRental: [GearRental]?
    var payment: Payment
    var status: BookingStatus
    var notes: String?
    var createdAt: Date
    var updatedAt: Date
    
    enum BookingStatus: String, Codable {
        case pending = "pending"
        case confirmed = "confirmed"
        case completed = "completed"
        case cancelled = "cancelled"
        case refunded = "refunded"
    }
    
    struct Participant: Identifiable, Codable {
        let id: String
        var name: String
        var email: String?
        var phoneNumber: String?
        var certificationLevel: String?
        var isFriend: Bool
        var friendUserId: String?
    }
    
    struct GearRental: Identifiable, Codable {
        let id: String
        var gearItemId: String
        var gearName: String
        var size: String
        var quantity: Int
        var price: Double
    }
    
    struct Payment: Codable {
        var method: PaymentMethod
        var amount: Double
        var currency: String
        var status: PaymentStatus
        var transactionId: String?
        var paidAt: Date?
        
        enum PaymentMethod: String, Codable {
            case online = "online"
            case onSite = "on_site"
            case applePay = "apple_pay"
            case googlePay = "google_pay"
        }
        
        enum PaymentStatus: String, Codable {
            case pending = "pending"
            case paid = "paid"
            case refunded = "refunded"
            case failed = "failed"
        }
    }
}
