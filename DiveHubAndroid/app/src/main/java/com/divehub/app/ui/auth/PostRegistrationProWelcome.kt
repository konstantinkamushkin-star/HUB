package com.divehub.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.data.remote.dto.isPostRegistrationProWelcomeEligible
import com.divehub.app.ui.theme.iosChromePageBackground
import kotlinx.coroutines.launch

/**
 * Full-screen PRO thank-you after sign-up, matching iOS [PostRegistrationProWelcomeView]
 * and [com.divehub.app.data.local.TokenStore] / [com.divehub.app.data.AuthRepository] flags.
 */
@Composable
fun PostRegistrationProWelcomeHost(
    graph: AppGraph,
    sessionUser: UserDto? = null,
) {
    val repo = remember(graph) { AuthRepository(graph) }
    var user by remember { mutableStateOf<UserDto?>(null) }
    var show by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(
        sessionUser?.id,
        sessionUser?.role,
        sessionUser?.subscriptionExpiresAt,
        sessionUser?.subscriptionStatus,
        sessionUser?.subscriptionTier,
    ) {
        user = sessionUser ?: repo.cachedUser()
    }

    LaunchedEffect(
        user?.id,
        user?.role,
        user?.subscriptionExpiresAt,
        user?.subscriptionStatus,
        user?.subscriptionTier,
    ) {
        val u = user ?: return@LaunchedEffect
        if (!graph.tokenStore.getPendingPostRegistrationProWelcome()) return@LaunchedEffect
        if (graph.tokenStore.isPostRegistrationProWelcomeDismissedForUser(u.id)) {
            graph.tokenStore.setPendingPostRegistrationProWelcome(false)
            return@LaunchedEffect
        }
        if (!u.isPostRegistrationProWelcomeEligible()) return@LaunchedEffect
        graph.tokenStore.setPendingPostRegistrationProWelcome(false)
        show = true
    }

    if (show && user != null) {
        val u = user!!
        PostRegistrationProWelcomeDialog(
            onDismiss = {
                scope.launch {
                    graph.tokenStore.recordPostRegistrationProWelcomeDismissed(u.id)
                    show = false
                }
            },
        )
    }
}

@Composable
private fun PostRegistrationProWelcomeDialog(
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val scroll = rememberScrollState()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(iosChromePageBackground()),
            color = iosChromePageBackground(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(20.dp),
            ) {
                val gradient = remember(primary) {
                    Brush.linearGradient(
                        listOf(primary, primary.copy(alpha = 0.78f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(gradient)
                        .padding(20.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Redeem,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Color.White.copy(alpha = 0.95f),
                        )
                        Text(
                            stringResource(R.string.ui_registration_pro_banner_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                        Text(
                            stringResource(R.string.ui_registration_pro_banner_subtitle),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.95f),
                        )
                    }
                }

                Text(
                    stringResource(R.string.pro_welcome_trial_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    stringResource(R.string.pro_subscription_details_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                ) {
                    Text(stringResource(R.string.ui_registration_pro_banner_cta))
                }
            }
        }
    }
}
