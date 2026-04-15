package com.divehub.app.ui.shops

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ReviewsRepository
import com.divehub.app.data.remote.dto.ReviewDto
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.reviews.AddReviewableDialog
import com.divehub.app.ui.reviews.ReviewListRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopPublicRoute(
    graph: AppGraph,
    shopId: String,
    innerNav: NavController,
) {
    val vm: ShopPublicViewModel = viewModel(
        key = "shop_public_$shopId",
        factory = ShopPublicViewModel.factory(graph, shopId),
    )
    val state by vm.state.collectAsState()
    var loggedIn by remember { mutableStateOf(false) }
    var reviews by remember { mutableStateOf<List<ReviewDto>>(emptyList()) }
    var reviewsLoading by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(graph.tokenStore) {
        loggedIn = !graph.tokenStore.getAccessToken().isNullOrBlank()
    }

    LaunchedEffect(shopId, loggedIn) {
        reviewsLoading = true
        reviews = if (loggedIn) {
            runCatching { ReviewsRepository(graph).listReviews("shop", shopId) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        reviewsLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.shop?.name ?: stringResource(R.string.shop_public_title),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (loggedIn) {
                        IconButton(
                            onClick = {
                                innerNav.navigate(InnerRoutes.businessChatOpen("shop", shopId))
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.explore_message_shop))
                        }
                    }
                    TextButton(
                        onClick = { innerNav.navigate(InnerRoutes.bookingWizard()) },
                    ) {
                        Text(stringResource(R.string.explore_book))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.shop == null && state.error == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.shop == null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.shop_public_not_found))
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.refresh() }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
            state.shop != null -> {
                val s = state.shop!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            s.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "%.1f".format(s.averageRating ?: 0.0),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    s.type?.takeIf { it.isNotBlank() }?.let { t ->
                        Spacer(Modifier.height(4.dp))
                        Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val loc = listOfNotNull(s.city?.trim(), s.country?.trim()).filter { it.isNotEmpty() }.joinToString(", ")
                    if (loc.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(loc, style = MaterialTheme.typography.bodyMedium)
                    }
                    s.address?.takeIf { it.isNotBlank() }?.let { a ->
                        Spacer(Modifier.height(4.dp))
                        Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        s.description?.trim().orEmpty().ifBlank { stringResource(R.string.explore_no_description) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.explore_reviews), fontWeight = FontWeight.SemiBold)
                        TextButton(
                            onClick = {
                                if (!loggedIn) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.review_login_required),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    showReviewDialog = true
                                }
                            },
                        ) {
                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.explore_add_review))
                        }
                    }
                    when {
                        reviewsLoading -> Row(
                            Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.chat_loading), style = MaterialTheme.typography.bodySmall)
                        }
                        !loggedIn -> Text(
                            stringResource(R.string.review_login_required),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        reviews.isEmpty() -> Text(
                            stringResource(R.string.explore_no_reviews_yet),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> {
                            reviews.forEach { r ->
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                ReviewListRow(r)
                            }
                        }
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    if (showReviewDialog) {
        AddReviewableDialog(
            reviewableType = "shop",
            reviewableId = shopId,
            graph = graph,
            onDismiss = { showReviewDialog = false },
            onSuccess = {
                showReviewDialog = false
                scope.launch {
                    reviews = runCatching {
                        ReviewsRepository(graph).listReviews("shop", shopId)
                    }.getOrElse { emptyList() }
                }
                vm.refresh()
                Toast.makeText(context, context.getString(R.string.review_sent), Toast.LENGTH_SHORT).show()
            },
        )
    }
}
