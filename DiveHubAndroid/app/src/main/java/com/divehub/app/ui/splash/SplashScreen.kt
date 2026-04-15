package com.divehub.app.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.needsProfileOnboarding
import com.divehub.app.ui.Routes
import kotlinx.coroutines.delay

@Composable
fun SplashRoute(nav: NavHostController, graph: AppGraph) {
    val entrance = remember { Animatable(0f) }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val logoScale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoScale",
    )

    LaunchedEffect(Unit) {
        entrance.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        delay(2000)
        val token = graph.tokenStore.getAccessToken()
        val user = graph.tokenStore.getUserJson()?.let {
            runCatching { graph.gson.fromJson(it, com.divehub.app.data.remote.dto.UserDto::class.java) }.getOrNull()
        }
        val completed = graph.tokenStore.hasCompletedOnboarding()
        when {
            token.isNullOrBlank() && !completed ->
                nav.navigate(Routes.Onboarding) { popUpTo(Routes.Splash) { inclusive = true } }
            token.isNullOrBlank() ->
                nav.navigate(Routes.Login) { popUpTo(Routes.Splash) { inclusive = true } }
            user?.mustChangePassword == true ->
                nav.navigate(Routes.ChangePassword) { popUpTo(Routes.Splash) { inclusive = true } }
            user != null && user.needsProfileOnboarding() ->
                nav.navigate(Routes.ProfileOnboarding) { popUpTo(Routes.Splash) { inclusive = true } }
            else ->
                nav.navigate(Routes.Main) { popUpTo(Routes.Splash) { inclusive = true } }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 24.dp)
                .scale(1.1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(38.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 120.dp, start = 22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                .padding(28.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(entrance.value)
                .scale(logoScale),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "DiveHub",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Dive into your next adventure",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            CircularProgressIndicator(strokeWidth = 3.dp)
        }
    }
}
