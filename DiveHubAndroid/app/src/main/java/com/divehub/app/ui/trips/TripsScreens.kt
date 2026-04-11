package com.divehub.app.ui.trips

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.GsonBuilder
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.data.remote.dto.TripListItemDto
import com.divehub.app.data.remote.dto.participantUserRows
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.util.absoluteMediaUrl
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsRoute(graph: AppGraph, innerNav: NavController) {
    val vm: TripsListViewModel = viewModel(factory = TripsListViewModel.factory(graph))
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_trips)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.trips.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.trips.isEmpty() -> Column(
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
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.trips, key = { it.id }) { trip ->
                    TripListCard(trip = trip, onClick = {
                        innerNav.navigate(InnerRoutes.tripDetail(trip.id))
                    })
                }
            }
        }
    }
}

/** Trips where `organizer_id` matches a managed dive center (admin). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterManagedTripsRoute(
    graph: AppGraph,
    centerId: String,
    innerNav: NavController,
) {
    var centerName by remember { mutableStateOf<String?>(null) }
    var tripPendingDelete by remember { mutableStateOf<TripListItemDto?>(null) }
    var deleteInProgress by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(centerId) {
        runCatching { TripsRepository(graph).listManagedDiveCenters() }
            .onSuccess { list -> centerName = list.find { it.id == centerId }?.name }
    }
    val vm: TripsListViewModel = viewModel(
        key = "center_trips_$centerId",
        factory = TripsListViewModel.factory(graph, organizerId = centerId),
    )
    val state by vm.state.collectAsState()

    tripPendingDelete?.let { t ->
        val booked = t.bookedSpots ?: 0
        AlertDialog(
            onDismissRequest = { if (!deleteInProgress) tripPendingDelete = null },
            title = { Text(stringResource(R.string.trip_delete_confirm_title)) },
            text = {
                Column {
                    if (booked > 0) {
                        Text(stringResource(R.string.trip_delete_blocked_bookings))
                    } else {
                        Text(stringResource(R.string.trip_delete_confirm_body))
                    }
                    deleteError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteError = null
                        deleteInProgress = true
                        vm.deleteTrip(t.id) { err ->
                            deleteInProgress = false
                            if (err == null) {
                                tripPendingDelete = null
                            } else {
                                deleteError = err.message?.takeIf { it.isNotBlank() } ?: "Error"
                            }
                        }
                    },
                    enabled = !deleteInProgress && booked == 0,
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { tripPendingDelete = null },
                    enabled = !deleteInProgress,
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.admin_center_trips_title))
                        centerName?.let { n ->
                            Text(
                                n,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.trips.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.trips.isEmpty() -> Column(
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
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.trips, key = { it.id }) { trip ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            TripListCard(trip = trip, onClick = {
                                innerNav.navigate(InnerRoutes.tripDetail(trip.id))
                            })
                        }
                        IconButton(
                            onClick = { tripPendingDelete = trip },
                            enabled = !deleteInProgress,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.trip_delete_cd),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Embedded list for partner shell tab (no back arrow). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsListTabContent(
    graph: AppGraph,
    innerNav: NavController,
    showCreateFab: Boolean = false,
    onCreateTrip: () -> Unit = {},
) {
    val vm: TripsListViewModel = viewModel(
        key = "partner_trips_tab",
        factory = TripsListViewModel.factory(graph),
    )
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_trips)) })
        },
        floatingActionButton = {
            if (showCreateFab) {
                FloatingActionButton(onClick = onCreateTrip) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.trip_create_cd_fab),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.loading && state.trips.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.trips.isEmpty() -> Column(
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
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.trips, key = { it.id }) { trip ->
                    TripListCard(trip = trip, onClick = {
                        innerNav.navigate(InnerRoutes.tripDetail(trip.id))
                    })
                }
            }
        }
    }
}

@Composable
private fun TripListCard(trip: TripListItemDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            val title = listOfNotNull(trip.region, trip.country).joinToString(", ").ifBlank { trip.tripType ?: trip.id }
            Text(title, style = MaterialTheme.typography.titleMedium)
            trip.startDate?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trip.description?.take(120)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailRoute(graph: AppGraph, tripId: String, innerNav: NavController) {
    val vm: TripDetailViewModel = viewModel(key = tripId, factory = TripDetailViewModel.factory(graph, tripId))
    val state by vm.state.collectAsState()
    var showJoinConfirm by remember { mutableStateOf(false) }
    val prettyGson = remember { GsonBuilder().setPrettyPrinting().create() }

    state.joinSuccessMessage?.let { ratio ->
        AlertDialog(
            onDismissRequest = { vm.clearJoinFeedback() },
            title = { Text(stringResource(R.string.trip_join_success_title)) },
            text = { Text(stringResource(R.string.trip_join_success_body, ratio)) },
            confirmButton = {
                TextButton(onClick = { vm.clearJoinFeedback() }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    state.joinError?.let { err ->
        val msg = when (err) {
            "login_required" -> stringResource(R.string.trip_join_need_login)
            "no_spots" -> stringResource(R.string.trip_join_no_spots)
            else -> err
        }
        AlertDialog(
            onDismissRequest = { vm.clearJoinFeedback() },
            title = { Text(stringResource(R.string.trip_join_error_title)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { vm.clearJoinFeedback() }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    if (showJoinConfirm) {
        AlertDialog(
            onDismissRequest = { showJoinConfirm = false },
            title = { Text(stringResource(R.string.trip_join_confirm_title)) },
            text = { Text(stringResource(R.string.trip_join_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showJoinConfirm = false
                        vm.joinTrip()
                    },
                    enabled = !state.joinInProgress,
                ) {
                    Text(stringResource(R.string.trip_join_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trip_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state.canManageTrip) {
                        TextButton(
                            onClick = { innerNav.navigate(InnerRoutes.tripEdit(tripId)) },
                        ) {
                            Text(stringResource(R.string.trip_manage_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error ?: stringResource(R.string.common_error))
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.load() }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
            state.trip != null -> {
                val t = state.trip!!
                val participantRows = t.participantUserRows()
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Text(
                        listOfNotNull(t.region, t.country).joinToString(", ").ifBlank { t.tripType ?: t.id },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    t.hotelLabel?.takeIf { it.isNotBlank() }?.let { h ->
                        Text(
                            stringResource(R.string.trip_detail_hotel, h),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    t.yachtLabel?.takeIf { it.isNotBlank() }?.let { y ->
                        Text(
                            stringResource(R.string.trip_detail_yacht, y),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    t.startDate?.let { Text(stringResource(R.string.trip_dates, it, t.endDate ?: "—")) }
                    Spacer(Modifier.height(12.dp))
                    t.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    Spacer(Modifier.height(16.dp))
                    val spots = t.totalSpots ?: 0
                    val booked = t.bookedSpots ?: 0
                    if (spots > 0) {
                        Text(
                            stringResource(R.string.trip_spots_format, booked, spots),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    t.minimumCertificationLevel?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.trip_min_cert, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    t.minimumDives?.takeIf { it > 0 }?.let { md ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.trip_detail_min_dives, md),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val yesStr = stringResource(R.string.trip_detail_yes)
                    val noStr = stringResource(R.string.trip_detail_no)
                    Text(
                        stringResource(
                            R.string.trip_detail_nitrox_line,
                            if (t.nitroxAvailable == true) yesStr else noStr,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(
                            R.string.trip_detail_equipment_line,
                            if (t.equipmentRentalAvailable == true) yesStr else noStr,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val isDaily = t.tripType?.equals("daily", ignoreCase = true) == true
                    val pd = t.priceDetails
                    val roomRows = parseRoomPrices(pd)
                    val cabinRows = parseCabinPrices(pd)
                    val rootDiv = pd.rootDivingPrice()
                    val rootNon = pd.rootNonDivingPrice()
                    val hasStructuredPrices =
                        (isDaily && roomRows.isNotEmpty()) || (!isDaily && cabinRows.isNotEmpty())
                    val hasRootPrices = rootDiv != null || rootNon != null
                    if (hasStructuredPrices || hasRootPrices) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.trip_detail_section_prices),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.trip_detail_prices_currency, priceDetailsCurrency(pd)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        when {
                            isDaily && roomRows.isNotEmpty() ->
                                roomRows.forEach { r ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(r.roomType, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                stringResource(
                                                    R.string.trip_create_room_line,
                                                    r.roomCount,
                                                    formatTripMoney(r.divingPrice),
                                                    formatTripMoney(r.nonDivingPrice),
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            !isDaily && cabinRows.isNotEmpty() ->
                                cabinRows.forEach { c ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(c.cabinType, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                stringResource(
                                                    R.string.trip_create_cabin_line,
                                                    c.cabinCount,
                                                    formatTripMoney(c.divingPrice),
                                                    formatTripMoney(c.nonDivingPrice),
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            hasRootPrices -> {
                                val dStr = rootDiv?.let { formatTripMoney(it) } ?: "—"
                                val nStr = rootNon?.let { formatTripMoney(it) } ?: "—"
                                Text(
                                    stringResource(R.string.trip_detail_simple_prices, dStr, nStr),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    val courseIds = t.availableCourses.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
                    if (courseIds.isNotEmpty() || state.loadingCourseNames) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.trip_detail_section_courses),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.loadingCourseNames) {
                            Spacer(Modifier.height(6.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Spacer(Modifier.height(6.dp))
                            courseIds.forEach { cid ->
                                val label = state.courseLabels[cid] ?: cid
                                Text(
                                    "· $label",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                    t.programDays?.takeIf { it.size() > 0 }?.let { arr ->
                        val programParsed = parseProgramDaysFromJson(arr)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.trip_detail_section_program),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (programParsed.isNotEmpty()) {
                            programParsed.forEach { day ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(day.dateYmd, style = MaterialTheme.typography.titleSmall)
                                        if (day.description.isNotBlank()) {
                                            Text(
                                                day.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.height(6.dp))
                                        }
                                        day.activities.forEach { act ->
                                            Text(
                                                stringResource(
                                                    R.string.trip_detail_program_activity_line,
                                                    act.time.ifBlank { "—" },
                                                    act.activity.ifBlank { "—" },
                                                ),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            act.notes?.takeIf { it.isNotBlank() }?.let { n ->
                                                Text(
                                                    n,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Text(
                                    prettyGson.toJson(arr),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                    val expenseRows = parseTripAdditionalExpenseRows(t.additionalExpenses)
                    if (expenseRows.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.trip_detail_section_expenses),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        expenseRows.forEach { row ->
                            val money = buildString {
                                row.cost?.let { append(formatTripMoney(it)) }
                                if (row.currency.isNotBlank()) {
                                    if (isNotEmpty()) append(' ')
                                    append(row.currency)
                                }
                            }.ifBlank { "—" }
                            Text(
                                stringResource(
                                    R.string.trip_detail_expense_row,
                                    row.expenseType.ifBlank { "—" },
                                    row.description.ifBlank { "—" },
                                    money,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    val photoUrls = t.photos.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
                    if (photoUrls.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.trip_detail_section_photos),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(photoUrls) { raw ->
                                val url = absoluteMediaUrl(state.imageApiRoot, raw)
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .width(168.dp)
                                        .height(112.dp),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                    if (participantRows.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.trip_participants_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.trip_participants_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.participantsNamesLoading) {
                            Spacer(Modifier.height(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(22.dp)
                                    .padding(vertical = 4.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        participantRows.forEach { (userId, joinedAt) ->
                            val display = state.participantDisplayNames[userId] ?: userId
                            val showIdUnderName = display != userId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { innerNav.navigate(InnerRoutes.userProfile(userId)) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        display,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (showIdUnderName) {
                                        Text(
                                            userId,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.trip_participant_profile_cta),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    joinedAt?.takeIf { it.isNotBlank() }?.let { ja ->
                                        Text(
                                            stringResource(R.string.trip_participant_joined_at, ja),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val canJoin = state.loggedIn && spots > 0 && booked < spots
                    if (canJoin || state.loggedIn) {
                        Spacer(Modifier.height(24.dp))
                        if (state.joinInProgress) {
                            CircularProgressIndicator(Modifier.padding(vertical = 8.dp))
                        } else if (canJoin) {
                            Button(
                                onClick = { showJoinConfirm = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.trip_join_reserve_button))
                            }
                        } else if (state.loggedIn && spots > 0 && booked >= spots) {
                            Text(
                                stringResource(R.string.trip_join_full),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.trip_join_login_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun formatTripMoney(v: Double): String =
    if (kotlin.math.abs(v - v.toLong().toDouble()) < 1e-9) {
        v.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", v)
    }
