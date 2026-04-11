//
//  DiveCenterPublicViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine

@MainActor
class DiveCenterPublicViewModel: ObservableObject {
    @Published var courses: [Course] = []
    @Published var instructors: [Instructor] = []
    @Published var upcomingTrips: [Trip] = []
    @Published var archivedTrips: [Trip] = []
    @Published var isLoading = false
    @Published var error: Error?
    
    private let centerId: String
    
    init(centerId: String) {
        self.centerId = centerId
    }
    
    func loadData() async {
        isLoading = true
        error = nil
        
        do {
            try await withThrowingTaskGroup(of: Void.self) { group in
                group.addTask { try await self.loadCourses() }
                group.addTask { try await self.loadInstructors() }
                group.addTask { try await self.loadTrips() }
                
                try await group.waitForAll()
            }
            
            isLoading = false
        } catch {
            self.error = error
            isLoading = false
            
            #if DEBUG
            // Fallback to test data on error in DEBUG only for явного демо-режима или карточки тестового центра.
            // Не подставляем чужие поездки/курсы для реальных UUID центров.
            if ProcessInfo.processInfo.environment["USE_TEST_DATA"] == "true" ||
                centerId == TestData.testDiveCenterId {
                courses = TestData.testCourses.filter { $0.diveCenterId == centerId }
                // Convert User instructors to Instructor format
                let instructorUsers = TestData.instructors.filter { $0.diveCenterId == centerId }
                instructors = instructorUsers.map { user in
                    Instructor(
                        id: user.id,
                        userId: user.id,
                        name: user.displayName,
                        avatarURL: user.avatarURL,
                        photoURL: user.avatarURL,
                        certifications: [],
                        languages: [],
                        bio: nil,
                        localizedBio: nil,
                        description: nil,
                        localizedDescription: nil,
                        trainingSystems: [],
                        credentials: [],
                        averageRating: 0,
                        reviewCount: 0,
                        aiSummary: nil,
                        schedule: nil,
                        diveCenterId: centerId
                    )
                }
                let now = Date()
                let allTestTrips = TestData.testTrips.filter {
                    $0.organizerId == centerId && $0.organizerType == .diveCenter
                }
                upcomingTrips = allTestTrips.filter { $0.startDate >= now }.sorted { $0.startDate < $1.startDate }
                archivedTrips = allTestTrips.filter { $0.endDate < now }.sorted { $0.startDate > $1.startDate }
            }
            #endif
        }
    }
    
    func loadCourses() async throws {
        
        #if DEBUG
        // Use test data if explicitly requested or if no user is logged in
        if ProcessInfo.processInfo.environment["USE_TEST_DATA"] == "true" || AuthenticationService.shared.currentUser == nil {
            courses = TestData.testCourses.filter { $0.diveCenterId == centerId }
            return
        }
        #endif
        
        let loadedCourses = try await NetworkService.shared.getCourses(diveCenterId: centerId)
        
        
        courses = loadedCourses
    }
    
    func loadInstructors() async throws {
        
        #if DEBUG
        // Use test data if explicitly requested or if no user is logged in
        if ProcessInfo.processInfo.environment["USE_TEST_DATA"] == "true" || AuthenticationService.shared.currentUser == nil {
            let instructorUsers = TestData.instructors.filter { $0.diveCenterId == centerId }
            instructors = instructorUsers.map { user in
                Instructor(
                    id: user.id,
                    userId: user.id,
                    name: user.displayName,
                    avatarURL: user.avatarURL,
                    photoURL: user.avatarURL,
                    certifications: [],
                    languages: [],
                    bio: nil,
                    localizedBio: nil,
                    description: nil,
                    localizedDescription: nil,
                    trainingSystems: [],
                    credentials: [],
                    averageRating: 0,
                    reviewCount: 0,
                    aiSummary: nil,
                    schedule: nil,
                    diveCenterId: centerId
                )
            }
            return
        }
        #endif
        
        let loadedInstructors = try await NetworkService.shared.getDiveCenterInstructors(diveCenterId: centerId)
        
        
        instructors = loadedInstructors
    }
    
    func loadTrips() async throws {
        
        #if DEBUG
        // Use test data if explicitly requested or if no user is logged in
        if ProcessInfo.processInfo.environment["USE_TEST_DATA"] == "true" || AuthenticationService.shared.currentUser == nil {
            let now = Date()
            let allTestTrips = TestData.testTrips.filter { $0.organizerId == centerId && $0.organizerType == .diveCenter }
            upcomingTrips = allTestTrips.filter { $0.startDate >= now }.sorted { $0.startDate < $1.startDate }
            archivedTrips = allTestTrips.filter { $0.endDate < now }.sorted { $0.startDate > $1.startDate }
            return
        }
        #endif
        
        // Load trips for this dive center
        // Filter trips where organizerId == centerId and organizerType == dive_center
        let allTrips = try await NetworkService.shared.getTrips()
        
        
        let now = Date()
        
        // Filter trips: when organizerType is dive_center, organizerId is the user ID, not the dive center ID
        // We need to use groupLeaderDiveCenterId to filter by dive center
        let filteredTrips = allTrips.filter { trip in
            if trip.organizerType == .diveCenter {
                // Use groupLeaderDiveCenterId if available, otherwise fallback to organizerId comparison
                if let groupLeaderDiveCenterId = trip.groupLeaderDiveCenterId {
                    return groupLeaderDiveCenterId == centerId
                } else {
                    // Fallback: try organizerId (might work if organizerId is actually the dive center ID)
                    return trip.organizerId == centerId
                }
            } else {
                return false
            }
        }
        
        
        // Если API не вернул поездок — не показываем демо с другого центра (только USE_TEST_DATA / тестовый ID).
        if filteredTrips.isEmpty {
            #if DEBUG
            if ProcessInfo.processInfo.environment["USE_TEST_DATA"] == "true" ||
                centerId == TestData.testDiveCenterId {
                let allTestTrips = TestData.testTrips.filter {
                    $0.organizerId == centerId && $0.organizerType == .diveCenter
                }
                upcomingTrips = allTestTrips.filter { $0.startDate >= now }.sorted { $0.startDate < $1.startDate }
                archivedTrips = allTestTrips.filter { $0.endDate < now }.sorted { $0.startDate > $1.startDate }
            } else {
                upcomingTrips = []
                archivedTrips = []
            }
            return
            #else
            upcomingTrips = []
            archivedTrips = []
            return
            #endif
        }
        
        // Check for trips that are currently active (started but not ended)
        let _ = filteredTrips.filter { trip in
            trip.startDate < now && trip.endDate >= now
        }
        
        upcomingTrips = filteredTrips.filter { trip in
            trip.startDate >= now
        }.sorted { $0.startDate < $1.startDate }
        
        archivedTrips = filteredTrips.filter { trip in
            trip.endDate < now
        }.sorted { $0.startDate > $1.startDate }
    }
    
    func getCoursesForInstructor(instructorId: String) -> [Course] {
        return courses.filter { $0.instructorId == instructorId }
    }
    
    func enrollInCourse(courseId: String) async throws {
        // TODO: Implement course enrollment
        // This will need a backend endpoint like POST /api/courses/{courseId}/enroll
        throw NetworkError.unknown(NSError(domain: "DiveCenterPublicViewModel", code: -1, userInfo: [NSLocalizedDescriptionKey: "Course enrollment not yet implemented"]))
    }
}
