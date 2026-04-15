//
//  Booking.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

struct Booking: Identifiable, Codable {
    enum BookingType: String, Codable {
        case openWater = "open_water"
        case pool = "pool"
    }
    
    enum RequestMode: String, Codable {
        case instant = "instant"
        case manualApproval = "manual_approval"
    }
    
    struct InstructorPreferences: Codable {
        var language: String?
        var notes: String?
    }
    
    struct EquipmentRentalRequest: Codable {
        var required: Bool
        var items: [String]?
    }
    
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
    var bookingType: BookingType? = nil
    var requestMode: RequestMode? = nil
    var dateEnd: Date? = nil
    var sessionId: String? = nil
    var participantsCount: Int? = nil
    var instructorPreferences: InstructorPreferences? = nil
    var equipmentRental: EquipmentRentalRequest? = nil
    var createdAt: Date
    var updatedAt: Date

    var manualVerificationNote: String? {
        guard let notes else { return nil }
        let lines = notes.split(whereSeparator: \.isNewline).map(String.init)
        guard let raw = lines.first(where: { $0.hasPrefix("manual_note=") }) else {
            return nil
        }
        let value = String(raw.dropFirst("manual_note=".count)).trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }

    var manualVerifiedPriceText: String? {
        guard let notes else { return nil }
        let lines = notes.split(whereSeparator: \.isNewline).map(String.init)
        guard let raw = lines.first(where: { $0.hasPrefix("manual_verified_price=") }) else {
            return nil
        }
        let value = String(raw.dropFirst("manual_verified_price=".count)).trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }

    var isPriceVerifiedByDiveCenter: Bool {
        manualVerifiedPriceText != nil
    }
    
    enum BookingStatus: String, Codable {
        case pending = "pending"
        case quoted = "quoted"
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
