package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AppleAuthRequest
import com.divehub.app.data.remote.dto.GoogleAuthRequest
import com.divehub.app.data.remote.dto.ChangePasswordBody
import com.divehub.app.data.remote.dto.DeleteMyAccountRequest
import com.divehub.app.data.remote.dto.ForgotPasswordRequest
import com.divehub.app.data.remote.dto.LoginRequest
import com.divehub.app.data.remote.dto.RegisterRequest
import com.divehub.app.data.remote.dto.ResetPasswordRequest
import com.divehub.app.data.remote.dto.UpdateProfileRequest
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.remote.dto.VerifyResetCodeRequest
import com.divehub.app.data.local.AdminDashboardLayout
import com.divehub.app.util.ConsentTexts
import com.google.gson.JsonParser
import com.divehub.app.R
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
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

    suspend fun loginWithApple(
        idToken: String,
        email: String?,
        firstName: String?,
        lastName: String?,
    ) {
        val api = graph.authApi()
        val res = api.apple(
            AppleAuthRequest(
                idToken = idToken,
                email = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                firstName = firstName?.trim()?.takeIf { it.isNotEmpty() },
                lastName = lastName?.trim()?.takeIf { it.isNotEmpty() },
                personalDataConsent = true,
                personalDataConsentText = ConsentTexts.appleOAuthConsentText(),
            ),
        )
        val user = res.user.copy(mustChangePassword = res.mustChangePassword ?: res.user.mustChangePassword)
        graph.tokenStore.saveSession(
            res.accessToken,
            res.refreshToken,
            graph.gson.toJson(user),
        )
    }

    suspend fun loginWithGoogle(
        idToken: String,
        email: String?,
        firstName: String?,
        lastName: String?,
    ) {
        val api = graph.authApi()
        val res = api.google(
            GoogleAuthRequest(
                idToken = idToken,
                accessToken = null,
                email = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                firstName = firstName?.trim()?.takeIf { it.isNotEmpty() },
                lastName = lastName?.trim()?.takeIf { it.isNotEmpty() },
                personalDataConsent = true,
                personalDataConsentText = ConsentTexts.googleOAuthConsentText(),
            ),
        )
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
        personalDataConsent: Boolean,
    ) {
        val local = email.trim()
            .substringBefore('@')
            .replace(Regex("[^a-zA-Z0-9._-]"), "")
            .ifBlank { "diver" }
            .take(80)
        val api = graph.authApi()
        val res = api.register(
            RegisterRequest(
                email = email.trim().lowercase(),
                password = password,
                firstName = local,
                lastName = local,
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

    /**
     * Persists partner admin home layout under `diverProfile.adminDashboardLayout` (server merge).
     */
    suspend fun patchAdminDashboardLayout(layout: AdminDashboardLayout): UserDto {
        val u = cachedUser() ?: throw IllegalStateException("not_signed_in")
        @Suppress("UNCHECKED_CAST")
        val inner = u.diverProfile?.get("adminDashboardLayout") as? Map<String, Any?>
        val merged = layout.mergeIntoExisting(inner)
        val dp = LinkedHashMap<String, Any?>()
        u.diverProfile?.let { dp.putAll(it) }
        dp["adminDashboardLayout"] = merged
        return updateProfile(diverProfile = dp)
    }

    suspend fun resetAdminDashboardLayout(): UserDto {
        val u = cachedUser() ?: throw IllegalStateException("not_signed_in")
        val dp = LinkedHashMap<String, Any?>()
        u.diverProfile?.let { dp.putAll(it) }
        dp["adminDashboardLayout"] = AdminDashboardLayout.defaultServerMapAllPlatforms()
        return updateProfile(diverProfile = dp)
    }

    suspend fun updateProfile(
        firstName: String? = null,
        lastName: String? = null,
        phone: String? = null,
        bio: String? = null,
        language: String? = null,
        avatarUrl: String? = null,
        countryCode: String? = null,
        diverProfile: Map<String, Any?>? = null,
    ): UserDto {
        val api = graph.authApi()
        val user = api.patchMe(
            UpdateProfileRequest(
                firstName = firstName?.trim()?.takeIf { it.isNotEmpty() },
                lastName = lastName?.trim()?.takeIf { it.isNotEmpty() },
                phone = phone?.trim()?.takeIf { it.isNotEmpty() },
                bio = bio?.trim()?.takeIf { it.isNotEmpty() },
                language = language?.trim()?.lowercase()?.ifBlank { null },
                avatarUrl = avatarUrl?.trim()?.takeIf { it.isNotEmpty() },
                countryCode = countryCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
                diverProfile = diverProfile,
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

    suspend fun deleteMyAccount(currentPassword: String?) {
        val api = graph.usersApi()
        val body = DeleteMyAccountRequest(
            confirmation = "DELETE",
            currentPassword = currentPassword?.trim()?.takeIf { it.isNotEmpty() },
        )
        api.deleteMyAccount(body = body).close()
    }

    suspend fun cachedUser(): UserDto? {
        val json = graph.tokenStore.getUserJson() ?: return null
        return try {
            graph.gson.fromJson(json, UserDto::class.java)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun persistCachedUser(user: UserDto) {
        graph.tokenStore.updateUserJson(graph.gson.toJson(user))
    }

    private fun extractApiMessage(raw: String): String? {
        if (raw.isBlank()) return null
        return try {
            val el = JsonParser.parseString(raw).asJsonObject
            when {
                el.has("message") && el.get("message").isJsonPrimitive ->
                    el.get("message").asString
                el.has("message") && el.get("message").isJsonArray -> {
                    val arr = el.getAsJsonArray("message")
                    (0 until arr.size()).joinToString("; ") { arr[it].asString }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun stripHttpFromMessage(message: String): String {
        return message
            .replace(Regex("\\s*\\(HTTP\\s*\\d+\\)\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^HTTP\\s*\\d+\\s*[–—:\\-]\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun isOfflineLike(t: Throwable): Boolean {
        when (t) {
            is UnknownHostException -> return true
            is SocketTimeoutException -> return true
            is ConnectException -> return true
        }
        if (t is IOException) {
            val m = t.message?.lowercase(Locale.ROOT).orEmpty()
            if (m.contains("unable to resolve host")) return true
            if (m.contains("network is unreachable")) return true
            if (m.contains("failed to connect")) return true
            if (m.contains("no route to host")) return true
            if (m.contains("connection reset")) return true
            if (m.contains("broken pipe")) return true
        }
        val c = t.cause
        return c != null && isOfflineLike(c)
    }

    fun parseErrorMessage(t: Throwable): String {
        val res = graph.application.resources
        if (isOfflineLike(t)) {
            return res.getString(R.string.api_error_no_internet)
        }
        if (t is HttpException) {
            val code = t.code()
            val raw = t.response()?.errorBody()?.string().orEmpty()
            val apiRaw = extractApiMessage(raw)?.trim().orEmpty()
            val cleaned = stripHttpFromMessage(apiRaw)
            val lower = cleaned.lowercase(Locale.ROOT)
            when {
                code == 401 ||
                    lower.contains("invalid email") ||
                    lower.contains("invalid password") ||
                    lower.contains("invalid credentials") ->
                    return res.getString(R.string.api_error_invalid_login)
                code == 403 -> return res.getString(R.string.api_error_forbidden)
                code == 404 -> return res.getString(R.string.api_error_not_found)
                code == 409 -> return res.getString(R.string.api_error_conflict)
                code == 400 &&
                    (
                        lower.contains("already exists") ||
                            lower.contains("user with this email")
                        ) ->
                    return res.getString(R.string.api_error_email_registered)
                code == 429 -> return res.getString(R.string.api_error_too_many_requests)
                code in 500..599 -> return res.getString(R.string.api_error_server)
            }
            val hasHttpCode = Regex("HTTP\\s*\\d+", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
            if (cleaned.isNotEmpty() && !hasHttpCode) {
                return cleaned
            }
            return res.getString(R.string.api_error_generic)
        }
        val msg = t.message?.trim().orEmpty()
        val msgHasHttpCode = Regex("HTTP\\s*\\d+", RegexOption.IGNORE_CASE).containsMatchIn(msg)
        if (msg.isNotEmpty() && !msgHasHttpCode) return msg
        return res.getString(R.string.api_error_generic)
    }
}
