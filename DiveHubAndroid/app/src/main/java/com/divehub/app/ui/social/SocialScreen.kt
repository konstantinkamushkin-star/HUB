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
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.divehub.app.data.remote.dto.FriendRequestDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.ui.theme.IosDesign

@Composable
fun SocialRoute(
    graph: AppGraph,
    innerNav: NavController,
    onOpenChat: (String) -> Unit,
) {
    val vm: SocialViewModel = viewModel(factory = SocialViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var requestsTab by remember { mutableIntStateOf(0) } // 0 = received, 1 = sent

    when {
        state.loading -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        state.error != null -> Column(Modifier.fillMaxSize().padding(16.dp)) { Text(state.error ?: stringResource(R.string.social_error_generic)) }
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
