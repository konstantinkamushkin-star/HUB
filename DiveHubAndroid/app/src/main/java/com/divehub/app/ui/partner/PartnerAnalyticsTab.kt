package com.divehub.app.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.remote.dto.AdminOverviewDto
import com.divehub.app.data.remote.dto.ErrorStatsDto
import com.divehub.app.ui.theme.IosDesign
import retrofit2.HttpException

@Composable
fun PartnerAnalyticsTab(graph: AppGraph) {
    var loading by remember { mutableStateOf(true) }
    var stats by remember { mutableStateOf<ErrorStatsDto?>(null) }
    var overview by remember { mutableStateOf<AdminOverviewDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching { graph.adminDashboardApi().errorStats() }
            .onSuccess { stats = it }
            .onFailure { e ->
                if (e is HttpException && e.code() == 403) {
                    error = null
                } else {
                    error = e.message
                }
            }
        runCatching { graph.adminDashboardApi().overview() }
            .onSuccess { overview = it }
            .onFailure { }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(IosDesign.ScreenPadding),
    ) {
        Text(
            stringResource(R.string.partner_analytics_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.partner_analytics_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        if (loading) {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
        } else {
            overview?.counts?.let { c ->
                val lines = listOfNotNull(
                    c.users?.let { stringResource(R.string.partner_analytics_users, it) },
                    c.diveCenters?.let { stringResource(R.string.partner_analytics_centers, it) },
                    c.diveSites?.let { stringResource(R.string.partner_analytics_sites, it) },
                )
                if (lines.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = IosDesign.CardCorner,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.partner_analytics_overview_section),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            lines.forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = IosDesign.CardCorner,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.partner_analytics_errors_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val t = stats?.totals
                    if (t == null && error != null) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    } else if (t == null) {
                        Text(
                            stringResource(R.string.partner_analytics_errors_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        StatLine(stringResource(R.string.partner_analytics_total_errors), "${t.allErrors ?: 0}")
                        HorizontalDivider()
                        StatLine(stringResource(R.string.partner_analytics_http_errors), "${t.httpErrors ?: 0}")
                        StatLine(stringResource(R.string.partner_analytics_uncaught), "${t.uncaughtExceptions ?: 0}")
                        StatLine(stringResource(R.string.partner_analytics_rejections), "${t.unhandledRejections ?: 0}")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
