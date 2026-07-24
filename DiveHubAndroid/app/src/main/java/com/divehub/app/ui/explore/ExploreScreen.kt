package com.divehub.app.ui.explore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import com.divehub.app.ui.components.IosBorderedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.remote.dto.profileCompletionFraction
import com.divehub.app.data.remote.dto.needsProfileOnboarding
import com.divehub.app.ui.components.DiveHubLogoMark
import com.divehub.app.ui.components.IosBlurSurface
import com.divehub.app.ui.components.IosMapChromeLocateButton
import com.divehub.app.ui.components.IosMapChromeZoomCluster
import com.divehub.app.ui.components.IosMaterialVariant
import com.divehub.app.ui.components.IosCapsuleChip
import com.divehub.app.ui.components.IosCapsuleTag
import com.divehub.app.ui.components.IosEmptyState
import com.divehub.app.ui.components.IosErrorState
import com.divehub.app.ui.components.IosFormSheetScaffold
import com.divehub.app.ui.components.IosFormToggleRow
import com.divehub.app.ui.components.IosSearchField
import com.divehub.app.ui.reviews.AddReviewableDialog
import com.divehub.app.ui.reviews.ReviewListRow
import com.divehub.app.ui.theme.IosDesign
import com.divehub.app.ui.theme.LocalDiveHubDarkTheme
import com.divehub.app.ui.theme.exploreChromeColors
import com.divehub.app.ui.theme.iosChromePageBackground
import com.divehub.app.ui.theme.iosGroupedCardColor
import com.divehub.app.ui.theme.iosHairlineStrokeColor
import com.divehub.app.ui.theme.iosLabelPrimaryColor
import com.divehub.app.ui.theme.iosSecondaryMutedTextColor
import com.divehub.app.ui.theme.iosSegmentThumbColor
import com.divehub.app.ui.theme.iosSegmentTrackColor
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

