package com.divehub.app

import android.app.Application
import com.divehub.app.data.local.TokenStore
import com.divehub.app.data.remote.ApiClientFactory
import com.divehub.app.data.remote.AdminDashboardApi
import com.divehub.app.data.remote.AuthApi
import com.divehub.app.data.remote.BookingApi
import com.divehub.app.data.remote.ChatApi
import com.divehub.app.data.remote.CoursesApi
import com.divehub.app.data.remote.DiveLogsApi
import com.divehub.app.data.remote.ExploreApi
import com.divehub.app.data.remote.FeedApi
import com.divehub.app.data.remote.NotificationsApi
import com.divehub.app.data.remote.PartnerAdminApi
import com.divehub.app.data.remote.PartnerRegistrationApi
import com.divehub.app.data.remote.ReviewsApi
import com.divehub.app.data.remote.ShopsApi
import com.divehub.app.data.remote.SocialApi
import com.divehub.app.data.remote.TripsApi
import com.divehub.app.data.remote.UsersApi
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

class AppGraph(application: Application) {
    val application: Application = application
    val tokenStore = TokenStore(application)
    val gson: Gson = Gson()

    private val factory = ApiClientFactory(tokenStore, gson)
    /** Same OkHttp as Retrofit (auth + refresh); use for Coil so media loads like API calls. */
    val httpClient: OkHttpClient get() = factory.sharedOkHttpClient
    private val mutex = Mutex()
    private var cachedRoot: String? = null
    private var retrofit: retrofit2.Retrofit? = null

    suspend fun authApi(): AuthApi {
        return api(AuthApi::class.java)
    }

    suspend fun adminDashboardApi(): AdminDashboardApi {
        return api(AdminDashboardApi::class.java)
    }

    suspend fun bookingApi(): BookingApi {
        return api(BookingApi::class.java)
    }

    suspend fun exploreApi(): ExploreApi {
        return api(ExploreApi::class.java)
    }

    suspend fun feedApi(): FeedApi {
        return api(FeedApi::class.java)
    }

    suspend fun diveLogsApi(): DiveLogsApi {
        return api(DiveLogsApi::class.java)
    }

    suspend fun socialApi(): SocialApi {
        return api(SocialApi::class.java)
    }

    suspend fun chatApi(): ChatApi {
        return api(ChatApi::class.java)
    }

    suspend fun reviewsApi(): ReviewsApi {
        return api(ReviewsApi::class.java)
    }

    suspend fun tripsApi(): TripsApi {
        return api(TripsApi::class.java)
    }

    suspend fun coursesApi(): CoursesApi {
        return api(CoursesApi::class.java)
    }

    suspend fun notificationsApi(): NotificationsApi {
        return api(NotificationsApi::class.java)
    }

    suspend fun usersApi(): UsersApi {
        return api(UsersApi::class.java)
    }

    suspend fun partnerAdminApi(): PartnerAdminApi {
        return api(PartnerAdminApi::class.java)
    }

    suspend fun shopsApi(): ShopsApi {
        return api(ShopsApi::class.java)
    }

    suspend fun partnerRegistrationApi(): PartnerRegistrationApi {
        return api(PartnerRegistrationApi::class.java)
    }

    private suspend fun <T> api(clazz: Class<T>): T {
        val root = tokenStore.getRootBaseUrl()
        return mutex.withLock {
            if (retrofit == null || cachedRoot != root) {
                retrofit = factory.buildRetrofit(root)
                cachedRoot = root
            }
            retrofit!!.create(clazz)
        }
    }

    suspend fun resetApiClient() {
        mutex.withLock {
            retrofit = null
            cachedRoot = null
        }
    }

    /** Handoff after [com.divehub.app.ui.chat.BusinessChatOpenRoute] — consumed when chat tab opens. */
    private val pendingChatLock = Any()
    private var pendingChatConversationJson: String? = null

    fun setPendingChatConversationJson(json: String?) {
        synchronized(pendingChatLock) {
            pendingChatConversationJson = json
        }
    }

    fun consumePendingChatConversationJson(): String? {
        synchronized(pendingChatLock) {
            val v = pendingChatConversationJson
            pendingChatConversationJson = null
            return v
        }
    }

    /** `divehub://search?q=…` — consumed when [com.divehub.app.ui.search.GlobalSearchRoute] opens. */
    private val pendingSearchLock = Any()
    private var pendingGlobalSearchQuery: String? = null

    fun setPendingGlobalSearchQuery(query: String?) {
        synchronized(pendingSearchLock) {
            pendingGlobalSearchQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    fun consumePendingGlobalSearchQuery(): String? {
        synchronized(pendingSearchLock) {
            val v = pendingGlobalSearchQuery
            pendingGlobalSearchQuery = null
            return v
        }
    }
}
