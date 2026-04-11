//
//  MockExploreData.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

struct MockExploreData {
    static let diveSites: [DiveSite] = [
        DiveSite(
            id: "1",
            name: "Blue Hole",
            description: "Famous deep dive site with stunning coral formations",
            location: DiveSite.Location(latitude: 25.7617, longitude: -80.1918, address: "Miami, FL"),
            siteType: .reef,
            difficulty: .advanced,
            maxDepth: 40,
            averageDepth: 25,
            visibility: "15-25m",
            waterTemp: 28,
            current: "Moderate",
            marineLife: ["Sharks", "Turtles", "Rays"],
            photos: [],
            videos: [],
            averageRating: 4.8,
            reviewCount: 245
        ),
        DiveSite(
            id: "2",
            name: "Shipwreck Paradise",
            description: "Historic shipwreck from the 1800s",
            location: DiveSite.Location(latitude: 25.7907, longitude: -80.1300, address: "Key Largo, FL"),
            siteType: .wreck,
            difficulty: .intermediate,
            maxDepth: 30,
            averageDepth: 20,
            visibility: "10-20m",
            waterTemp: 27,
            current: "Light",
            marineLife: ["Groupers", "Barracudas"],
            photos: [],
            videos: [],
            averageRating: 4.5,
            reviewCount: 189
        ),
        DiveSite(
            id: "3",
            name: "Coral Garden",
            description: "Beautiful shallow reef perfect for beginners",
            location: DiveSite.Location(latitude: 25.7217, longitude: -80.1618, address: "Miami Beach, FL"),
            siteType: .reef,
            difficulty: .beginner,
            maxDepth: 15,
            averageDepth: 10,
            visibility: "20-30m",
            waterTemp: 29,
            current: "None",
            marineLife: ["Tropical Fish", "Corals"],
            photos: [],
            videos: [],
            averageRating: 4.6,
            reviewCount: 312
        ),
        DiveSite(
            id: "4",
            name: "The Abyss",
            description: "Deep cave system for expert divers",
            location: DiveSite.Location(latitude: 25.7517, longitude: -80.2018, address: "Miami, FL"),
            siteType: .cave,
            difficulty: .expert,
            maxDepth: 60,
            averageDepth: 45,
            visibility: "5-10m",
            waterTemp: 26,
            current: "Strong",
            marineLife: ["Cave Fish"],
            photos: [],
            videos: [],
            averageRating: 4.9,
            reviewCount: 78
        )
    ]
    
    static let diveCenters: [DiveCenter] = [
        DiveCenter(
            id: "1",
            name: "Ocean Blue Dive Center",
            description: "Premier dive center with PADI certification",
            location: DiveCenter.Location(
                latitude: 25.7617,
                longitude: -80.1918,
                address: "123 Ocean Drive",
                city: "Miami",
                country: "USA"
            ),
            contactInfo: DiveCenter.ContactInfo(
                phone: "+1-305-555-0123",
                email: "info@oceanblue.com",
                website: "https://oceanblue.com"
            ),
            photos: [],
            videos: [],
            averageRating: 4.7,
            reviewCount: 156,
            aiSummary: nil,
            instructors: [],
            affiliatedSites: ["1", "3"],
            services: [],
            operatingHours: DiveCenter.OperatingHours(),
            certificationAgency: "PADI",
            languages: ["English", "Spanish"],
            nitroxAvailable: true,
            priceFrom: 80
        ),
        DiveCenter(
            id: "2",
            name: "Deep Sea Adventures",
            description: "SSI certified center specializing in technical diving",
            location: DiveCenter.Location(
                latitude: 25.7907,
                longitude: -80.1300,
                address: "456 Reef Road",
                city: "Key Largo",
                country: "USA"
            ),
            contactInfo: DiveCenter.ContactInfo(
                phone: "+1-305-555-0456",
                email: "contact@deepsea.com",
                website: "https://deepsea.com"
            ),
            photos: [],
            videos: [],
            averageRating: 4.9,
            reviewCount: 203,
            aiSummary: nil,
            instructors: [],
            affiliatedSites: ["2", "4"],
            services: [],
            operatingHours: DiveCenter.OperatingHours(),
            certificationAgency: "SSI",
            languages: ["English", "French"],
            nitroxAvailable: true,
            priceFrom: 120
        )
    ]
    
    static let shops: [Shop] = [
        Shop(
            id: "1",
            name: "Dive Gear Pro",
            description: "Complete dive equipment store with all major brands",
            localizedName: nil,
            localizedDescription: nil,
            type: .offline,
            brands: ["Scubapro", "Aqualung", "Mares", "Cressi"],
            serviceAvailable: true,
            averageRating: 4.6,
            reviewCount: 89,
            location: Shop.Location(
                latitude: 25.7617,
                longitude: -80.1918,
                address: "789 Gear Street",
                city: "Miami",
                country: "USA"
            ),
            photos: [],
            contactInfo: Shop.ContactInfo(
                phone: "+1-305-555-0789",
                email: "sales@divegearpro.com",
                website: "https://divegearpro.com"
            ),
            createdAt: Date(),
            updatedAt: Date()
        ),
        Shop(
            id: "2",
            name: "Online Dive Store",
            description: "Worldwide shipping on all dive equipment",
            localizedName: nil,
            localizedDescription: nil,
            type: .online,
            brands: ["Scubapro", "Aqualung", "Atomic", "Suunto"],
            serviceAvailable: true,
            averageRating: 4.8,
            reviewCount: 456,
            location: Shop.Location(
                latitude: 25.7907,
                longitude: -80.1300,
                address: nil,
                city: "Key Largo",
                country: "USA"
            ),
            photos: [],
            contactInfo: Shop.ContactInfo(
                phone: "+1-800-555-0123",
                email: "support@onlinedivestore.com",
                website: "https://onlinedivestore.com"
            ),
            createdAt: Date(),
            updatedAt: Date()
        )
    ]
}
