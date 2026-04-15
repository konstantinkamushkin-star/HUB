package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.CertificationDto
import com.divehub.app.data.remote.dto.CreateCertificationRequest
import com.divehub.app.data.remote.dto.DeleteMyAccountRequest
import com.divehub.app.data.remote.dto.NotificationSettingsPatch
import com.divehub.app.data.remote.dto.PrivacySettingsPatch
import com.divehub.app.data.remote.dto.RegisterPushTokenRequest
import com.divehub.app.data.remote.dto.UserDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST

interface UsersApi {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): UserDto

    @POST("users/me/push-token")
    suspend fun registerPushToken(@Body body: RegisterPushTokenRequest): Map<String, Boolean>

    @PATCH("users/me/settings/notifications")
    suspend fun patchNotificationSettings(@Body body: NotificationSettingsPatch): ResponseBody

    @PATCH("users/me/settings/privacy")
    suspend fun patchPrivacySettings(@Body body: PrivacySettingsPatch): ResponseBody

    @GET("users/{userId}/certifications")
    suspend fun listCertifications(@Path("userId") userId: String): List<CertificationDto>

    @POST("users/{userId}/certifications")
    suspend fun createCertification(
        @Path("userId") userId: String,
        @Body body: CreateCertificationRequest,
    ): CertificationDto

    @DELETE("users/certifications/{certificationId}")
    suspend fun deleteCertification(@Path("certificationId") certificationId: String): ResponseBody

    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteMyAccount(
        @Header("X-Account-Delete-Confirm") confirmHeader: String = "true",
        @Body body: DeleteMyAccountRequest,
    ): ResponseBody
}
