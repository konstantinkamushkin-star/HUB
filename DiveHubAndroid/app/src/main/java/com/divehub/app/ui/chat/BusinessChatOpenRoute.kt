package com.divehub.app.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ChatRepository
import com.divehub.app.diveHubApp

/**
 * iOS `BusinessChatLaunchView`: open or create chat with dive center / shop, then switch to Messages tab.
 */
@Composable
fun BusinessChatOpenRoute(
    graph: AppGraph,
    innerNav: NavController,
    peerType: String,
    peerId: String,
) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(peerType, peerId) {
        if (finished) return@LaunchedEffect
        runCatching {
            ChatRepository(graph).openConversation(peerId = peerId, peerType = peerType)
        }.onSuccess { conv ->
            graph.setPendingChatConversationJson(graph.gson.toJson(conv))
            finished = true
            innerNav.popBackStack()
            ctx.diveHubApp().emitDiverTab(4)
        }.onFailure { e ->
            error = e.message ?: ctx.getString(R.string.chat_error_generic)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { innerNav.popBackStack() }) {
                    Text(stringResource(R.string.common_close))
                }
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.business_chat_opening),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
