package com.divehub.app.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.diveHubApp
import com.divehub.app.data.AdminBookingsRepository
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.InventoryRepository
import com.divehub.app.data.local.AdminDashboardLayout
import com.divehub.app.data.remote.dto.DiveCenterBriefDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.remote.dto.ShopV1DetailDto
import com.divehub.app.data.repository.ShopRepository
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.ui.admin.AdminWebPanelRoute
import com.divehub.app.ui.chat.ChatRoute
import com.divehub.app.ui.navigation.AppShellKind
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.Routes
import com.divehub.app.ui.partner.InstructorPhotoTab
import com.divehub.app.ui.partner.InstructorScheduleTab
import com.divehub.app.ui.partner.PartnerAnalyticsTab
import com.divehub.app.ui.partner.PartnerCoursesTab
import com.divehub.app.ui.partner.ShopSellTab
import com.divehub.app.ui.profile.ProfileScreen
import com.divehub.app.ui.trips.TripsListTabContent
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

private val IosScreenBg = Color(0xFFF2F2F7)

@Composable
fun PartnerAppShell(
    kind: AppShellKind,
    graph: AppGraph,
    innerNav: NavController,
    rootNav: NavController,
    sessionVm: SessionViewModel,
    onLoggedOut: () -> Unit,
) {
    val user by sessionVm.user.collectAsState()
    var tab by remember(kind) { mutableIntStateOf(0) }

    val title = when (kind) {
        AppShellKind.ADMIN -> stringResource(R.string.partner_portal_admin_title)
        AppShellKind.SHOP -> stringResource(R.string.partner_portal_shop_title)
        AppShellKind.INSTRUCTOR -> stringResource(R.string.partner_portal_instructor_title)
        AppShellKind.DIVER -> stringResource(R.string.app_name)
    }
    val isSuperAdmin = kind == AppShellKind.ADMIN && user?.role?.uppercase(Locale.ROOT) == "SUPER_ADMIN"
    val showCreateTripFab = kind == AppShellKind.ADMIN || kind == AppShellKind.INSTRUCTOR

    LaunchedEffect(isSuperAdmin) {
        if (isSuperAdmin && tab !in 0..1) tab = 0
        if (!isSuperAdmin && tab !in 0..5) tab = 0
    }

    val app = LocalContext.current.diveHubApp()
    LaunchedEffect(Unit) {
        app.innerNavDeepLinkRequests.collect { route ->
            innerNav.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = IosScreenBg,
        contentColor = Color.Black,
        bottomBar = {
            if (kind != AppShellKind.DIVER) {
                NavigationBar {
                when (kind) {
                    AppShellKind.ADMIN -> {
                        if (isSuperAdmin) {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Icon(Icons.Default.Language, contentDescription = null) },
                                label = { Text(stringResource(R.string.partner_tab_web_panel)) },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                                label = { Text(stringResource(R.string.profile_title)) },
                            )
                        } else {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text(stringResource(R.string.partner_tab_home)) },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                                label = { Text(stringResource(R.string.partner_tab_courses)) },
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                                label = { Text(stringResource(R.string.partner_tab_trips)) },
                            )
                            NavigationBarItem(
                                selected = tab == 3,
                                onClick = { tab = 3 },
                                icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                                label = { Text(stringResource(R.string.partner_tab_analytics)) },
                            )
                            NavigationBarItem(
                                selected = tab == 4,
                                onClick = { tab = 4 },
                                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                                label = { Text(stringResource(R.string.partner_tab_chats)) },
                            )
                            NavigationBarItem(
                                selected = tab == 5,
                                onClick = { tab = 5 },
                                icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                                label = { Text(stringResource(R.string.partner_tab_more)) },
                            )
                        }
                    }
                    AppShellKind.INSTRUCTOR -> {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text(stringResource(R.string.partner_tab_home)) },
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            label = { Text(stringResource(R.string.partner_tab_schedule)) },
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            label = { Text(stringResource(R.string.partner_tab_photo)) },
                        )
                        NavigationBarItem(
                            selected = tab == 3,
                            onClick = { tab = 3 },
                            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                            label = { Text(stringResource(R.string.partner_tab_more)) },
                        )
                    }
                    AppShellKind.SHOP -> {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text(stringResource(R.string.partner_tab_home)) },
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.Store, contentDescription = null) },
                            label = { Text(stringResource(R.string.shop_tab_store)) },
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            label = { Text(stringResource(R.string.shop_tab_sell)) },
                        )
                        NavigationBarItem(
                            selected = tab == 3,
                            onClick = { tab = 3 },
                            icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                            label = { Text(stringResource(R.string.partner_tab_analytics)) },
                        )
                        NavigationBarItem(
                            selected = tab == 4,
                            onClick = { tab = 4 },
                            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                            label = { Text(stringResource(R.string.partner_tab_more)) },
                        )
                    }
                    else -> Unit
                }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (kind) {
                AppShellKind.ADMIN -> {
                    if (isSuperAdmin) {
                        when (tab) {
                            0 -> AdminWebPanelRoute(graph = graph, innerNav = innerNav, user = user)
                            1 -> ProfileScreen(
                                graph = graph,
                                sessionVm = sessionVm,
                                user = user,
                                innerNav = innerNav,
                                rootNav = rootNav,
                                onLoggedOut = onLoggedOut,
                            )
                        }
                    } else {
                        when (tab) {
                            0 -> AdminHomeTab(
                                graph = graph,
                                sessionVm = sessionVm,
                                portalTitle = title,
                                user = user,
                                onOpenTrips = { tab = 2 },
                                onCreateTrip = { innerNav.navigate(InnerRoutes.TripCreate) },
                                onOpenBookings = { innerNav.navigate(InnerRoutes.AdminBookingManagement) },
                                onOpenCalendar = { innerNav.navigate(InnerRoutes.AdminBookingCalendar) },
                                onOpenInventory = { innerNav.navigate(InnerRoutes.Inventory) },
                                onOpenCenterInstructors = { c ->
                                    innerNav.navigate(InnerRoutes.centerInstructors(c.id))
                                },
                                onOpenCenterTrips = { c ->
                                    innerNav.navigate(InnerRoutes.centerTrips(c.id))
                                },
                            )
                            1 -> PartnerCoursesTab(graph = graph)
                            2 -> TripsListTabContent(
                                graph = graph,
                                innerNav = innerNav,
                                showCreateFab = showCreateTripFab,
                                onCreateTrip = { innerNav.navigate(InnerRoutes.TripCreate) },
                            )
                            3 -> PartnerAnalyticsTab(graph = graph)
                            4 -> ChatRoute(graph = graph)
                            5 -> PartnerMoreTab(
                                kind = kind,
                                graph = graph,
                                innerNav = innerNav,
                                rootNav = rootNav,
                                sessionVm = sessionVm,
                                onLoggedOut = onLoggedOut,
                            )
                        }
                    }
                }
                AppShellKind.INSTRUCTOR -> when (tab) {
                    0 -> InstructorHomeTab(
                        graph = graph,
                        innerNav = innerNav,
                        portalTitle = title,
                        user = user,
                        onOpenSchedule = { tab = 1 },
                        onOpenPhoto = { tab = 2 },
                    )
                    1 -> InstructorScheduleTab(graph = graph)
                    2 -> InstructorPhotoTab(innerNav = innerNav)
                    3 -> PartnerMoreTab(
                        kind = kind,
                        graph = graph,
                        innerNav = innerNav,
                        rootNav = rootNav,
                        sessionVm = sessionVm,
                        onLoggedOut = onLoggedOut,
                    )
                }
                AppShellKind.SHOP -> when (tab) {
                    0 -> ShopHomeTab(
                        graph = graph,
                        shopId = user?.shopId,
                        portalTitle = title,
                        user = user,
                        onOpenTrips = { tab = 2 },
                    )
                    1 -> ShopHomeTab(
                        graph = graph,
                        shopId = user?.shopId,
                        portalTitle = stringResource(R.string.shop_tab_store_title),
                        user = user,
                        onOpenTrips = { tab = 2 },
                    )
                    2 -> ShopSellTab(graph = graph, innerNav = innerNav)
                    3 -> PartnerAnalyticsTab(graph = graph)
                    4 -> PartnerMoreTab(
                        kind = kind,
                        graph = graph,
                        innerNav = innerNav,
                        rootNav = rootNav,
                        sessionVm = sessionVm,
                        onLoggedOut = onLoggedOut,
                    )
                }
                AppShellKind.DIVER -> PartnerDashboardTab(portalTitle = title, user = user)
            }
        }
    }
}

