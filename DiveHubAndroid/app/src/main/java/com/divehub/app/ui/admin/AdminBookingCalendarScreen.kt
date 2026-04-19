package com.divehub.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min

private enum class BookingCalendarMode { CALENDAR, LIST }

data class AdminBookingCalendarUiState(
    val loading: Boolean = true,
    val bookings: List<AdminBookingLocal> = emptyList(),
    val error: String? = null,
)

class AdminBookingCalendarViewModel(
    private val repo: AdminBookingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminBookingCalendarUiState())
    val state: StateFlow<AdminBookingCalendarUiState> = _state.asStateFlow()

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
                bookings = list.sortedByDescending { it.date + it.startTime },
            )
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdminBookingCalendarViewModel(AdminBookingsRepository(graph)) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBookingCalendarRoute(graph: AppGraph, innerNav: NavController) {
    val vm: AdminBookingCalendarViewModel = viewModel(factory = AdminBookingCalendarViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var mode by remember { mutableStateOf(BookingCalendarMode.CALENDAR) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var displayMonth by remember { mutableStateOf(YearMonth.from(LocalDate.now())) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    val bookingsByDate = remember(state.bookings) {
        state.bookings.mapNotNull { b ->
            val d = runCatching { LocalDate.parse(b.date) }.getOrNull() ?: return@mapNotNull null
            d to b
        }.groupBy({ it.first }, { it.second })
    }

    val listForDate = remember(state.bookings, selectedDate) {
        state.bookings.filter { runCatching { LocalDate.parse(it.date) }.getOrNull() == selectedDate }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_booking_calendar_title)) },
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = mode == BookingCalendarMode.CALENDAR, onClick = { mode = BookingCalendarMode.CALENDAR })
                                Text(stringResource(R.string.admin_booking_calendar_mode_calendar))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = mode == BookingCalendarMode.LIST, onClick = { mode = BookingCalendarMode.LIST })
                                Text(stringResource(R.string.admin_booking_calendar_mode_list))
                            }
                        }
                    }
                    if (mode == BookingCalendarMode.CALENDAR) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                                    Text(stringResource(R.string.admin_booking_calendar_prev_month))
                                }
                                Text(
                                    displayMonth.format(
                                        DateTimeFormatter.ofPattern("LLLL uuuu", locale),
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                TextButton(onClick = { displayMonth = displayMonth.plusMonths(1) }) {
                                    Text(stringResource(R.string.admin_booking_calendar_next_month))
                                }
                            }
                        }
                        item {
                            BookingMonthHeatmap(
                                yearMonth = displayMonth,
                                bookingsByDate = bookingsByDate,
                                selectedDate = selectedDate,
                                locale = locale,
                                onSelectDay = { day ->
                                    selectedDate = day
                                    displayMonth = YearMonth.from(day)
                                },
                            )
                        }
                        item {
                            Text(
                                stringResource(R.string.admin_booking_calendar_heatmap_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text(stringResource(R.string.admin_booking_calendar_selected_date), fontWeight = FontWeight.SemiBold)
                                    Text(selectedDate.toString(), style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { showDatePicker = true }) {
                                        Text(stringResource(R.string.admin_booking_calendar_pick_date))
                                    }
                                }
                            }
                        }
                        item {
                            Text(stringResource(R.string.admin_booking_calendar_for_date), style = MaterialTheme.typography.titleSmall)
                        }
                        if (listForDate.isEmpty()) {
                            item { Text(stringResource(R.string.admin_booking_calendar_no_bookings)) }
                        } else {
                            items(listForDate, key = { it.id }) { booking ->
                                BookingCalendarRow(booking)
                            }
                        }
                    } else {
                        if (state.bookings.isEmpty()) {
                            item { Text(stringResource(R.string.admin_booking_calendar_no_bookings)) }
                        } else {
                            items(state.bookings, key = { it.id }) { booking ->
                                BookingCalendarRow(booking)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        selectedDate = picked
                        displayMonth = YearMonth.from(picked)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private val HeatmapDowOrder: List<DayOfWeek> = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

@Composable
private fun BookingMonthHeatmap(
    yearMonth: YearMonth,
    bookingsByDate: Map<LocalDate, List<AdminBookingLocal>>,
    selectedDate: LocalDate,
    locale: Locale,
    onSelectDay: (LocalDate) -> Unit,
) {
    val first = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val offset = (first.dayOfWeek.value + 6) % 7
    val numWeeks = (offset + daysInMonth + 6) / 7
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                HeatmapDowOrder.forEach { d ->
                    Text(
                        d.getDisplayName(TextStyle.NARROW, locale),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            for (week in 0 until numWeeks) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (dow in 0 until 7) {
                        val dayIndex = week * 7 + dow - offset + 1
                        if (dayIndex in 1..daysInMonth) {
                            val date = yearMonth.atDay(dayIndex)
                            val dayBookings = bookingsByDate[date].orEmpty()
                            MonthDayCell(
                                dayNum = dayIndex,
                                count = dayBookings.size,
                                dominantStatus = dominantBookingStatus(dayBookings),
                                selected = date == selectedDate,
                                onClick = { onSelectDay(date) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

private fun dominantBookingStatus(list: List<AdminBookingLocal>): String? {
    if (list.isEmpty()) return null
    val order = listOf("pending", "confirmed", "completed", "cancelled")
    for (s in order) {
        if (list.any { it.status.equals(s, ignoreCase = true) }) return s
    }
    return list.first().status
}

@Composable
private fun MonthDayCell(
    dayNum: Int,
    count: Int,
    dominantStatus: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val intensity = when {
        count <= 0 -> 0.08f
        count == 1 -> 0.22f
        count == 2 -> 0.35f
        else -> min(0.25f + count * 0.1f, 0.88f)
    }
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = intensity)
    val borderWidth = if (selected) 2.dp else 0.dp
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .background(fill, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dayNum.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            if (count > 0) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            dominantStatus?.let {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(bookingStatusHeatColor(it)),
                )
            }
        }
    }
}

private fun bookingStatusHeatColor(status: String): Color = when (status.lowercase()) {
    "pending" -> Color(0xFFF57C00)
    "confirmed" -> Color(0xFF1565C0)
    "completed" -> Color(0xFF2E7D32)
    "cancelled" -> Color(0xFFC62828)
    else -> Color(0xFF757575)
}

@Composable
private fun BookingCalendarRow(booking: AdminBookingLocal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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

