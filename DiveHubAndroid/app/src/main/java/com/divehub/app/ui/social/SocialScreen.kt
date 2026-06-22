package com.divehub.app.ui.social

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.divehub.app.util.absoluteMediaUrl
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.data.remote.dto.FriendLocationDto
import com.divehub.app.data.remote.dto.FriendRequestDto
import com.divehub.app.data.remote.dto.GroupTripDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.ui.explore.rememberUserLatLngForMap
import com.divehub.app.ui.theme.IosDesign

@Composable
fun SocialRoute(
    graph: AppGraph,
    innerNav: NavController,
    onOpenChat: (String) -> Unit,
) {
    val vm: SocialViewModel = viewModel(factory = SocialViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var mainTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 2 }

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

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = mainTab) {
            Tab(
                selected = mainTab == 0,
                onClick = { mainTab = 0 },
                text = { Text(stringResource(R.string.social_friends)) },
            )
            Tab(
                selected = mainTab == 1,
                onClick = { mainTab = 1 },
                text = { Text(stringResource(R.string.social_tab_tracking)) },
            )
        }
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
    var requestsTab by remember { mutableIntStateOf(0) }
    val userLatLng = rememberUserLatLngForMap()
    LaunchedEffect(userLatLng) {
        val (lat, lng) = userLatLng ?: return@LaunchedEffect
        vm.loadDiversNearby(lat, lng)
    }

    when {
        state.loading -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        state.error != null -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(state.error ?: stringResource(R.string.social_error_generic))
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(IosDesign.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(IosDesign.SectionSpacing),
        ) {
            item {
                Text(stringResource(R.string.social_add_friend), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = vm::setSearchQuery,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 50.dp),
                        label = { Text(stringResource(R.string.social_name_or_email)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                        ),
                    )
                    TextButton(
                        onClick = vm::searchUsers,
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    ) { Text(stringResource(R.string.social_search)) }
                }
                if (state.searching) {
                    Spacer(Modifier.height(6.dp))
                    CircularProgressIndicator()
                }
                if (!state.searchError.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    val msg = if (state.searchError == SocialViewModel.ERR_MIN_QUERY) {
                        stringResource(R.string.social_min_query)
                    } else {
                        state.searchError ?: ""
                    }
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }
                if (state.searchResults.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                }
            }
            items(state.searchResults, key = { it.id }) { user ->
                SearchUserCard(
                    user = user,
                    imageApiRoot = state.imageApiRoot,
                    onAdd = { vm.sendRequest(user.id) },
                    onOpenProfile = { innerNav.navigate(InnerRoutes.userProfile(user.id)) },
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.social_divers_nearby), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                if (userLatLng == null) {
                    Text(
                        stringResource(R.string.social_location_permission_for_map),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val discoverMapPins = remember(state.discoverNearby, userLatLng) {
                        val (lat, lng) = userLatLng!!
                        discoverPinsForMap(state.discoverNearby).toMutableList().apply {
                            add(
                                SocialMapPin(
                                    id = "self",
                                    latitude = lat,
                                    longitude = lng,
                                    title = "You",
                                    kind = SocialMapPinKind.Self,
                                ),
                            )
                        }
                    }
                    SocialFriendMapOsm(
                        pins = discoverMapPins,
                        mapHeight = 320.dp,
                        onPinTap = { pin ->
                            pin.userId?.let { innerNav.navigate(InnerRoutes.userProfile(it)) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.discoverNearby.isEmpty()) {
                        Text(
                            stringResource(R.string.social_discover_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.discoverNearby, key = { it.userId }) { user ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(IosDesign.ScreenPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { innerNav.navigate(InnerRoutes.userProfile(user.userId)) },
                        ) {
                            Text(user.displayName(), fontWeight = FontWeight.SemiBold)
                            Text("%.1f km".format(user.distanceKm), style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { vm.sendRequest(user.userId) }) {
                            Text(stringResource(R.string.social_add_friend))
                        }
                    }
                }
            }
            item { HorizontalDivider() }
            item {
                Text(stringResource(R.string.social_friend_requests), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = requestsTab == 0,
                        onClick = { requestsTab = 0 },
                        label = { Text(stringResource(R.string.social_received)) },
                    )
                    FilterChip(
                        selected = requestsTab == 1,
                        onClick = { requestsTab = 1 },
                        label = { Text(stringResource(R.string.social_sent)) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (requestsTab == 0 && state.received.isEmpty()) {
                    Text(stringResource(R.string.social_no_pending_received), style = MaterialTheme.typography.bodyMedium)
                } else if (requestsTab == 1 && state.sent.isEmpty()) {
                    Text(stringResource(R.string.social_no_pending_sent), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (requestsTab == 0) {
                items(state.received, key = { it.id }) { req ->
                    RequestCard(
                        req = req,
                        imageApiRoot = state.imageApiRoot,
                        onAccept = { vm.accept(req.user.id) },
                        onDecline = { vm.decline(req.id) },
                    )
                }
            } else {
                items(state.sent, key = { it.id }) { req ->
                    SentRequestCard(req = req, imageApiRoot = state.imageApiRoot)
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.social_friends), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                if (state.friends.isEmpty()) {
                    Text(stringResource(R.string.social_no_friends), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(state.friends, key = { it.id }) { friend ->
                FriendCard(
                    friend = friend,
                    imageApiRoot = state.imageApiRoot,
                    onOpenProfile = { innerNav.navigate(InnerRoutes.userProfile(friend.id)) },
                    onOpenChat = { onOpenChat(friend.id) },
                )
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
        OutlinedButton(
            onClick = { showCreateTrip = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.groupTripsLoading,
        ) {
            Text(stringResource(R.string.social_create_trip))
        }
        if (state.groupTrips.isEmpty()) {
            Text(
                stringResource(R.string.social_no_group_trips),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.groupTrips.forEach { trip ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = IosDesign.CardCorner,
                ) {
                    Column(Modifier.padding(IosDesign.ScreenPadding)) {
                        Text(trip.name, fontWeight = FontWeight.SemiBold)
                        trip.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${trip.participants.size} members",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { innerNav.navigate(InnerRoutes.tripGroupChatOpen(trip.chatId)) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.social_open_group_chat))
                        }
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
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedFriendIds = if (selected) {
                                selectedFriendIds - friend.id
                            } else {
                                selectedFriendIds + friend.id
                            }
                        },
                        label = { Text(friend.displayName()) },
                    )
                }
            }
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = {
                        saveError = null
                        if (tripName.isBlank()) {
                            saveError = "Name required"
                            return@Button
                        }
                        vm.createGroupTrip(
                            name = tripName.trim(),
                            description = tripDescription.trim().ifBlank { null },
                            destination = destination.trim().ifBlank { null },
                            startDate = startDate.trim().ifBlank { null } ?: java.time.LocalDate.now().toString(),
                            memberIds = selectedFriendIds.toList(),
                            onSuccess = onCreated,
                            onFailure = { saveError = it.message ?: "Error" },
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = tripName.isNotBlank() && !state.groupTripsLoading,
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
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
    val userLatLng = rememberUserLatLngForMap()
    LaunchedEffect(userLatLng) {
        val (lat, lng) = userLatLng ?: return@LaunchedEffect
        vm.loadFriendLocations(lat, lng)
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val mapPins = remember(state.friendLocations, userLatLng) {
        val pins = friendPinsForMap(state.friendLocations).toMutableList()
        userLatLng?.let { (lat, lng) ->
            pins.add(
                SocialMapPin(
                    id = "self",
                    latitude = lat,
                    longitude = lng,
                    title = "You",
                    kind = SocialMapPinKind.Self,
                ),
            )
        }
        pins
    }

    Column(Modifier.fillMaxSize().padding(IosDesign.ScreenPadding)) {
        Spacer(Modifier.height(8.dp))
        if (userLatLng == null) {
            Text(
                stringResource(R.string.social_location_permission_for_map),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
        } else {
            SocialFriendMapOsm(
                pins = mapPins,
                onPinTap = { pin ->
                    pin.userId?.let { innerNav.navigate(InnerRoutes.userProfile(it)) }
                },
            )
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(IosDesign.SectionSpacing),
        ) {
            if (userLatLng == null) {
                item {
                    Text(
                        stringResource(R.string.social_enable_location_access),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.friendLocations.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.social_no_friend_locations),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.friendLocations, key = { it.userId }) { pin ->
                    FriendLocationCard(pin, state.imageApiRoot) {
                        innerNav.navigate(InnerRoutes.userProfile(pin.userId))
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendLocationCard(
    pin: FriendLocationDto,
    apiRoot: String,
    onOpenProfile: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenProfile),
        shape = IosDesign.CardCorner,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(IosDesign.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                displayName = pin.displayName(),
                avatarUrl = pin.avatarUrl,
                apiRoot = apiRoot,
                size = IosDesign.AvatarSizeSmall,
            )
            Spacer(Modifier.size(8.dp))
            Column {
                Text(pin.displayName(), fontWeight = FontWeight.SemiBold)
                val dist = pin.distanceKm?.let { "%.1f km · ".format(it) }.orEmpty()
                Text(dist + pin.updatedAt, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SearchUserCard(
    user: UserDto,
    imageApiRoot: String,
    onAdd: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(IosDesign.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenProfile),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    displayName = user.displayName(),
                    avatarUrl = user.avatarUrl,
                    apiRoot = imageApiRoot,
                    size = IosDesign.AvatarSizeSmall,
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.displayName(), fontWeight = FontWeight.SemiBold)
                    Text(user.email, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = IosDesign.CardCorner,
            ) { Text(stringResource(R.string.social_add)) }
        }
    }
}

@Composable
private fun SentRequestCard(req: FriendRequestDto, imageApiRoot: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(IosDesign.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
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
}

@Composable
private fun FriendCard(
    friend: UserDto,
    imageApiRoot: String,
    onOpenProfile: () -> Unit,
    onOpenChat: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(IosDesign.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenProfile),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    displayName = friend.displayName(),
                    avatarUrl = friend.avatarUrl,
                    apiRoot = imageApiRoot,
                    size = IosDesign.AvatarSizeLarge,
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(friend.displayName(), fontWeight = FontWeight.SemiBold)
                    Text(friend.email, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(
                onClick = onOpenChat,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                shape = IosDesign.CardCorner,
            ) { Text(stringResource(R.string.social_chat)) }
        }
    }
}

@Composable
private fun RequestCard(
    req: FriendRequestDto,
    imageApiRoot: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(IosDesign.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            UserAvatar(
                displayName = req.user.displayName(),
                avatarUrl = req.user.avatarUrl,
                apiRoot = imageApiRoot,
                size = IosDesign.AvatarSizeLarge,
            )
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(req.user.displayName(), fontWeight = FontWeight.SemiBold)
                Text(req.user.email, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onAccept) {
                    Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.social_accept))
                }
                OutlinedButton(onClick = onDecline) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.social_decline))
                }
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
    val trimmed = avatarUrl?.trim().orEmpty()
    val resolved = if (trimmed.isEmpty()) "" else absoluteMediaUrl(apiRoot, trimmed)
    val showImage = resolved.isNotBlank() &&
        resolved.startsWith("http", ignoreCase = true)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (showImage) {
            AsyncImage(
                model = resolved,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(initial, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
