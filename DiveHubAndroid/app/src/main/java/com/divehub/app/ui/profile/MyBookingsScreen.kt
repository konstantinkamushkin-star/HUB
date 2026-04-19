package com.divehub.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.divehub.app.data.BookingRepository
import com.divehub.app.data.remote.dto.UserBookingDto
import com.divehub.app.data.remote.dto.manualVerifiedPriceFromNotes
import com.divehub.app.ui.navigation.InnerRoutes
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.Locale

private const val FILTER_ALL = "all"

private val STATUS_FILTERS = listOf(
    FILTER_ALL,
    "pending",
    "quoted",
    "confirmed",
    "completed",
    "cancelled",
    "refunded",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsRoute(graph: AppGraph, innerNav: NavController) {
    val scope = rememberCoroutineScope()
    val repo = remember { BookingRepository(graph) }
    var rows by remember { mutableStateOf<List<UserBookingDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf(FILTER_ALL) }
    var selectedBooking by remember { mutableStateOf<UserBookingDto?>(null) }

    fun load() {
        scope.launch {
            if (rows.isEmpty()) loading = true else refreshing = true
            error = null
            repo.listMine()
                .onSuccess {
                    rows = it.sortedByDescending { b -> b.createdAt }
                }
                .onFailure { error = it.message ?: "Error" }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val filtered = remember(rows, statusFilter) {
        if (statusFilter == FILTER_ALL) rows
        else rows.filter { it.status.equals(statusFilter, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.screen_my_bookings)) },
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
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        item {
                            Text(
                                stringResource(R.string.my_bookings_filter_by_status),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                STATUS_FILTERS.forEach { key ->
                                    FilterChip(
                                        selected = statusFilter == key,
                                        onClick = { statusFilter = key },
                                        label = { Text(statusFilterLabel(key)) },
                                    )
                                }
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    if (rows.isEmpty()) {
                                        stringResource(R.string.my_bookings_empty_all)
                                    } else {
                                        stringResource(R.string.my_bookings_empty_filter)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { booking ->
                                BookingCard(
                                    booking = booking,
                                    onOpenDetail = { selectedBooking = booking },
                                    onOpenChat = {
                                        innerNav.navigate(InnerRoutes.businessChatOpen("dive_center", booking.diveCenterId))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        selectedBooking?.let { sel ->
            MyBookingDetailBottomSheet(
                booking = sel,
                onDismiss = { selectedBooking = null },
                onOpenChat = {
                    innerNav.navigate(InnerRoutes.businessChatOpen("dive_center", sel.diveCenterId))
                },
            )
        }
    }
}

@Composable
private fun statusFilterLabel(key: String): String = when (key) {
    FILTER_ALL -> stringResource(R.string.admin_bookings_status_all)
    "pending" -> stringResource(R.string.admin_bookings_status_pending)
    "quoted" -> stringResource(R.string.my_bookings_status_quoted)
    "confirmed" -> stringResource(R.string.admin_bookings_status_confirmed)
    "completed" -> stringResource(R.string.admin_bookings_status_completed)
    "cancelled" -> stringResource(R.string.admin_bookings_status_cancelled)
    "refunded" -> stringResource(R.string.my_bookings_status_refunded)
    else -> key
}

@Composable
private fun BookingCard(
    booking: UserBookingDto,
    onOpenDetail: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val statusColor = when (booking.status.lowercase(Locale.ROOT)) {
        "pending" -> MaterialTheme.colorScheme.tertiary
        "quoted" -> MaterialTheme.colorScheme.secondary
        "confirmed" -> MaterialTheme.colorScheme.primary
        "completed" -> MaterialTheme.colorScheme.primaryContainer
        "cancelled", "refunded" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val amount = booking.payment?.amount
    val currency = booking.payment?.currency?.takeIf { it.isNotBlank() } ?: "USD"
    val amountLine = if (amount != null) {
        stringResource(R.string.my_bookings_amount_line, amount, currency)
    } else {
        stringResource(R.string.my_bookings_amount_unknown)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDetail),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatBookingDate(booking.date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        booking.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(amountLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (booking.manualVerifiedPriceFromNotes() != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.my_bookings_sheet_verified),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                booking.serviceId.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.my_bookings_service_id, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.my_bookings_open_chat))
            }
        }
    }
}

private fun formatBookingDate(raw: String): String =
    runCatching {
        OffsetDateTime.parse(raw).toLocalDate().toString()
    }.getOrElse {
        raw.take(16).ifBlank { raw }
    }
