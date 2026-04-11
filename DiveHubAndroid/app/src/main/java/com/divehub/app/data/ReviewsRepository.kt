package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.CreateReviewRequest
import com.divehub.app.data.remote.dto.ReviewDto

class ReviewsRepository(private val graph: AppGraph) {
    suspend fun listReviews(reviewableType: String, reviewableId: String): List<ReviewDto> {
        return graph.reviewsApi().listReviews(reviewableType, reviewableId)
    }

    suspend fun createReview(body: CreateReviewRequest): ReviewDto {
        return graph.reviewsApi().createReview(body)
    }
}
