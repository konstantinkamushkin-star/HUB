package com.divehub.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AdminBookingsRepository
import com.divehub.app.data.remote.dto.AdminBookingLocal
import com.divehub.app.data.remote.dto.TripListItemDto
import com.divehub.app.data.repository.TripsRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DiveHubBlue = Color(0xFF0080CC)

@Composable
fun AdminHomeDashboardIos(
    config: AdminIosHomeConfig,
    graph: AppGraph,
    loadGen: Int,
    onOpenCustomize: () -> Unit,
    onNavigateToShellTab: (String) -> Unit,
    onOpenTripDetail: (String) -> Unit,
    onOpenTrips: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var displayMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var trips by remember { mutableStateOf<List<TripListItemDto>>(emptyList()) }
    var adminBookings by remember { mutableStateOf<List<AdminBookingLocal>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(loadGen) {
        loading = true
        runCatching { TripsRepository(graph).listTrips() }
            .onSuccess { trips = it }
        runCatching { AdminBookingsRepository(graph).syncFromRemoteWithFallback(null).first }
            .onSuccess { adminBookings = it }
        loading = false
    }

    val bookingsByDate = remember(adminBookings) {
        adminBookings.mapNotNull { b ->
            val d = runCatching { LocalDate.parse(b.date) }.getOrNull() ?: return@mapNotNull null
            d to b
        }.groupBy({ it.first }, { it.second })
    }

    val tripsForDay = remember(trips, selectedDate) {
        trips.filter { it.coversDateLocal(selectedDate) }
    }

    val monthTitle = remember(displayMonth, locale) {
        val raw = displayMonth.format(DateTimeFormatter.ofPattern("LLLL uuuu", locale))
        val t = raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        if (locale.language.equals("ru", ignoreCase = true)) {
            "${t}г."
        } else {
            t
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.admin_home_nav_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = onOpenCustomize,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.admin_dashboard_customize),
                    tint = DiveHubBlue,
                )
            }
        }

        if (loading && trips.isEmpty() && adminBookings.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = DiveHubBlue,
                    strokeWidth = 2.dp,
                )
            }
        }

        val order = config.blockOrder.filter { it == "quick" || it == "cal" }
            .ifEmpty { listOf("quick", "cal") }
        for (block in order) {
            when (block) {
                "quick" -> if (config.showQuick) {
                    AdminHomeQuickSection(
                        targets = config.quickActionTargets,
                        onTarget = { onNavigateToShellTab(it) },
                    )
                }
                "cal" -> if (config.showCal) {
                    AdminHomeCalendarSection(
                        monthTitle = monthTitle,
                        displayMonth = displayMonth,
                        onPrevMonth = { displayMonth = displayMonth.minusMonths(1) },
                        onNextMonth = { displayMonth = displayMonth.plusMonths(1) },
                        onSelectDay = { day ->
                            selectedDate = day
                            displayMonth = YearMonth.from(day)
                        },
                        selectedDate = selectedDate,
                        bookingsByDate = bookingsByDate,
                        locale = locale,
                        tripsForDay = tripsForDay,
                        onOpenTripDetail = onOpenTripDetail,
                        onViewAllTrips = onOpenTrips,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminHomeQuickSection(
    targets: List<String>,
    onTarget: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.admin_home_quick_actions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (targets.isEmpty()) {
            Text(
                stringResource(R.string.admin_home_no_quick_actions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        for (row in targets.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (t in row) {
                    QuickActionCard(
                        label = quickActionLabel(t),
                        icon = quickActionIcon(t),
                        onClick = { onTarget(t) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(108.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DiveHubBlue,
                modifier = Modifier.size(32.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun quickActionLabel(target: String): String = when (target.lowercase()) {
    "instructors" -> stringResource(R.string.admin_quick_action_instructors)
    "services" -> stringResource(R.string.admin_quick_action_services)
    "dashboard" -> stringResource(R.string.partner_tab_home)
    "explore" -> stringResource(R.string.nav_explore)
    "feed" -> stringResource(R.string.nav_feed)
    "courses" -> stringResource(R.string.partner_tab_courses)
    "trips" -> stringResource(R.string.partner_tab_trips)
    "photo" -> stringResource(R.string.partner_tab_photo)
    "chats" -> stringResource(R.string.partner_tab_chats)
    "profile" -> stringResource(R.string.profile_title)
    else -> target
}

@Composable
private fun AdminHomeCalendarSection(
    monthTitle: String,
    displayMonth: YearMonth,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    selectedDate: LocalDate,
    bookingsByDate: Map<LocalDate, List<AdminBookingLocal>>,
    locale: Locale,
    tripsForDay: List<TripListItemDto>,
    onOpenTripDetail: (String) -> Unit,
    onViewAllTrips: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.admin_home_calendar),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onPrevMonth) {
                Text("‹", color = DiveHubBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                monthTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onNextMonth) {
                Text("›", color = DiveHubBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        AdminBookingMonthGrid(
            yearMonth = displayMonth,
            bookingsByDate = bookingsByDate,
            selectedDate = selectedDate,
            locale = locale,
            onSelectDay = onSelectDay,
            weekStartsOnSunday = true,
            solidSelectedFill = true,
        )
        BookingStatusLegend()
        if (tripsForDay.isNotEmpty()) {
            Text(
                stringResource(R.string.nav_trips),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (tripsForDay.isEmpty()) {
            Text(
                stringResource(R.string.admin_home_no_trips_for_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        } else {
            for (t in tripsForDay.take(3)) {
                val title = listOfNotNull(t.region, t.country).joinToString(", ")
                    .ifBlank { t.tripType ?: t.id }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTripDetail(t.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (tripsForDay.size > 3) {
                TextButton(onClick = onViewAllTrips) {
                    Text(
                        stringResource(R.string.admin_home_view_all_trips),
                        color = DiveHubBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookingStatusLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        legendDot(stringResource(R.string.admin_bookings_status_pending), adminBookingStatusColor("pending"))
        legendDot(stringResource(R.string.admin_bookings_status_confirmed), adminBookingStatusColor("confirmed"))
        legendDot(stringResource(R.string.admin_bookings_status_completed), adminBookingStatusColor("completed"))
        legendDot(stringResource(R.string.admin_bookings_status_cancelled), adminBookingStatusColor("cancelled"))
    }
}

@Composable
private fun legendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun TripListItemDto.coversDateLocal(d: LocalDate): Boolean {
    val s = startDate?.let { LocalDate.parse(it) } ?: return false
    val e = endDate?.let { LocalDate.parse(it) } ?: s
    return !d.isBefore(s) && !d.isAfter(e)
}

private fun quickActionIcon(target: String): ImageVector = when (target.lowercase()) {
    "instructors" -> Icons.Filled.People
    "services" -> Icons.Filled.LocalOffer
    "explore" -> Icons.Filled.Search
    "feed" -> Icons.Filled.Newspaper
    "courses" -> Icons.AutoMirrored.Filled.MenuBook
    "trips" -> Icons.Filled.AirplanemodeActive
    "photo" -> Icons.Filled.PhotoLibrary
    "chats" -> Icons.Filled.Chat
    "profile" -> Icons.Filled.Person
    "dashboard" -> Icons.Filled.Home
    else -> Icons.Filled.Home
}
