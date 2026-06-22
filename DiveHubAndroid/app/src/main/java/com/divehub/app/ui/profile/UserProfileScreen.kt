package com.divehub.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.divehub.app.util.absoluteMediaUrl
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.UsersRepository
import com.divehub.app.data.remote.dto.CertificationDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.remote.dto.UserProfileSummaryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileRoute(
    graph: AppGraph,
    userId: String,
    innerNav: NavController,
) {
    val repo = remember { UsersRepository(graph) }
    var user by remember { mutableStateOf<UserDto?>(null) }
    var summary by remember { mutableStateOf<UserProfileSummaryDto?>(null) }
    var certs by remember { mutableStateOf<List<CertificationDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var apiRoot by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apiRoot = graph.tokenStore.getRootBaseUrl()
    }

    LaunchedEffect(userId) {
        loading = true
        error = null
        runCatching { repo.getUser(userId) }
            .onSuccess { user = it }
            .onFailure { e -> error = e.message ?: "Error" }
        runCatching { repo.getUserSummary(userId) }
            .onSuccess { s ->
                summary = s
                if (s.certificationLevel != null) {
                    runCatching { repo.listCertifications(userId) }
                        .onSuccess { certs = it }
                }
            }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
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
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val avatar = u.avatarUrl?.trim().orEmpty()
                    val avatarUrl = if (avatar.isNotEmpty()) absoluteMediaUrl(apiRoot, avatar) else ""
                    if (avatarUrl.isNotBlank() && avatarUrl.startsWith("http", ignoreCase = true)) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(88.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                u.displayName().trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(u.displayName(), style = MaterialTheme.typography.headlineSmall)
                    summary?.city?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    u.bio?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                    }

                    summary?.let { s ->
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.screen_statistics), style = MaterialTheme.typography.titleMedium)
                        s.certificationLevel?.let {
                            Text(it, fontWeight = FontWeight.SemiBold)
                        }
                        s.certifyingAgencies?.takeIf { it.isNotEmpty() }?.let {
                            Text(it.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                        s.totalDives?.let {
                            Text(stringResource(R.string.user_profile_total_dives, it))
                        }
                        s.deepestDiveMeters?.let {
                            Text(stringResource(R.string.user_profile_max_depth, it))
                        }
                        s.uniqueDiveSitesCount?.let {
                            Text(stringResource(R.string.user_profile_unique_sites, it))
                        }
                        s.countriesDived?.takeIf { it.isNotEmpty() }?.let { countries ->
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.user_profile_countries))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                countries.forEach { code ->
                                    Text(
                                        code,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(16.dp),
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                        if (certs.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.screen_certifications))
                            certs.forEach { c ->
                                Text("${c.agency} — ${c.level}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } ?: u.totalDives?.takeIf { it >= 0 }?.let {
                        Text(
                            stringResource(R.string.user_profile_total_dives, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
