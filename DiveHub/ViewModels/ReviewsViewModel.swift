//
//  ReviewsViewModel.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine

@MainActor
class ReviewsViewModel: ObservableObject {
    @Published var reviews: [Review] = []
    @Published var isLoading = false
    @Published var isSubmitting = false
    @Published var error: Error?

    /// Current user's review for the loaded place, if any.
    var myReview: Review? {
        guard let uid = AuthenticationService.shared.currentUser?.id else { return nil }
        return reviews.first { $0.userId == uid }
    }
    
    /// Submit a new review and reload the list on success.
    func submitReview(reviewableType: ReviewableType, reviewableId: String, rating: Int, text: String) async throws {
        isSubmitting = true
        error = nil
        defer { isSubmitting = false }
        
        let language = LocalizationService.shared.currentLanguage.rawValue
        let request = CreateReviewRequest(
            reviewableType: reviewableType,
            reviewableId: reviewableId,
            rating: rating,
            text: text,
            language: language
        )
        _ = try await NetworkService.shared.createReview(request)
        await loadReviews(type: reviewableType, id: reviewableId)
    }

    func updateReview(id: String, reviewableType: ReviewableType, reviewableId: String, rating: Int, text: String) async throws {
        isSubmitting = true
        error = nil
        defer { isSubmitting = false }
        let language = LocalizationService.shared.currentLanguage.rawValue
        let body = UpdateReviewRequest(rating: rating, text: text, language: language)
        _ = try await NetworkService.shared.updateReview(id: id, body)
        await loadReviews(type: reviewableType, id: reviewableId)
    }

    func deleteReview(id: String, reviewableType: ReviewableType, reviewableId: String) async throws {
        isSubmitting = true
        error = nil
        defer { isSubmitting = false }
        try await NetworkService.shared.deleteReview(id: id)
        await loadReviews(type: reviewableType, id: reviewableId)
    }
    
    func loadReviews(type: ReviewableType, id: String) async {
        
        isLoading = true
        error = nil
        
        do {
            reviews = try await NetworkService.shared.getReviews(reviewableType: type, reviewableId: id)
            
            
            isLoading = false
        } catch {
            
            self.error = error
            isLoading = false
        }
    }
}
