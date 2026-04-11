package com.divehub.app.ui.achievements

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.ui.components.DiveHubLogoMark

private val UnlockedTint = Color(0xFF007AFF)
private val LockedTint = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsRoute(graph: AppGraph, innerNav: NavController) {
    val app = LocalContext.current.applicationContext as Application
    val vm: AchievementsViewModel = viewModel(factory = AchievementsViewModel.factory(graph, app))
    val state by vm.state.collectAsState()
    val unlocked = state.achievements.count { it.unlocked }
    val total = state.achievements.size.coerceAtLeast(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_achievements)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.achievements.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.achievements.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error ?: stringResource(R.string.common_error))
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.refresh() }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.achievements_progress_format, unlocked, total),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { unlocked.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.achievements, key = { it.id }) { a ->
                        AchievementCell(a)
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementCell(a: AchievementUi) {
    val tint = if (a.unlocked) UnlockedTint else LockedTint
    val bg = if (a.unlocked) {
        UnlockedTint.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (a.iconKind == AchievementIconKind.Waves) {
                DiveHubLogoMark(
                    modifier = Modifier
                        .padding(4.dp)
                        .height(24.dp)
                        .aspectRatio(1.3f),
                    color = tint,
                )
            } else {
                Icon(iconFor(a.iconKind), contentDescription = null, tint = tint, modifier = Modifier.padding(4.dp))
            }
            Text(
                a.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private fun iconFor(kind: AchievementIconKind): ImageVector = when (kind) {
    AchievementIconKind.Waves -> Icons.AutoMirrored.Filled.MenuBook
    AchievementIconKind.Depth -> Icons.Default.ArrowDownward
    AchievementIconKind.Night -> Icons.Default.DarkMode
    AchievementIconKind.Camera -> Icons.Default.Image
    AchievementIconKind.Timer -> Icons.Default.Schedule
    AchievementIconKind.Logbook -> Icons.AutoMirrored.Filled.MenuBook
}
