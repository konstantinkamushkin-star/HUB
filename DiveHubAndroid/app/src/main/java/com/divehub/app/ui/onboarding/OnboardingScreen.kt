package com.divehub.app.ui.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.ui.Routes
import kotlinx.coroutines.launch

private data class OnboardingLanguageOption(val tag: String, val labelRes: Int)

private val onboardingLanguages = listOf(
    OnboardingLanguageOption("en", R.string.onboarding_language_en),
    OnboardingLanguageOption("ru", R.string.onboarding_language_ru),
    OnboardingLanguageOption("es", R.string.onboarding_language_es),
    OnboardingLanguageOption("de", R.string.onboarding_language_de),
    OnboardingLanguageOption("fr", R.string.onboarding_language_fr),
    OnboardingLanguageOption("zh", R.string.onboarding_language_zh),
    OnboardingLanguageOption("ar", R.string.onboarding_language_ar),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingRoute(nav: NavHostController, graph: AppGraph) {
    var page by remember { mutableStateOf(0) }
    var selectedLanguage by remember { mutableStateOf("en") }
    val scope = rememberCoroutineScope()
    val marketingPages = listOf(
        Triple(Icons.Default.Map, R.string.onboarding_marketing_title_1, R.string.onboarding_marketing_body_1),
        Triple(Icons.Default.Groups, R.string.onboarding_marketing_title_2, R.string.onboarding_marketing_body_2),
        Triple(Icons.Default.Book, R.string.onboarding_marketing_title_3, R.string.onboarding_marketing_body_3),
        Triple(Icons.Default.Person, R.string.onboarding_marketing_title_4, R.string.onboarding_marketing_body_4),
    )
    val pagerState = rememberPagerState(initialPage = 0) { marketingPages.size }

    fun completeOnboarding() {
        scope.launch {
            graph.tokenStore.setHasCompletedOnboarding(true)
            nav.navigate(Routes.Login) {
                popUpTo(Routes.Onboarding) { inclusive = true }
            }
        }
    }

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
    ) {
        if (page > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { completeOnboarding() }) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }

        when (page) {
            0 -> LanguageSelectionStep(
                selectedLanguage = selectedLanguage,
                onSelect = { selectedLanguage = it },
                onContinue = {
                    scope.launch {
                        graph.tokenStore.setAppLanguageTag(selectedLanguage)
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(selectedLanguage),
                        )
                        page = 1
                    }
                },
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { index ->
                    val item = marketingPages[index]
                    OnboardingMarketingPage(
                        icon = item.first,
                        titleRes = item.second,
                        bodyRes = item.third,
                    )
                }
                if (pagerState.currentPage < marketingPages.lastIndex) {
                    Text(
                        stringResource(R.string.onboarding_swipe_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                } else {
                    Button(
                        onClick = { completeOnboarding() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 24.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_get_started))
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageSelectionStep(
    selectedLanguage: String,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_language_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_language_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        onboardingLanguages.forEach { lang ->
            FilterChip(
                selected = selectedLanguage == lang.tag,
                onClick = { onSelect(lang.tag) },
                label = { Text(stringResource(lang.labelRes)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}

@Composable
private fun OnboardingMarketingPage(
    icon: ImageVector,
    titleRes: Int,
    bodyRes: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(titleRes), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
