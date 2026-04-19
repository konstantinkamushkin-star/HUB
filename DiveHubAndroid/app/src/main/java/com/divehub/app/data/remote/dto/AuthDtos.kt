package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
    val personalDataConsent: Boolean,
    val personalDataConsentText: String,
)

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String,
)

/** `POST auth/apple` — matches `AppleAuthDto` on the backend. */
data class AppleAuthRequest(
    @SerializedName("idToken") val idToken: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("personalDataConsent") val personalDataConsent: Boolean,
    @SerializedName("personalDataConsentText") val personalDataConsentText: String,
)

/** `POST auth/google` — matches `GoogleAuthDto` on the backend. */
data class GoogleAuthRequest(
    @SerializedName("idToken") val idToken: String,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("personalDataConsent") val personalDataConsent: Boolean,
    @SerializedName("personalDataConsentText") val personalDataConsentText: String,
)

data class AuthSessionResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("user") val user: UserDto,
    @SerializedName("mustChangePassword") val mustChangePassword: Boolean? = null,
)

data class ChangePasswordBody(
    @SerializedName("currentPassword") val currentPassword: String,
    @SerializedName("newPassword") val newPassword: String,
)

data class ChangePasswordResponse(
    @SerializedName("ok") val ok: Boolean? = null,
    @SerializedName("user") val user: UserDto,
)

data class ForgotPasswordRequest(
    @SerializedName("email") val email: String,
)

data class VerifyResetCodeRequest(
    @SerializedName("email") val email: String,
    @SerializedName("code") val code: String,
)

data class ResetPasswordRequest(
    @SerializedName("email") val email: String,
    @SerializedName("code") val code: String,
    @SerializedName("newPassword") val newPassword: String,
)

data class GenericMessageResponse(
    @SerializedName("message") val message: String? = null,
)

data class UpdateProfileRequest(
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("countryCode") val countryCode: String? = null,
    @SerializedName("diverProfile") val diverProfile: Map<String, Any?>? = null,
)

data class DeleteMyAccountRequest(
    @SerializedName("confirmation") val confirmation: String = "DELETE",
    @SerializedName("currentPassword") val currentPassword: String? = null,
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("subscriptionTier") val subscriptionTier: String? = null,
    @SerializedName("subscriptionExpiresAt") val subscriptionExpiresAt: String? = null,
    /** Client-side mirror of iOS `subscriptionStatus` when server omits it. */
    @SerializedName("subscriptionStatus") val subscriptionStatus: String? = null,
    @SerializedName("certificationLevel") val certificationLevel: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("diveCenterId") val diveCenterId: String? = null,
    @SerializedName("shopId") val shopId: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("countryCode") val countryCode: String? = null,
    @SerializedName("diverProfile") val diverProfile: Map<String, Any?>? = null,
    @SerializedName("totalDives") val totalDives: Int? = null,
    @SerializedName("mustChangePassword") val mustChangePassword: Boolean? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
) {
    fun displayName(): String {
        val fn = firstName?.trim().orEmpty()
        val ln = lastName?.trim().orEmpty()
        return when {
            fn.isNotEmpty() && ln.isNotEmpty() -> "$fn $ln"
            fn.isNotEmpty() -> fn
            ln.isNotEmpty() -> ln
            else -> email.substringBefore('@')
        }
    }
}
