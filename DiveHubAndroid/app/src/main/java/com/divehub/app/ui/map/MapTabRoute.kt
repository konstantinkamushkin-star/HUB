package com.divehub.app.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ExploreRepository
import com.divehub.app.data.remote.dto.ExploreDiveSite
import com.divehub.app.ui.explore.ExploreMapActions
import com.divehub.app.ui.explore.ExploreMapOsm
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.theme.IosDesign
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun getLastKnownLocation(context: Context): Pair<Double, Double>? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
    val best = providers.mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.accuracy }
    return best?.let { it.latitude to it.longitude }
}

private suspend fun AppGraph.loadDiveSitesForMapTab(): List<ExploreDiveSite> {
    val lang = tokenStore.getAppLanguageTag().ifBlank { "en" }
    return ExploreRepository(this).getDiveSites(language = lang, page = 1, limit = 200)
}

private fun applyFilters(
    sites: List<ExploreDiveSite>,
    selectedDiveType: String?,
    selectedDifficulty: String?,
): List<ExploreDiveSite> = sites.filter { site ->
    val typeOk = selectedDiveType?.let { site.diveType.equals(it, ignoreCase = true) } ?: true
    val difficultyOk = selectedDifficulty?.let { site.difficulty.equals(it, ignoreCase = true) } ?: true
    typeOk && difficultyOk
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTabRoute(graph: AppGraph, innerNav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allSites by remember { mutableStateOf<List<ExploreDiveSite>>(emptyList()) }
    var filteredSites by remember { mutableStateOf<List<ExploreDiveSite>>(emptyList()) }
    var selectedDiveType by remember { mutableStateOf<String?>(null) }
    var selectedDifficulty by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var mapActions by remember { mutableStateOf<ExploreMapActions?>(null) }
    var pendingCenterOnUser by remember { mutableStateOf(false) }

    fun applyCurrentFilters() {
        filteredSites = applyFilters(allSites, selectedDiveType = selectedDiveType, selectedDifficulty = selectedDifficulty)
    }

    suspend fun reloadData() {
        loading = true
        loadError = null
        runCatching { graph.loadDiveSitesForMapTab() }
            .onSuccess {
                allSites = it
                applyCurrentFilters()
                loading = false
            }
            .onFailure { e ->
                loadError = e.message ?: context.getString(R.string.common_error)
                loading = false
            }
    }

    LaunchedEffect(Unit) { reloadData() }

    LaunchedEffect(selectedDiveType, selectedDifficulty) {
        applyCurrentFilters()
    }

    LaunchedEffect(Unit) {
        graph.tokenStore.appLanguageTagFlow
            .drop(1)
            .distinctUntilChanged()
            .collect { reloadData() }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && pendingCenterOnUser) {
            pendingCenterOnUser = false
            getLastKnownLocation(context)?.let { (lat, lng) ->
                mapActions?.centerOn?.invoke(lat, lng)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(IosDesign.Explore.listBackground),
    ) {
        when {
            loading && allSites.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            loadError != null && allSites.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(loadError ?: "", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { scope.launch { reloadData() } }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
            else -> {
                ExploreMapOsm(
                    sites = filteredSites,
                    onSiteTap = { site ->
                        innerNav.navigate(
                            InnerRoutes.bookingWizard(centerId = null, siteId = site.id, instructorId = null),
                        )
                    },
                    onActionsReady = { mapActions = it },
                )
                MapTabControls(
                    onOpenFilters = { showFilters = true },
                    onZoomIn = { mapActions?.zoomIn?.invoke() },
                    onZoomOut = { mapActions?.zoomOut?.invoke() },
                    onCenterOnUser = {
                        if (hasLocationPermission(context)) {
                            getLastKnownLocation(context)?.let { (lat, lng) ->
                                mapActions?.centerOn?.invoke(lat, lng)
                            }
                        } else {
                            pendingCenterOnUser = true
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            MapFilterSheet(
                selectedDiveType = selectedDiveType,
                selectedDifficulty = selectedDifficulty,
                onSelectDiveType = { selectedDiveType = it },
                onSelectDifficulty = { selectedDifficulty = it },
                onReset = {
                    selectedDiveType = null
                    selectedDifficulty = null
                },
                onClose = { showFilters = false },
            )
        }
    }
}

@Composable
private fun MapTabControls(
    onOpenFilters: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onCenterOnUser: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        IconButton(
            onClick = onOpenFilters,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White)
                .shadow(3.dp, CircleShape),
        ) {
            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.map_filter_title), tint = IosDesign.Explore.mapAccent)
        }
        Column(horizontalAlignment = Alignment.End) {
            Column(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .shadow(3.dp, RoundedCornerShape(22.dp)),
            ) {
                IconButton(onClick = onZoomIn, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Add, null, tint = IosDesign.Explore.mapAccent)
                }
                HorizontalDivider(thickness = 1.dp, color = Color(0x22000000))
                IconButton(onClick = onZoomOut, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Remove, null, tint = IosDesign.Explore.mapAccent)
                }
            }
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(52.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onCenterOnUser),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = null,
                    tint = IosDesign.Explore.mapAccent,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun MapFilterSheet(
    selectedDiveType: String?,
    selectedDifficulty: String?,
    onSelectDiveType: (String?) -> Unit,
    onSelectDifficulty: (String?) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    val types = listOf(
        stringResource(R.string.explore_reef),
        stringResource(R.string.explore_wreck),
        stringResource(R.string.explore_cave),
    )
    val levels = listOf(
        stringResource(R.string.explore_beginner),
        stringResource(R.string.explore_intermediate),
        stringResource(R.string.explore_advanced),
        stringResource(R.string.explore_expert),
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.map_filter_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.map_filter_types), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 8.dp)) {
            MapFilterChip(
                label = stringResource(R.string.explore_all_types),
                selected = selectedDiveType == null,
                onClick = { onSelectDiveType(null) },
            )
        }
        Row(Modifier.padding(top = 8.dp)) {
            types.forEach { t ->
                MapFilterChip(
                    label = t,
                    selected = selectedDiveType == t,
                    onClick = { onSelectDiveType(if (selectedDiveType == t) null else t) },
                )
            }
        }
        Text(stringResource(R.string.map_filter_levels), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 8.dp)) {
            MapFilterChip(
                label = stringResource(R.string.explore_all_levels),
                selected = selectedDifficulty == null,
                onClick = { onSelectDifficulty(null) },
            )
        }
        Row(Modifier.padding(top = 8.dp)) {
            levels.forEach { level ->
                MapFilterChip(
                    label = level,
                    selected = selectedDifficulty == level,
                    onClick = { onSelectDifficulty(if (selectedDifficulty == level) null else level) },
                )
            }
        }
        Row(
            Modifier
                .padding(top = 16.dp)
                .align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onReset) { Text(stringResource(R.string.map_filter_reset)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.common_close)) }
        }
    }
}

@Composable
private fun MapFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) IosDesign.Explore.mapAccent else Color(0xFFF3F5F8),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .padding(end = 8.dp, bottom = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF1F2328),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
