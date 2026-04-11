package com.divehub.app.ui.statistics

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsRoute(graph: AppGraph, innerNav: NavController) {
    val app = LocalContext.current.applicationContext as Application
    val vm: StatisticsViewModel = viewModel(factory = StatisticsViewModel.factory(graph, app))
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_statistics)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.stats == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.stats == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error ?: stringResource(R.string.common_error))
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.refresh() }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
            state.stats != null -> {
                val s = state.stats!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.stats_card_total_dives),
                            value = s.totalDives.toString(),
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.stats_card_bottom_time),
                            value = formatHoursMinutes(s.totalBottomTimeMinutes),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.stats_card_deepest),
                            value = stringResource(R.string.stats_depth_m_format, s.deepestDiveMeters.toInt()),
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.stats_card_longest),
                            value = stringResource(R.string.stats_minutes_format, s.longestDiveMinutes),
                        )
                    }
                    s.averageDepthMeters?.let { avg ->
                        Text(
                            stringResource(R.string.stats_avg_depth_line, avg),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (s.diveByMonth.isNotEmpty()) {
                        Text(stringResource(R.string.stats_by_month), style = MaterialTheme.typography.titleMedium)
                        MonthBars(s.diveByMonth)
                    }
                    if (s.diveByType.isNotEmpty()) {
                        Text(stringResource(R.string.stats_by_type), style = MaterialTheme.typography.titleMedium)
                        s.diveByType.take(8).forEach { (type, count) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(type, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(count.toString(), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (s.milestones.isNotEmpty()) {
                        Text(stringResource(R.string.stats_milestones_section), style = MaterialTheme.typography.titleMedium)
                        s.milestones.forEach { m ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(m.title, fontWeight = FontWeight.SemiBold)
                                    Text(m.description, style = MaterialTheme.typography.bodySmall)
                                    m.dateLabel?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatHoursMinutes(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, title: String, value: String) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthBars(rows: List<Pair<String, Int>>) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (month, count) ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(month, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
                    LinearProgressIndicator(
                        progress = { count.toFloat() / max },
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp),
                    )
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp).width(28.dp),
                    )
                }
            }
        }
    }
}
