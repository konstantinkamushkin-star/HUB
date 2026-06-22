package com.divehub.app.ui.localization

import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.divehub.app.AppGraph
import com.divehub.app.data.LocalizationRepository

/**
 * Runtime overlay for server strings — mirrors iOS `LocalizationService.localizedString(_:table:)`.
 * Falls back to embedded `strings.xml` via [@StringRes].
 */
class LocalizationOverlay(
    private val repository: LocalizationRepository,
    private val languageTag: String,
) {
    fun resolve(table: String, key: String, fallback: String): String =
        repository.resolve(languageTag, table, key) ?: fallback

    @Composable
    fun string(@StringRes fallbackRes: Int, table: String, key: String, vararg args: Any): String {
        val embedded = if (args.isEmpty()) {
            stringResource(fallbackRes)
        } else {
            stringResource(fallbackRes, *args)
        }
        return resolve(table, key, embedded)
    }
}

val LocalLocalization = staticCompositionLocalOf<LocalizationOverlay?> { null }

@Composable
fun ProvideLocalization(graph: AppGraph, content: @Composable () -> Unit) {
    val langTag by graph.tokenStore.appLanguageTagFlow.collectAsStateWithLifecycle(initialValue = "")
    val revision by graph.localizationRepository.revision.collectAsStateWithLifecycle(initialValue = 0)
    val effectiveLang = langTag.ifBlank { "ru" }
    LaunchedEffect(effectiveLang) {
        graph.localizationRepository.sync(effectiveLang)
    }
    val overlay = remember(effectiveLang, revision) {
        LocalizationOverlay(graph.localizationRepository, effectiveLang)
    }
    CompositionLocalProvider(LocalLocalization provides overlay, content = content)
}

@Composable
fun localizedString(
    table: String,
    key: String,
    @StringRes fallbackRes: Int,
    vararg formatArgs: Any,
): String {
    val overlay = LocalLocalization.current
    return overlay?.string(fallbackRes, table, key, *formatArgs)
        ?: if (formatArgs.isEmpty()) stringResource(fallbackRes) else stringResource(fallbackRes, *formatArgs)
}

@Composable
fun LocalizedText(
    table: String,
    key: String,
    @StringRes fallbackRes: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    vararg formatArgs: Any,
) {
    Text(
        text = localizedString(table, key, fallbackRes, *formatArgs),
        modifier = modifier,
        style = style,
    )
}
