package com.divehub.app.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.remote.dto.needsProfileOnboarding
import com.divehub.app.ui.Routes
import kotlinx.coroutines.delay

@Composable
fun SplashRoute(nav: NavHostController, graph: AppGraph) {
    var progress by remember { mutableFloatStateOf(0f) }
    val entrance by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "splashEntrance",
    )

    LaunchedEffect(Unit) {
        progress = 1f
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

    val logoScale = 0.8f + 0.1f * entrance
    val logoOpacity = 0.5f + 0.5f * entrance
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(primary),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = stringResource(R.string.splash_logo_accessibility),
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale)
                    .alpha(logoOpacity),
            )
            Text(
                text = stringResource(R.string.splash_brand_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .alpha(logoOpacity),
            )
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 28.dp),
            )
        }
    }
}
