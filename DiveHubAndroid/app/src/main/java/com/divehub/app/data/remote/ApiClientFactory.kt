package com.divehub.app.data.remote

import com.divehub.app.BuildConfig
import com.divehub.app.data.local.TokenStore
import com.divehub.app.data.remote.dto.AuthSessionResponse
import com.divehub.app.data.remote.dto.RefreshRequest
import com.divehub.app.util.mediaOriginBaseUrl
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClientFactory(
    private val tokenStore: TokenStore,
    private val gson: Gson,
) {

    private val logging: HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    private val authInterceptor = Interceptor { chain ->
        val token = runBlocking { tokenStore.getAccessToken() }
        val req = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) {
                header("Authorization", "Bearer $token")
            }
        }.build()
        chain.proceed(req)
    }

    /** Shared with Retrofit and Coil so image requests get the same auth + token refresh as API calls. */
    val sharedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .authenticator(TokenRefreshAuthenticator(tokenStore, gson))
            .build()
    }

    fun buildRetrofit(rootBaseUrl: String): Retrofit {
        val root = mediaOriginBaseUrl(rootBaseUrl).trimEnd('/')
        return Retrofit.Builder()
            .baseUrl("$root/api/")
            .client(sharedOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private class TokenRefreshAuthenticator(
        private val tokenStore: TokenStore,
        private val gson: Gson,
    ) : Authenticator {

        override fun authenticate(route: Route?, response: Response): Request? {
            val path = response.request.url.encodedPath
            if (path.contains("auth/refresh")) return null
            if (responseCount(response) >= 3) return null

            val refresh = runBlocking { tokenStore.getRefreshToken() } ?: return null
            val rootBaseUrl = runBlocking { tokenStore.getRootBaseUrl() }
            val url = "${mediaOriginBaseUrl(rootBaseUrl).trimEnd('/')}/api/auth/refresh"
            val json = gson.toJson(RefreshRequest(refresh))
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            val plain = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val refreshReq = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            val refreshRes = try {
                plain.newCall(refreshReq).execute()
            } catch (_: Exception) {
                return null
            }

            refreshRes.use {
                if (!it.isSuccessful) {
                    runBlocking { tokenStore.clearSession() }
                    return null
                }
                val text = it.body?.string() ?: return null
                val parsed = try {
                    gson.fromJson(text, AuthSessionResponse::class.java)
                } catch (_: Exception) {
                    return null
                }
                runBlocking {
                    tokenStore.saveSession(
                        parsed.accessToken,
                        parsed.refreshToken,
                        gson.toJson(parsed.user),
                    )
                }
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${parsed.accessToken}")
                    .build()
            }
        }

        private fun responseCount(response: Response): Int {
            var n = 1
            var p = response.priorResponse
            while (p != null) {
                n++
                p = p.priorResponse
            }
            return n
        }
    }
}
