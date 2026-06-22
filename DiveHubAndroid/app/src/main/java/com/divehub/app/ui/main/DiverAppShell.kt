package com.divehub.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
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
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.chat.ChatRoute
import com.divehub.app.ui.diveeditor.DiveEditorRoute
import com.divehub.app.ui.explore.ExploreRoute
import com.divehub.app.ui.feed.FeedRoute
import com.divehub.app.ui.logbook.LogbookRoute
import com.divehub.app.ui.profile.ProfileScreen
import com.divehub.app.ui.social.SocialRoute
import com.divehub.app.ui.trips.TripsTabRoute

private val IosNavBlue = Color(0xFF007AFF)
private val IosBarFill = Color(0xE6FFFFFF)
private val IosScreenBg = Color(0xFFF2F2F7)
private val IosActiveIconBg = Color(0xFFE8E8ED)

/** Bottom area under scrollable content: tab row + gesture bar (matches iOS `tabBarContentHeight` + home indicator). */
private val DiverTabBarContentHeight = 52.dp

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
    val app = LocalContext.current.diveHubApp()

    LaunchedEffect(Unit) {
        app.diverTabEvents.collect { tab = it }
    }

    LaunchedEffect(Unit) {
        app.businessChatOpenRequests.collect { (peerType, peerId) ->
            innerNav.navigate(InnerRoutes.businessChatOpen(peerType, peerId))
        }
    }

    LaunchedEffect(Unit) {
        app.innerNavDeepLinkRequests.collect { route ->
            innerNav.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(diveEditorEnabled) {
        if (diveEditorEnabled) {
            if (tab == DiverTabIndices.PROFILE_NO_EDITOR) tab = DiverTabIndices.PROFILE_WITH_EDITOR
        } else {
            when (tab) {
                DiverTabIndices.PROFILE_WITH_EDITOR -> tab = DiverTabIndices.PROFILE_NO_EDITOR
                DiverTabIndices.DIVE_EDITOR -> tab = DiverTabIndices.EXPLORE
            }
        }
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
            val bottomInset = DiverTabBarContentHeight + 10.dp
            when (tab) {
                DiverTabIndices.EXPLORE -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                    ExploreRoute(graph = graph, innerNav = innerNav)
                }
                DiverTabIndices.FEED -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) { FeedRoute(graph, innerNav) }
                DiverTabIndices.LOGBOOK -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) { LogbookRoute(graph, innerNav) }
                DiverTabIndices.TRIPS -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                    TripsTabRoute(graph = graph, innerNav = innerNav, currentUser = user)
                }
                DiverTabIndices.SOCIAL -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                    SocialRoute(
                        graph = graph,
                        innerNav = innerNav,
                        onOpenChat = { friendId ->
                            openChatFriendId = friendId
                            tab = DiverTabIndices.CHAT
                        },
                    )
                }
                DiverTabIndices.CHAT -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
                    ChatRoute(
                        graph = graph,
                        openFriendId = openChatFriendId,
                        onOpenFriendConsumed = { openChatFriendId = null },
                    )
                }
                DiverTabIndices.DIVE_EDITOR -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
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
                DiverTabIndices.PROFILE_WITH_EDITOR -> Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
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

            DiverIosScrollTabBar(
                diveEditorEnabled = diveEditorEnabled,
                selectedTab = tab,
                onSelectTab = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * iOS `DiverTabView`: horizontal scroll, all diver roots visible (explore … profile),
 * `.regularMaterial` bar + divider.
 */
@Composable
private fun DiverIosScrollTabBar(
    diveEditorEnabled: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val entries: List<Triple<Int, ImageVector, String>> = buildList {
            add(Triple(DiverTabIndices.EXPLORE, Icons.Default.Search, stringResource(R.string.nav_explore)))
            add(Triple(DiverTabIndices.FEED, Icons.AutoMirrored.Filled.Article, stringResource(R.string.nav_feed)))
            add(Triple(DiverTabIndices.LOGBOOK, Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.nav_logbook)))
            add(Triple(DiverTabIndices.TRIPS, Icons.Default.Flight, stringResource(R.string.nav_trips)))
            add(Triple(DiverTabIndices.SOCIAL, Icons.Default.People, stringResource(R.string.nav_social)))
            add(Triple(DiverTabIndices.CHAT, Icons.AutoMirrored.Filled.Chat, stringResource(R.string.nav_chat)))
            if (diveEditorEnabled) {
                add(Triple(DiverTabIndices.DIVE_EDITOR, Icons.Default.CameraEnhance, stringResource(R.string.nav_dive_editor)))
                add(Triple(DiverTabIndices.PROFILE_WITH_EDITOR, Icons.Default.AccountCircle, stringResource(R.string.nav_profile)))
            } else {
                add(Triple(DiverTabIndices.PROFILE_NO_EDITOR, Icons.Default.AccountCircle, stringResource(R.string.nav_profile)))
            }
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = IosBarFill,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            HorizontalDivider(color = Color(0x33000000), thickness = 0.5.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
                    .height(DiverTabBarContentHeight)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                entries.forEach { (tabIndex, icon, label) ->
                    val selected = selectedTab == tabIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .widthIn(min = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectTab(tabIndex) }
                            .padding(vertical = 2.dp, horizontal = 2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
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
                        Spacer(Modifier.height(1.dp))
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
    }
}
