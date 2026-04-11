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

    /** `divehub://social`, `divehub://chat`, … — см. нотификации бэкенда */
    fun handleDeepLink(uri: Uri?) {
        if (uri == null || uri.scheme?.lowercase(Locale.ROOT) != "divehub") return
        val host = uri.host?.lowercase(Locale.ROOT) ?: return
        appScope.launch {
            val editorOn = graph.tokenStore.isDiveEditorEnabled()
            when (host) {
                "social" -> emitDiverTab(3)
                "chat" -> emitDiverTab(4)
                "explore", "home" -> emitDiverTab(0)
                "feed" -> emitDiverTab(1)
                "logbook" -> emitDiverTab(2)
                "profile" -> emitDiverTab(if (editorOn) 6 else 5)
                else -> emitDiverTab(4)
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
