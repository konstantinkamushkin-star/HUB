//
//  BuddySearch.swift
//  DiveHub
//
//  Find buddy — place + time intent (not tied to commercial trips).
//

import Foundation

struct BuddySearchIntent: Identifiable, Codable, Hashable {
    let id: String
    let userId: String
    var place: String
    var dateFrom: String
    var dateTo: String
    var certificationLevel: String?
    var diveCount: Int?
    var languages: [String]
    var interests: [String]
    var status: String?
    var createdAt: Date?
    var updatedAt: Date?

    var dateRangeLabel: String {
        "\(dateFrom) → \(dateTo)"
    }
}

struct BuddySearchMatch: Identifiable, Codable, Hashable {
    var id: String { search.id }
    var score: Int
    var search: BuddySearchIntent
    var user: User?
}

struct BuddySearchUpsertBody: Codable {
    let place: String
    let dateFrom: String
    let dateTo: String
    let certificationLevel: String?
    let diveCount: Int?
    let languages: [String]
    let interests: [String]
}

struct BuddySearchUpsertResponse: Codable {
    let search: BuddySearchIntent
    let matchCount: Int
    let matches: [BuddySearchMatch]
}

struct BuddySearchMatchesResponse: Codable {
    let matchCount: Int
    let matches: [BuddySearchMatch]
}
