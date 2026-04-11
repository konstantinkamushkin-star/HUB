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