@Composable
private fun InstructorHomeTab(
    graph: AppGraph,
    innerNav: NavController,
    portalTitle: String,
    user: UserDto?,
    onOpenSchedule: () -> Unit,
    onOpenPhoto: () -> Unit,
) {
    var loadGen by remember { mutableIntStateOf(0) }
    var bookingsTotal by remember { mutableIntStateOf(0) }
    var bookingsPending by remember { mutableIntStateOf(0) }
    var bookingsUpcoming by remember { mutableIntStateOf(0) }
    var bookingsRevenue by remember { mutableStateOf(0.0) }

    LaunchedEffect(loadGen, user?.id) {
        runCatching { AdminBookingsRepository(graph).loadInstructorSchedule() }
            .onSuccess { rows ->
                bookingsTotal = rows.size
                bookingsPending = rows.count { it.status.equals("pending", ignoreCase = true) }
                val today = runCatching { LocalDate.now() }.getOrNull()
                bookingsUpcoming = if (today == null) {
                    0
                } else {
                    rows.count { b ->
                        val d = runCatching { LocalDate.parse(b.date) }.getOrNull() ?: return@count false
                        !d.isBefore(today) && !b.status.equals("cancelled", ignoreCase = true)
                    }
                }
                bookingsRevenue = rows.filter { it.status.equals("completed", ignoreCase = true) }.sumOf { it.amount }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(portalTitle, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.partner_portal_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        user?.let { u ->
            Spacer(Modifier.height(8.dp))
            Text(u.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            u.role?.let {
                Text(
                    stringResource(R.string.profile_role, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.instructor_dashboard_kpi_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(
                R.string.instructor_dashboard_kpi_bookings,
                bookingsTotal,
                bookingsPending,
                bookingsUpcoming,
                "$" + String.format(Locale.US, "%.2f", bookingsRevenue),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { loadGen++ }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.common_refresh))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.instructor_dashboard_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onOpenSchedule,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.instructor_open_schedule))
        }
        OutlinedButton(
            onClick = onOpenPhoto,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.instructor_open_photo_tab))
        }
        OutlinedButton(
            onClick = { innerNav.navigate(InnerRoutes.DiveEditor) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.instructor_open_dive_editor))
        }
        OutlinedButton(
            onClick = { innerNav.navigate(InnerRoutes.Notifications) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.instructor_open_notifications))
        }
    }
}

