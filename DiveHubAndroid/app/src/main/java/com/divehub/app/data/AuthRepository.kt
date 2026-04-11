package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.ChangePasswordBody
import com.divehub.app.data.remote.dto.ForgotPasswordRequest
import com.divehub.app.data.remote.dto.LoginRequest
import com.divehub.app.data.remote.dto.RegisterRequest
import com.divehub.app.data.remote.dto.ResetPasswordRequest
import com.divehub.app.data.remote.dto.UpdateProfileRequest
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.remote.dto.VerifyResetCodeRequest
import com.divehub.app.util.ConsentTexts
import com.google.gson.JsonParser
import retrofit2.HttpException

class AuthRepository(private val graph: AppGraph) {

    suspend fun login(email: String, password: String) {
        val api = graph.authApi()
        val res = api.login(LoginRequest(email.trim(), password))
        val user = res.user.copy(mustChangePassword = res.mustChangePassword ?: res.user.mustChangePassword)
        graph.tokenStore.saveSession(
            res.accessToken,
            res.refreshToken,
            graph.gson.toJson(user),
        )
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        personalDataConsent: Boolean,
    ) {
        val parts = displayName.trim().split(Regex("\\s+"), limit = 2)
        val first = parts.firstOrNull().orEmpty().ifEmpty { "User" }
        val last = parts.getOrNull(1).orEmpty()
        val api = graph.authApi()
        val res = api.register(
            RegisterRequest(
                email = email.trim().lowercase(),
                password = password,
                firstName = first,
                lastName = last,
                phone = null,
                personalDataConsent = personalDataConsent,
                personalDataConsentText = ConsentTexts.registrationConsentText(),
            ),
        )
        graph.tokenStore.saveSession(
            res.accessToken,
            res.refreshToken,
            graph.gson.toJson(res.user),
        )
    }

    suspend fun requestPasswordReset(email: String) {
        val api = graph.authApi()
        api.forgotPassword(ForgotPasswordRequest(email = email.trim().lowercase()))
    }

    suspend fun verifyResetCode(email: String, code: String) {
        val api = graph.authApi()
        api.verifyResetCode(
            VerifyResetCodeRequest(email = email.trim().lowercase(), code = code.trim()),
        )
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        val api = graph.authApi()
        api.resetPassword(
            ResetPasswordRequest(
                email = email.trim().lowercase(),
                code = code.trim(),
                newPassword = newPassword,
            ),
        )
    }

    suspend fun refreshProfile(): UserDto {
        val api = graph.authApi()
        val user = api.me()
        graph.tokenStore.updateUserJson(graph.gson.toJson(user))
        return user
    }

    suspend fun updateProfile(
        firstName: String,
        lastName: String,
        phone: String?,
        bio: String?,
        language: String,
        avatarUrl: String?,
    ): UserDto {
        val api = graph.authApi()
        val user = api.patchMe(
            UpdateProfileRequest(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                phone = phone?.trim()?.takeIf { it.isNotEmpty() },
                bio = bio?.trim()?.takeIf { it.isNotEmpty() },
                language = language.trim().lowercase().ifBlank { "en" },
                avatarUrl = avatarUrl?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        graph.tokenStore.updateUserJson(graph.gson.toJson(user))
        return user
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): UserDto {
        val api = graph.authApi()
        val res = api.changePassword(
            ChangePasswordBody(currentPassword = currentPassword, newPassword = newPassword),
        )
        graph.tokenStore.updateUserJson(graph.gson.toJson(res.user))
        return res.user
    }

    suspend fun logout() {
        graph.tokenStore.clearSession()
        graph.resetApiClient()
    }

    suspend fun cachedUser(): UserDto? {
        val json = graph.tokenStore.getUserJson() ?: return null
        return try {
            graph.gson.fromJson(json, UserDto::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun parseErrorMessage(t: Throwable): String {
        if (t is HttpException) {
            val raw = t.response()?.errorBody()?.string().orEmpty()
            try {
                val el = JsonParser.parseString(raw).asJsonObject
                when {
                    el.has("message") && el.get("message").isJsonPrimitive ->
                        return el.get("message").asString
                    el.has("message") && el.get("message").isJsonArray -> {
                        val arr = el.getAsJsonArray("message")
                        return (0 until arr.size()).joinToString("; ") { arr[it].asString }
                    }
                }
            } catch (_: Exception) {
                // fall through
            }
            return t.message ?: "HTTP ${t.code()}"
        }
        return t.message ?: t.javaClass.simpleName
    }
}
