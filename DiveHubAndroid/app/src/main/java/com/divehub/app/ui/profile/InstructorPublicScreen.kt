package com.divehub.app.ui.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ReviewsRepository
import com.divehub.app.data.UsersRepository
import com.divehub.app.data.remote.dto.ReviewDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.ui.navigation.InnerRoutes
import com.divehub.app.ui.reviews.AddReviewableDialog
import com.divehub.app.ui.reviews.ReviewListRow
import com.divehub.app.util.absoluteMediaUrl
import kotlinx.coroutines.launch

private fun UserDto.certificationsFromProfile(): List<String> {
    val dp = diverProfile ?: return emptyList()
    val raw = dp["certifications"] ?: dp["certification"] ?: return emptyList()
    return when (raw) {
        is List<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
        is String -> raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }
}

private fun UserDto.languagesFromProfile(): List<String> {
    val dp = diverProfile ?: return emptyList()
    val raw = dp["languages"] ?: dp["spokenLanguages"] ?: return emptyList()
    return when (raw) {
        is List<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
        is String -> raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }
}

private fun UserDto.fallbackCertifications(): List<String> {
    val c = certificationsFromProfile()
    if (c.isNotEmpty()) return c
    val level = certificationLevel?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    return level.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InstructorPublicRoute(
    graph: AppGraph,
    userId: String,
    centerId: String?,
    innerNav: NavController,
) {
    val repo = remember { UsersRepository(graph) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var user by remember { mutableStateOf<UserDto?>(null) }
    var reviews by remember { mutableStateOf<List<ReviewDto>>(emptyList()) }
    var apiRoot by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showReviewDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        loading = true
        error = null
        apiRoot = graph.tokenStore.getRootBaseUrl()
        runCatching {
            val u = repo.getUser(userId)
            user = u
            reviews = ReviewsRepository(graph).listReviews("instructor", userId)
        }.onFailure { e ->
            error = e.message ?: context.getString(R.string.common_error)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.displayName() ?: stringResource(R.string.instructor_public_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (user != null) {
                        TextButton(
                            onClick = {
                                val c = centerId?.takeIf { it.isNotBlank() }
                                innerNav.navigate(
                                    InnerRoutes.bookingWizard(
                                        centerId = c,
                                        siteId = null,
                                        instructorId = userId,
                                        courseId = null,
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.explore_book))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            error != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error ?: "")
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { innerNav.popBackStack() }) {
                    Text(stringResource(R.string.common_back))
                }
            }
            user != null -> {
                val u = user!!
                val avg = if (reviews.isNotEmpty()) {
                    reviews.map { it.rating }.average()
                } else {
                    null
                }
                val certs = u.fallbackCertifications()
                val langs = u.languagesFromProfile()
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val avatar = u.avatarUrl?.trim()
                        if (!avatar.isNullOrEmpty()) {
                            AsyncImage(
                                model = absoluteMediaUrl(apiRoot, avatar),
                                contentDescription = null,
                                modifier = Modifier.size(100.dp),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                Modifier.size(100.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    (u.displayName().firstOrNull() ?: '?').toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(u.displayName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (avg != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Text(
                                        String.format("%.1f", avg),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        stringResource(R.string.instructor_public_reviews_count, reviews.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    u.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                        Text(stringResource(R.string.instructor_public_about), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(bio, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (certs.isNotEmpty()) {
                        Text(stringResource(R.string.instructor_public_certifications), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        certs.forEach { line ->
                            Text("• $line", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (langs.isNotEmpty()) {
                        Text(stringResource(R.string.instructor_public_languages), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            langs.forEach { lang ->
                                Text(
                                    lang,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val c = centerId?.takeIf { it.isNotBlank() }
                            innerNav.navigate(
                                InnerRoutes.bookingWizard(
                                    centerId = c,
                                    siteId = null,
                                    instructorId = userId,
                                    courseId = null,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.explore_book))
                    }

                    Text(stringResource(R.string.instructor_public_reviews_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (reviews.isEmpty()) {
                        Text(
                            stringResource(R.string.explore_no_reviews_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        reviews.forEach { r -> ReviewListRow(r) }
                    }
                    TextButton(
                        onClick = { showReviewDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.explore_add_review))
                    }
                }
            }
        }
    }

    if (showReviewDialog && user != null) {
        AddReviewableDialog(
            reviewableType = "instructor",
            reviewableId = userId,
            graph = graph,
            onDismiss = { showReviewDialog = false },
            onSuccess = {
                showReviewDialog = false
                scope.launch {
                    runCatching {
                        reviews = ReviewsRepository(graph).listReviews("instructor", userId)
                    }
                    Toast.makeText(context, context.getString(R.string.review_sent), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}
