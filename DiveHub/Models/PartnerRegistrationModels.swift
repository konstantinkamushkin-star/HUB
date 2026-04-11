//
//  PartnerRegistrationModels.swift
//  DiveHub
//

import Foundation

/// Тело `POST /api/v1/partner-registrations` для дайв-центра (`kind: dive_center`).
struct PartnerRegistrationRequestBody: Encodable {
    let kind: String
    let name: String
    let description: String?
    let contactEmail: String
    let contactPhone: String
    let country: String
    let city: String
    let address: String?
    let website: String?
    let latitude: Double
    let longitude: Double
    let personalDataConsent: Bool
    let personalDataConsentText: String
}

struct PartnerRegistrationAPIResponse: Decodable {
    let message: String?
    let diveCenterId: String?
    let shopId: String?
    let verificationRequestId: String?
}
