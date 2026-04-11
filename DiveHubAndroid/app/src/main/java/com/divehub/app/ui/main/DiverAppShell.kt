package com.divehub.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.diveHubApp
import com.divehub.app.ui.chat.ChatRoute
import com.divehub.app.ui.diveeditor.DiveEditorRoute
import com.divehub.app.ui.explore.ExploreRoute
import com.divehub.app.ui.feed.FeedRoute
import com.divehub.app.ui.logbook.LogbookRoute
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.Routes
import com.divehub.app.ui.profile.ProfileScreen
import com.divehub.app.ui.social.SocialRoute

private val IosNavBlue = Color(0xFF007AFF)
private val IosBarFill = Color(0xE6FFFFFF)
private val IosScreenBg = Color(0xFFF2F2F7)
private val IosActiveIconBg = Color(0xFFE8E8ED)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiverAppShell(
    graph: AppGraph,
    innerNav: NavController,
    rootNav: NavController,
    sessionVm: SessionViewModel,
    onLoggedOut: () -> Unit,
) {
    val user by sessionVm.user.collectAsState()
    var diveEditorEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        diveEditorEnabled = graph.tokenStore.isDiveEditorEnabled()
    }

    var tab by remember { mutableIntStateOf(0) }
    var openChatFriendId by remember { mutableStateOf<String?>(null) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val app = LocalContext.current.diveHubApp()

    LaunchedEffect(Unit) {
        app.diverTabEvents.collect { tab = it }
    }

    Scaffold(
        containerColor = IosScreenBg,
        contentColor = Color.Black,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(IosScreenBg),
        ) {
            val bottomInset = 88.dp
            when (tab) {
                0 -> ExploreRoute(graph = graph, innerNav = innerNav)
                1 -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) { FeedRoute(graph) }
                2 -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) { LogbookRoute(graph) }
                3 -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                    SocialRoute(
                        graph = graph,
                        innerNav = innerNav,
                        onOpenChat = { friendId ->
                            openChatFriendId = friendId
                            tab = 4
                        },
                    )
                }
                4 -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                    ChatRoute(
                        graph = graph,
                        openFriendId = openChatFriendId,
                        onOpenFriendConsumed = { openChatFriendId = null },
                    )
                }
                5 -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                    if (diveEditorEnabled) {
                        DiveEditorRoute()
                    } else {
                        ProfileScreen(
                            graph = graph,
                            sessionVm = sessionVm,
                            user = user,
                            innerNav = innerNav,
                            rootNav = rootNav,
                            onLoggedOut = onLoggedOut,
                        )
                    }
                }
                6 -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                    ProfileScreen(
                        graph = graph,
                        sessionVm = sessionVm,
                        user = user,
                        innerNav = innerNav,
                        rootNav = rootNav,
                        onLoggedOut = onLoggedOut,
                    )
                }
            }

            val selectedBarIndex = when (tab) {
                in 0..4 -> tab
                else -> 5
            }

            IosFloatingTabBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
                selectedIndex = selectedBarIndex,
                onExplore = { tab = 0 },
                onFeed = { tab = 1 },
                onLogbook = { tab = 2 },
                onSocial = { tab = 3 },
                onChat = { tab = 4 },
                onMore = { showMoreSheet = true },
            )
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                TextButton(
                    onClick = {
                        innerNav.navigate(InnerRoutes.Trips)
                        showMoreSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.nav_trips))
                }
                TextButton(
                    onClick = {
                        innerNav.navigate(InnerRoutes.Notifications)
                        showMoreSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.screen_notifications))
                }
                TextButton(
                    onClick = {
                        innerNav.navigate(InnerRoutes.Help)
                        showMoreSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.screen_help))
                }
                TextButton(
                    onClick = {
                        rootNav.navigate(Routes.PartnerRegistration)
                        showMoreSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.help_partner_application_button))
                }
                if (diveEditorEnabled) {
                    TextButton(
                        onClick = {
                            tab = 5
                            showMoreSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.nav_dive_editor))
                    }
                    TextButton(
                        onClick = {
                            tab = 6
                            showMoreSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.nav_profile))
                    }
                } else {
                    TextButton(
                        onClick = {
                            tab = 5
                            showMoreSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.nav_profile))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun IosFloatingTabBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onExplore: () -> Unit,
    onFeed: () -> Unit,
    onLogbook: () -> Unit,
    onSocial: () -> Unit,
    onChat: () -> Unit,
    onMore: () -> Unit,
) {
    val items = listOf(
        Triple(Icons.Default.Search, stringResource(R.string.nav_explore), onExplore),
        Triple(Icons.AutoMirrored.Filled.Article, stringResource(R.string.nav_feed), onFeed),
        Triple(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.nav_logbook), onLogbook),
        Triple(Icons.Default.People, stringResource(R.string.nav_social), onSocial),
        Triple(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.nav_chat), onChat),
        Triple(Icons.Default.MoreHoriz, stringResource(R.string.nav_more), onMore),
    )
    Row(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(40.dp))
            .clip(RoundedCornerShape(40.dp))
            .background(IosBarFill)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, (icon, label, onClick) ->
            val selected = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick)
                    .padding(vertical = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (selected) IosActiveIconBg else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) IosNavBlue else Color(0xFF3C3C43),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) IosNavBlue else Color(0xFF3C3C43),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
