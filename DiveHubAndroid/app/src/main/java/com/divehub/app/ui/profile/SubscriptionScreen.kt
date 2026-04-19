package com.divehub.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.ui.main.SessionViewModel
import kotlinx.coroutines.launch

private fun UserDto.hasProEntitlement(): Boolean {
    val r = role?.trim()?.uppercase().orEmpty()
    if (r == "DIVER_PRO") return true
    val tier = subscriptionTier?.trim()?.uppercase().orEmpty()
    return tier == "PRO" || tier == "DIVER_PRO"
}

private fun UserDto.isDiverBasic(): Boolean {
    if (hasProEntitlement()) return false
    val r = role?.trim()?.uppercase().orEmpty()
    return r.isEmpty() || r == "DIVER_BASIC" || r == "DIVER"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionRoute(
    graph: AppGraph,
    sessionVm: SessionViewModel,
    innerNav: NavController,
) {
    val user by sessionVm.user.collectAsState()
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository(graph) }
    val snack = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }

    fun syncFromServer() {
        scope.launch {
            busy = true
            runCatching { repo.refreshProfile() }
                .onSuccess { sessionVm.onUserUpdated(it) }
                .onFailure { e -> snack.showSnackbar(repo.parseErrorMessage(e)) }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { repo.refreshProfile() }
            .onSuccess { sessionVm.onUserUpdated(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_subscription)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
            val current = user
            when {
                current == null -> Text(stringResource(R.string.profile_loading))
                current.isDiverBasic() -> {
                    Text(stringResource(R.string.subscription_upgrade_title), style = MaterialTheme.typography.titleMedium)
                    val tierLabel = current.subscriptionTier?.trim()?.takeIf { it.isNotEmpty() }
                        ?: current.role?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "—"
                    Text(
                        stringResource(R.string.subscription_tier_current, tierLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.subscription_explanation_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { syncFromServer() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.subscription_sync_account))
                    }
                    Text(stringResource(R.string.subscription_pro_features), style = MaterialTheme.typography.titleSmall)
                    ProBullet(stringResource(R.string.subscription_bullet_logbook))
                    ProBullet(stringResource(R.string.subscription_bullet_social))
                    ProBullet(stringResource(R.string.subscription_bullet_chats))
                    ProBullet(stringResource(R.string.subscription_bullet_gear))
                }
                current.hasProEntitlement() -> {
                    Text(stringResource(R.string.subscription_active_title), color = MaterialTheme.colorScheme.primary)
                    current.subscriptionExpiresAt?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            stringResource(R.string.subscription_expires, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(R.string.subscription_pro_manage_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { syncFromServer() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.subscription_sync_account))
                    }
                }
                else -> Text(stringResource(R.string.subscription_not_diver_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProBullet(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium)
}
