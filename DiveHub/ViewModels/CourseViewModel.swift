//
//  CourseViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine

@MainActor
class CourseViewModel: ObservableObject {
    @Published var courses: [Course] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    enum CourseBookingError: LocalizedError {
        case notAuthenticated
        case missingDiveCenter
        case emptyParticipants
        case instructorRequired
        
        var errorDescription: String? {
            switch self {
            case .notAuthenticated:
                return "User is not authenticated."
            case .missingDiveCenter:
                return "Course is not linked to a dive center."
            case .emptyParticipants:
                return "At least one participant is required."
            case .instructorRequired:
                return NSLocalizedString("mustSelectInstructor", tableName: "courses", comment: "")
            }
        }
    }
    
    func loadCourses(diveCenterId: String? = nil) async {
        isLoading = true
        errorMessage = nil
        
        #if DEBUG
        // Use test data if explicitly requested or if no user is logged in
        if ProcessInfo.processInfo.environment["USE_TEST_DATA"] == "true" || AuthenticationService.shared.currentUser == nil {
            courses = TestData.testCourses
            isLoading = false
            return
        }
        #endif
        
        do {
            let loadedCourses = try await NetworkService.shared.getCourses(diveCenterId: diveCenterId)
            // Replace courses instead of appending to avoid duplicates
            courses = loadedCourses
            isLoading = false
        } catch {
            #if DEBUG
            // Fallback to test data on error in DEBUG mode
            courses = TestData.testCourses
            #endif
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }
    
    func createCourse(_ course: Course) async throws -> Course {
        return try await NetworkService.shared.createCourse(course)
    }
    
    func updateCourse(_ course: Course) async throws -> Course {
        return try await NetworkService.shared.updateCourse(course)
    }
    
    func deleteCourse(courseId: String) async throws {
        try await NetworkService.shared.deleteCourse(courseId: courseId)
        courses.removeAll { $0.id == courseId }
    }
    
    func bookCourse(
        _ course: Course,
        preferredDate: Date,
        participants: [Booking.Participant],
        paymentMethod: Booking.Payment.PaymentMethod,
        notes: String?,
        instructorUserId: String? = nil
    ) async throws -> Booking {
        guard let user = AuthenticationService.shared.currentUser else {
            throw CourseBookingError.notAuthenticated
        }
        guard let diveCenterId = course.diveCenterId, !diveCenterId.isEmpty else {
            throw CourseBookingError.missingDiveCenter
        }
        guard !participants.isEmpty else {
            throw CourseBookingError.emptyParticipants
        }
        
        let assigned = course.assignedInstructorUserIds
        let resolvedInstructor: String?
        if assigned.count > 1 {
            guard let picked = instructorUserId, assigned.contains(picked) else {
                throw CourseBookingError.instructorRequired
            }
            resolvedInstructor = picked
        } else if assigned.count == 1 {
            resolvedInstructor = assigned[0]
        } else {
            resolvedInstructor = instructorUserId ?? course.instructorId
        }
        
        let booking = Booking(
            id: UUID().uuidString,
            userId: user.id,
            diveCenterId: diveCenterId,
            serviceId: course.id,
            diveSiteId: nil,
            instructorId: resolvedInstructor,
            date: preferredDate,
            startTime: "09:00",
            participants: participants,
            gearRental: nil,
            payment: Booking.Payment(
                method: paymentMethod,
                amount: 0,
                currency: "USD",
                status: .pending,
                transactionId: nil,
                paidAt: nil
            ),
            status: .pending,
            notes: notes,
            createdAt: Date(),
            updatedAt: Date()
        )
        
        return try await NetworkService.shared.createBooking(booking)
    }
}
