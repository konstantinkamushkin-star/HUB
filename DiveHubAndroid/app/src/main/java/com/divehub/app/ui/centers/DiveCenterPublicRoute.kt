package com.divehub.app.ui.centers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ReviewsRepository
import com.divehub.app.data.remote.dto.CourseListItemDto
import com.divehub.app.data.remote.dto.DiveCenterInstructorDto
import com.divehub.app.data.remote.dto.ReviewDto
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.reviews.AddReviewableDialog
import com.divehub.app.ui.reviews.ReviewListRow
import com.divehub.app.ui.trips.TripListCard
import com.divehub.app.util.absoluteMediaUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiveCenterPublicRoute(
    graph: AppGraph,
    centerId: String,
    innerNav: NavController,
) {
    val vm: DiveCenterPublicViewModel = viewModel(
        key = "dive_center_public_$centerId",
        factory = DiveCenterPublicViewModel.factory(graph, centerId),
    )
    val state by vm.state.collectAsState()
    var loggedIn by remember { mutableStateOf(false) }
    var reviews by remember { mutableStateOf<List<ReviewDto>>(emptyList()) }
    var reviewsLoading by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<CourseListItemDto?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(graph.tokenStore) {
        loggedIn = !graph.tokenStore.getAccessToken().isNullOrBlank()
    }

    LaunchedEffect(centerId, loggedIn) {
        reviewsLoading = true
        reviews = if (loggedIn) {
            runCatching { ReviewsRepository(graph).listReviews("dive_center", centerId) }.getOrElse { emptyList() }
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
                        state.center?.name ?: stringResource(R.string.dive_center_public_title),
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
                                innerNav.navigate(
                                    InnerRoutes.businessChatOpen("dive_center", centerId),
                                )
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.dive_center_public_cd_message))
                        }
                    }
                    TextButton(
                        onClick = {
                            innerNav.navigate(
                                InnerRoutes.bookingWizard(centerId = centerId, siteId = null, instructorId = null),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.explore_book))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.center == null && state.error == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.center == null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when (state.error) {
                        "not_found" -> stringResource(R.string.dive_center_public_not_found)
                        else -> state.error ?: stringResource(R.string.common_error)
                    },
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.refresh() }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
            state.center != null -> {
                val c = state.center!!
                val photos = c.photos?.filter { it.isNotBlank() }.orEmpty()
                    .ifEmpty { listOfNotNull(c.thumbnailUrl?.takeIf { it.isNotBlank() }) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (photos.isNotEmpty()) {
                        item {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                items(photos, key = { it }) { raw ->
                                    AsyncImage(
                                        model = absoluteMediaUrl(state.imageApiRoot, raw),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .width(280.dp)
                                            .height(180.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    val loc = listOfNotNull(c.city?.trim(), c.country?.trim()).filter { it.isNotEmpty() }.joinToString(", ")
                                    if (loc.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(loc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "%.1f".format(c.averageRating ?: 0.0),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (c.nitroxAvailable == true) {
                                Text(stringResource(R.string.dive_center_public_nitrox), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(4.dp))
                            }
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                c.description?.trim().orEmpty().ifBlank { stringResource(R.string.explore_no_description) },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.explore_reviews),
                                    fontWeight = FontWeight.SemiBold,
                                )
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
                    if (state.courses.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.dive_center_public_section_courses),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        items(state.courses, key = { it.id }) { course ->
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .clickable { selectedCourse = course },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                ),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(course.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    course.level?.takeIf { it.isNotBlank() }?.let {
                                        Spacer(Modifier.height(4.dp))
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (state.instructors.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.dive_center_public_section_instructors),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        items(state.instructors, key = { it.id }) { inst ->
                            InstructorRow(
                                instructor = inst,
                                imageRoot = state.imageApiRoot,
                                onClick = { innerNav.navigate(InnerRoutes.userProfile(inst.id)) },
                            )
                        }
                    }
                    if (state.upcomingTrips.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.dive_center_public_section_trips),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        items(state.upcomingTrips, key = { it.id }) { trip ->
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                TripListCard(trip = trip, onClick = { innerNav.navigate(InnerRoutes.tripDetail(trip.id)) })
                            }
                        }
                    }
                    if (state.pastTrips.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.dive_center_public_section_trips_past),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        items(state.pastTrips, key = { it.id }) { trip ->
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                TripListCard(trip = trip, onClick = { innerNav.navigate(InnerRoutes.tripDetail(trip.id)) })
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

    selectedCourse?.let { course ->
        ModalBottomSheet(onDismissRequest = { selectedCourse = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(course.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                course.level?.takeIf { it.isNotBlank() }?.let { level ->
                    Spacer(Modifier.height(6.dp))
                    Text(level, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    course.description?.trim().orEmpty().ifBlank { stringResource(R.string.explore_no_description) },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        selectedCourse = null
                        innerNav.navigate(
                            InnerRoutes.bookingWizard(
                                centerId = centerId,
                                siteId = null,
                                instructorId = null,
                                courseId = course.id,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.explore_book))
                }
            }
        }
    }

    if (showReviewDialog) {
        AddReviewableDialog(
            reviewableType = "dive_center",
            reviewableId = centerId,
            graph = graph,
            onDismiss = { showReviewDialog = false },
            onSuccess = {
                showReviewDialog = false
                scope.launch {
                    reviews = runCatching {
                        ReviewsRepository(graph).listReviews("dive_center", centerId)
                    }.getOrElse { emptyList() }
                }
                vm.refresh()
                Toast.makeText(context, context.getString(R.string.review_sent), Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun InstructorRow(
    instructor: DiveCenterInstructorDto,
    imageRoot: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatar = instructor.avatarURL ?: instructor.photoURL
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = absoluteMediaUrl(imageRoot, avatar),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    (instructor.name?.firstOrNull() ?: '?').toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(instructor.name?.trim().orEmpty().ifBlank { "—" }, fontWeight = FontWeight.SemiBold)
            instructor.bio?.trim()?.takeIf { it.isNotEmpty() }?.let { bio ->
                Text(bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}
