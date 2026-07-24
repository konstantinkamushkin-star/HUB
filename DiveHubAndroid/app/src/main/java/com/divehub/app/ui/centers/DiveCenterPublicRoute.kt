package com.divehub.app.ui.centers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ReviewsRepository
import com.divehub.app.data.remote.dto.CourseListItemDto
import com.divehub.app.data.remote.dto.DiveCenterInstructorDto
import com.divehub.app.data.remote.dto.ReviewDto
import com.divehub.app.ui.components.DiveCenterPromoCard
import com.divehub.app.ui.components.IosFormSheetScaffold
import com.divehub.app.ui.components.IosProminentButton
import com.divehub.app.ui.components.IosPushRouteHeader
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.reviews.AddReviewableDialog
import com.divehub.app.ui.reviews.ReviewListRow
import com.divehub.app.ui.trips.TripListCard
import com.divehub.app.ui.theme.iosChromePageBackground
import com.divehub.app.util.absoluteMediaUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var reviews by remember { mutableStateOf<List<ReviewDto>>(emptyList()) }
    var reviewsLoading by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    val myReview = reviews.firstOrNull { !it.userId.isNullOrBlank() && it.userId == currentUserId }
    var selectedCourse by remember { mutableStateOf<CourseListItemDto?>(null) }
    var showAllBranches by remember { mutableStateOf(false) }
    var claimPrefill by remember { mutableStateOf<CatalogClaimPrefill?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val openUri = rememberOpenUri()

    LaunchedEffect(graph.tokenStore) {
        loggedIn = !graph.tokenStore.getAccessToken().isNullOrBlank()
        currentUserId = graph.tokenStore.getUserJson()?.let { raw ->
            runCatching {
                graph.gson.fromJson(raw, com.divehub.app.data.remote.dto.UserDto::class.java).id
            }.getOrNull()
        }
    }

    LaunchedEffect(centerId) {
        reviewsLoading = true
        // Always load reviews (public). Backend also self-heals average_rating / review_count.
        reviews = runCatching {
            ReviewsRepository(graph).listReviews("dive_center", centerId)
        }.getOrElse { emptyList() }
        reviewsLoading = false
        vm.refresh()
    }

    // Header already applies statusBarsPadding — don't let Scaffold add it again.
    Scaffold(
        containerColor = iosChromePageBackground(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            IosPushRouteHeader(
                title = state.center?.name ?: stringResource(R.string.dive_center_public_title),
                onBack = { innerNav.popBackStack() },
                backContentDescription = stringResource(R.string.common_back),
                useLargeTitle = false,
                toolbarTrailing = {
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
                },
            )
        when {
            state.loading && state.center == null && state.error == null -> Box(
                Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.center == null -> Column(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
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
                val branches = c.locations.orEmpty()
                val hasMultipleBranches = branches.size > 1
                val showsPartnerProgram =
                    state.courses.isNotEmpty() ||
                        state.instructors.isNotEmpty() ||
                        state.upcomingTrips.isNotEmpty()
                val isCatalog = c.isCatalogListing()
                val centerPhones = splitContactPhones(c.phone)
                val primaryAddress = primaryAddressLine(c, branches)
                val visibleBranches = when {
                    !hasMultipleBranches -> emptyList()
                    showAllBranches || branches.size <= 4 -> branches
                    else -> branches.take(4)
                }
                val trainingSystems = buildList {
                    addAll(tokenizeTrainingSystems(c.certificationAgency))
                }.distinctBy { it.lowercase() }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        // Always the iOS Alpha-style full-bleed hero (never a 280dp inset strip).
                        CatalogListingLogoCard(
                            name = c.name,
                            photos = photos,
                            website = c.website,
                            imageApiRoot = state.imageApiRoot,
                        )
                    }
                    item {
                        Column(
                            Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            val loc = listOfNotNull(
                                c.city?.trim()?.takeIf { it.isNotEmpty() },
                                localizeCountryName(c.country).takeIf { it.isNotEmpty() },
                            ).joinToString(", ")
                            if (loc.isNotBlank()) {
                                Text(
                                    loc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Prefer live reviews list so header matches the section below
                            // (denormalized average_rating/review_count can lag until backend heals).
                            val displayReviewCount = if (!reviewsLoading) {
                                reviews.size
                            } else {
                                c.reviewCount ?: 0
                            }
                            val displayRating = when {
                                !reviewsLoading && reviews.isNotEmpty() ->
                                    reviews.map { it.rating.toDouble() }.average()
                                !reviewsLoading -> 0.0
                                else -> c.averageRating ?: 0.0
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFCC00),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "%.1f".format(displayRating),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(
                                        R.string.dive_center_public_reviews_count,
                                        displayReviewCount,
                                        when {
                                            displayReviewCount % 10 == 1 && displayReviewCount % 100 != 11 ->
                                                stringResource(R.string.dive_center_public_review_one)
                                            displayReviewCount % 10 in 2..4 && displayReviewCount % 100 !in 12..14 ->
                                                stringResource(R.string.dive_center_public_review_few)
                                            else ->
                                                stringResource(R.string.dive_center_public_review_many)
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (c.nitroxAvailable == true) {
                                Text(stringResource(R.string.dive_center_public_nitrox), style = MaterialTheme.typography.bodySmall)
                            }
                            if (trainingSystems.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    trainingSystems.forEach { system ->
                                        Text(
                                            system,
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                    shape = RoundedCornerShape(50),
                                                )
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            if (showsPartnerProgram) {
                                Spacer(Modifier.height(4.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(4.dp))
                                DiveCenterPromoCard()
                            }
                            val hasLocationContent = primaryAddress != null ||
                                visibleBranches.any {
                                    branchDisplayLine(it.city, it.address).isNotBlank()
                                }
                            if (hasLocationContent) {
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (hasMultipleBranches) {
                                    stringResource(R.string.dive_center_public_addresses)
                                } else {
                                    stringResource(R.string.dive_center_public_location)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            primaryAddress?.let { DiveCenterAddressRow(it) }
                            visibleBranches.forEachIndexed { index, branch ->
                                val line = branchDisplayLine(branch.city, branch.address)
                                if (line.isNotBlank()) {
                                    if (index > 0 || primaryAddress != null) {
                                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        DiveCenterAddressRow(line)
                                        val branchPhone = splitContactPhones(branch.phone).firstOrNull()
                                        if (branchPhone != null && centerPhones.none { samePhone(it, branchPhone) }) {
                                            DiveCenterContactLinkRow(
                                                icon = Icons.Default.Phone,
                                                text = branchPhone,
                                                onClick = { openUri(telUri(branchPhone)) },
                                            )
                                        }
                                    }
                                }
                            }
                            if (branches.size > 4) {
                                TextButton(
                                    onClick = { showAllBranches = !showAllBranches },
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    Text(
                                        if (showAllBranches) {
                                            stringResource(R.string.dive_center_public_show_less_addresses)
                                        } else {
                                            stringResource(
                                                R.string.dive_center_public_show_more_addresses,
                                                branches.size - 4,
                                            )
                                        },
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            } // hasLocationContent
                            val hasContacts = centerPhones.isNotEmpty() ||
                                !c.email.isNullOrBlank() ||
                                !c.website.isNullOrBlank()
                            if (hasContacts) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.dive_center_public_contacts),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                centerPhones.forEach { phone ->
                                    DiveCenterContactLinkRow(
                                        icon = Icons.Default.Phone,
                                        text = phone,
                                        onClick = { openUri(telUri(phone)) },
                                    )
                                }
                                c.email?.takeIf { it.isNotBlank() }?.let { email ->
                                    DiveCenterContactLinkRow(
                                        icon = Icons.Default.Email,
                                        text = email,
                                        onClick = { openUri("mailto:$email") },
                                    )
                                }
                                c.website?.takeIf { it.isNotBlank() }?.let { website ->
                                    DiveCenterContactLinkRow(
                                        icon = Icons.Default.Language,
                                        text = website,
                                        onClick = { openUri(website) },
                                    )
                                }
                            }
                            if (showsPartnerProgram) {
                                val desc = c.description?.trim().orEmpty()
                                if (desc.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(desc, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
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
                                    Text(
                                        stringResource(
                                            if (myReview != null) R.string.review_edit else R.string.explore_add_review,
                                        ),
                                    )
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
                                reviews.isEmpty() -> Text(
                                    stringResource(R.string.explore_no_reviews_yet),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                else -> {
                                    reviews.forEach { r ->
                                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                        ReviewListRow(
                                            r = r,
                                            isMine = !r.userId.isNullOrBlank() && r.userId == currentUserId,
                                            onEdit = { showReviewDialog = true },
                                        )
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
                                onClick = {
                                    innerNav.navigate(InnerRoutes.instructorPublic(inst.id, centerId))
                                },
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
                    if (isCatalog) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                                CatalogListingClaimFooter(
                                    onContact = {
                                        claimPrefill = CatalogClaimPrefill.from(c)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        }
    }

    selectedCourse?.let { course ->
        IosFormSheetScaffold(
            title = course.name,
            onDismiss = { selectedCourse = null },
            cancelLabel = stringResource(R.string.common_cancel),
            onCancel = { selectedCourse = null },
        ) {
            course.level?.takeIf { it.isNotBlank() }?.let { level ->
                Text(
                    level,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                course.description?.trim().orEmpty().ifBlank { stringResource(R.string.explore_no_description) },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))
            IosProminentButton(
                text = stringResource(R.string.explore_book),
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
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showReviewDialog) {
        AddReviewableDialog(
            reviewableType = "dive_center",
            reviewableId = centerId,
            graph = graph,
            existing = myReview,
            onDismiss = { showReviewDialog = false },
            onSuccess = {
                val editingMine = myReview != null
                showReviewDialog = false
                scope.launch {
                    reviews = runCatching {
                        ReviewsRepository(graph).listReviews("dive_center", centerId)
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
                vm.refresh()
            },
        )
    }

    claimPrefill?.let { prefill ->
        CatalogPartnerContactSheet(
            graph = graph,
            prefill = prefill,
            onDismiss = { claimPrefill = null },
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

private val knownTrainingSystems = listOf(
    "PADI", "SSI", "NAUI", "CMAS", "BSAC", "SDI", "TDI",
    "RAID", "GUE", "IANTD", "ANDI", "NDL", "ISO", "WRSTC", "PSA",
)

private fun tokenizeTrainingSystems(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val knownMap = knownTrainingSystems.associateBy { it.lowercase() }
    val chunks = raw.split(',', ';', '/', '|').map { it.trim() }.filter { it.isNotEmpty() }
    val tokens = mutableListOf<String>()
    for (chunk in chunks) {
        val exact = knownMap[chunk.lowercase()]
        if (exact != null) {
            tokens.add(exact)
            continue
        }
        val words = chunk.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size > 1 && words.all { knownMap.containsKey(it.lowercase()) }) {
            tokens.addAll(words.mapNotNull { knownMap[it.lowercase()] })
        } else {
            tokens.add(chunk)
        }
    }
    return tokens.distinctBy { it.lowercase() }
}
