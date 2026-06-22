package com.divehub.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalDiveHubDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ProvideDiveHubDarkTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDiveHubDarkTheme provides darkTheme, content = content)
}

@Composable
fun iosChromePageBackground(): Color =
    if (LocalDiveHubDarkTheme.current) Color(0xFF121212) else Color(0xFFF2F2F7)

@Composable
fun iosGroupedFormPageBackground(): Color = iosChromePageBackground()

@Composable
fun iosGroupedCardColor(): Color =
    if (LocalDiveHubDarkTheme.current) Color(0xFF1E1E1E) else Color.White

@Composable
fun iosAccentLinkColor(): Color = Color(0xFF007AFF)

@Composable
fun iosSecondaryMutedTextColor(): Color =
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (LocalDiveHubDarkTheme.current) 0.72f else 0.65f)

object DarkChrome {
    val pageBackground = Color(0xFF121212)
    val groupedCard = Color(0xFF1E1E1E)
}

object Chat {
    val bubbleOutgoing = Color(0xFF007AFF)
    val bubbleIncomingLight = Color(0xFFE5E5EA)
    val bubbleIncomingDark = Color(0xFF2C2C2E)
}

@Composable
fun exploreChromeColors(): ExploreChromeColors = ExploreChromeColors(
    pageBackground = if (LocalDiveHubDarkTheme.current) Color(0xFF121212) else IosDesign.Explore.pageBackground,
    listBackground = if (LocalDiveHubDarkTheme.current) Color(0xFF1E1E1E) else IosDesign.Explore.listBackground,
)

data class ExploreChromeColors(
    val pageBackground: Color,
    val listBackground: Color,
)

@Composable
fun groupedSurface(): Color = iosGroupedCardColor()

@Composable
fun linkBlue(): Color = iosAccentLinkColor()

@Composable
fun secondaryLabel(): Color = iosSecondaryMutedTextColor()
