package com.divehub.app.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.handleAppActionUrl
import com.divehub.app.data.remote.dto.AppNotificationDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsRoute(graph: AppGraph, innerNav: NavController) {
    val vm: NotificationsViewModel = viewModel(factory = NotificationsViewModel.factory(graph))
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_notifications)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.refresh() },
                        enabled = !state.loading,
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.common_refresh_list),
                        )
                    }
                    TextButton(
                        onClick = { vm.markAllRead() },
                        enabled = state.notifications.any { !it.isRead },
                    ) {
                        Text(stringResource(R.string.notifications_mark_all_read))
                    }
                },
            )
        },
    ) { padding ->
        val context = LocalContext.current
        NotificationsTabPanel(
            state = state,
            onRefresh = { vm.refresh() },
            onDelete = { vm.delete(it) },
            openActionUrl = { url -> context.handleAppActionUrl(url) },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

/**
 * Inbox list for embedding under the Chat tab (segment «Push / notifications»).
 */
@Composable
fun NotificationsTabEmbed(graph: AppGraph, modifier: Modifier = Modifier) {
    val vm: NotificationsViewModel = viewModel(factory = NotificationsViewModel.factory(graph))
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { vm.refresh() },
                enabled = !state.loading,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.common_refresh_list),
                )
            }
            TextButton(
                onClick = { vm.markAllRead() },
                enabled = state.notifications.any { !it.isRead },
            ) {
                Text(stringResource(R.string.notifications_mark_all_read))
            }
        }
        NotificationsTabPanel(
            state = state,
            onRefresh = { vm.refresh() },
            onDelete = { vm.delete(it) },
            openActionUrl = { url -> context.handleAppActionUrl(url) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsTabPanel(
    state: NotificationsUiState,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    openActionUrl: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading && state.notifications.isEmpty() -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        state.error != null && state.notifications.isEmpty() && !state.loading -> Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.error ?: stringResource(R.string.common_error))
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.common_retry))
            }
        }
        state.notifications.isEmpty() -> Box(
            modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.notifications_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> PullToRefreshBox(
            isRefreshing = state.loading && state.notifications.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.error != null) {
                    item {
                        Text(
                            state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                items(state.notifications, key = { it.id }) { n ->
                    NotificationRow(
                        notification = n,
                        onDelete = { onDelete(n.id) },
                        onOpenAction = { openActionUrl(n.actionUrl) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotificationDto,
    onDelete: () -> Unit,
    onOpenAction: () -> Unit,
) {
    val openable = !notification.actionUrl.isNullOrBlank()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (openable) Modifier.clickable(onClick = onOpenAction) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (notification.icon?.lowercase()) {
                    "message" -> Icons.Default.Info
                    else -> Icons.Default.Notifications
                },
                contentDescription = null,
                tint = if (notification.isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(36.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    notification.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                notification.createdAt?.let { at ->
                    Spacer(Modifier.height(4.dp))
                    Text(at, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.notifications_delete))
            }
        }
    }
}