internal fun openExploreSiteFromExplore(
    site: ExploreDiveSite,
    graph: AppGraph,
    innerNav: NavController,
) {
    when (site.kind) {
        ExploreItemKind.DIVE_CENTER -> innerNav.navigate(InnerRoutes.diveCenterPublic(site.id))
        ExploreItemKind.SHOP -> innerNav.navigate(InnerRoutes.shopPublic(site.id))
        ExploreItemKind.DIVE_SITE -> {
            graph.setPendingExploreDiveSite(site)
            innerNav.navigate(InnerRoutes.exploreDiveSiteDetail(site.id))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreRoute(graph: AppGraph, innerNav: NavController) {
    val vm: ExploreViewModel = viewModel(factory = ExploreViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var mapActions by remember { mutableStateOf<ExploreMapActions?>(null) }
    var pendingCenterOnUser by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var mapFocusLatLngZoom by remember { mutableStateOf<Triple<Double, Double, Double>?>(null) }
    var contributionMode by remember { mutableStateOf<DiveSiteContributionMode?>(null) }
    val loggedIn by produceState(initialValue = false) {
        graph.tokenStore.accessToken.collect { value = !it.isNullOrBlank() }
    }
    val currentUser by produceState<UserDto?>(initialValue = null) {
        graph.tokenStore.userJson.collect { json ->
            value = json?.let {
                runCatching { graph.gson.fromJson(it, UserDto::class.java) }.getOrNull()
            }
        }
    }
    val context = LocalContext.current
    var bannerHiddenUntil by remember { mutableStateOf(0L) }
    LaunchedEffect(currentUser?.id) {
        val uid = currentUser?.id ?: return@LaunchedEffect
        bannerHiddenUntil = context.getSharedPreferences("explore_ui", Context.MODE_PRIVATE)
            .getLong("profile_banner_hidden_$uid", 0L)
    }
    var userGeo by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val liveUserLatLng = rememberUserLatLngForMap()
    LaunchedEffect(liveUserLatLng) {
        liveUserLatLng?.let { (lat, lng) ->
            userGeo = lat to lng
            vm.setUserLocation(lat, lng)
        }
    }
    LaunchedEffect(context) {
        if (hasLocationPermission(context) && userGeo == null) {
            userGeo = getLastKnownLocation(context)
            userGeo?.let { (lat, lng) -> vm.setUserLocation(lat, lng) }
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
        if (granted) {
            getLastKnownLocation(context)?.let { (lat, lng) ->
                userGeo = lat to lng
                vm.setUserLocation(lat, lng)
                if (pendingCenterOnUser) {
                    pendingCenterOnUser = false
                    mapActions?.centerOn?.invoke(lat, lng)
                }
            }
        }
    }

    val showProfileBanner = remember(currentUser, bannerHiddenUntil, loggedIn) {
        val u = currentUser ?: return@remember false
        if (!loggedIn || u.needsProfileOnboarding()) return@remember false
        if (System.currentTimeMillis() < bannerHiddenUntil) return@remember false
        u.profileCompletionFraction() < 0.7
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iosChromePageBackground()),
    ) {
        ExploreTopChrome(
            showProfileBanner = showProfileBanner,
            currentUser = currentUser,
            onDismissBanner = {
                val uid = currentUser?.id ?: return@ExploreTopChrome
                val until = System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000
                bannerHiddenUntil = until
                context.getSharedPreferences("explore_ui", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("profile_banner_hidden_$uid", until)
                    .apply()
            },
            onEditProfile = { innerNav.navigate(InnerRoutes.EditProfile) },
            onFilterTap = if (state.selectedCategory != ExploreCategory.SHOPS) {
                { showFilterSheet = true }
            } else {
                null
            },
            onSuggestNewSite = if (loggedIn) {
                { contributionMode = DiveSiteContributionMode.NewSite }
            } else {
                null
            },
            activeFilterCount = state.activeFilterCount(),
            selectedSort = state.selectedSort,
            showDistanceSort = userGeo != null,
            onSortSelect = vm::setSort,
            category = state.selectedCategory,
            viewMode = state.viewMode,
            searchQuery = state.searchQuery,
            selectedDiveType = state.selectedDiveType,
            selectedDifficulty = state.selectedDifficulty,
            certificationAgency = state.certificationAgency,
            onCategory = vm::setCategory,
            onViewMode = vm::setViewMode,
            onSearch = vm::setSearch,
            onDiveType = vm::setDiveTypeFilter,
            onDifficulty = vm::setDifficultyFilter,
            onCertificationAgency = vm::setCertificationAgency,
        )
        if (state.viewMode == ExploreViewMode.MAP) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ExploreMapOsm(
                    sites = state.filteredSites,
                    onSiteTap = { openExploreSiteFromExplore(it, graph, innerNav) },
                    onActionsReady = { mapActions = it },
                    showUserLocation = hasLocationPermission(context),
                    userLatLng = userGeo ?: liveUserLatLng,
                    onViewportSettled = { north, south, east, west ->
                        if (state.selectedCategory == ExploreCategory.DIVE_SITES) {
                            vm.loadMapSites(north, south, east, west)
                        }
                    },
                )
                MapControls(
                    onZoomIn = { mapActions?.zoomIn?.invoke() },
                    onZoomOut = { mapActions?.zoomOut?.invoke() },
                    onCenterOnUser = {
                        if (hasLocationPermission(context)) {
                            getLastKnownLocation(context)?.let { (lat, lng) ->
                                userGeo = lat to lng
                                vm.setUserLocation(lat, lng)
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
        } else {
            when {
                state.loading && state.allSites.isEmpty() -> Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.error != null && state.allSites.isEmpty() -> Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    ErrorView(state.error ?: stringResource(R.string.common_error), onRetry = vm::refresh)
                }
                else -> PullToRefreshBox(
                    isRefreshing = state.loading && state.allSites.isNotEmpty(),
                    onRefresh = { vm.refresh() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    ExploreList(
                        sites = state.filteredSites,
                        category = state.selectedCategory,
                        hasActiveFilters = state.hasActiveFilters(),
                        onResetFilters = vm::clearFilters,
                        userLatLng = userGeo,
                        onTap = { openExploreSiteFromExplore(it, graph, innerNav) },
                        onAddToTrip = if (loggedIn) {
                            { innerNav.navigate(InnerRoutes.TripCreate) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            category = state.selectedCategory,
            allSites = state.allSites,
            selectedDiveType = state.selectedDiveType,
            selectedDifficulty = state.selectedDifficulty,
            filterCountry = state.filterCountry,
            minRating = state.minRating,
            minDepth = state.minDepth,
            maxDepth = state.maxDepth,
            certificationAgency = state.certificationAgency,
            shopTypeFilter = state.shopTypeFilter,
            serviceOnly = state.serviceOnly,
            onSelectDiveType = vm::setDiveTypeFilter,
            onSelectDifficulty = vm::setDifficultyFilter,
            onFilterCountry = vm::setFilterCountry,
            onMinRating = vm::setMinRating,
            onMinDepth = vm::setMinDepth,
            onMaxDepth = vm::setMaxDepth,
            onCertificationAgency = vm::setCertificationAgency,
            onShopTypeFilter = vm::setShopTypeFilter,
            onServiceOnly = vm::setServiceOnly,
            onResetAll = vm::clearFilters,
            onClose = { showFilterSheet = false },
        )
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
private fun ExploreMapTopChrome(
    showProfileBanner: Boolean,
    currentUser: UserDto?,
    onDismissBanner: () -> Unit,
    onEditProfile: () -> Unit,
    onFilterTap: (() -> Unit)?,
    onSuggestNewSite: (() -> Unit)?,
    activeFilterCount: Int,
    selectedSort: ExploreSort,
    showDistanceSort: Boolean,
    onSortSelect: (ExploreSort) -> Unit,
    category: ExploreCategory,
    viewMode: ExploreViewMode,
    searchQuery: String,
    onCategory: (ExploreCategory) -> Unit,
    onViewMode: (ExploreViewMode) -> Unit,
    onSearch: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (showProfileBanner) {
            CompleteProfileBanner(onEditProfile = onEditProfile, onDismiss = onDismissBanner)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IosDesign.ScreenPadding)
                .padding(top = 2.dp, bottom = 2.dp)
                .defaultMinSize(minHeight = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.explore_title),
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = iosLabelPrimaryColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ExploreToolbarCapsule(
                activeFilterCount = activeFilterCount,
                selectedSort = selectedSort,
                showDistanceSort = showDistanceSort,
                onSortSelect = onSortSelect,
                onFilterTap = onFilterTap,
                onSuggestNewSite = onSuggestNewSite,
            )
        }
        CategoryAndViewModeRow(
            category = category,
            viewMode = viewMode,
            onCategory = onCategory,
            onViewMode = onViewMode,
        )
        IosSearchField(
            query = searchQuery,
            onQueryChange = onSearch,
            placeholder = stringResource(
                when (category) {
                    ExploreCategory.DIVE_SITES -> R.string.explore_search_label
                    ExploreCategory.DIVE_CENTERS -> R.string.explore_search_centers
                    ExploreCategory.SHOPS -> R.string.explore_search_shops
                },
            ),
            modifier = Modifier
                .padding(horizontal = IosDesign.ScreenPadding)
                .padding(bottom = 6.dp),
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ExploreTopChrome(
    showProfileBanner: Boolean,
    currentUser: UserDto?,
    onDismissBanner: () -> Unit,
    onEditProfile: () -> Unit,
    onFilterTap: (() -> Unit)?,
    onSuggestNewSite: (() -> Unit)?,
    activeFilterCount: Int,
    selectedSort: ExploreSort,
    showDistanceSort: Boolean,
    onSortSelect: (ExploreSort) -> Unit,
    category: ExploreCategory,
    viewMode: ExploreViewMode,
    searchQuery: String,
    selectedDiveType: String?,
    selectedDifficulty: String?,
    certificationAgency: String?,
    onCategory: (ExploreCategory) -> Unit,
    onViewMode: (ExploreViewMode) -> Unit,
    onSearch: (String) -> Unit,
    onDiveType: (String?) -> Unit,
    onDifficulty: (String?) -> Unit,
    onCertificationAgency: (String?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
    if (showProfileBanner) {
        CompleteProfileBanner(
            onEditProfile = onEditProfile,
            onDismiss = onDismissBanner,
        )
    }
    ExploreHeader(
        activeFilterCount = activeFilterCount,
        selectedSort = selectedSort,
        showDistanceSort = showDistanceSort,
        onSortSelect = onSortSelect,
        onFilterTap = onFilterTap,
        onSuggestNewSite = onSuggestNewSite,
    )
    // Live iOS ExploreView: category segment + compact list/map icons on one row.
    CategoryAndViewModeRow(
        category = category,
        viewMode = viewMode,
        onCategory = onCategory,
        onViewMode = onViewMode,
    )
    IosSearchField(
        query = searchQuery,
        onQueryChange = onSearch,
        placeholder = stringResource(
            when (category) {
                ExploreCategory.DIVE_SITES -> R.string.explore_search_label
                ExploreCategory.DIVE_CENTERS -> R.string.explore_search_centers
                ExploreCategory.SHOPS -> R.string.explore_search_shops
            },
        ),
        modifier = Modifier
            .padding(horizontal = IosDesign.ScreenPadding)
            .padding(
                top = 2.dp,
                bottom = if (viewMode == ExploreViewMode.LIST && category != ExploreCategory.SHOPS) {
                    6.dp
                } else {
                    8.dp
                },
            ),
    )
    if (viewMode == ExploreViewMode.LIST && category != ExploreCategory.SHOPS) {
        QuickFilters(
            category = category,
            selectedDiveType = selectedDiveType,
            selectedDifficulty = selectedDifficulty,
            certificationAgency = certificationAgency,
            onDiveType = onDiveType,
            onDifficulty = onDifficulty,
            onCertificationAgency = onCertificationAgency,
        )
    }
    }
}

@Composable
private fun CompleteProfileBanner(
    onEditProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IosDesign.ScreenPadding, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1F0080CC))
            .clickable(onClick = onEditProfile)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.complete_profile_banner_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = iosLabelPrimaryColor(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.complete_profile_banner_body),
                fontSize = 13.sp,
                color = IosDesign.Explore.labelSecondary,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.complete_profile_banner_dismiss),
                tint = IosDesign.Explore.labelSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** App Store Explore header: large title + trailing toolbar capsule. */
@Composable
private fun ExploreHeader(
    activeFilterCount: Int,
    selectedSort: ExploreSort,
    showDistanceSort: Boolean,
    onSortSelect: (ExploreSort) -> Unit,
    onFilterTap: (() -> Unit)?,
    onSuggestNewSite: (() -> Unit)? = null,
) {
    // Match iOS large-title + trailing glass capsule density (tight under status bar).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IosDesign.ScreenPadding)
            .padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.explore_title),
            modifier = Modifier.weight(1f, fill = false),
            fontSize = 34.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            color = iosLabelPrimaryColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ExploreToolbarCapsule(
            activeFilterCount = activeFilterCount,
            selectedSort = selectedSort,
            showDistanceSort = showDistanceSort,
            onSortSelect = onSortSelect,
            onFilterTap = onFilterTap,
            onSuggestNewSite = onSuggestNewSite,
        )
    }
}

@Composable
private fun ExploreToolbarCapsule(
    activeFilterCount: Int,
    selectedSort: ExploreSort,
    showDistanceSort: Boolean,
    onSortSelect: (ExploreSort) -> Unit,
    onFilterTap: (() -> Unit)?,
    onSuggestNewSite: (() -> Unit)?,
) {
    val isDark = LocalDiveHubDarkTheme.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    // iOS exploreToolbarActions — Material icons need ~22dp to match SF ~15pt weight.
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(IosDesign.CapsuleShape)
            .background(
                if (isDark) IosDesign.ultraThinMaterialDark else IosDesign.ultraThinMaterialLight,
            )
            .border(0.5.dp, iosHairlineStrokeColor(0.08f), IosDesign.CapsuleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onSuggestNewSite != null) {
            ExploreToolbarIconButton(
                onClick = onSuggestNewSite,
                icon = Icons.Outlined.CreateNewFolder,
                contentDescription = stringResource(R.string.dive_site_contribution_suggest_new_cd),
            )
        }
        Box {
            ExploreToolbarIconButton(
                onClick = { sortMenuExpanded = true },
                icon = Icons.Default.SwapVert,
                contentDescription = stringResource(R.string.explore_sort_title),
            )
            ExploreSortDropdownMenu(
                expanded = sortMenuExpanded,
                onDismiss = { sortMenuExpanded = false },
                selectedSort = selectedSort,
                showDistance = showDistanceSort,
                onSelect = {
                    onSortSelect(it)
                    sortMenuExpanded = false
                },
            )
        }
        if (onFilterTap != null) {
            Box {
                ExploreToolbarIconButton(
                    onClick = onFilterTap,
                    icon = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.explore_filters_sheet_title),
                )
                if (activeFilterCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-4).dp)
                            .clip(IosDesign.CapsuleShape)
                            .background(Color.Red)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = activeFilterCount.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreSortDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    selectedSort: ExploreSort,
    showDistance: Boolean,
    onSelect: (ExploreSort) -> Unit,
) {
    val options = buildList {
        add(ExploreSort.RELEVANCE to stringResource(R.string.explore_sort_relevance))
        if (showDistance) {
            add(ExploreSort.DISTANCE to stringResource(R.string.explore_sort_distance))
        }
        add(ExploreSort.RATING_DESC to stringResource(R.string.explore_sort_top_rated))
        add(ExploreSort.DEPTH_ASC to stringResource(R.string.explore_sort_shallow_first))
        add(ExploreSort.NAME_ASC to stringResource(R.string.explore_sort_name_az))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        options.forEach { (sort, label) ->
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label)
                        if (selectedSort == sort) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = IosDesign.Explore.filterActiveBlue,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                onClick = { onSelect(sort) },
            )
        }
    }
}

@Composable
private fun ExploreToolbarIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
) {
    val divePrimary = Color(0xFF0080CC)
    // Hit target ~36×32; glyph larger so Material icons match SF visual weight.
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = divePrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CategoryAndViewModeRow(
    category: ExploreCategory,
    viewMode: ExploreViewMode,
    onCategory: (ExploreCategory) -> Unit,
    onViewMode: (ExploreViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IosDesign.ScreenPadding)
            .padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryToggle(
            category = category,
            onCategory = onCategory,
            modifier = Modifier.weight(1f),
        )
        CompactViewModeToggle(viewMode = viewMode, onViewMode = onViewMode)
    }
}

@Composable
private fun CompactViewModeToggle(
    viewMode: ExploreViewMode,
    onViewMode: (ExploreViewMode) -> Unit,
) {
    val listLabel = stringResource(R.string.explore_list)
    val mapLabel = stringResource(R.string.explore_map)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(iosSegmentTrackColor())
            .padding(2.dp),
    ) {
        CompactViewModeButton(
            selected = viewMode == ExploreViewMode.LIST,
            icon = Icons.AutoMirrored.Filled.List,
            label = listLabel,
            onClick = { onViewMode(ExploreViewMode.LIST) },
        )
        CompactViewModeButton(
            selected = viewMode == ExploreViewMode.MAP,
            icon = Icons.Default.Map,
            label = mapLabel,
            onClick = { onViewMode(ExploreViewMode.MAP) },
        )
    }
}

@Composable
private fun CompactViewModeButton(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val thumbShape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 28.dp)
            .clip(thumbShape)
            .then(
                if (selected) {
                    Modifier
                        .border(0.5.dp, iosHairlineStrokeColor(0.06f), thumbShape)
                        .background(iosSegmentThumbColor())
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(13.dp),
            tint = if (selected) {
                iosLabelPrimaryColor()
            } else {
                iosLabelPrimaryColor().copy(alpha = 0.45f)
            },
        )
    }
}

@Composable
private fun FilterSheet(
    category: ExploreCategory,
    allSites: List<ExploreDiveSite>,
    selectedDiveType: String?,
    selectedDifficulty: String?,
    filterCountry: String?,
    minRating: Double?,
    minDepth: Double?,
    maxDepth: Double?,
    certificationAgency: String?,
    shopTypeFilter: String?,
    serviceOnly: Boolean,
    onSelectDiveType: (String?) -> Unit,
    onSelectDifficulty: (String?) -> Unit,
    onFilterCountry: (String?) -> Unit,
    onMinRating: (Double?) -> Unit,
    onMinDepth: (Double?) -> Unit,
    onMaxDepth: (Double?) -> Unit,
    onCertificationAgency: (String?) -> Unit,
    onShopTypeFilter: (String?) -> Unit,
    onServiceOnly: (Boolean) -> Unit,
    onResetAll: () -> Unit,
    onClose: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val countries = remember(allSites) {
        allSites.map { it.country }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val trainingSystems = remember(allSites, category) {
        if (category != ExploreCategory.DIVE_CENTERS) {
            emptyList()
        } else {
            val fromData = allSites
                .mapNotNull { it.certificationAgency?.trim()?.takeIf { a -> a.isNotEmpty() } }
                .flatMap { raw ->
                    raw.split(',', ';', '/', '|', ' ')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                }
                .distinctBy { it.lowercase() }
                .sorted()
            fromData.ifEmpty {
                listOf(
                    ExploreFilterKeys.CENTER_PADI,
                    ExploreFilterKeys.CENTER_SSI,
                    ExploreFilterKeys.CENTER_CMAS,
                    "NAUI",
                    "NDL",
                    "ISO",
                )
            }
        }
    }
    val typeOptions = listOf(
        ExploreFilterKeys.TYPE_REEF to R.string.explore_reef,
        ExploreFilterKeys.TYPE_WRECK to R.string.explore_wreck,
        ExploreFilterKeys.TYPE_CAVE to R.string.explore_cave,
    )
    val levelOptions = listOf(
        ExploreFilterKeys.DIFF_BEGINNER to R.string.explore_beginner,
        ExploreFilterKeys.DIFF_INTERMEDIATE to R.string.explore_intermediate,
        ExploreFilterKeys.DIFF_ADVANCED to R.string.explore_advanced,
        ExploreFilterKeys.DIFF_EXPERT to R.string.explore_expert,
    )
    val shopTypeOptions = listOf(
        ExploreFilterKeys.SHOP_GEAR to R.string.explore_filter_gear,
        ExploreFilterKeys.SHOP_RENTAL to R.string.explore_filter_rental,
        ExploreFilterKeys.SHOP_SERVICE to R.string.explore_filter_service_chip,
        ExploreFilterKeys.SHOP_STORE to R.string.explore_filter_store,
    )
    var minRatingText by remember(minRating) { mutableStateOf(minRating?.let { formatFilterNumber(it) }.orEmpty()) }
    var minDepthText by remember(minDepth) { mutableStateOf(minDepth?.let { formatFilterNumber(it) }.orEmpty()) }
    var maxDepthText by remember(maxDepth) { mutableStateOf(maxDepth?.let { formatFilterNumber(it) }.orEmpty()) }
    IosFormSheetScaffold(
        title = stringResource(R.string.explore_filters_sheet_title),
        onDismiss = onClose,
        cancelLabel = stringResource(R.string.map_filter_reset),
        onCancel = onResetAll,
        doneLabel = stringResource(R.string.common_done),
        onDone = onClose,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
        when (category) {
            ExploreCategory.DIVE_SITES -> {
                Text(
                    stringResource(R.string.explore_filter_place_type_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    IosCapsuleChip(
                        text = stringResource(R.string.explore_all_types),
                        selected = selectedDiveType == null,
                        onClick = { onSelectDiveType(null) },
                    )
                    typeOptions.forEach { (key, labelRes) ->
                        IosCapsuleChip(
                            text = stringResource(labelRes),
                            selected = selectedDiveType == key,
                            onClick = { onSelectDiveType(if (selectedDiveType == key) null else key) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.explore_filter_difficulty_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    IosCapsuleChip(
                        text = stringResource(R.string.explore_all_levels),
                        selected = selectedDifficulty == null,
                        onClick = { onSelectDifficulty(null) },
                    )
                    levelOptions.forEach { (key, labelRes) ->
                        IosCapsuleChip(
                            text = stringResource(labelRes),
                            selected = selectedDifficulty == key,
                            onClick = { onSelectDifficulty(if (selectedDifficulty == key) null else key) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                FilterCountrySection(countries, filterCountry, onFilterCountry)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.explore_filter_depth_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minDepthText,
                        onValueChange = { text ->
                            minDepthText = text
                            onMinDepth(parseFilterDouble(text))
                        },
                        label = { Text(stringResource(R.string.explore_filter_min_depth)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxDepthText,
                        onValueChange = { text ->
                            maxDepthText = text
                            onMaxDepth(parseFilterDouble(text))
                        },
                        label = { Text(stringResource(R.string.explore_filter_max_depth)) },
                        placeholder = { Text(stringResource(R.string.explore_filter_infinity)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                FilterMinRatingField(minRatingText) { text ->
                    minRatingText = text
                    onMinRating(parseFilterDouble(text))
                }
            }
            ExploreCategory.DIVE_CENTERS -> {
                FilterCountrySection(countries, filterCountry, onFilterCountry)
                Spacer(Modifier.height(12.dp))
                FilterMinRatingField(minRatingText) { text ->
                    minRatingText = text
                    onMinRating(parseFilterDouble(text))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.partner_courses_training_systems),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    IosCapsuleChip(
                        text = stringResource(R.string.explore_filter_all),
                        selected = certificationAgency == null,
                        onClick = { onCertificationAgency(null) },
                    )
                    trainingSystems.forEach { agency ->
                        IosCapsuleChip(
                            text = agency,
                            selected = certificationAgency.equals(agency, ignoreCase = true),
                            onClick = {
                                onCertificationAgency(
                                    if (certificationAgency.equals(agency, ignoreCase = true)) null else agency,
                                )
                            },
                        )
                    }
                }
            }
            ExploreCategory.SHOPS -> {
                // Shop filters temporarily disabled.
            }
        }
        Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FilterCountrySection(
    countries: List<String>,
    filterCountry: String?,
    onFilterCountry: (String?) -> Unit,
) {
    Text(
        stringResource(R.string.explore_filter_country),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(8.dp))
    if (countries.isEmpty()) {
        Text(
            stringResource(R.string.explore_filter_no_options),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            IosCapsuleChip(
                text = stringResource(R.string.explore_filter_all),
                selected = filterCountry == null,
                onClick = { onFilterCountry(null) },
            )
            countries.forEach { country ->
                IosCapsuleChip(
                    text = country,
                    selected = filterCountry == country,
                    onClick = { onFilterCountry(if (filterCountry == country) null else country) },
                )
            }
        }
    }
}

@Composable
private fun FilterMinRatingField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Text(
        stringResource(R.string.explore_filter_rating_section),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.explore_filter_min_rating)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun parseFilterDouble(text: String): Double? =
    text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

private fun formatFilterNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable
private fun ExploreListEmptyState(
    category: ExploreCategory,
    showsFilteredEmpty: Boolean,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when {
        showsFilteredEmpty -> R.string.explore_empty_filters_title
        category == ExploreCategory.DIVE_SITES -> R.string.explore_empty_dive_sites_title
        category == ExploreCategory.DIVE_CENTERS -> R.string.explore_empty_centers_title
        else -> R.string.explore_empty_shops_title
    }
    val bodyRes = when {
        showsFilteredEmpty -> R.string.explore_empty_filters_body
        category == ExploreCategory.DIVE_SITES -> R.string.explore_empty_dive_sites_body
        category == ExploreCategory.DIVE_CENTERS -> R.string.explore_empty_centers_body
        else -> R.string.explore_empty_shops_body
    }
    IosEmptyState(
        title = stringResource(titleRes),
        body = stringResource(bodyRes),
        modifier = modifier.background(exploreChromeColors().listBackground),
        actionLabel = if (showsFilteredEmpty) stringResource(R.string.map_filter_reset) else null,
        onAction = if (showsFilteredEmpty) onResetFilters else null,
    )
}

@Composable
private fun CategoryToggle(
    category: ExploreCategory,
    onCategory: (ExploreCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(iosSegmentTrackColor())
            .padding(2.dp),
    ) {
        segments.forEach { (cat, label) ->
            val selected = category == cat
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(thumbShape)
                    .then(
                        if (selected) {
                            Modifier
                                .border(0.5.dp, iosHairlineStrokeColor(0.06f), thumbShape)
                                .background(iosSegmentThumbColor())
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onCategory(cat) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        iosLabelPrimaryColor()
                    } else {
                        iosLabelPrimaryColor().copy(alpha = 0.45f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickFilters(
    category: ExploreCategory,
    selectedDiveType: String?,
    selectedDifficulty: String?,
    certificationAgency: String?,
    onDiveType: (String?) -> Unit,
    onDifficulty: (String?) -> Unit,
    onCertificationAgency: (String?) -> Unit,
) {
    val diveTypeOptions = when (category) {
        ExploreCategory.DIVE_SITES -> listOf(
            ExploreFilterKeys.TYPE_REEF to R.string.explore_reef,
            ExploreFilterKeys.TYPE_WRECK to R.string.explore_wreck,
            ExploreFilterKeys.TYPE_CAVE to R.string.explore_cave,
        )
        ExploreCategory.DIVE_CENTERS -> emptyList()
        ExploreCategory.SHOPS -> emptyList()
    }
    val levelOptions = when (category) {
        ExploreCategory.DIVE_SITES -> listOf(
            ExploreFilterKeys.DIFF_BEGINNER to R.string.explore_beginner,
            ExploreFilterKeys.DIFF_INTERMEDIATE to R.string.explore_intermediate,
            ExploreFilterKeys.DIFF_ADVANCED to R.string.explore_advanced,
            ExploreFilterKeys.DIFF_EXPERT to R.string.explore_expert,
        )
        ExploreCategory.DIVE_CENTERS -> emptyList()
        ExploreCategory.SHOPS -> emptyList()
    }
    val agencyOptions = when (category) {
        ExploreCategory.DIVE_CENTERS -> listOf(
            ExploreFilterKeys.CENTER_PADI,
            ExploreFilterKeys.CENTER_SSI,
            ExploreFilterKeys.CENTER_CMAS,
        )
        else -> emptyList()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        if (category == ExploreCategory.DIVE_SITES) {
            LazyRow(
                contentPadding = PaddingValues(start = IosDesign.ScreenPadding, end = IosDesign.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    IosCapsuleChip(
                        text = stringResource(R.string.explore_beginner),
                        selected = selectedDifficulty == ExploreFilterKeys.DIFF_BEGINNER,
                        onClick = {
                            onDifficulty(
                                if (selectedDifficulty == ExploreFilterKeys.DIFF_BEGINNER) {
                                    null
                                } else {
                                    ExploreFilterKeys.DIFF_BEGINNER
                                },
                            )
                        },
                    )
                }
                item {
                    IosCapsuleChip(
                        text = stringResource(R.string.explore_reef),
                        selected = selectedDiveType == ExploreFilterKeys.TYPE_REEF,
                        onClick = {
                            onDiveType(
                                if (selectedDiveType == ExploreFilterKeys.TYPE_REEF) {
                                    null
                                } else {
                                    ExploreFilterKeys.TYPE_REEF
                                },
                            )
                        },
                    )
                }
                item {
                    IosCapsuleChip(
                        text = stringResource(R.string.explore_wreck),
                        selected = selectedDiveType == ExploreFilterKeys.TYPE_WRECK,
                        onClick = {
                            onDiveType(
                                if (selectedDiveType == ExploreFilterKeys.TYPE_WRECK) {
                                    null
                                } else {
                                    ExploreFilterKeys.TYPE_WRECK
                                },
                            )
                        },
                    )
                }
            }
        } else if (agencyOptions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(start = IosDesign.ScreenPadding, end = IosDesign.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(agencyOptions, key = { it }) { agency ->
                    IosCapsuleChip(
                        text = agency,
                        selected = certificationAgency.equals(agency, ignoreCase = true),
                        onClick = {
                            onCertificationAgency(
                                if (certificationAgency.equals(agency, ignoreCase = true)) null else agency,
                            )
                        },
                    )
                }
            }
        } else {
            if (diveTypeOptions.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(start = IosDesign.ScreenPadding, end = IosDesign.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        IosCapsuleChip(
                            text = stringResource(R.string.explore_all_types),
                            selected = selectedDiveType == null,
                            onClick = { onDiveType(null) },
                        )
                    }
                    items(diveTypeOptions, key = { it.first }) { (key, labelRes) ->
                        IosCapsuleChip(
                            text = stringResource(labelRes),
                            selected = selectedDiveType == key,
                            onClick = { onDiveType(if (selectedDiveType == key) null else key) },
                        )
                    }
                }
            }
            if (levelOptions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(start = IosDesign.ScreenPadding, end = IosDesign.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        IosCapsuleChip(
                            text = stringResource(R.string.explore_all_levels),
                            selected = selectedDifficulty == null,
                            onClick = { onDifficulty(null) },
                        )
                    }
                    items(levelOptions, key = { it.first }) { (key, labelRes) ->
                        IosCapsuleChip(
                            text = stringResource(labelRes),
                            selected = selectedDifficulty == key,
                            onClick = { onDifficulty(if (selectedDifficulty == key) null else key) },
                        )
                    }
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
    val tabBarInset = com.divehub.app.ui.main.diveHubIosScrollTabBarBottomInset()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = tabBarInset + 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IosMapChromeZoomCluster(onZoomIn = onZoomIn, onZoomOut = onZoomOut)
            IosMapChromeLocateButton(onClick = onCenterOnUser)
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
    category: ExploreCategory,
    hasActiveFilters: Boolean,
    onResetFilters: () -> Unit,
    userLatLng: Pair<Double, Double>?,
    onTap: (ExploreDiveSite) -> Unit,
    onAddToTrip: ((ExploreDiveSite) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (sites.isEmpty()) {
        ExploreListEmptyState(
            category = category,
            showsFilteredEmpty = hasActiveFilters,
            onResetFilters = onResetFilters,
            modifier = modifier,
        )
        return
    }
    val bottomBarClearance = com.divehub.app.ui.main.diveHubIosScrollTabBarBottomInset()
    LazyColumn(
        modifier = modifier.background(exploreChromeColors().listBackground),
        contentPadding = PaddingValues(
            start = IosDesign.ScreenPadding,
            end = IosDesign.ScreenPadding,
            top = 2.dp,
            bottom = IosDesign.ScreenPadding + bottomBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sites, key = { it.id }) { site ->
            SiteCard(
                site = site,
                userLatLng = userLatLng,
                onTap = { onTap(site) },
                onAddToTrip = onAddToTrip?.takeIf { site.kind == ExploreItemKind.DIVE_SITE }?.let { cb ->
                    { cb(site) }
                },
            )
        }
    }
}

@Composable
private fun exploreDiveTypeLabel(typeKey: String): String = when (typeKey.lowercase()) {
    ExploreFilterKeys.TYPE_REEF -> stringResource(R.string.explore_reef)
    ExploreFilterKeys.TYPE_WRECK -> stringResource(R.string.explore_wreck)
    ExploreFilterKeys.TYPE_CAVE -> stringResource(R.string.explore_cave)
    "dive_center" -> stringResource(R.string.explore_filter_dive_center)
    ExploreFilterKeys.SHOP_GEAR -> stringResource(R.string.explore_filter_gear)
    ExploreFilterKeys.SHOP_RENTAL -> stringResource(R.string.explore_filter_rental)
    "online", "offline" -> typeKey.replaceFirstChar { it.uppercase() }
    else -> typeKey.replaceFirstChar { it.uppercase() }
}

@Composable
private fun exploreDifficultyLabel(difficultyKey: String): String = when (difficultyKey.lowercase()) {
    ExploreFilterKeys.DIFF_BEGINNER -> stringResource(R.string.explore_beginner)
    ExploreFilterKeys.DIFF_INTERMEDIATE -> stringResource(R.string.explore_intermediate)
    ExploreFilterKeys.DIFF_ADVANCED -> stringResource(R.string.explore_advanced)
    ExploreFilterKeys.DIFF_EXPERT -> stringResource(R.string.explore_expert)
    ExploreFilterKeys.CENTER_NITROX -> stringResource(R.string.explore_filter_nitrox_chip)
    ExploreFilterKeys.CENTER_STANDARD -> stringResource(R.string.explore_filter_standard)
    ExploreFilterKeys.SHOP_SERVICE -> stringResource(R.string.explore_filter_service_chip)
    ExploreFilterKeys.SHOP_STORE -> stringResource(R.string.explore_filter_store)
    else -> difficultyKey.replaceFirstChar { it.uppercase() }
}

@Composable
private fun SiteCard(
    site: ExploreDiveSite,
    userLatLng: Pair<Double, Double>?,
    onTap: () -> Unit,
    onAddToTrip: (() -> Unit)? = null,
) {
    val isDark = LocalDiveHubDarkTheme.current
    val shape = IosDesign.CardCorner
    val divePrimary = Color(0xFF0080CC)
    // iOS ListCard: VStack(spacing: 12) + .padding() — keep 12 between blocks,
    // but never use Material TextButton (48dp min height) for the trip action.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 10.dp else 4.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.08f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.08f),
            )
            .background(iosGroupedCardColor(), shape)
            .border(1.dp, iosHairlineStrokeColor(if (isDark) 0.12f else 0.06f), shape)
            .clip(shape)
            .clickable { onTap() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    site.name,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosLabelPrimaryColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SiteCardSubtitle(site)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                userLatLng?.let { (ulat, ulng) ->
                    val d = distanceMeters(ulat, ulng, site.latitude, site.longitude)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            formatDistanceMeters(d),
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            color = Color(0xFF007AFF),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFCC00),
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        "%.1f".format(site.rating),
                        fontSize = 15.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabelPrimaryColor(),
                    )
                    Text(
                        "(${site.reviewCount})",
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        color = iosSecondaryMutedTextColor(),
                    )
                }
            }
        }

        when (site.kind) {
            ExploreItemKind.DIVE_CENTER -> DiveCenterDetailChips(site)
            ExploreItemKind.DIVE_SITE -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    ExploreDetailChip(
                        icon = Icons.Outlined.BarChart,
                        text = exploreDifficultyLabel(site.difficulty),
                    )
                }
            }
            ExploreItemKind.SHOP -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    ExploreDetailChip(
                        icon = Icons.Default.LocationOn,
                        text = site.country.ifBlank { stringResource(R.string.explore_unknown) },
                    )
                }
            }
        }

        if (onAddToTrip != null) {
            // Compact caption control — iOS `.font(.caption)` Label, not Material min-touch button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onAddToTrip)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = divePrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stringResource(R.string.explore_add_to_trip),
                        color = divePrimary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteCardSubtitle(site: ExploreDiveSite) {
    val secondary = iosSecondaryMutedTextColor()
    when (site.kind) {
        ExploreItemKind.DIVE_CENTER -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    site.region.ifBlank { site.country }.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                site.certificationAgency?.trim()?.takeIf { it.isNotEmpty() }?.let { agency ->
                    Text("·", color = secondary, fontSize = 12.sp)
                    Text(
                        agency,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        ExploreItemKind.DIVE_SITE -> {
            // iOS ListCard: logo + type • ↓ depth
            val typeLabel = exploreDiveTypeLabel(site.diveType)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DiveHubLogoMark(
                        modifier = Modifier.size(16.dp),
                        color = secondary,
                    )
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = secondary,
                    )
                }
                if (site.depthMax > 0) {
                    Text("•", color = secondary, fontSize = 12.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = secondary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            "${site.depthMax.toInt()}m",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = secondary,
                        )
                    }
                }
            }
        }
        ExploreItemKind.SHOP -> {
            val typeLabel = exploreDiveTypeLabel(site.diveType)
            val loc = listOfNotNull(
                site.region.takeIf { it.isNotBlank() },
                site.country.takeIf { it.isNotBlank() },
            ).joinToString(", ")
            Text(
                if (loc.isNotBlank()) "$loc · $typeLabel" else typeLabel,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = secondary,
            )
        }
    }
}

@Composable
private fun DiveCenterDetailChips(site: ExploreDiveSite) {
    val phone = site.phone
        ?.split(',', ';', '\n', '/', '|')
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotEmpty() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        if (site.listingOnly) {
            ExploreDetailChip(
                icon = Icons.Default.Info,
                text = stringResource(R.string.explore_listing_chip),
            )
        }
        if (site.nitroxAvailable) {
            ExploreDetailChip(
                icon = Icons.Default.Air,
                text = stringResource(R.string.explore_filter_nitrox_chip),
            )
        }
        site.certificationAgency?.trim()?.takeIf { it.isNotEmpty() }?.let { agency ->
            ExploreDetailChip(icon = Icons.Default.Verified, text = agency)
        }
        phone?.let { ExploreDetailChip(icon = Icons.Default.Phone, text = it) }
    }
}

/** iOS `DetailChip` — icon + caption in a soft gray rounded rect. */
@Composable
private fun ExploreDetailChip(
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(iosSegmentTrackColor())
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iosSecondaryMutedTextColor(),
            modifier = Modifier.size(11.dp),
        )
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = iosSecondaryMutedTextColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun exploreKindToChatPeerType(kind: ExploreItemKind): String = when (kind) {
    ExploreItemKind.DIVE_CENTER -> "dive_center"
    ExploreItemKind.SHOP -> "shop"
    ExploreItemKind.DIVE_SITE -> "user"
}

@Composable
internal fun ExploreDiveSiteDetailContent(
    site: ExploreDiveSite,
    graph: AppGraph,
    onReviewSubmitted: () -> Unit,
    innerNav: NavController,
    onRequestClose: () -> Unit,
    onReportInaccuracy: (() -> Unit)? = null,
    onShowOnMap: () -> Unit = {},
    onBusinessChat: (() -> Unit)? = null,
    /** When true, map/report live in the nav bar (iOS DiveSiteDetailView). */
    chromeInNavBar: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var reviews by remember { mutableStateOf<List<ReviewDto>>(emptyList()) }
    var reviewsLoading by remember { mutableStateOf(true) }
    var loggedIn by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var showReviewDialog by remember { mutableStateOf(false) }
    val myReview = reviews.firstOrNull { !it.userId.isNullOrBlank() && it.userId == currentUserId }

    LaunchedEffect(site.id, site.kind) {
        loggedIn = !graph.tokenStore.getAccessToken().isNullOrBlank()
        currentUserId = graph.tokenStore.getUserJson()?.let { raw ->
            runCatching { graph.gson.fromJson(raw, UserDto::class.java).id }.getOrNull()
        }
        reviewsLoading = true
        // iOS ReviewsSection loads for everyone; add button stays auth-gated.
        reviews = runCatching {
            ReviewsRepository(graph).listReviews(site.kind.toApiReviewType(), site.id)
        }.getOrElse { emptyList() }
        reviewsLoading = false
    }

    val divePrimary = Color(0xFF0080CC)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (site.kind != ExploreItemKind.DIVE_SITE) {
            if (!chromeInNavBar) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(site.name, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onShowOnMap) {
                        Text(stringResource(R.string.explore_show_on_map))
                    }
                }
            }
            if (onBusinessChat != null && loggedIn) {
                IosBorderedButton(
                    onClick = { onBusinessChat.invoke() },
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
                IosBorderedButton(
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
                IosBorderedButton(
                    onClick = {
                        onRequestClose()
                        innerNav.navigate(InnerRoutes.shopPublic(site.id))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.shop_public_open_profile))
                }
            }
            val loc = listOfNotNull(site.region, site.country).filter { it.isNotBlank() }.joinToString(", ")
            if (loc.isNotBlank()) {
                Text(loc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Title + rating — prefer live reviews so header matches the list below.
        val displayReviewCount = if (!reviewsLoading) reviews.size else site.reviewCount
        val displayRating = when {
            !reviewsLoading && reviews.isNotEmpty() -> reviews.map { it.rating.toDouble() }.average()
            !reviewsLoading -> 0.0
            else -> site.rating
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    site.name,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = iosLabelPrimaryColor(),
                )
                Text(
                    exploreDiveTypeLabel(site.diveType),
                    fontSize = 15.sp,
                    color = iosSecondaryMutedTextColor(),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFCC00), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "%.1f".format(displayRating),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabelPrimaryColor(),
                    )
                }
                Text(
                    stringResource(R.string.explore_reviews_count, displayReviewCount),
                    color = iosSecondaryMutedTextColor(),
                    fontSize = 12.sp,
                )
            }
        }

        HorizontalDivider(color = iosHairlineStrokeColor(0.18f), thickness = 0.5.dp)

        if (site.kind == ExploreItemKind.DIVE_SITE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DiveSiteInfoRow(
                    icon = Icons.Outlined.Speed,
                    label = stringResource(R.string.explore_max_depth),
                    value = "${site.depthMax.toInt()}m",
                    iconTint = divePrimary,
                )
                DiveSiteInfoRow(
                    icon = Icons.Outlined.BarChart,
                    label = stringResource(R.string.explore_avg_depth),
                    value = "${site.depthAvg.toInt()}m",
                    iconTint = divePrimary,
                )
                DiveSiteInfoRow(
                    icon = Icons.Outlined.Warning,
                    label = stringResource(R.string.explore_difficulty),
                    value = exploreDifficultyLabel(site.difficulty),
                    iconTint = divePrimary,
                )
            }

            if (site.description.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.explore_description_section),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabelPrimaryColor(),
                    )
                    Text(
                        site.description,
                        fontSize = 17.sp,
                        color = iosLabelPrimaryColor(),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.explore_recent_dives),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosLabelPrimaryColor(),
                )
                Text(
                    stringResource(R.string.explore_no_recent_dives),
                    color = iosSecondaryMutedTextColor(),
                    fontSize = 15.sp,
                )
            }
        } else {
            DiveSiteInfoRow(
                icon = Icons.Outlined.Warning,
                label = stringResource(R.string.explore_difficulty),
                value = exploreDifficultyLabel(site.difficulty),
                iconTint = divePrimary,
            )
        }

        // iOS ReviewsSection: common.reviews ("отзывов") + `.headline`
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.common_reviews),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = iosLabelPrimaryColor(),
            )
            if (loggedIn) {
                TextButton(
                    onClick = { showReviewDialog = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Default.AddCircle, null, tint = divePrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(
                            if (myReview != null) R.string.review_edit else R.string.explore_add_review,
                        ),
                        color = divePrimary,
                        fontSize = 15.sp,
                    )
                }
            }
        }
        when {
            reviewsLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chat_loading), style = MaterialTheme.typography.bodySmall)
            }
            reviews.isEmpty() -> Text(
                stringResource(R.string.explore_no_reviews_yet),
                color = iosSecondaryMutedTextColor(),
                fontSize = 15.sp,
            )
            else -> reviews.forEach { r ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = iosHairlineStrokeColor(0.12f))
                ReviewListRow(
                    r = r,
                    isMine = !r.userId.isNullOrBlank() && r.userId == currentUserId,
                    onEdit = { showReviewDialog = true },
                )
            }
        }

        if (site.kind != ExploreItemKind.DIVE_SITE) {
            if (site.description.isNotBlank()) {
                Text(site.description, style = MaterialTheme.typography.bodyMedium, color = iosLabelPrimaryColor())
            } else {
                Text(
                    stringResource(R.string.explore_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = iosSecondaryMutedTextColor(),
                )
            }
        }
    }

    if (showReviewDialog) {
        AddReviewableDialog(
            reviewableType = site.kind.toApiReviewType(),
            reviewableId = site.id,
            graph = graph,
            existing = myReview,
            onDismiss = { showReviewDialog = false },
            onSuccess = {
                val editingMine = myReview != null
                showReviewDialog = false
                scope.launch {
                    reviews = runCatching {
                        ReviewsRepository(graph).listReviews(site.kind.toApiReviewType(), site.id)
                    }.getOrElse { emptyList() }
                    val stillMine = reviews.any { it.userId == currentUserId }
                    Toast.makeText(
                        context,
                        context.getString(
                            if (editingMine && !stillMine) R.string.review_deleted else R.string.review_sent,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                onReviewSubmitted()
            },
        )
    }
}

@Composable
private fun DiveSiteInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(width = 24.dp, height = 22.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            label,
            color = iosSecondaryMutedTextColor(),
            fontSize = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = iosLabelPrimaryColor(),
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
            repeat(5) { index ->
                Icon(
                    imageVector = if (index < r.rating.coerceIn(1, 5)) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.Star
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (index < r.rating.coerceIn(1, 5)) Color(0xFFF2C94C) else iosSecondaryMutedTextColor(),
                )
            }
        }
        Text(r.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit) {
    IosErrorState(error = error, onRetry = onRetry)
}
