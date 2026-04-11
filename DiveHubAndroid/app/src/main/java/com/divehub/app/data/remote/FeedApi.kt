package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.CreateFeedPostRequest
import com.divehub.app.data.remote.dto.FeedCommentDto
import com.divehub.app.data.remote.dto.FeedCommentRequest
import com.divehub.app.data.remote.dto.FeedListResponse
import com.divehub.app.data.remote.dto.FeedPostDto
import com.divehub.app.data.remote.dto.UploadMediaResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Part
import retrofit2.http.Query

interface FeedApi {
    @GET("feed/posts")
    suspend fun listPosts(
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
    ): FeedListResponse

    @POST("feed/posts")
    suspend fun createPost(@Body body: CreateFeedPostRequest): FeedPostDto

    @POST("feed/posts/{postId}/like")
    suspend fun toggleLike(@Path("postId") postId: String): FeedPostDto

    @GET("feed/posts/{postId}/comments")
    suspend fun comments(@Path("postId") postId: String): List<FeedCommentDto>

    @POST("feed/posts/{postId}/comments")
    suspend fun addComment(@Path("postId") postId: String, @Body body: FeedCommentRequest): FeedCommentDto

    @Multipart
    @POST("media/upload")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): UploadMediaResponse
}
