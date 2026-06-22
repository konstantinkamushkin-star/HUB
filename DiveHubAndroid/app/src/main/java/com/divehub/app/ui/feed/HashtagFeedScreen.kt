package com.divehub.app.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.theme.IosDesign
import com.divehub.app.ui.theme.iosChromePageBackground
import com.divehub.app.util.FeedHashtagParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagFeedRoute(
    graph: AppGraph,
    innerNav: NavController,
    tag: String,
) {
    val vm: HashtagFeedViewModel = viewModel(
        factory = HashtagFeedViewModel.factory(graph, tag),
    )
    val state by vm.state.collectAsState()
    val displayTag = rememberDisplayTag(tag)

    LaunchedEffect(tag) { vm.load() }

    Scaffold(
        containerColor = iosChromePageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(displayTag) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.posts.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.posts.isEmpty() && !state.loading -> {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.feed_no_posts_for_hashtag),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> PullToRefreshBox(
                    isRefreshing = state.loading && state.posts.isNotEmpty(),
                    onRefresh = { vm.load() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(IosDesign.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(IosDesign.ScreenPadding),
                    ) {
                        itemsIndexed(state.posts, key = { _, it -> it.id }) { idx, post ->
                            FeedCard(
                                post = post,
                                imageApiRoot = state.imageApiRoot,
                                onLike = { vm.toggleLike(post.id) },
                                onComments = { /* comments on main feed only */ },
                                onOpenDiveLog = { },
                                onHashtagClick = { t ->
                                    innerNav.navigate(InnerRoutes.hashtagFeed(t)) { launchSingleTop = true }
                                },
                            )
                            if (idx >= state.posts.size - 2) vm.loadMore()
                        }
                        if (state.loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberDisplayTag(tag: String): String {
    val n = FeedHashtagParser.normalize(tag)
    return if (n.isEmpty()) tag else "#$n"
}
