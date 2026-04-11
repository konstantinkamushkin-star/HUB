package com.divehub.app.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.remote.dto.DiveCenterBriefDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.remote.dto.ShopV1DetailDto
import com.divehub.app.data.repository.ShopRepository
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.ui.navigation.AppShellKind
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.Routes
import com.divehub.app.ui.trips.TripsListTabContent
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    val title = when (kind) {
        AppShellKind.ADMIN -> stringResource(R.string.partner_portal_admin_title)
        AppShellKind.SHOP -> stringResource(R.string.partner_portal_shop_title)
        AppShellKind.INSTRUCTOR -> stringResource(R.string.partner_portal_instructor_title)
        AppShellKind.DIVER -> stringResource(R.string.app_name)
    }
    val secondTabLabel = if (kind == AppShellKind.INSTRUCTOR) {
        stringResource(R.string.partner_tab_schedule)
    } else {
        stringResource(R.string.partner_tab_trips)
    }
    val showCreateTripFab = kind == AppShellKind.ADMIN || kind == AppShellKind.INSTRUCTOR

    Scaffold(
        containerColor = IosScreenBg,
        contentColor = Color.Black,
        bottomBar = {
            NavigationBar {
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
                    label = { Text(secondTabLabel) },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = { Text(stringResource(R.string.partner_tab_alerts)) },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                    label = { Text(stringResource(R.string.partner_tab_more)) },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                0 -> {
                    when (kind) {
                        AppShellKind.INSTRUCTOR -> InstructorHomeTab(
                            portalTitle = title,
                            user = user,
                            onOpenSchedule = { tab = 1 },
                        )
                        AppShellKind.ADMIN -> AdminHomeTab(
                            graph = graph,
                            portalTitle = title,
                            user = user,
                            onOpenTrips = { tab = 1 },
                            onCreateTrip = { innerNav.navigate(InnerRoutes.TripCreate) },
                            onOpenCenterInstructors = { c ->
                                innerNav.navigate(InnerRoutes.centerInstructors(c.id))
                            },
                            onOpenCenterTrips = { c ->
                                innerNav.navigate(InnerRoutes.centerTrips(c.id))
                            },
                        )
                        AppShellKind.SHOP -> ShopHomeTab(
                            graph = graph,
                            shopId = user?.shopId,
                            portalTitle = title,
                            user = user,
                            onOpenTrips = { tab = 1 },
                        )
                        AppShellKind.DIVER -> PartnerDashboardTab(
                            portalTitle = title,
                            user = user,
                        )
                    }
                }
                1 -> TripsListTabContent(
                    graph = graph,
                    innerNav = innerNav,
                    showCreateFab = showCreateTripFab,
                    onCreateTrip = { innerNav.navigate(InnerRoutes.TripCreate) },
                )
                2 -> PartnerAlertsTab(innerNav = innerNav)
                3 -> PartnerMoreTab(
                    graph = graph,
                    innerNav = innerNav,
                    rootNav = rootNav,
                    sessionVm = sessionVm,
                    onLoggedOut = onLoggedOut,
                )
            }
        }
    }
}

@Composable
private fun InstructorHomeTab(
    portalTitle: String,
    user: UserDto?,
    onOpenSchedule: () -> Unit,
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
    }
}

@Composable
private fun AdminHomeTab(
    graph: AppGraph,
    portalTitle: String,
    user: UserDto?,
    onOpenTrips: () -> Unit,
    onCreateTrip: () -> Unit,
    onOpenCenterInstructors: (DiveCenterBriefDto) -> Unit,
    onOpenCenterTrips: (DiveCenterBriefDto) -> Unit,
) {
    var loadGen by remember { mutableIntStateOf(0) }
    var managed by remember { mutableStateOf<List<DiveCenterBriefDto>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loadGen) {
        managed = null
        loadError = null
        runCatching { TripsRepository(graph).listManagedDiveCenters() }
            .onSuccess { managed = it }
            .onFailure { e ->
                loadError = e.message
                managed = emptyList()
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
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val list = managed!!
                val show = list.take(8)
                show.forEach { c ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            c.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
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
                if (list.size > show.size) {
                    Text(
                        stringResource(R.string.admin_centers_and_more, list.size - show.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
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
            Text(stringResource(R.string.admin_open_trips))
        }
        OutlinedButton(
            onClick = onCreateTrip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.trip_create_title))
        }
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
private fun PartnerAlertsTab(innerNav: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.partner_alerts_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { innerNav.navigate(InnerRoutes.Notifications) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.partner_open_notifications))
        }
    }
}

@Composable
private fun PartnerMoreTab(
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.partner_quick_links),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedButton(
            onClick = { innerNav.navigate(InnerRoutes.TripCreate) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.trip_create_title))
        }
        OutlinedButton(
            onClick = { innerNav.navigate(InnerRoutes.Settings) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.screen_settings))
        }
        OutlinedButton(
            onClick = { innerNav.navigate(InnerRoutes.Help) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.screen_help))
        }
        OutlinedButton(
            onClick = { rootNav.navigate(Routes.PartnerRegistration) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.help_partner_application_button))
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val base = graph.tokenStore.getRootBaseUrl()
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(base)))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.partner_open_website))
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    sessionVm.logout()
                    onLoggedOut()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.profile_logout))
        }
    }
}
