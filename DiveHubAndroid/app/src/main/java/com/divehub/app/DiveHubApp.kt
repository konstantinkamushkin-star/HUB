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
            val lang = graph.tokenStore.getAppLanguageTag()
            if (lang.isNotBlank()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
            }
        }
    }

    /**
     * In-app `divehub://` links (notifications, share).
     * Tabs: `social`, `chat`, `explore`/`home`, `map`, `feed`, `logbook`, `profile`.
     * Routes (first path segment or `?id=`): `trip`/`trips`, `dive_center`/`center`, `shop`/`shops`, `user`.
     * Search: `search?q=…` sets [AppGraph.setPendingGlobalSearchQuery] then opens Search.
     */
    fun handleDeepLink(uri: Uri?) {
        if (uri == null || uri.scheme?.lowercase(Locale.ROOT) != "divehub") return
        val host = uri.host?.lowercase(Locale.ROOT) ?: return
        appScope.launch {
            val editorOn = graph.tokenStore.isDiveEditorEnabled()
            when (host) {
                "social" -> emitDiverTab(4)
                "chat" -> {
                    val peerId = uri.getQueryParameter("peerId")
                    val peerType = uri.getQueryParameter("peerType") ?: "user"
                    if (!peerId.isNullOrBlank()) {
                        requestBusinessChatOpen(peerType, peerId)
                    }
                    emitDiverTab(5)
                }
                "explore", "home" -> emitDiverTab(0)
                "map" -> emitDiverTab(1)
                "feed" -> emitDiverTab(2)
                "logbook" -> emitDiverTab(3)
                "profile" -> emitDiverTab(if (editorOn) 7 else 6)
                "trip", "trips" -> firstPathOrQueryId(uri)?.let { id ->
                    requestInnerNavRoute(InnerRoutes.tripDetail(id))
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
                "search" -> {
                    val q = uri.getQueryParameter("q")?.trim()?.takeIf { it.isNotEmpty() }
                    if (q != null) {
                        graph.setPendingGlobalSearchQuery(q)
                    }
                    requestInnerNavRoute(InnerRoutes.Search)
                }
                else -> emitDiverTab(5)
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
