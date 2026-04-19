package com.divehub.app.ui.partner

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AdminBookingsRepository
import com.divehub.app.data.remote.dto.AdminBookingLocal
import com.divehub.app.ui.theme.IosDesign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorScheduleTab(graph: AppGraph) {
    val vm: InstructorScheduleViewModel = viewModel(factory = InstructorScheduleViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var mode by remember { mutableIntStateOf(0) } // 0 calendar, 1 list
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Picker)
    var selectedMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val selectedDate = remember(selectedMillis) {
        Instant.ofEpochMilli(selectedMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    val dateLabel = remember(selectedMillis) {
        selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    val forDay = remember(state.bookings, selectedDate) {
        state.bookings.filter { runCatching { LocalDate.parse(it.date) }.getOrNull() == selectedDate }
    }

    when {
        state.loading && state.bookings.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        state.error != null && state.bookings.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(IosDesign.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(state.error ?: stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.common_retry)) }
        }
        else -> PullToRefreshBox(
            isRefreshing = state.loading && state.bookings.isNotEmpty(),
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(IosDesign.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.partner_schedule_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh_list))
                        }
                    }
                    Text(
                        stringResource(R.string.partner_schedule_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = mode == 0,
                            onClick = { mode = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text(stringResource(R.string.partner_schedule_mode_calendar)) }
                        SegmentedButton(
                            selected = mode == 1,
                            onClick = { mode = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text(stringResource(R.string.partner_schedule_mode_list)) }
                    }
                }
                item {
                    TextButton(onClick = { showPicker = true }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.partner_schedule_pick_date, dateLabel))
                    }
                    if (mode == 0) {
                        Text(
                            stringResource(R.string.partner_schedule_calendar_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (mode == 0) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.partner_schedule_for_day),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (forDay.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.partner_schedule_no_bookings),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(forDay, key = { it.id }) { booking ->
                            InstructorBookingRow(booking)
                        }
                    }
                } else {
                    if (state.bookings.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.partner_schedule_no_bookings),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(state.bookings, key = { it.id }) { booking ->
                            InstructorBookingRow(booking)
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedMillis = it }
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private data class InstructorScheduleUiState(
    val loading: Boolean = true,
    val bookings: List<AdminBookingLocal> = emptyList(),
    val error: String? = null,
)

private class InstructorScheduleViewModel(
    private val repo: AdminBookingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InstructorScheduleUiState())
    val state: StateFlow<InstructorScheduleUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null)
            runCatching { repo.loadInstructorSchedule() }
                .onSuccess { list ->
                    _state.value = prev.copy(
                        loading = false,
                        error = null,
                        bookings = list.sortedByDescending { it.date + it.startTime },
                    )
                }
                .onFailure { e ->
                    _state.value = prev.copy(loading = false, error = e.message ?: "Error")
                }
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return InstructorScheduleViewModel(AdminBookingsRepository(graph)) as T
            }
        }
    }
}

@Composable
private fun InstructorBookingRow(booking: AdminBookingLocal) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(booking.date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(booking.startTime, style = MaterialTheme.typography.bodySmall)
                Text("Center: ${booking.diveCenterId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val amount = runCatching { "$${"%.2f".format(booking.amount)}" }.getOrElse { "$0.00" }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    booking.status,
                    color = when (booking.status.lowercase()) {
                        "pending" -> Color(0xFFF57C00)
                        "confirmed" -> Color(0xFF1565C0)
                        "completed" -> Color(0xFF2E7D32)
                        "cancelled" -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
