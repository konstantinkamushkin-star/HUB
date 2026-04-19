package com.divehub.app.ui.admin

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AdminBookingsRepository
import com.divehub.app.data.remote.dto.AdminBookingLocal
import com.divehub.app.ui.navigation.InnerRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

private const val BOOKING_FILTER_ALL = "all"

data class AdminBookingsUiState(
    val loading: Boolean = true,
    val bookings: List<AdminBookingLocal> = emptyList(),
    val error: String? = null,
)

class AdminBookingsViewModel(
    private val repo: AdminBookingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminBookingsUiState())
    val state: StateFlow<AdminBookingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            val (list, err) = repo.syncFromRemoteWithFallback(null)
            _state.value = _state.value.copy(
                loading = false,
                error = err,
                bookings = list.sortedByDescending { it.createdAt },
            )
        }
    }

    fun setStatus(id: String, status: String) {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            runCatching { repo.updateBookingStatusRemote(id, status) }
                .onFailure { e ->
                    _state.value = prev.copy(loading = false, error = e.message ?: "Error")
                }
                .onSuccess {
                    refresh()
                }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdminBookingsViewModel(AdminBookingsRepository(graph)) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBookingManagementRoute(graph: AppGraph, innerNav: NavController) {
    val vm: AdminBookingsViewModel = viewModel(factory = AdminBookingsViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var selected by remember { mutableStateOf<AdminBookingLocal?>(null) }
    var statusFilter by remember { mutableStateOf(BOOKING_FILTER_ALL) }
    var query by remember { mutableStateOf("") }
    val visible = remember(state.bookings, statusFilter, query) {
        val q = query.trim().lowercase()
        state.bookings.filter { b ->
            val passStatus = statusFilter == BOOKING_FILTER_ALL || b.status.equals(statusFilter, ignoreCase = true)
            val passQuery = q.isBlank() || listOf(
                b.id,
                b.diveCenterId,
                b.serviceId,
                b.date,
            ).joinToString(" ").lowercase().contains(q)
            passStatus && passQuery
        }
    }
    val total = visible.size
    val pending = visible.count { it.status.equals("pending", ignoreCase = true) }
    val confirmed = visible.count { it.status.equals("confirmed", ignoreCase = true) }
    val revenue = visible.filter { it.status.equals("completed", ignoreCase = true) }.sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_bookings_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh_list))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.bookings.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null && state.bookings.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.common_retry)) }
            }
            else -> PullToRefreshBox(
                isRefreshing = state.loading && state.bookings.isNotEmpty(),
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.admin_bookings_search_label)) },
                            singleLine = true,
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                BOOKING_FILTER_ALL,
                                "pending",
                                "confirmed",
                                "completed",
                                "cancelled",
                            ).forEach { key ->
                                FilterChip(
                                    selected = statusFilter == key,
                                    onClick = { statusFilter = key },
                                    label = {
                                        Text(
                                            when (key) {
                                                BOOKING_FILTER_ALL -> stringResource(R.string.admin_bookings_status_all)
                                                "pending" -> stringResource(R.string.admin_bookings_status_pending)
                                                "confirmed" -> stringResource(R.string.admin_bookings_status_confirmed)
                                                "completed" -> stringResource(R.string.admin_bookings_status_completed)
                                                else -> stringResource(R.string.admin_bookings_status_cancelled)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            stringResource(
                                R.string.admin_bookings_kpi_line,
                                total,
                                pending,
                                confirmed,
                                "$" + String.format(Locale.US, "%.2f", revenue),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.error != null) {
                        item {
                            Text(
                                state.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (visible.isEmpty()) {
                        item { Text(stringResource(R.string.admin_bookings_empty)) }
                    } else {
                        items(visible, key = { it.id }) { booking ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                onClick = { selected = booking },
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(booking.date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${booking.startTime} • ${booking.participantsCount}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            "Center: ${booking.diveCenterId}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        booking.status.uppercase(Locale.US),
                                        color = bookingStatusColor(booking.status),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .align(Alignment.CenterVertically),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { booking ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.admin_bookings_detail_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("ID: ${booking.id}", style = MaterialTheme.typography.bodySmall)
                Text("Service: ${booking.serviceId}", style = MaterialTheme.typography.bodySmall)
                Text("Date: ${booking.date} ${booking.startTime}", style = MaterialTheme.typography.bodySmall)
                Text("Participants: ${booking.participantsCount}", style = MaterialTheme.typography.bodySmall)
                Text("Amount: $${"%.2f".format(booking.amount)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.setStatus(booking.id, "pending") }) {
                        Text(stringResource(R.string.admin_bookings_action_pending))
                    }
                    TextButton(onClick = { vm.setStatus(booking.id, "confirmed") }) {
                        Text(stringResource(R.string.admin_bookings_action_confirm))
                    }
                    TextButton(onClick = { vm.setStatus(booking.id, "completed") }) {
                        Text(stringResource(R.string.admin_bookings_action_complete))
                    }
                    TextButton(onClick = { vm.setStatus(booking.id, "cancelled") }) {
                        Text(stringResource(R.string.admin_bookings_action_cancel))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { innerNav.navigate(InnerRoutes.centerTrips(booking.diveCenterId)) },
                    ) {
                        Text(stringResource(R.string.admin_bookings_open_center_trips))
                    }
                    TextButton(onClick = { selected = null }) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun bookingStatusColor(status: String): Color = when {
    status.equals("pending", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
    status.equals("confirmed", ignoreCase = true) -> MaterialTheme.colorScheme.primary
    status.equals("completed", ignoreCase = true) -> Color(0xFF2E7D32)
    status.equals("cancelled", ignoreCase = true) -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

