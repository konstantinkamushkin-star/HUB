package com.divehub.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AdminAffiliatedSitesRepository
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.remote.dto.DiveCenterItemDto
import com.divehub.app.data.repository.TripsRepository
import com.divehub.app.ui.main.SessionViewModel
import com.divehub.app.ui.navigation.InnerRoutes
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiveCenterAdminProfileRoute(
    graph: AppGraph,
    innerNav: NavController,
    sessionVm: SessionViewModel,
) {
    val user by sessionVm.user.collectAsState()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var center by remember { mutableStateOf<DiveCenterItemDto?>(null) }
    var instructorsCount by remember { mutableStateOf(0) }
    var affiliatedSitesCount by remember { mutableStateOf(0) }
    var tripsCount by remember { mutableStateOf(0) }

    LaunchedEffect(user?.diveCenterId) {
        val centerId = user?.diveCenterId?.trim().orEmpty()
        if (centerId.isBlank()) {
            loading = false
            error = graph.application.getString(R.string.dive_center_admin_no_center)
            center = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching {
            val repo = ExploreRepository(graph)
            val loadedCenter = repo.getDiveCenterById(centerId)
            val instructors = repo.listDiveCenterInstructors(centerId)
            val sites = AdminAffiliatedSitesRepository(graph).getCenterSites(centerId)
            val trips = runCatching { TripsRepository(graph).listTrips(organizerId = centerId) }.getOrElse { emptyList() }
            LoadedCenterAdminData(
                center = loadedCenter,
                instructors = instructors.size,
                affiliatedSites = sites.size,
                trips = trips.size,
            )
        }.onSuccess { loaded ->
            center = loaded.center
            instructorsCount = loaded.instructors
            affiliatedSitesCount = loaded.affiliatedSites
            tripsCount = loaded.trips
            loading = false
            if (loaded.center == null) {
                error = graph.application.getString(R.string.dive_center_admin_not_found)
            }
        }.onFailure { e ->
            loading = false
            error = e.message ?: graph.application.getString(R.string.common_error)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dive_center_admin_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null && center == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { innerNav.popBackStack() }) {
                        Text(stringResource(R.string.common_back))
                    }
                }
            }
            center != null -> {
                val c = center!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    val loc = listOfNotNull(c.city?.trim(), c.country?.trim()).filter { it.isNotEmpty() }.joinToString(", ")
                    if (loc.isNotBlank()) {
                        Text(loc, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    c.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.dive_center_admin_contact_section), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.dive_center_admin_contact_unavailable))

                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.dive_center_admin_stats_section), fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null)
                        Text(stringResource(R.string.dive_center_admin_rating_value, c.averageRating ?: 0.0))
                    }
                    Text(stringResource(R.string.dive_center_admin_reviews_value, c.reviewCount ?: 0))
                    Text(stringResource(R.string.dive_center_admin_instructors_value, instructorsCount))
                    Text(stringResource(R.string.dive_center_admin_affiliated_sites_value, affiliatedSitesCount))
                    Text(stringResource(R.string.dive_center_admin_trips_value, tripsCount))
                    Text(
                        stringResource(
                            R.string.dive_center_admin_nitrox_value,
                            if (c.nitroxAvailable == true) {
                                stringResource(R.string.dive_center_admin_yes)
                            } else {
                                stringResource(R.string.dive_center_admin_no)
                            },
                        ),
                    )
                    c.priceFrom?.let {
                        Text(
                            stringResource(
                                R.string.dive_center_admin_price_from_value,
                                "$" + String.format(Locale.US, "%.2f", it),
                            ),
                        )
                    }
                    val services = c.services.orEmpty().filter { it.isNotBlank() }
                    if (services.isNotEmpty()) {
                        Text(stringResource(R.string.dive_center_admin_services_count, services.size))
                    }
                    val photosCount = c.photos?.size ?: 0
                    if (photosCount > 0) {
                        Text(stringResource(R.string.dive_center_admin_photos_count, photosCount))
                    }

                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { innerNav.navigate(InnerRoutes.centerInstructors(c.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.dive_center_admin_manage_instructors))
                    }
                    OutlinedButton(
                        onClick = { innerNav.navigate(InnerRoutes.AdminAffiliatedSites) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.dive_center_admin_manage_sites))
                    }
                    OutlinedButton(
                        onClick = { innerNav.navigate(InnerRoutes.AdminBookingCalendar) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.admin_booking_calendar_title))
                    }
                    OutlinedButton(
                        onClick = { innerNav.navigate(InnerRoutes.AdminBookingManagement) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.admin_bookings_title))
                    }
                    OutlinedButton(
                        onClick = { innerNav.navigate(InnerRoutes.Inventory) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.inventory_title))
                    }
                    OutlinedButton(
                        onClick = { innerNav.navigate(InnerRoutes.AdminGearManagement) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.admin_gear_title))
                    }
                    OutlinedButton(
                        onClick = { innerNav.navigate(InnerRoutes.diveCenterPublic(c.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.dive_center_public_open_profile))
                    }
                    OutlinedButton(
                        onClick = {
                            sessionVm.setPreferDiverShell(false)
                            innerNav.navigate(InnerRoutes.Home) { launchSingleTop = true }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.dive_center_admin_open_dashboard))
                    }
                }
            }
        }
    }
}

private data class LoadedCenterAdminData(
    val center: DiveCenterItemDto?,
    val instructors: Int,
    val affiliatedSites: Int,
    val trips: Int,
)

