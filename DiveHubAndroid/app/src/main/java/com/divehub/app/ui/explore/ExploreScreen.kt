package com.divehub.app.ui.explore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.data.ReviewsRepository
import com.divehub.app.data.remote.dto.ExploreDiveSite
import com.divehub.app.data.remote.dto.ExploreItemKind
import com.divehub.app.data.remote.dto.ReviewDto
import com.divehub.app.ui.components.DiveCenterPromoCard
import com.divehub.app.ui.components.DiveHubLogoMark
import com.divehub.app.ui.reviews.AddReviewableDialog
import com.divehub.app.ui.theme.IosDesign
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(p1) * cos(p2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun formatDistanceMeters(m: Double): String =
    if (m < 1000) "%.0f m".format(m) else "%.1f km".format(m / 1000.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreRoute(graph: AppGraph, innerNav: NavController) {
    val vm: ExploreViewModel = viewModel(factory = ExploreViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var selectedSite by remember { mutableStateOf<ExploreDiveSite?>(null) }
    var mapActions by remember { mutableStateOf<ExploreMapActions?>(null) }
    var pendingCenterOnUser by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var mapFocusLatLngZoom by remember { mutableStateOf<Triple<Double, Double, Double>?>(null) }
    var contributionMode by remember { mutableStateOf<DiveSiteContributionMode?>(null) }
    val loggedIn by produceState(initialValue = false) {
        graph.tokenStore.accessToken.collect { value = !it.isNullOrBlank() }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var userGeo by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(context) {
        if (hasLocationPermission(context)) {
            userGeo = getLastKnownLocation(context)
        }
    }

    LaunchedEffect(graph.tokenStore) {
        graph.tokenStore.appLanguageTagFlow
            .drop(1)
            .distinctUntilChanged()
            .collect { vm.refresh() }
    }

    LaunchedEffect(mapFocusLatLngZoom, mapActions, state.viewMode) {
        val t = mapFocusLatLngZoom ?: return@LaunchedEffect
        if (state.viewMode != ExploreViewMode.MAP) return@LaunchedEffect
        val ma = mapActions ?: return@LaunchedEffect
        delay(280)
        ma.centerOnZoom(t.first, t.second, t.third)
        mapFocusLatLngZoom = null
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && pendingCenterOnUser) {
            pendingCenterOnUser = false
            getLastKnownLocation(context)?.let { (lat, lng) ->
                userGeo = lat to lng
                mapActions?.centerOn?.invoke(lat, lng)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosDesign.Explore.pageBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(IosDesign.Explore.pageBackground),
        ) {
            ExploreHeader(
                onGlobalSearch = { innerNav.navigate(InnerRoutes.Search) },
                onSortTap = { showSortSheet = true },
                onFilterTap = { showFilterSheet = true },
                onFullscreenMap = { innerNav.navigate(InnerRoutes.MapFullscreen) },
                onSuggestNewSite = if (loggedIn) {
                    { contributionMode = DiveSiteContributionMode.NewSite }
                } else {
                    null
                },
            )
            CategoryToggle(state.selectedCategory, onCategory = vm::setCategory)
            SearchBar(state.searchQuery, onChange = vm::setSearch)
            QuickFilters(
                category = state.selectedCategory,
                selectedDiveType = state.selectedDiveType,
                selectedDifficulty = state.selectedDifficulty,
                onDiveType = vm::setDiveTypeFilter,
                onDifficulty = vm::setDifficultyFilter,
            )
            ViewModeToggle(state.viewMode, onMode = vm::setViewMode)
        }

        when {
            state.loading && state.allSites.isEmpty() -> Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(IosDesign.Explore.listBackground),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null && state.allSites.isEmpty() -> Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(IosDesign.Explore.listBackground),
            ) {
                ErrorView(state.error ?: stringResource(R.string.common_error), onRetry = vm::refresh)
            }
            state.viewMode == ExploreViewMode.LIST -> PullToRefreshBox(
                isRefreshing = state.loading && state.allSites.isNotEmpty(),
                onRefresh = { vm.refresh() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                ExploreList(
                    sites = state.filteredSites,
                    userLatLng = userGeo,
                    onTap = { selectedSite = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(IosDesign.Explore.listBackground),
            ) {
                ExploreMapOsm(
                    sites = state.filteredSites,
                    onSiteTap = { selectedSite = it },
                    onActionsReady = { mapActions = it },
                )
                MapControls(
                    onZoomIn = { mapActions?.zoomIn?.invoke() },
                    onZoomOut = { mapActions?.zoomOut?.invoke() },
                    onCenterOnUser = {
                        if (hasLocationPermission(context)) {
                            getLastKnownLocation(context)?.let { (lat, lng) ->
                                userGeo = lat to lng
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

    if (selectedSite != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedSite = null },
        ) {
            val site = selectedSite!!
            DiveSiteDetailSheet(
                site = site,
                graph = graph,
                onReviewSubmitted = { vm.refresh() },
                innerNav = innerNav,
                onRequestClose = { selectedSite = null },
                onReportInaccuracy = if (site.kind == ExploreItemKind.DIVE_SITE) {
                    {
                        selectedSite = null
                        contributionMode = DiveSiteContributionMode.Correction(site)
                    }
                } else {
                    null
                },
                onShowOnMap = {
                    val lat = site.latitude
                    val lng = site.longitude
                    selectedSite = null
                    vm.setViewMode(ExploreViewMode.MAP)
                    mapFocusLatLngZoom = Triple(lat, lng, 12.0)
                },
                onBusinessChat = when (site.kind) {
                    ExploreItemKind.DIVE_CENTER, ExploreItemKind.SHOP -> fun() {
                        selectedSite = null
                        innerNav.navigate(
                            InnerRoutes.businessChatOpen(
                                exploreKindToChatPeerType(site.kind),
                                site.id,
                            ),
                        )
                    }
                    else -> null
                },
            )
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            SortSheet(
                selectedSort = state.selectedSort,
                onSelect = {
                    vm.setSort(it)
                    showSortSheet = false
                },
            )
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            FilterSheet(
                selectedDiveType = state.selectedDiveType,
                selectedDifficulty = state.selectedDifficulty,
                onSelectDiveType = vm::setDiveTypeFilter,
                onSelectDifficulty = vm::setDifficultyFilter,
                onResetAll = vm::clearFilters,
                onClose = { showFilterSheet = false },
            )
        }
    }

    if (contributionMode != null) {
        ModalBottomSheet(onDismissRequest = { contributionMode = null }) {
            DiveSiteContributionSheetContent(
                mode = contributionMode!!,
                graph = graph,
                onDismiss = { contributionMode = null },
            )
        }
    }
}

@Composable
private fun ExploreHeader(
    onGlobalSearch: () -> Unit,
    onSortTap: () -> Unit,
    onFilterTap: () -> Unit,
    onFullscreenMap: () -> Unit,
    onSuggestNewSite: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IosDesign.ScreenPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onSuggestNewSite != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(IosDesign.Explore.navBarButtonFill),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = onSuggestNewSite, modifier = Modifier.size(44.dp)) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.dive_site_contribution_suggest_new_cd),
                                tint = IosDesign.Explore.navBarIconTint,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(IosDesign.Explore.navBarButtonFill),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onSortTap, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = IosDesign.Explore.navBarIconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(IosDesign.Explore.navBarButtonFill),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onFilterTap, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = IosDesign.Explore.navBarIconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(IosDesign.Explore.navBarButtonFill),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onFullscreenMap, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = stringResource(R.string.explore_action_open_map),
                            tint = IosDesign.Explore.navBarIconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(IosDesign.Explore.navBarButtonFill),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onGlobalSearch, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.explore_action_global_search),
                            tint = IosDesign.Explore.navBarIconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.explore_title),
            modifier = Modifier.padding(bottom = 6.dp),
            fontSize = 34.sp,
            lineHeight = 41.sp,
            fontWeight = FontWeight.Bold,
            color = IosDesign.Explore.labelPrimary,
        )
    }
}

@Composable
private fun SortSheet(
    selectedSort: ExploreSort,
    onSelect: (ExploreSort) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.explore_sort_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        SortOptionRow(stringResource(R.string.explore_sort_relevance), selectedSort == ExploreSort.RELEVANCE) { onSelect(ExploreSort.RELEVANCE) }
        SortOptionRow(stringResource(R.string.explore_sort_top_rated), selectedSort == ExploreSort.RATING_DESC) { onSelect(ExploreSort.RATING_DESC) }
        SortOptionRow(stringResource(R.string.explore_sort_shallow_first), selectedSort == ExploreSort.DEPTH_ASC) { onSelect(ExploreSort.DEPTH_ASC) }
        SortOptionRow(stringResource(R.string.explore_sort_name_az), selectedSort == ExploreSort.NAME_ASC) { onSelect(ExploreSort.NAME_ASC) }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SortOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FilterSheet(
    selectedDiveType: String?,
    selectedDifficulty: String?,
    onSelectDiveType: (String?) -> Unit,
    onSelectDifficulty: (String?) -> Unit,
    onResetAll: () -> Unit,
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
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.explore_filters_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.explore_filter_type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            IosExplorePill(stringResource(R.string.explore_all_types), selectedDiveType == null) { onSelectDiveType(null) }
            types.forEach { type ->
                IosExplorePill(type, selectedDiveType == type) {
                    onSelectDiveType(if (selectedDiveType == type) null else type)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.explore_filter_level), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            IosExplorePill(stringResource(R.string.explore_all_levels), selectedDifficulty == null) { onSelectDifficulty(null) }
            levels.forEach { level ->
                IosExplorePill(level, selectedDifficulty == level) {
                    onSelectDifficulty(if (selectedDifficulty == level) null else level)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onResetAll) { Text(stringResource(R.string.map_filter_reset)) }
            Button(onClick = onClose) { Text(stringResource(R.string.common_done)) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CategoryToggle(category: ExploreCategory, onCategory: (ExploreCategory) -> Unit) {
    val sites = stringResource(R.string.explore_sites)
    val centers = stringResource(R.string.explore_centers)
    val shops = stringResource(R.string.explore_shops)
    val segments = listOf(
        ExploreCategory.DIVE_SITES to sites,
        ExploreCategory.DIVE_CENTERS to centers,
        ExploreCategory.SHOPS to shops,
    )
    val thumbShape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IosDesign.ScreenPadding, vertical = 6.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(IosDesign.Explore.segmentTrack)
            .padding(2.dp),
    ) {
        segments.forEach { (cat, label) ->
            val selected = category == cat
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (selected) {
                            Modifier.shadow(
                                elevation = 2.dp,
                                shape = thumbShape,
                                ambientColor = IosDesign.Explore.segmentShadowAmbient,
                                spotColor = IosDesign.Explore.segmentShadowSpot,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clip(thumbShape)
                    .background(if (selected) IosDesign.Explore.segmentThumb else Color.Transparent)
                    .clickable { onCategory(cat) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        IosDesign.Explore.labelPrimary
                    } else {
                        IosDesign.Explore.labelPrimary.copy(alpha = 0.45f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = IosDesign.ScreenPadding, vertical = 8.dp),
        placeholder = {
            Text(
                stringResource(R.string.explore_search_label),
                color = IosDesign.Explore.labelSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = IosDesign.Explore.labelSecondary,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(999.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = IosDesign.Explore.searchFill,
            unfocusedContainerColor = IosDesign.Explore.searchFill,
            disabledContainerColor = IosDesign.Explore.searchFill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = IosDesign.Explore.labelPrimary,
            unfocusedTextColor = IosDesign.Explore.labelPrimary,
            cursorColor = IosDesign.Explore.filterActiveBlue,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ViewModeToggle(mode: ExploreViewMode, onMode: (ExploreViewMode) -> Unit) {
    val listLabel = stringResource(R.string.explore_list)
    val mapLabel = stringResource(R.string.explore_map)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IosDesign.ScreenPadding, vertical = 6.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(IosDesign.Explore.segmentTrack)
            .padding(2.dp),
    ) {
        IosExploreViewModeSegment(
            selected = mode == ExploreViewMode.LIST,
            onClick = { onMode(ExploreViewMode.LIST) },
            label = listLabel,
            leadingIcon = if (mode == ExploreViewMode.LIST) Icons.Default.Check else null,
            modifier = Modifier.weight(1f),
        )
        IosExploreViewModeSegment(
            selected = mode == ExploreViewMode.MAP,
            onClick = { onMode(ExploreViewMode.MAP) },
            label = mapLabel,
            leadingIcon = if (mode == ExploreViewMode.MAP) Icons.Default.Map else null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IosExploreViewModeSegment(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    leadingIcon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    val thumbShape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 2.dp,
                        shape = thumbShape,
                        ambientColor = IosDesign.Explore.segmentShadowAmbient,
                        spotColor = IosDesign.Explore.segmentShadowSpot,
                    )
                } else {
                    Modifier
                },
            )
            .clip(thumbShape)
            .background(if (selected) IosDesign.Explore.segmentThumb else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) IosDesign.Explore.labelPrimary else IosDesign.Explore.labelSecondary,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                IosDesign.Explore.labelPrimary
            } else {
                IosDesign.Explore.labelPrimary.copy(alpha = 0.45f)
            },
        )
    }
}

@Composable
private fun IosExplorePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) IosDesign.Explore.filterSelectedFill else IosDesign.Explore.filterInactiveFill
    val fg = IosDesign.Explore.labelPrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun QuickFilters(
    category: ExploreCategory,
    selectedDiveType: String?,
    selectedDifficulty: String?,
    onDiveType: (String?) -> Unit,
    onDifficulty: (String?) -> Unit,
) {
    val diveTypes = when (category) {
        ExploreCategory.DIVE_SITES -> listOf(
            stringResource(R.string.explore_reef),
            stringResource(R.string.explore_wreck),
            stringResource(R.string.explore_cave),
            "Drift",
        )
        ExploreCategory.DIVE_CENTERS -> listOf("Dive Center")
        ExploreCategory.SHOPS -> listOf("Gear", "Rental", "Service")
    }
    val levels = when (category) {
        ExploreCategory.DIVE_SITES -> listOf(
            stringResource(R.string.explore_beginner),
            stringResource(R.string.explore_intermediate),
            "Advanced",
            "Expert",
        )
        ExploreCategory.DIVE_CENTERS -> listOf("Nitrox", "Standard")
        ExploreCategory.SHOPS -> listOf("Service", "Store")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        if (category == ExploreCategory.DIVE_SITES) {
            val beginner = stringResource(R.string.explore_beginner)
            val reef = stringResource(R.string.explore_reef)
            val wreck = stringResource(R.string.explore_wreck)
            LazyRow(
                contentPadding = PaddingValues(start = IosDesign.ScreenPadding, end = IosDesign.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    IosExplorePill(
                        text = beginner,
                        selected = selectedDifficulty == beginner,
                        onClick = {
                            onDifficulty(if (selectedDifficulty == beginner) null else beginner)
                        },
                    )
                }
                item {
                    IosExplorePill(
                        text = reef,
                        selected = selectedDiveType == reef,
                        onClick = {
                            onDiveType(if (selectedDiveType == reef) null else reef)
                        },
                    )
                }
                item {
                    IosExplorePill(
                        text = wreck,
                        selected = selectedDiveType == wreck,
                        onClick = {
                            onDiveType(if (selectedDiveType == wreck) null else wreck)
                        },
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(start = IosDesign.ScreenPadding, end = IosDesign.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    IosExplorePill(
                        text = stringResource(R.string.explore_all_types),
                        selected = selectedDiveType == null,
                        onClick = { onDiveType(null) },
                    )
                }
                items(diveTypes) { type ->
                    IosExplorePill(
                        text = type,
                        selected = selectedDiveType == type,
                        onClick = { onDiveType(if (selectedDiveType == type) null else type) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(start = IosDesign.ScreenPadding, end = IosDesign.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    IosExplorePill(
                        text = stringResource(R.string.explore_all_levels),
                        selected = selectedDifficulty == null,
                        onClick = { onDifficulty(null) },
                    )
                }
                items(levels) { level ->
                    IosExplorePill(
                        text = level,
                        selected = selectedDifficulty == level,
                        onClick = { onDifficulty(if (selectedDifficulty == level) null else level) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MapControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onCenterOnUser: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(bottom = 80.dp),
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

@Composable
private fun ExploreList(
    sites: List<ExploreDiveSite>,
    userLatLng: Pair<Double, Double>?,
    onTap: (ExploreDiveSite) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomBarClearance = 72.dp
    LazyColumn(
        modifier = modifier.background(IosDesign.Explore.listBackground),
        contentPadding = PaddingValues(
            start = IosDesign.ScreenPadding,
            end = IosDesign.ScreenPadding,
            top = IosDesign.ScreenPadding,
            bottom = IosDesign.ScreenPadding + bottomBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(IosDesign.SectionSpacing),
    ) {
        items(sites, key = { it.id }) { site ->
            SiteCard(site, userLatLng = userLatLng, onTap = { onTap(site) })
        }
    }
}

@Composable
private fun SiteCard(site: ExploreDiveSite, userLatLng: Pair<Double, Double>?, onTap: () -> Unit) {
    Card(
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
        modifier = Modifier.fillMaxWidth().clickable { onTap() },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(site.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (site.rating >= 4.5) {
                            Spacer(Modifier.width(8.dp))
                            Badge(containerColor = Color(0xFFE8F4FF), contentColor = Color(0xFF0A84A6)) {
                                Text(stringResource(R.string.explore_recommended))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (site.kind == ExploreItemKind.DIVE_SITE) {
                            "${site.diveType} \u2022 ${site.depthMax.toInt()}m"
                        } else {
                            site.diveType
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    userLatLng?.let { (ulat, ulng) ->
                        val d = distanceMeters(ulat, ulng, site.latitude, site.longitude)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = IosDesign.Explore.filterActiveBlue,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                formatDistanceMeters(d),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = IosDesign.Explore.filterActiveBlue,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFF2C94C))
                        Text(
                            "${"%.1f".format(site.rating)} (${site.reviewCount})",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(site.difficulty) },
                    leadingIcon = { DiveHubLogoMark(modifier = Modifier.size(16.dp)) },
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(site.country.ifBlank { stringResource(R.string.explore_unknown) }) },
                )
            }
        }
    }
}

private fun exploreKindToChatPeerType(kind: ExploreItemKind): String = when (kind) {
    ExploreItemKind.DIVE_CENTER -> "dive_center"
    ExploreItemKind.SHOP -> "shop"
    ExploreItemKind.DIVE_SITE -> "user"
}

@Composable
private fun DiveSiteDetailSheet(
    site: ExploreDiveSite,
    graph: AppGraph,
    onReviewSubmitted: () -> Unit,
    innerNav: NavController,
    onRequestClose: () -> Unit,
    onReportInaccuracy: (() -> Unit)? = null,
    onShowOnMap: () -> Unit = {},
    onBusinessChat: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var reviews by remember { mutableStateOf<List<ReviewDto>>(emptyList()) }
    var reviewsLoading by remember { mutableStateOf(true) }
    var loggedIn by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }

    LaunchedEffect(site.id, site.kind) {
        loggedIn = !graph.tokenStore.getAccessToken().isNullOrBlank()
        reviewsLoading = true
        reviews = if (loggedIn) {
            runCatching {
                ReviewsRepository(graph).listReviews(site.kind.toApiReviewType(), site.id)
            }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        reviewsLoading = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(site.name, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onShowOnMap) {
                    Text(stringResource(R.string.explore_show_on_map))
                }
                if (site.kind == ExploreItemKind.SHOP) {
                    OutlinedButton(
                        onClick = {
                            onRequestClose()
                            innerNav.navigate(
                                InnerRoutes.bookingWizard(centerId = null, siteId = null, instructorId = null),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.explore_book))
                    }
                }
            }
        }
        if (site.kind == ExploreItemKind.DIVE_CENTER) {
            Spacer(Modifier.height(8.dp))
            DiveCenterPromoCard()
        }
        if (onBusinessChat != null && loggedIn) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onBusinessChat.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (site.kind) {
                        ExploreItemKind.SHOP -> stringResource(R.string.explore_message_shop)
                        else -> stringResource(R.string.explore_message_center)
                    },
                )
            }
        }
        if (site.kind == ExploreItemKind.DIVE_CENTER) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onRequestClose()
                    innerNav.navigate(InnerRoutes.diveCenterPublic(site.id))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dive_center_public_open_profile))
            }
        }
        if (site.kind == ExploreItemKind.SHOP) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onRequestClose()
                    innerNav.navigate(InnerRoutes.shopPublic(site.id))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.shop_public_open_profile))
            }
        }
        if (site.kind != ExploreItemKind.DIVE_SITE) {
            val loc = listOfNotNull(site.region, site.country).filter { it.isNotBlank() }.joinToString(", ")
            if (loc.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(loc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text(site.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(site.diveType, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFF2C94C))
                    Spacer(Modifier.width(4.dp))
                    Text("%.1f".format(site.rating), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text("(${site.reviewCount} reviews)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        if (site.kind == ExploreItemKind.DIVE_SITE) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.explore_max_depth, site.depthMax.toInt()))
                Text("${site.depthMax.toInt()}m", fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.explore_avg_depth))
                Text("${(site.depthMax * 0.55).toInt()}m", fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.explore_difficulty))
                Text(site.difficulty, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.explore_recent_dives), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.explore_no_recent_dives), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (loggedIn && onReportInaccuracy != null) {
                OutlinedButton(
                    onClick = onReportInaccuracy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dive_site_report_inaccuracy))
                }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.explore_difficulty))
                Text(site.difficulty, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.explore_reviews), fontWeight = FontWeight.SemiBold)
            TextButton(
                onClick = {
                    scope.launch {
                        val ok = !graph.tokenStore.getAccessToken().isNullOrBlank()
                        if (!ok) {
                            Toast.makeText(context, context.getString(R.string.review_login_required), Toast.LENGTH_LONG).show()
                        } else {
                            loggedIn = true
                            showReviewDialog = true
                        }
                    }
                },
            ) {
                Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.explore_add_review))
            }
        }
        when {
            reviewsLoading -> Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chat_loading), style = MaterialTheme.typography.bodySmall)
            }
            !loggedIn -> Text(stringResource(R.string.review_login_required), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            reviews.isEmpty() -> Text(stringResource(R.string.explore_no_reviews_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                reviews.forEach { r ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    ReviewListItem(r)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(site.description.ifBlank { stringResource(R.string.explore_no_description) }, style = MaterialTheme.typography.bodyMedium)
    }

    if (showReviewDialog) {
        AddReviewableDialog(
            reviewableType = site.kind.toApiReviewType(),
            reviewableId = site.id,
            graph = graph,
            onDismiss = { showReviewDialog = false },
            onSuccess = {
                showReviewDialog = false
                scope.launch {
                    reviews = runCatching {
                        ReviewsRepository(graph).listReviews(site.kind.toApiReviewType(), site.id)
                    }.getOrElse { emptyList() }
                }
                onReviewSubmitted()
                Toast.makeText(context, context.getString(R.string.review_sent), Toast.LENGTH_SHORT).show()
            },
        )
    }

}

@Composable
private fun ReviewListItem(r: ReviewDto) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            r.userName?.trim().orEmpty().ifBlank { "—" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(r.rating.coerceIn(1, 5)) {
                Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color(0xFFF2C94C))
            }
        }
        Text(r.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
        }
    }
}
