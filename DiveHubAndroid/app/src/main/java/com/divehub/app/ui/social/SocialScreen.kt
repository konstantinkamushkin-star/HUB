package com.divehub.app.ui.social

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.remote.dto.FriendLocationDto
import com.divehub.app.data.remote.dto.FriendRequestDto
import com.divehub.app.data.remote.dto.GroupTripDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.ui.components.DiveHubUserAvatar
import com.divehub.app.ui.components.IosBorderedButton
import com.divehub.app.ui.components.IosCapsuleChip
import com.divehub.app.ui.components.IosErrorState
import com.divehub.app.ui.components.IosFormSheetScaffold
import com.divehub.app.ui.components.IosGroupedDivider
import com.divehub.app.ui.components.IosGroupedListRow
import com.divehub.app.ui.components.IosGroupedSection
import com.divehub.app.ui.components.IosLargeTitle
import com.divehub.app.ui.components.IosMapChromeLocateButton
import com.divehub.app.ui.components.IosMapChromeZoomCluster
import com.divehub.app.ui.components.IosProminentButton
import com.divehub.app.ui.components.IosSearchField
import com.divehub.app.ui.components.IosSectionHeader
import com.divehub.app.ui.components.IosSegmentedControl
import com.divehub.app.ui.components.IosSettingsRow
import com.divehub.app.ui.explore.hasExploreLocationPermission
import com.divehub.app.ui.explore.rememberUserLatLngForMap
import com.divehub.app.ui.main.diveHubTabBarContentPadding
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.theme.IosDesign
import com.divehub.app.ui.theme.iosAccentLinkColor
import com.divehub.app.ui.theme.iosChromePageBackground
import com.divehub.app.ui.theme.iosGroupedCardColor
import com.divehub.app.ui.theme.iosSecondaryMutedTextColor
import com.divehub.app.ui.trips.TripBuddyMatchBlock
import com.divehub.app.util.absoluteMediaUrl
import com.divehub.app.util.haversineKmRounded1
import java.util.Locale

