package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.AuthSessionResponse
import com.divehub.app.data.remote.dto.LoginRequest
import com.divehub.app.data.remote.dto.RefreshRequest
import com.divehub.app.data.remote.dto.RegisterRequest
import com.divehub.app.data.remote.dto.ChangePasswordBody
import com.divehub.app.data.remote.dto.ChangePasswordResponse
import com.divehub.app.data.remote.dto.AppleAuthRequest
import com.divehub.app.data.remote.dto.GoogleAuthRequest
import com.divehub.app.data.remote.dto.ForgotPasswordRequest
import com.divehub.app.data.remote.dto.GenericMessageResponse
import com.divehub.app.data.remote.dto.ResetPasswordRequest
import com.divehub.app.data.remote.dto.UpdateProfileRequest
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.remote.dto.VerifyResetCodeRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthSessionResponse

    @POST("auth/google")
    suspend fun google(@Body body: GoogleAuthRequest): AuthSessionResponse

    @POST("auth/apple")
    suspend fun apple(@Body body: AppleAuthRequest): AuthSessionResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthSessionResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthSessionResponse

    @GET("auth/me")
    suspend fun me(): UserDto

    @PATCH("auth/me")
    suspend fun patchMe(@Body body: UpdateProfileRequest): UserDto

    @PATCH("auth/password")
    suspend fun changePassword(@Body body: ChangePasswordBody): ChangePasswordResponse

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): GenericMessageResponse

    @POST("auth/verify-reset-code")
    suspend fun verifyResetCode(@Body body: VerifyResetCodeRequest): GenericMessageResponse

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): GenericMessageResponse
}