@Composable
private fun AdminHomeTab(
    graph: AppGraph,
    sessionVm: SessionViewModel,
    portalTitle: String,
    user: UserDto?,
    onOpenTrips: () -> Unit,
    onCreateTrip: () -> Unit,
    onOpenBookings: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenCenterInstructors: (DiveCenterBriefDto) -> Unit,
    onOpenCenterTrips: (DiveCenterBriefDto) -> Unit,
) {
    var loadGen by remember { mutableIntStateOf(0) }
    var managed by remember { mutableStateOf<List<DiveCenterBriefDto>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var bookingsCount by remember { mutableIntStateOf(0) }
    var bookingsPending by remember { mutableIntStateOf(0) }
    var bookingsRevenue by remember { mutableStateOf(0.0) }
    var inventoryCount by remember { mutableIntStateOf(0) }
    var inventoryMaintenance by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository(graph) }
    var showCustomize by remember { mutableStateOf(false) }

    val layout = AdminDashboardLayout.fromDiverProfile(user?.diverProfile)

    LaunchedEffect(loadGen) {
        managed = null
        loadError = null
        runCatching { TripsRepository(graph).listManagedDiveCenters() }
            .onSuccess { managed = it }
            .onFailure { e ->
                loadError = e.message
                managed = emptyList()
            }
        runCatching {
            val (rows, _) = AdminBookingsRepository(graph).syncFromRemoteWithFallback(null)
            bookingsCount = rows.size
            bookingsPending = rows.count { it.status.equals("pending", ignoreCase = true) }
            bookingsRevenue = rows.filter { it.status.equals("completed", ignoreCase = true) }.sumOf { it.amount }
        }
        runCatching { InventoryRepository(graph).loadItems() }
            .onSuccess { items ->
                inventoryCount = items.size
                inventoryMaintenance = items.count { it.status.equals("maintenance", ignoreCase = true) }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(portalTitle, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.partner_portal_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        user?.let { u ->
            Spacer(Modifier.height(8.dp))
            Text(u.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            u.role?.let {
                Text(
                    stringResource(R.string.profile_role, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { loadGen++ }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.admin_dashboard_refresh_cd),
                )
            }
            TextButton(onClick = { showCustomize = true }) {
                Text(stringResource(R.string.admin_dashboard_customize))
            }
        }
        var firstHomeSection = true
        for (sid in layout.normalizedSectionOrder()) {
            if (!layout.showsSection(sid)) continue
            if (!firstHomeSection) {
                Spacer(Modifier.height(8.dp))
            }
            firstHomeSection = false
            when (sid) {
                AdminDashboardLayout.KEY_MANAGED -> {
                    Text(
                        stringResource(R.string.admin_your_centers),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.admin_centers_actions_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        managed == null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                stringResource(R.string.admin_centers_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        loadError != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                loadError ?: stringResource(R.string.common_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { loadGen++ }) {
                                Text(stringResource(R.string.common_retry))
                            }
                        }
                        managed!!.isEmpty() -> Text(
                            stringResource(R.string.admin_centers_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val list = managed!!
                            val show = list.take(8)
                            show.forEach { c ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                    ) {
                                        Text(
                                            c.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = { onOpenCenterInstructors(c) }) {
                                                Text(stringResource(R.string.admin_center_instructors_link))
                                            }
                                            TextButton(onClick = { onOpenCenterTrips(c) }) {
                                                Text(stringResource(R.string.admin_center_trips_link))
                                            }
                                        }
                                    }
                                }
                            }
                            if (list.size > show.size) {
                                Text(
                                    stringResource(R.string.admin_centers_and_more, list.size - show.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                AdminDashboardLayout.KEY_KPI -> {
                    Text(
                        stringResource(R.string.admin_dashboard_kpi_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    AdminKpiGrid(
                        bookingsCount = bookingsCount,
                        bookingsPending = bookingsPending,
                        bookingsRevenue = "$" + String.format(java.util.Locale.US, "%.2f", bookingsRevenue),
                        inventoryCount = inventoryCount,
                        inventoryMaintenance = inventoryMaintenance,
                    )
                }
                AdminDashboardLayout.KEY_BOOKINGS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onOpenBookings,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.admin_bookings_title),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenCalendar,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.Event,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.admin_booking_calendar_title),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                            )
                        }
                    }
                }
                AdminDashboardLayout.KEY_INVENTORY -> {
                    FilledTonalButton(
                        onClick = onOpenInventory,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.inventory_title))
                    }
                }
                AdminDashboardLayout.KEY_TRIPS -> {
                    Text(
                        stringResource(R.string.admin_dashboard_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenTrips,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.admin_open_trips))
                    }
                    OutlinedButton(
                        onClick = onCreateTrip,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.trip_create_title))
                    }
                }
            }
        }
    }

    if (showCustomize) {
        AlertDialog(
            onDismissRequest = { showCustomize = false },
            title = { Text(stringResource(R.string.admin_dashboard_customize)) },
            text = {
                val orderScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(orderScroll),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.admin_dashboard_customize_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.admin_dashboard_section_order_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val ord = layout.normalizedSectionOrder()
                    ord.forEachIndexed { index, sid ->
                        val label = when (sid) {
                            AdminDashboardLayout.KEY_MANAGED -> stringResource(R.string.admin_dashboard_opt_managed)
                            AdminDashboardLayout.KEY_KPI -> stringResource(R.string.admin_dashboard_opt_kpi)
                            AdminDashboardLayout.KEY_BOOKINGS -> stringResource(R.string.admin_dashboard_opt_bookings)
                            AdminDashboardLayout.KEY_INVENTORY -> stringResource(R.string.admin_dashboard_opt_inventory)
                            AdminDashboardLayout.KEY_TRIPS -> stringResource(R.string.admin_dashboard_opt_trips)
                            else -> sid
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                            )
                            Row {
                                TextButton(
                                    enabled = index > 0,
                                    onClick = {
                                        val list = layout.normalizedSectionOrder().toMutableList()
                                        val t = list[index]
                                        list[index] = list[index - 1]
                                        list[index - 1] = t
                                        scope.launch {
                                            runCatching {
                                                authRepo.patchAdminDashboardLayout(layout.copy(sectionOrder = list))
                                            }.onSuccess { sessionVm.onUserUpdated(it) }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.admin_dashboard_move_up))
                                }
                                TextButton(
                                    enabled = index < ord.lastIndex,
                                    onClick = {
                                        val list = layout.normalizedSectionOrder().toMutableList()
                                        val t = list[index]
                                        list[index] = list[index + 1]
                                        list[index + 1] = t
                                        scope.launch {
                                            runCatching {
                                                authRepo.patchAdminDashboardLayout(layout.copy(sectionOrder = list))
                                            }.onSuccess { sessionVm.onUserUpdated(it) }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.admin_dashboard_move_down))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.admin_dashboard_help_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.admin_dashboard_help_centers_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.admin_dashboard_help_center_instructors),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.admin_dashboard_help_center_trips),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.admin_dashboard_help_bookings_btn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.admin_dashboard_help_calendar_btn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.admin_dashboard_help_inventory_btn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.admin_dashboard_help_trips_btns),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.partner_quick_links),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AdminDashboardToggleRow(
                        label = stringResource(R.string.admin_dashboard_opt_managed),
                        checked = layout.showManagedCenters,
                        onCheckedChange = { v ->
                            scope.launch {
                                runCatching {
                                    authRepo.patchAdminDashboardLayout(layout.copy(showManagedCenters = v))
                                }.onSuccess { sessionVm.onUserUpdated(it) }
                            }
                        },
                    )
                    AdminDashboardToggleRow(
                        label = stringResource(R.string.admin_dashboard_opt_kpi),
                        checked = layout.showKpis,
                        onCheckedChange = { v ->
                            scope.launch {
                                runCatching {
                                    authRepo.patchAdminDashboardLayout(layout.copy(showKpis = v))
                                }.onSuccess { sessionVm.onUserUpdated(it) }
                            }
                        },
                    )
                    AdminDashboardToggleRow(
                        label = stringResource(R.string.admin_dashboard_opt_bookings),
                        checked = layout.showBookingShortcuts,
                        onCheckedChange = { v ->
                            scope.launch {
                                runCatching {
                                    authRepo.patchAdminDashboardLayout(layout.copy(showBookingShortcuts = v))
                                }.onSuccess { sessionVm.onUserUpdated(it) }
                            }
                        },
                    )
                    AdminDashboardToggleRow(
                        label = stringResource(R.string.admin_dashboard_opt_inventory),
                        checked = layout.showInventoryButton,
                        onCheckedChange = { v ->
                            scope.launch {
                                runCatching {
                                    authRepo.patchAdminDashboardLayout(layout.copy(showInventoryButton = v))
                                }.onSuccess { sessionVm.onUserUpdated(it) }
                            }
                        },
                    )
                    AdminDashboardToggleRow(
                        label = stringResource(R.string.admin_dashboard_opt_trips),
                        checked = layout.showTripsSection,
                        onCheckedChange = { v ->
                            scope.launch {
                                runCatching {
                                    authRepo.patchAdminDashboardLayout(layout.copy(showTripsSection = v))
                                }.onSuccess { sessionVm.onUserUpdated(it) }
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomize = false }) {
                    Text(stringResource(R.string.common_done))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { authRepo.resetAdminDashboardLayout() }
                                .onSuccess { sessionVm.onUserUpdated(it) }
                        }
                    },
                ) {
                    Text(stringResource(R.string.admin_dashboard_reset))
                }
            },
        )
    }
}

