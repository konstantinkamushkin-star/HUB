package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.CreateReviewRequest
import com.divehub.app.data.remote.dto.ReviewDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ReviewsApi {
    @GET("reviews")
    suspend fun listReviews(
        @Query("type") type: String,
        @Query("id") id: String,
    ): List<ReviewDto>

    @POST("reviews")
    suspend fun createReview(@Body body: CreateReviewRequest): ReviewDto
}
