package com.divehub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.divehub.app.ui.RootNav
import com.divehub.app.ui.theme.DiveHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deliverDeepLink(intent)
        val graph = diveHubApp().graph
        setContent {
            DiveHubTheme {
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
