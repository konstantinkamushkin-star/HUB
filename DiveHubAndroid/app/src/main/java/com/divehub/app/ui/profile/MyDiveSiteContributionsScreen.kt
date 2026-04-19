package com.divehub.app.ui.profile

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.remote.dto.DiveSiteContributionMineDto
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDiveSiteContributionsRoute(graph: AppGraph, innerNav: NavController) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<DiveSiteContributionMineDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            if (rows.isEmpty()) loading = true else refreshing = true
            error = null
            runCatching { ExploreRepository(graph).listMyDiveSiteContributions() }
                .onSuccess { rows = it }
                .onFailure { error = it.message ?: "Error" }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_my_dive_site_contributions)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loading && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null && rows.isEmpty() -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { load() }) { Text(stringResource(R.string.common_retry)) }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(rows, key = { it.id }) { row ->
                        ContributionCard(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributionCard(row: DiveSiteContributionMineDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    contributionTypeLabel(row.contributionType),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    contributionStatusLabel(row.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.diveSiteId?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.contribution_row_site_id, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.message?.takeIf { it.isNotBlank() }?.let { msg ->
                Spacer(Modifier.height(4.dp))
                Text(msg, style = MaterialTheme.typography.bodyMedium)
            }
            row.rejectionReason?.takeIf { it.isNotBlank() }?.let { r ->
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.contribution_row_rejection, r),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            row.createdAt?.takeIf { it.isNotBlank() }?.let { t ->
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun contributionTypeLabel(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
    "correction" -> stringResource(R.string.contribution_type_correction)
    "new_site" -> stringResource(R.string.contribution_type_new_site)
    else -> raw
}

@Composable
private fun contributionStatusLabel(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
    "pending" -> stringResource(R.string.contribution_status_pending)
    "approved" -> stringResource(R.string.contribution_status_approved)
    "rejected" -> stringResource(R.string.contribution_status_rejected)
    else -> raw
}
