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
import androidx.compose.material3.LinearProgressIndicator
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
import com.divehub.app.data.AdminBookingsRepository
import com.divehub.app.data.InventoryRepository
import com.divehub.app.data.remote.dto.AdminBookingLocal
import com.divehub.app.data.remote.dto.AdminOverviewDto
import com.divehub.app.data.remote.dto.ErrorStatsDto
import com.divehub.app.ui.theme.IosDesign
import retrofit2.HttpException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Composable
fun PartnerAnalyticsTab(graph: AppGraph) {
    var loading by remember { mutableStateOf(true) }
    var stats by remember { mutableStateOf<ErrorStatsDto?>(null) }
    var overview by remember { mutableStateOf<AdminOverviewDto?>(null) }
    var localBookings by remember { mutableStateOf<LocalBookingKpis?>(null) }
    var localInventory by remember { mutableStateOf<LocalInventoryKpis?>(null) }
    var bookingRows by remember { mutableStateOf<List<AdminBookingLocal>>(emptyList()) }
    var bookingsSyncNote by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val bookingTrend = remember(bookingRows) { adminBookingsDailyTrend7d(bookingRows) }
    val bookingTrendMax = remember(bookingTrend) {
        bookingTrend.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    }

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
        runCatching {
            val (rows, syncErr) = AdminBookingsRepository(graph).syncFromRemoteWithFallback(null)
            bookingRows = rows
            bookingsSyncNote = syncErr
            LocalBookingKpis(
                total = rows.size,
                pending = rows.count { it.status.equals("pending", ignoreCase = true) },
                confirmed = rows.count { it.status.equals("confirmed", ignoreCase = true) },
                completed = rows.count { it.status.equals("completed", ignoreCase = true) },
                cancelled = rows.count { it.status.equals("cancelled", ignoreCase = true) },
                revenue = rows.filter { it.status.equals("completed", ignoreCase = true) }.sumOf { it.amount },
            )
        }.onSuccess { localBookings = it }
        runCatching {
            val repo = InventoryRepository(graph)
            val items = repo.loadItems()
            val tickets = repo.loadTickets()
            LocalInventoryKpis(
                items = items.size,
                available = items.count { it.status.equals("available", ignoreCase = true) },
                issued = items.count { it.status.equals("issued", ignoreCase = true) },
                maintenance = items.count { it.status.equals("maintenance", ignoreCase = true) },
                ticketsOpen = tickets.count { it.status.equals("open", ignoreCase = true) },
                ticketsHigh = tickets.count { it.priority.equals("high", ignoreCase = true) },
            )
        }.onSuccess { localInventory = it }
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
        bookingsSyncNote?.takeIf { it.isNotBlank() }?.let { msg ->
            Text(
                stringResource(R.string.partner_analytics_bookings_sync_warning, msg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
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
            localBookings?.let { b ->
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
                            stringResource(R.string.partner_analytics_bookings_section),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        StatLine(stringResource(R.string.partner_analytics_bookings_total), "${b.total}")
                        HorizontalDivider()
                        StatLine(stringResource(R.string.partner_analytics_bookings_pending), "${b.pending}")
                        StatLine(stringResource(R.string.partner_analytics_bookings_confirmed), "${b.confirmed}")
                        StatLine(stringResource(R.string.partner_analytics_bookings_completed), "${b.completed}")
                        StatLine(stringResource(R.string.partner_analytics_bookings_cancelled), "${b.cancelled}")
                        val rev = "$" + String.format(Locale.US, "%.2f", b.revenue)
                        StatLine(stringResource(R.string.partner_analytics_bookings_revenue_completed), rev)
                    }
                }
            }
            if (bookingRows.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = IosDesign.CardCorner,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.partner_analytics_bookings_trend_7d),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        bookingTrend.forEach { entry ->
                            PartnerAnalyticsTrendRow(
                                label = entry.date.toString(),
                                value = entry.count,
                                total = bookingTrendMax,
                            )
                        }
                    }
                }
            }
            localInventory?.let { inv ->
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
                            stringResource(R.string.partner_analytics_inventory_section),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        StatLine(stringResource(R.string.partner_analytics_inventory_items), "${inv.items}")
                        HorizontalDivider()
                        StatLine(stringResource(R.string.partner_analytics_inventory_available), "${inv.available}")
                        StatLine(stringResource(R.string.partner_analytics_inventory_issued), "${inv.issued}")
                        StatLine(stringResource(R.string.partner_analytics_inventory_maintenance), "${inv.maintenance}")
                        StatLine(stringResource(R.string.partner_analytics_inventory_open_tickets), "${inv.ticketsOpen}")
                        StatLine(stringResource(R.string.partner_analytics_inventory_high_tickets), "${inv.ticketsHigh}")
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

private data class AdminDailyBookingTrend(val date: LocalDate, val count: Int)

private fun adminBookingsDailyTrend7d(rows: List<AdminBookingLocal>): List<AdminDailyBookingTrend> {
    val today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate()
    val counts = rows
        .mapNotNull { row ->
            runCatching { Instant.parse(row.createdAt).atZone(ZoneOffset.UTC).toLocalDate() }.getOrNull()
        }
        .groupingBy { it }
        .eachCount()
    return (6 downTo 0).map { offset ->
        val day = today.minusDays(offset.toLong())
        AdminDailyBookingTrend(day, counts[day] ?: 0)
    }
}

@Composable
private fun PartnerAnalyticsTrendRow(label: String, value: Int, total: Int) {
    val fraction = if (total <= 0) 0f else (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                value.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

private data class LocalBookingKpis(
    val total: Int,
    val pending: Int,
    val confirmed: Int,
    val completed: Int,
    val cancelled: Int,
    val revenue: Double,
)

private data class LocalInventoryKpis(
    val items: Int,
    val available: Int,
    val issued: Int,
    val maintenance: Int,
    val ticketsOpen: Int,
    val ticketsHigh: Int,
)

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