@Composable
private fun AdminDashboardToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ShopHomeTab(
    graph: AppGraph,
    shopId: String?,
    portalTitle: String,
    user: UserDto?,
    onOpenTrips: () -> Unit,
) {
    var loadGen by remember { mutableIntStateOf(0) }
    var shop by remember { mutableStateOf<ShopV1DetailDto?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    LaunchedEffect(shopId, loadGen) {
        when {
            shopId.isNullOrBlank() -> {
                shop = null
                loadError = null
                loading = false
            }
            else -> {
                loading = true
                loadError = null
                shop = null
                runCatching { ShopRepository(graph).getShop(shopId) }
                    .onSuccess {
                        shop = it
                        loading = false
                    }
                    .onFailure { e ->
                        loadError = e.message
                        loading = false
                    }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(portalTitle, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.shop_portal_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        user?.let { u ->
            Spacer(Modifier.height(8.dp))
            Text(u.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            u.role?.let {
                Text(
                    stringResource(R.string.profile_role, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.shop_my_store_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        when {
            shopId.isNullOrBlank() -> Text(
                stringResource(R.string.shop_no_shop_id),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            loading && shop == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.shop_loading_profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            loadError != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    loadError ?: stringResource(R.string.common_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { loadGen++ }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
            shop != null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val s = shop!!
                Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                listOfNotNull(s.city, s.country).joinToString(", ").takeIf { it.isNotBlank() }?.let { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.address?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val rating = s.averageRating
                val reviews = s.reviewCount
                if (rating != null && rating > 0.0) {
                    Text(
                        stringResource(R.string.shop_rating_line, rating, reviews ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                s.description?.take(200)?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                s.website?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val href = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(href)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.shop_open_website))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.shop_dashboard_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onOpenTrips,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.shop_open_trips))
        }
    }
}

@Composable
private fun PartnerDashboardTab(
    portalTitle: String,
    user: UserDto?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(portalTitle, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.partner_portal_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        user?.let { u ->
            Spacer(Modifier.height(8.dp))
            Text(u.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            u.role?.let {
                Text(
                    stringResource(R.string.profile_role, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.partner_dashboard_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PartnerMoreTab(
    kind: AppShellKind,
    graph: AppGraph,
    innerNav: NavController,
    rootNav: NavController,
    sessionVm: SessionViewModel,
    onLoggedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(R.string.partner_quick_links),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.partner_portal_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PartnerMoreSection(title = stringResource(R.string.partner_more_section_tools)) {
            MoreLinkRow(
                label = stringResource(R.string.screen_notifications),
                icon = Icons.Default.Notifications,
                onClick = { innerNav.navigate(InnerRoutes.Notifications) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            MoreLinkRow(
                label = stringResource(R.string.partner_photo_open_editor),
                icon = Icons.Default.PhotoCamera,
                onClick = { innerNav.navigate(InnerRoutes.DiveEditor) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            MoreLinkRow(
                label = stringResource(R.string.profile_edit_title),
                icon = Icons.Default.Person,
                onClick = { innerNav.navigate(InnerRoutes.EditProfile) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            MoreLinkRow(
                label = stringResource(R.string.screen_statistics),
                icon = Icons.Default.BarChart,
                onClick = { innerNav.navigate(InnerRoutes.Statistics) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            MoreLinkRow(
                label = stringResource(R.string.trip_create_title),
                icon = Icons.Default.Add,
                onClick = { innerNav.navigate(InnerRoutes.TripCreate) },
            )
        }

        if (kind == AppShellKind.ADMIN) {
            PartnerMoreSection(title = stringResource(R.string.partner_more_section_admin)) {
                MoreLinkRow(
                    label = stringResource(R.string.admin_web_panel_title),
                    icon = Icons.Default.Language,
                    onClick = { innerNav.navigate(InnerRoutes.AdminWebPanel) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                MoreLinkRow(
                    label = stringResource(R.string.inventory_title),
                    icon = Icons.Default.Inventory2,
                    onClick = { innerNav.navigate(InnerRoutes.Inventory) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                MoreLinkRow(
                    label = stringResource(R.string.admin_booking_calendar_title),
                    icon = Icons.Default.DateRange,
                    onClick = { innerNav.navigate(InnerRoutes.AdminBookingCalendar) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                MoreLinkRow(
                    label = stringResource(R.string.admin_bookings_title),
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    onClick = { innerNav.navigate(InnerRoutes.AdminBookingManagement) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                MoreLinkRow(
                    label = stringResource(R.string.admin_affiliated_sites_title),
                    icon = Icons.Default.Link,
                    onClick = { innerNav.navigate(InnerRoutes.AdminAffiliatedSites) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                MoreLinkRow(
                    label = stringResource(R.string.admin_shops_title),
                    icon = Icons.Default.Store,
                    onClick = { innerNav.navigate(InnerRoutes.AdminShopsManagement) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                MoreLinkRow(
                    label = stringResource(R.string.admin_gear_title),
                    icon = Icons.Default.Build,
                    onClick = { innerNav.navigate(InnerRoutes.AdminGearManagement) },
                )
            }
        }

        PartnerMoreSection(title = stringResource(R.string.partner_more_section_account)) {
            MoreLinkRow(
                label = stringResource(R.string.screen_settings),
                icon = Icons.Default.Settings,
                onClick = { innerNav.navigate(InnerRoutes.Settings) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            MoreLinkRow(
                label = stringResource(R.string.screen_help),
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                onClick = { innerNav.navigate(InnerRoutes.Help) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            MoreLinkRow(
                label = stringResource(R.string.help_partner_application_button),
                icon = Icons.Default.Person,
                onClick = { rootNav.navigate(Routes.PartnerRegistration) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            MoreLinkRow(
                label = stringResource(R.string.partner_open_website),
                icon = Icons.Default.Public,
                onClick = {
                    scope.launch {
                        val base = graph.tokenStore.getRootBaseUrl()
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(base)))
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    sessionVm.logout()
                    onLoggedOut()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.profile_logout))
        }
    }
}

@Composable
private fun PartnerMoreSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun MoreLinkRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun AdminKpiGrid(
    bookingsCount: Int,
    bookingsPending: Int,
    bookingsRevenue: String,
    inventoryCount: Int,
    inventoryMaintenance: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdminStatCard(
                label = stringResource(R.string.admin_stat_bookings_total),
                value = bookingsCount.toString(),
                modifier = Modifier.weight(1f),
            )
            AdminStatCard(
                label = stringResource(R.string.admin_stat_pending),
                value = bookingsPending.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdminStatCard(
                label = stringResource(R.string.admin_stat_revenue),
                value = bookingsRevenue,
                modifier = Modifier.weight(1f),
            )
            AdminStatCard(
                label = stringResource(R.string.admin_stat_inventory),
                value = inventoryCount.toString(),
                subtitle = stringResource(R.string.admin_stat_inventory_maint, inventoryMaintenance),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AdminStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            subtitle?.let { line ->
                Spacer(Modifier.height(4.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
