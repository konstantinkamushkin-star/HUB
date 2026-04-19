package com.divehub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.divehub.app.ui.RootNav
import com.divehub.app.ui.theme.DiveHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deliverDeepLink(intent)
        val graph = diveHubApp().graph
        setContent {
            val themePref by graph.tokenStore.appThemeFlow.collectAsStateWithLifecycle(initialValue = "")
            val systemDark = isSystemInDarkTheme()
            val useDark = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            DiveHubTheme(darkTheme = useDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootNav(graph = graph)
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
