package com.divehub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.divehub.app.ui.RootNav
import com.divehub.app.ui.localization.ProvideLocalization
import com.divehub.app.ui.theme.DiveHubTheme
import com.divehub.app.ui.theme.ProvideDiveHubDarkTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deliverDeepLink(intent)
        val graph = diveHubApp().graph
        runBlocking { graph.tokenStore.ensureDefaultLanguageRuIfUnset() }
        setContent {
            val themePref by graph.tokenStore.appThemeFlow.collectAsStateWithLifecycle(initialValue = "")
            val scalePreset by graph.tokenStore.interfaceScaleFlow.collectAsStateWithLifecycle(initialValue = "standard")
            val systemDark = isSystemInDarkTheme()
            val useDark = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            val scaleFactor = when (scalePreset) {
                "compact" -> 0.9f
                "comfortable" -> 1.1f
                "large" -> 1.25f
                else -> 1f
            }
            ProvideDiveHubDarkTheme(darkTheme = useDark) {
                DiveHubTheme(darkTheme = useDark) {
                    ProvideLocalization(graph = graph) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .scale(scaleFactor),
                        ) {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                RootNav(graph = graph)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverDeepLink(intent)
    }

    private fun deliverDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        diveHubApp().handleDeepLink(uri)
    }
}
