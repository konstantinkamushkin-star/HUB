package com.divehub.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.osmdroid.config.Configuration
import java.util.Locale
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.main.DiverTabIndices

class DiveHubApp : Application(), ImageLoaderFactory {
    lateinit var graph: AppGraph
        private set

    private val appJob = SupervisorJob()
    private val appScope = CoroutineScope(appJob + Dispatchers.Main.immediate)

    /** FCM token upload, IO work off the main thread */
    private val workJob = SupervisorJob()
    val applicationWorkScope = CoroutineScope(workJob + Dispatchers.IO)

    private val _diverTabEvents = MutableSharedFlow<Int>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val diverTabEvents = _diverTabEvents.asSharedFlow()

    fun emitDiverTab(tabIndex: Int) {
        _diverTabEvents.tryEmit(tabIndex)
    }

    /** `divehub://chat?peerType=dive_center&peerId=…` — open business/user chat (iOS parity). */
    private val _businessChatOpenRequests = MutableSharedFlow<Pair<String, String>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val businessChatOpenRequests = _businessChatOpenRequests.asSharedFlow()

    fun requestBusinessChatOpen(peerType: String, peerId: String) {
        _businessChatOpenRequests.tryEmit(peerType to peerId)
    }

    /** Full `InnerRoutes.*` destination for [MainShell] / diver + partner `innerNav`. */
    private val _innerNavDeepLinkRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val innerNavDeepLinkRequests = _innerNavDeepLinkRequests.asSharedFlow()

    private fun requestInnerNavRoute(route: String) {
        if (route.isNotBlank()) {
            _innerNavDeepLinkRequests.tryEmit(route)
        }
    }

    private fun firstPathOrQueryId(uri: Uri): String? {
        val q = uri.getQueryParameter("id")?.trim().orEmpty().takeIf { it.isNotEmpty() }
        if (q != null) return q
        val seg = uri.pathSegments.firstOrNull()?.trim().orEmpty().takeIf { it.isNotEmpty() }
        return seg
    }

    override fun newImageLoader(): ImageLoader {
        val app = applicationContext as DiveHubApp
        val client =
            if (app::graph.isInitialized) {
                app.graph.httpClient
            } else {
                okhttp3.OkHttpClient()
            }
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        graph = AppGraph(this)
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        runBlocking {
            graph.tokenStore.ensureDefaultLanguageRuIfUnset()
            val lang = graph.tokenStore.getAppLanguageTag().ifBlank { "ru" }
            if (lang.isNotBlank()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
            }
            graph.localizationRepository.sync(lang)
        }
        applicationWorkScope.launch {
            com.divehub.app.services.PhotoEnhancementQueue.resumeIncompleteJobs(
                this@DiveHubApp,
                graph.photoEnhancementJobStore,
            )
        }
    }

    /**
     * In-app `divehub://` links (notifications, share).
     * Tabs: `social`, `chat`, `explore`/`home`, `map` (opens Explore + fullscreen map), `feed`, `logbook`, `profile`.
     * Routes (first path segment or `?id=`): `trip`/`trips`, `dive_center`/`center`, `shop`/`shops`, `user`.
     * Search: `search?q=…` sets [AppGraph.setPendingGlobalSearchQuery] then opens Search.
     */
    fun handleDeepLink(uri: Uri?) {
        if (uri == null || uri.scheme?.lowercase(Locale.ROOT) != "divehub") return
        val host = uri.host?.lowercase(Locale.ROOT) ?: return
        appScope.launch {
            val editorOn = graph.tokenStore.isDiveEditorEnabled()
            when (host) {
                "social" -> emitDiverTab(DiverTabIndices.SOCIAL)
                "chat" -> {
                    val peerId = uri.getQueryParameter("peerId")
                    val peerType = uri.getQueryParameter("peerType") ?: "user"
                    if (!peerId.isNullOrBlank()) {
                        requestBusinessChatOpen(peerType, peerId)
                    }
                    emitDiverTab(DiverTabIndices.CHAT)
                }
                "explore", "home" -> emitDiverTab(DiverTabIndices.EXPLORE)
                "map" -> {
                    emitDiverTab(DiverTabIndices.EXPLORE)
                    requestInnerNavRoute(InnerRoutes.MapFullscreen)
                }
                "feed" -> emitDiverTab(DiverTabIndices.FEED)
                "logbook" -> emitDiverTab(DiverTabIndices.LOGBOOK)
                "profile" -> emitDiverTab(DiverTabIndices.profileTab(editorOn))
                "trip", "trips" -> {
                    val id = firstPathOrQueryId(uri)
                    if (id != null) {
                        emitDiverTab(DiverTabIndices.TRIPS)
                        requestInnerNavRoute(InnerRoutes.tripDetail(id))
                    } else {
                        emitDiverTab(DiverTabIndices.TRIPS)
                    }
                }
                "dive_center", "center" -> firstPathOrQueryId(uri)?.let { id ->
                    requestInnerNavRoute(InnerRoutes.diveCenterPublic(id))
                }
                "shop", "shops" -> firstPathOrQueryId(uri)?.let { id ->
                    requestInnerNavRoute(InnerRoutes.shopPublic(id))
                }
                "user" -> firstPathOrQueryId(uri)?.let { id ->
                    requestInnerNavRoute(InnerRoutes.userProfile(id))
                }
                "instructor" -> firstPathOrQueryId(uri)?.let { id ->
                    requestInnerNavRoute(InnerRoutes.instructorPublic(id, null))
                }
                "search" -> {
                    val q = uri.getQueryParameter("q")?.trim()?.takeIf { it.isNotEmpty() }
                    if (q != null) {
                        graph.setPendingGlobalSearchQuery(q)
                    }
                    requestInnerNavRoute(InnerRoutes.Search)
                }
                "hashtag" -> {
                    val tag = uri.pathSegments.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                        ?: uri.lastPathSegment?.trim()?.takeIf { it.isNotEmpty() }
                    if (tag != null) {
                        emitDiverTab(DiverTabIndices.FEED)
                        requestInnerNavRoute(InnerRoutes.hashtagFeed(tag))
                    }
                }
                else -> emitDiverTab(DiverTabIndices.CHAT)
            }
        }
    }
}

fun android.content.Context.diveHubApp(): DiveHubApp = applicationContext as DiveHubApp

/** In-app `divehub://` or external https links from notifications. */
fun Context.handleAppActionUrl(url: String?) {
    if (url.isNullOrBlank()) return
    val uri = Uri.parse(url.trim())
    when (uri.scheme?.lowercase(Locale.ROOT)) {
        "divehub" -> diveHubApp().handleDeepLink(uri)
        "http", "https" -> runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
