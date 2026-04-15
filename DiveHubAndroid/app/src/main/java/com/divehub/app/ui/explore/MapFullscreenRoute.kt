package com.divehub.app.ui.explore

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.divehub.app.data.remote.dto.ExploreItemKind
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

private suspend fun AppGraph.loadDiveSitesForMap(): List<ExploreDiveSite> {
    val lang = tokenStore.getAppLanguageTag().ifBlank { "en" }
    return ExploreRepository(this).getDiveSites(language = lang, page = 1, limit = 200)
}

/** Full-screen map (iOS `MapTabView`-style entry from Explore). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapFullscreenRoute(graph: AppGraph, innerNav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sites by remember { mutableStateOf<List<ExploreDiveSite>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var mapActions by remember { mutableStateOf<ExploreMapActions?>(null) }
    var pendingCenterOnUser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        loadError = null
        runCatching { graph.loadDiveSitesForMap() }
            .onSuccess {
                sites = it
                loading = false
            }
            .onFailure { e ->
                loadError = e.message ?: context.getString(R.string.common_error)
                loading = false
            }
    }

    LaunchedEffect(Unit) {
        graph.tokenStore.appLanguageTagFlow
            .drop(1)
            .distinctUntilChanged()
            .collect {
                runCatching { graph.loadDiveSitesForMap() }
                    .onSuccess { list -> sites = list }
                    .onFailure { e -> loadError = e.message }
            }
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

    Scaffold(
        containerColor = IosDesign.Explore.listBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.explore_map_fullscreen_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .clipToBounds()
                .background(IosDesign.Explore.listBackground),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                loadError != null -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(loadError ?: "", color = MaterialTheme.colorScheme.error)
                    TextButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                loadError = null
                                runCatching { graph.loadDiveSitesForMap() }
                                    .onSuccess {
                                        sites = it
                                        loading = false
                                    }
                                    .onFailure { e ->
                                        loadError = e.message
                                        loading = false
                                    }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
                else -> {
                    ExploreMapOsm(
                        sites = sites,
                        onSiteTap = { site ->
                            when (site.kind) {
                                ExploreItemKind.DIVE_SITE ->
                                    innerNav.navigate(
                                        InnerRoutes.bookingWizard(centerId = null, siteId = site.id, instructorId = null),
                                    )
                                ExploreItemKind.DIVE_CENTER ->
                                    innerNav.navigate(InnerRoutes.diveCenterPublic(site.id))
                                ExploreItemKind.SHOP ->
                                    innerNav.navigate(InnerRoutes.shopPublic(site.id))
                            }
                        },
                        onActionsReady = { mapActions = it },
                    )
                    MapFullscreenControls(
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
    }
}

@Composable
private fun MapFullscreenControls(
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
