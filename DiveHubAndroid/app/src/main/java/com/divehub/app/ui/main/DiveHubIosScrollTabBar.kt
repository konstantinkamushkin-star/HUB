package com.divehub.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divehub.app.ui.theme.LocalDiveHubDarkTheme

/**
 * iOS `DiveHubCarouselTabBar`: ~5 column slots, horizontal scroll, pill selection.
 * Used for diver main tabs and partner (dive center) shell — not Material [NavigationBar].
 */
object DiveHubCarouselTabBarTokens {
    /** Room for two-line RU/EN labels under the icon without harsh ellipsis. */
    val rowHeight = 66.dp
    val selectionCorner = 18.dp
    val iconSquare = 28.dp
    val iconLabelSpacing = 4.dp
    val selectionPaddingH = 6.dp
    val selectionPaddingV = 5.dp
    val itemSpacing = 6.dp
    val rowHorizontalPadding = 16.dp
    val slotEdgePadding = 16.dp
    val slotInterItem = 6.dp
    val slotMin = 54.dp
    val slotMax = 110.dp
    val hairline = 1.dp
}

/** Wider slots when the bar is horizontally scrollable (5+ items) so long RU labels fit. */
fun diveHubTabSlotWidth(screenWidthDp: Int, itemCount: Int): Dp {
    val visible = when {
        itemCount <= 4 -> minOf(4, maxOf(2, itemCount))
        else -> 3
    }
    val w = screenWidthDp.dp
    val gaps = DiveHubCarouselTabBarTokens.slotInterItem * (visible - 1)
    val usable = w - DiveHubCarouselTabBarTokens.slotEdgePadding * 2 - gaps
    val per = usable / visible
    return per.coerceIn(DiveHubCarouselTabBarTokens.slotMin, DiveHubCarouselTabBarTokens.slotMax)
}

private val DiveHubPrimary = Color(0xFF0080CC)

@Composable
fun diveHubIosScrollTabBarBottomInset(): Dp {
    // `navigationBars` matches the system gesture / 3-button area; `safeDrawing` bottom
    // can be larger on some devices and read as a fat band under the bar.
    val systemNavBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return DiveHubCarouselTabBarTokens.hairline +
        DiveHubCarouselTabBarTokens.rowHeight +
        systemNavBottom
}

data class DiveHubScrollTabItem(
    val index: Int,
    val icon: ImageVector,
    val label: String,
)

/**
 * @param items Each [DiveHubScrollTabItem.index] is the selected tab id (0..n-1).
 */
@Composable
fun DiveHubIosScrollTabBar(
    items: List<DiveHubScrollTabItem>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val configuration = LocalConfiguration.current
    val slotW = remember(items.size, configuration.screenWidthDp) {
        diveHubTabSlotWidth(configuration.screenWidthDp, items.size)
    }
    val listState = rememberLazyListState()
    val isDark = LocalDiveHubDarkTheme.current
    val barBase = if (isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    } else {
        Color(0xE6FFFFFF)
    }
    val sorted = remember(items) { items.sortedBy { it.index } }
    val scrollTo = sorted.indexOfFirst { it.index == selectedIndex }.coerceAtLeast(0)
    LaunchedEffect(selectedIndex, items.size) {
        listState.animateScrollToItem(scrollTo)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(0.dp),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.12f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.12f),
            ),
        color = Color.Transparent,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(barBase),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(DiveHubCarouselTabBarTokens.hairline)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                DiveHubPrimary.copy(alpha = if (isDark) 0.22f else 0.14f),
                                DiveHubPrimary.copy(alpha = 0.04f),
                            ),
                        ),
                    ),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.35f else 0.12f),
                thickness = 0.5.dp,
            )
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(DiveHubCarouselTabBarTokens.itemSpacing),
                contentPadding = PaddingValues(horizontal = DiveHubCarouselTabBarTokens.rowHorizontalPadding),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DiveHubCarouselTabBarTokens.rowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(sorted) { _, item ->
                    val selected = selectedIndex == item.index
                    val onSurface = MaterialTheme.colorScheme.onSurface
                    val muted = if (isDark) 0.58f else 0.48f
                    val iconTint = if (selected) DiveHubPrimary else onSurface.copy(alpha = 0.42f)
                    val textColor = if (selected) DiveHubPrimary else onSurface.copy(alpha = muted)
                    val pillFill = if (selected) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.08f else 0.22f),
                                DiveHubPrimary.copy(alpha = if (isDark) 0.22f else 0.14f),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent))
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(DiveHubCarouselTabBarTokens.iconLabelSpacing),
                        modifier = Modifier
                            .width(slotW)
                            .height(DiveHubCarouselTabBarTokens.rowHeight)
                            .clip(RoundedCornerShape(DiveHubCarouselTabBarTokens.selectionCorner))
                            .background(pillFill, RoundedCornerShape(DiveHubCarouselTabBarTokens.selectionCorner))
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = DiveHubPrimary.copy(alpha = 0.38f),
                                        shape = RoundedCornerShape(DiveHubCarouselTabBarTokens.selectionCorner),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onSelectIndex(item.index) }
                            .padding(
                                horizontal = DiveHubCarouselTabBarTokens.selectionPaddingH,
                                vertical = DiveHubCarouselTabBarTokens.selectionPaddingV,
                            ),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = iconTint,
                            modifier = Modifier.size(DiveHubCarouselTabBarTokens.iconSquare),
                        )
                        Text(
                            text = item.label,
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    .background(barBase),
            )
        }
    }
}
