package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.CreateDiveLogRequest
import com.divehub.app.data.remote.dto.DiveLogDto
import com.divehub.app.data.remote.dto.UploadMediaResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface DiveLogsApi {
    @GET("dive-logs")
    suspend fun list(@Query("userId") userId: String? = null): List<DiveLogDto>

    @POST("dive-logs")
    suspend fun create(@Body body: CreateDiveLogRequest): DiveLogDto

    @Multipart
    @POST("media/upload")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): UploadMediaResponse
}