@Composable
fun SocialRoute(
    graph: AppGraph,
    innerNav: NavController,
    onOpenChat: (String) -> Unit,
) {
    val vm: SocialViewModel = viewModel(factory = SocialViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var mainTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 3 }

    LaunchedEffect(mainTab) {
        if (pagerState.currentPage != mainTab) {
            pagerState.animateScrollToPage(mainTab)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (mainTab != pagerState.currentPage) {
            mainTab = pagerState.currentPage
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(iosChromePageBackground()),
    ) {
        IosLargeTitle(title = stringResource(R.string.nav_social))
        IosSegmentedControl(
            segments = listOf(
                stringResource(R.string.social_friends),
                stringResource(R.string.social_tab_tracking),
                stringResource(R.string.social_tab_find_buddy),
            ),
            selectedIndex = mainTab,
            onSelect = { mainTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IosDesign.ScreenPadding)
                .padding(vertical = 8.dp),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
        ) { page ->
            when (page) {
                0 -> FriendsTabContent(
                    state = state,
                    vm = vm,
                    innerNav = innerNav,
                    onOpenChat = onOpenChat,
                )
                1 -> TrackingTabContent(state = state, vm = vm, onOpenChat = onOpenChat, innerNav = innerNav)
                else -> FindTabContent(
                    graph = graph,
                    innerNav = innerNav,
                    state = state,
                    vm = vm,
                    onOpenChat = onOpenChat,
                )
            }
        }
    }
}

@Composable
private fun FriendsTabContent(
    state: SocialUiState,
    vm: SocialViewModel,
    innerNav: NavController,
    onOpenChat: (String) -> Unit,
) {
    var showRequests by remember { mutableStateOf(false) }
    var friendsFilter by remember { mutableStateOf("") }
    val link = iosAccentLinkColor()
    val filteredFriends = remember(state.friends, friendsFilter) {
        val q = friendsFilter.trim().lowercase()
        if (q.isEmpty()) {
            state.friends
        } else {
            state.friends.filter { friend ->
                friend.displayName().lowercase().contains(q) ||
                    friend.email.lowercase().contains(q) ||
                    friend.username.orEmpty().lowercase().contains(q)
            }
        }
    }

    if (showRequests) {
        FriendRequestsSheet(
            state = state,
            vm = vm,
            onDismiss = {
                showRequests = false
                vm.refresh()
            },
        )
    }

    when {
        state.loading && state.friends.isEmpty() && state.error == null -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        state.error != null && state.friends.isEmpty() -> IosErrorState(
            error = state.error,
            onRetry = { vm.refresh() },
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = diveHubTabBarContentPadding(extra = IosDesign.ScreenPadding),
        ) {
            item {
                IosGroupedSection {
                    IosSettingsRow(
                        title = stringResource(R.string.social_friend_requests),
                        leadingIcon = Icons.Default.People,
                        onClick = { showRequests = true },
                        showChevron = false,
                        tint = link,
                    )
                }
            }
            item {
                IosSearchField(
                    query = friendsFilter,
                    onQueryChange = { friendsFilter = it },
                    placeholder = stringResource(R.string.social_search),
                    modifier = Modifier
                        .padding(horizontal = IosDesign.ScreenPadding)
                        .padding(top = 8.dp, bottom = 8.dp),
                )
            }
            item {
                IosSectionHeader(stringResource(R.string.social_friends))
            }
            item {
                IosGroupedSection {
                    if (filteredFriends.isEmpty()) {
                        Text(
                            stringResource(R.string.social_no_friends),
                            style = MaterialTheme.typography.bodyMedium,
                            color = iosSecondaryMutedTextColor(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    } else {
                        filteredFriends.forEachIndexed { index, friend ->
                            if (index > 0) IosGroupedDivider()
                            FriendRow(
                                friend = friend,
                                imageApiRoot = state.imageApiRoot,
                                onClick = { innerNav.navigate(InnerRoutes.userProfile(friend.id)) },
                                onMessage = { onOpenChat(friend.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindTabContent(
    graph: AppGraph,
    innerNav: NavController,
    state: SocialUiState,
    vm: SocialViewModel,
    onOpenChat: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(diveHubTabBarContentPadding(extra = 12.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TripBuddyMatchBlock(
            graph = graph,
            innerNav = innerNav,
            onOpenChat = onOpenChat,
            modifier = Modifier.padding(horizontal = IosDesign.ScreenPadding),
        )
        Spacer(Modifier.height(8.dp))
        DiscoverFriendsContent(
            state = state,
            vm = vm,
            innerNav = innerNav,
        )
    }
}

@Composable
private fun DiscoverFriendsContent(
    state: SocialUiState,
    vm: SocialViewModel,
    innerNav: NavController,
) {
    val userLatLng = rememberUserLatLngForMap()
    var mapActions by remember { mutableStateOf<SocialMapActions?>(null) }
    var didCenterDiscoverOnUser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadDiversNearby(20.0, 0.0, 20_015.0)
    }
    LaunchedEffect(userLatLng, mapActions) {
        val (lat, lng) = userLatLng ?: return@LaunchedEffect
        vm.syncOwnLocationIfSharing(lat, lng)
        if (!didCenterDiscoverOnUser && mapActions != null) {
            didCenterDiscoverOnUser = true
            mapActions?.centerOnZoom?.invoke(lat, lng, 10.0)
            // Initial nearby list around the viewer (not the world map center).
            vm.loadDiversNearby(lat, lng, 500.0, fromLat = lat, fromLng = lng)
        }
    }

    // Distance badge must be from the viewer GPS, even when the map is panned elsewhere.
    val discoverForDisplay = remember(state.discoverNearby, userLatLng) {
        val origin = userLatLng ?: return@remember state.discoverNearby
        val (olat, olng) = origin
        state.discoverNearby
            .map { u ->
                u.copy(
                    distanceKm = haversineKmRounded1(olat, olng, u.latitude, u.longitude),
                )
            }
            .sortedBy { it.distanceKm }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IosDesign.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IosSearchField(
                    query = state.searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    placeholder = stringResource(R.string.social_name_or_email),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = vm::searchUsers,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text(
                        stringResource(R.string.social_search),
                        color = IosDesign.Profile.linkBlue,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (state.searching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = IosDesign.ScreenPadding),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            if (!state.searchError.isNullOrBlank()) {
                val msg = if (state.searchError == SocialViewModel.ERR_MIN_QUERY) {
                    stringResource(R.string.social_min_query)
                } else {
                    state.searchError ?: ""
                }
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = IosDesign.ScreenPadding),
                )
            }
            state.searchResults.forEach { user ->
                SearchUserCard(
                    user = user,
                    imageApiRoot = state.imageApiRoot,
                    isPending = user.id in state.pendingRequestUserIds,
                    onAdd = { vm.sendRequest(user.id) },
                    onOpenProfile = { innerNav.navigate(InnerRoutes.userProfile(user.id)) },
                )
            }

            Text(
                text = stringResource(R.string.social_divers_nearby).uppercase(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IosDesign.ScreenPadding)
                    .padding(top = 4.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = iosSecondaryMutedTextColor(),
            )
            if (userLatLng == null) {
                Text(
                    stringResource(R.string.social_location_permission_for_map),
                    style = MaterialTheme.typography.bodySmall,
                    color = iosSecondaryMutedTextColor(),
                    modifier = Modifier.padding(horizontal = IosDesign.ScreenPadding),
                )
            }
            val discoverMapPins = remember(discoverForDisplay) {
                discoverPinsForMap(discoverForDisplay)
            }
            Box(
                Modifier
                    .padding(horizontal = IosDesign.ScreenPadding)
                    .clip(IosDesign.CardCorner)
                    .background(iosGroupedCardColor())
                    .nestedScroll(rememberMapPanNestedScrollConnection()),
            ) {
                SocialFriendMapOsm(
                    pins = discoverMapPins,
                    mapHeight = 220.dp,
                    showUserLocation = userLatLng != null,
                    userLatLng = userLatLng,
                    autoFitPins = false,
                    onPinTap = { pin ->
                        pin.userId?.let { innerNav.navigate(InnerRoutes.userProfile(it)) }
                    },
                    onActionsReady = { mapActions = it },
                    onViewportSettled = { lat, lng, radiusKm ->
                        val from = userLatLng
                        vm.loadDiversNearby(
                            lat,
                            lng,
                            radiusKm,
                            fromLat = from?.first,
                            fromLng = from?.second,
                        )
                    },
                )
            }
            Text(
                text = stringResource(R.string.social_map_explore_hint),
                style = MaterialTheme.typography.labelSmall,
                color = iosSecondaryMutedTextColor(),
                modifier = Modifier
                    .padding(horizontal = IosDesign.ScreenPadding)
                    .fillMaxWidth(),
            )
            if (discoverForDisplay.isEmpty()) {
                Text(
                    stringResource(R.string.social_discover_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = iosSecondaryMutedTextColor(),
                    modifier = Modifier.padding(horizontal = IosDesign.ScreenPadding),
                )
            } else {
                Column(
                    Modifier.padding(horizontal = IosDesign.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    discoverForDisplay.forEach { user ->
                        DiscoverNearbyCard(
                            name = user.displayName(),
                            distanceKm = user.distanceKm,
                            updatedAt = user.updatedAt,
                            imageApiRoot = state.imageApiRoot,
                            avatarUrl = user.avatarUrl,
                            isPending = user.userId in state.pendingRequestUserIds,
                            onOpenProfile = { innerNav.navigate(InnerRoutes.userProfile(user.userId)) },
                            onAdd = { vm.sendRequest(user.userId) },
                        )
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendRequestsSheet(
    state: SocialUiState,
    vm: SocialViewModel,
    onDismiss: () -> Unit,
) {
    var requestsTab by remember { mutableIntStateOf(0) }

    IosFormSheetScaffold(
        title = stringResource(R.string.social_friend_requests),
        onDismiss = onDismiss,
        cancelLabel = stringResource(R.string.common_cancel),
        onCancel = onDismiss,
        doneLabel = stringResource(R.string.common_done),
        onDone = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IosDesign.ScreenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IosCapsuleChip(
                    text = stringResource(R.string.social_received),
                    selected = requestsTab == 0,
                    onClick = { requestsTab = 0 },
                )
                IosCapsuleChip(
                    text = stringResource(R.string.social_sent),
                    selected = requestsTab == 1,
                    onClick = { requestsTab = 1 },
                )
            }
            when (requestsTab) {
                0 -> {
                    if (state.received.isEmpty()) {
                        Text(
                            stringResource(R.string.social_no_pending_received),
                            style = MaterialTheme.typography.bodyMedium,
                            color = iosSecondaryMutedTextColor(),
                            modifier = Modifier.padding(IosDesign.ScreenPadding),
                        )
                    } else {
                        IosGroupedSection {
                            state.received.forEachIndexed { index, req ->
                                if (index > 0) IosGroupedDivider()
                                RequestRow(
                                    req = req,
                                    imageApiRoot = state.imageApiRoot,
                                    onAccept = { vm.accept(req.user.id) },
                                    onDecline = { vm.decline(req.id) },
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (state.sent.isEmpty()) {
                        Text(
                            stringResource(R.string.social_no_pending_sent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = iosSecondaryMutedTextColor(),
                            modifier = Modifier.padding(IosDesign.ScreenPadding),
                        )
                    } else {
                        IosGroupedSection {
                            state.sent.forEachIndexed { index, req ->
                                if (index > 0) IosGroupedDivider()
                                SentRequestRow(req = req, imageApiRoot = state.imageApiRoot)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupTripsTabContent(
    state: SocialUiState,
    vm: SocialViewModel,
    innerNav: NavController,
    onOpenChat: (String) -> Unit,
) {
    var showCreateTrip by remember { mutableStateOf(false) }

    if (showCreateTrip) {
        CreateGroupTripSheet(
            state = state,
            vm = vm,
            onDismiss = { showCreateTrip = false },
            onCreated = { trip ->
                showCreateTrip = false
                innerNav.navigate(InnerRoutes.tripGroupChatOpen(trip.chatId))
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(IosDesign.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(IosDesign.SectionSpacing),
    ) {
        IosBorderedButton(
            onClick = { showCreateTrip = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.groupTripsLoading,
        ) {
            Text(stringResource(R.string.social_create_trip), color = IosDesign.Profile.linkBlue)
        }
        if (state.groupTrips.isEmpty()) {
            Text(
                stringResource(R.string.social_no_group_trips),
                style = MaterialTheme.typography.bodyMedium,
                color = IosDesign.Profile.secondaryLabel,
            )
        } else {
            IosGroupedSection {
                state.groupTrips.forEachIndexed { index, trip ->
                    if (index > 0) IosGroupedDivider()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { innerNav.navigate(InnerRoutes.tripGroupChatOpen(trip.chatId)) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(trip.name, fontWeight = FontWeight.SemiBold)
                        trip.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = IosDesign.Profile.secondaryLabel)
                        }
                        Text(
                            "${trip.participants.size} members",
                            style = MaterialTheme.typography.bodySmall,
                            color = IosDesign.Profile.secondaryLabel,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateGroupTripSheet(
    state: SocialUiState,
    vm: SocialViewModel,
    onDismiss: () -> Unit,
    onCreated: (GroupTripDto) -> Unit,
) {
    var tripName by remember { mutableStateOf("") }
    var tripDescription by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var selectedFriendIds by remember { mutableStateOf(setOf<String>()) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.refresh()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = IosDesign.ScreenPadding)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.social_create_trip),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = tripName,
                onValueChange = { tripName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.social_trip_name)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = tripDescription,
                onValueChange = { tripDescription = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.social_trip_description)) },
                minLines = 2,
            )
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.social_trip_destination)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.social_trip_start_date)) },
                singleLine = true,
                placeholder = { Text("YYYY-MM-DD") },
            )
            Text(
                stringResource(R.string.social_friends),
                style = MaterialTheme.typography.titleSmall,
            )
            if (state.friends.isEmpty()) {
                Text(
                    stringResource(R.string.social_no_friends),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.friends.forEach { friend ->
                    val selected = selectedFriendIds.contains(friend.id)
                    IosCapsuleChip(
                        text = friend.displayName(),
                        selected = selected,
                        onClick = {
                            selectedFriendIds = if (selected) {
                                selectedFriendIds - friend.id
                            } else {
                                selectedFriendIds + friend.id
                            }
                        },
                    )
                }
            }
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            IosProminentButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    saveError = null
                    if (tripName.isBlank()) {
                        saveError = "Name required"
                    } else {
                        vm.createGroupTrip(
                            name = tripName.trim(),
                            description = tripDescription.trim().ifBlank { null },
                            destination = destination.trim().ifBlank { null },
                            startDate = startDate.trim().ifBlank { null } ?: java.time.LocalDate.now().toString(),
                            memberIds = selectedFriendIds.toList(),
                            onSuccess = onCreated,
                            onFailure = { saveError = it.message ?: "Error" },
                        )
                    }
                },
                enabled = tripName.isNotBlank() && !state.groupTripsLoading,
            )
        }
    }
}

@Composable
private fun TrackingTabContent(
    state: SocialUiState,
    vm: SocialViewModel,
    innerNav: NavController,
    onOpenChat: (String) -> Unit,
) {
    val context = LocalContext.current
    var permissionRevision by remember { mutableIntStateOf(0) }
    var askedLocationOnce by remember { mutableStateOf(false) }
    var mapActions by remember { mutableStateOf<SocialMapActions?>(null) }
    var selectedPin by remember { mutableStateOf<SocialMapPin?>(null) }
    val hasLocationPermission = remember(permissionRevision) {
        hasExploreLocationPermission(context)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRevision++
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRevision++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        vm.startTrackingRefresh()
        onDispose { vm.stopTrackingRefresh() }
    }
    val userLatLng = rememberUserLatLngForMap(permissionRevision = permissionRevision)
    LaunchedEffect(Unit) {
        vm.loadFriendLocations(null, null)
    }
    LaunchedEffect(userLatLng) {
        val (lat, lng) = userLatLng ?: return@LaunchedEffect
        vm.loadFriendLocations(lat, lng)
    }

    fun requestOrOpenLocationAccess() {
        if (hasExploreLocationPermission(context)) {
            permissionRevision++
            return
        }
        val activity = context.findActivity()
        val fineRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } == true
        val coarseRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } == true
        if (askedLocationOnce && !fineRationale && !coarseRationale) {
            openAppDetailsSettings(context)
            return
        }
        askedLocationOnce = true
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    val mapPins = remember(state.friendLocations) {
        friendPinsForMap(state.friendLocations)
    }
    val tabBarInset = com.divehub.app.ui.main.diveHubIosScrollTabBarBottomInset()
    val hasFix = hasLocationPermission && userLatLng != null
    val anyFriendShares = remember(state.friends) {
        state.friends.any { it.shareLocation == true }
    }
    val selectedLocation = remember(selectedPin, state.friendLocations) {
        selectedPin?.userId?.let { id -> state.friendLocations.find { it.userId == id } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SocialFriendMapOsm(
            pins = mapPins,
            modifier = Modifier.fillMaxSize(),
            showUserLocation = hasFix,
            userLatLng = userLatLng,
            autoFitPins = true,
            onPinTap = { pin ->
                selectedPin = pin
            },
            onActionsReady = { mapActions = it },
        )

        if (!state.shareLocationEnabled) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp)
                    .clip(IosDesign.CardCorner)
                    .background(iosGroupedCardColor().copy(alpha = 0.92f))
                    .clickable { innerNav.navigate(InnerRoutes.PrivacySettings) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.social_location_sharing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = iosSecondaryMutedTextColor(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.social_open_privacy_settings),
                    color = iosAccentLinkColor(),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        when {
            selectedPin != null -> {
                val pin = selectedPin!!
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = tabBarInset + 12.dp)
                        .clip(IosDesign.CardCorner)
                        .background(iosGroupedCardColor().copy(alpha = 0.92f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DiveHubUserAvatar(
                        avatarUrl = selectedLocation?.avatarUrl,
                        apiRoot = state.imageApiRoot,
                        size = 48.dp,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pin.title, fontWeight = FontWeight.SemiBold)
                        val subtitle = rememberLocationUpdatedSubtitle(pin.distanceKm, pin.updatedAt)
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = iosSecondaryMutedTextColor(),
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            pin.userId?.let { innerNav.navigate(InnerRoutes.userProfile(it)) }
                        },
                    ) {
                        Text(
                            stringResource(R.string.social_view_profile),
                            color = iosAccentLinkColor(),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    pin.userId?.let { userId ->
                        IosBorderedButton(onClick = { onOpenChat(userId) }) {
                            Text(
                                stringResource(R.string.chat_message_label),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
            state.friendLocationsError != null -> {
                Text(
                    text = state.friendLocationsError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = tabBarInset + 12.dp)
                        .clip(IosDesign.CardCorner)
                        .background(iosGroupedCardColor().copy(alpha = 0.92f))
                        .clickable {
                            vm.loadFriendLocations(userLatLng?.first, userLatLng?.second)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            !hasFix -> {
                Text(
                    text = stringResource(R.string.social_location_permission_for_map),
                    style = MaterialTheme.typography.bodySmall,
                    color = iosSecondaryMutedTextColor(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = tabBarInset + 12.dp)
                        .clip(IosDesign.CardCorner)
                        .background(iosGroupedCardColor().copy(alpha = 0.92f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clickable { requestOrOpenLocationAccess() },
                )
            }
            mapPins.isEmpty() && !state.friendLocationsLoading -> {
                val emptyText = when {
                    state.friends.isEmpty() || anyFriendShares ->
                        stringResource(R.string.social_no_friend_locations)
                    else ->
                        stringResource(R.string.social_friends_location_off_hint)
                }
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = iosSecondaryMutedTextColor(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = tabBarInset + 12.dp)
                        .clip(IosDesign.CardCorner)
                        .background(iosGroupedCardColor().copy(alpha = 0.92f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = tabBarInset + 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            IosMapChromeZoomCluster(
                onZoomIn = { mapActions?.zoomIn?.invoke() },
                onZoomOut = { mapActions?.zoomOut?.invoke() },
            )
            IosMapChromeLocateButton(
                onClick = {
                    userLatLng?.let { (lat, lng) ->
                        mapActions?.centerOn?.invoke(lat, lng)
                    } ?: requestOrOpenLocationAccess()
                },
            )
        }
    }
}


private fun openAppDetailsSettings(context: android.content.Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun DiscoverNearbyCard(
    name: String,
    distanceKm: Double,
    updatedAt: String?,
    imageApiRoot: String,
    avatarUrl: String?,
    isPending: Boolean,
    onOpenProfile: () -> Unit,
    onAdd: () -> Unit,
) {
    val ageLabel = rememberLocationAgeOnly(updatedAt)
    val orange = Color(0xFFFF9500)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IosDesign.CardCorner)
            .background(iosGroupedCardColor())
            .clickable(onClick = onOpenProfile)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(2.5.dp, orange, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            UserAvatar(
                displayName = name,
                avatarUrl = avatarUrl,
                apiRoot = imageApiRoot,
                size = 44.dp,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "%.1f km".format(distanceKm),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = orange,
                    modifier = Modifier
                        .clip(IosDesign.CapsuleShape)
                        .background(orange.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                if (ageLabel.isNotBlank()) {
                    Text(
                        ageLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = iosSecondaryMutedTextColor(),
                        maxLines = 1,
                    )
                }
            }
        }
        if (isPending) {
            Text(
                text = stringResource(R.string.social_request_sent_wait),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = iosSecondaryMutedTextColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .clip(IosDesign.CapsuleShape)
                    .background(iosSecondaryMutedTextColor().copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else {
            TextButton(onClick = onAdd) {
                Text(
                    stringResource(R.string.social_add_short),
                    color = iosAccentLinkColor(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun rememberLocationAgeOnly(updatedAt: String?): String {
    val full = rememberLocationUpdatedSubtitle(distanceKm = null, updatedAt = updatedAt)
    val prefix = stringResource(R.string.social_location_updated)
    return full.removePrefix(prefix).trim().removePrefix("·").trim().ifBlank { full }
}

@Composable
private fun rememberLocationUpdatedSubtitle(distanceKm: Double?, updatedAt: String?): String {
    val updatedPrefix = stringResource(R.string.social_location_updated)
    val justNow = stringResource(R.string.social_location_updated_just_now)
    val about30 = stringResource(R.string.social_location_updated_about_30_min)
    val about1h = stringResource(R.string.social_location_updated_about_1_hour)
    val aboutHoursFmt = stringResource(R.string.social_location_updated_about_hours)
    val about1d = stringResource(R.string.social_location_updated_about_1_day)
    val aboutDaysFmt = stringResource(R.string.social_location_updated_about_days)
    val aboutWeeksFmt = stringResource(R.string.social_location_updated_about_weeks)
    val aboutMonthsFmt = stringResource(R.string.social_location_updated_about_months)
    return remember(
        distanceKm,
        updatedAt,
        updatedPrefix,
        justNow,
        about30,
        about1h,
        aboutHoursFmt,
        about1d,
        aboutDaysFmt,
        aboutWeeksFmt,
        aboutMonthsFmt,
    ) {
        com.divehub.app.util.RelativeLocationAge.subtitle(
            distanceKm = distanceKm,
            updatedAtIso = updatedAt,
            updatedPrefix = updatedPrefix,
            justNow = justNow,
            about30Min = about30,
            about1Hour = about1h,
            aboutHours = { n -> aboutHoursFmt.format(n) },
            about1Day = about1d,
            aboutDays = { n -> aboutDaysFmt.format(n) },
            aboutWeeks = { n -> aboutWeeksFmt.format(n) },
            aboutMonths = { n -> aboutMonthsFmt.format(n) },
        )
    }
}

@Composable
private fun FriendLocationCard(
    pin: FriendLocationDto,
    apiRoot: String,
    onOpenProfile: () -> Unit,
) {
    IosGroupedSection {
        IosGroupedListRow(
            title = pin.displayName(),
            subtitle = rememberLocationUpdatedSubtitle(pin.distanceKm, pin.updatedAt),
            onClick = onOpenProfile,
            showChevron = true,
        )
    }
}

@Composable
private fun SearchUserCard(
    user: UserDto,
    imageApiRoot: String,
    isPending: Boolean,
    onAdd: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    IosGroupedSection {
        IosGroupedListRow(
            title = user.displayName(),
            subtitle = user.email,
            onClick = onOpenProfile,
            trailing = {
                if (isPending) {
                    Text(
                        text = stringResource(R.string.social_request_sent_wait),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = iosSecondaryMutedTextColor(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 120.dp)
                            .clip(IosDesign.CapsuleShape)
                            .background(iosSecondaryMutedTextColor().copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                } else {
                    TextButton(onClick = onAdd) {
                        Text(stringResource(R.string.social_add))
                    }
                }
            },
            showChevron = false,
        )
    }
}

@Composable
private fun SentRequestRow(req: FriendRequestDto, imageApiRoot: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            displayName = req.user.displayName(),
            avatarUrl = req.user.avatarUrl,
            apiRoot = imageApiRoot,
            size = IosDesign.AvatarSizeSmall,
        )
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(req.user.displayName(), fontWeight = FontWeight.SemiBold)
            Text(req.user.email, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            stringResource(R.string.social_pending),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB25E00),
            modifier = Modifier
                .clip(IosDesign.SmallChipCorner)
                .background(Color(0xFFFFE9D1))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FriendRow(
    friend: UserDto,
    imageApiRoot: String,
    onClick: () -> Unit,
    onMessage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                displayName = friend.displayName(),
                avatarUrl = friend.avatarUrl,
                apiRoot = imageApiRoot,
                size = IosDesign.AvatarSizeMedium,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(friend.displayName(), fontWeight = FontWeight.SemiBold)
                Text(
                    socialRoleLabel(friend.role),
                    style = MaterialTheme.typography.bodySmall,
                    color = iosSecondaryMutedTextColor(),
                )
            }
        }
        IosBorderedButton(onClick = onMessage) {
            Text(
                stringResource(R.string.chat_message_label),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun socialRoleLabel(role: String?): String {
    val r = role?.trim()?.uppercase(Locale.ROOT).orEmpty()
    return when (r) {
        "DIVER_PRO", "DIVERPRO" -> stringResource(R.string.profile_role_diver_pro)
        "DIVER_BASIC", "DIVER", "" -> stringResource(R.string.profile_role_diver_basic)
        "INSTRUCTOR" -> stringResource(R.string.profile_role_instructor)
        "DIVE_CENTER_ADMIN" -> stringResource(R.string.profile_role_dive_center_admin)
        "SHOP_ADMIN" -> stringResource(R.string.profile_role_shop_admin)
        "SUPER_ADMIN" -> stringResource(R.string.profile_role_super_admin)
        else -> role?.trim().orEmpty().ifBlank { stringResource(R.string.profile_role_diver_basic) }
    }
}

@Composable
private fun RequestRow(
    req: FriendRequestDto,
    imageApiRoot: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            displayName = req.user.displayName(),
            avatarUrl = req.user.avatarUrl,
            apiRoot = imageApiRoot,
            size = IosDesign.AvatarSizeSmall,
        )
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(req.user.displayName(), fontWeight = FontWeight.SemiBold)
            Text(req.user.email, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onAccept) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.social_accept),
                    tint = IosDesign.Explore.filterActiveBlue,
                )
            }
            IconButton(onClick = onDecline) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.social_decline),
                    tint = IosDesign.destructiveRed,
                )
            }
        }
    }
}

@Composable
private fun UserAvatar(
    displayName: String,
    avatarUrl: String?,
    apiRoot: String,
    size: Dp,
) {
    DiveHubUserAvatar(
        avatarUrl = avatarUrl,
        apiRoot = apiRoot,
        size = size,
    )
}
