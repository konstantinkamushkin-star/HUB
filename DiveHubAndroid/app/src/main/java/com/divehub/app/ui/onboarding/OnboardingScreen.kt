package com.divehub.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.ui.Routes
import kotlinx.coroutines.launch

@Composable
fun OnboardingRoute(nav: NavHostController, graph: AppGraph) {
    var step by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val pages = listOf(
        Triple(Icons.Default.Map, "Откройте новые дайв-сайты", "Карта, фильтры и подробные карточки мест."),
        Triple(Icons.Default.Groups, "Планируйте с командой", "Чаты, социальные функции и совместные поездки."),
        Triple(Icons.Default.Explore, "Погружайтесь с DiveHub", "Логбук, достижения и бронирования в одном месте."),
    )

    val page = pages[step]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
            contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(page.first, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            Text(page.second, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(page.third, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))

            Button(onClick = {
                if (step < 2) step++
                else {
                    scope.launch {
                        graph.tokenStore.setHasCompletedOnboarding(true)
                        nav.navigate(Routes.Login) {
                            popUpTo(Routes.Onboarding) { inclusive = true }
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (step < 2) "Далее" else "Начать")
            }
        }
    }
}
