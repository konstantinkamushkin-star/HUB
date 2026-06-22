package com.divehub.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object IosDesign {
    val ScreenPadding = 16.dp
    val SectionSpacing = 12.dp
    /** Matches iOS `DiveHubTabBarStyle.rowHeight` (62pt). */
    val TabBarRowHeight = 62.dp
    val CardCorner = RoundedCornerShape(12.dp)
    val BubbleCorner = RoundedCornerShape(16.dp)
    val SmallChipCorner = RoundedCornerShape(8.dp)
    val CardElevation = 5.dp
    val AvatarSizeLarge = 50.dp
    val AvatarSizeSmall = 40.dp
    val BubbleMaxWidth = 300.dp

    /** UISegmentedControl track, UISearchBar fill, filter pills — iOS system colors */
    object Explore {
        val pageBackground = Color.White
        val listBackground = Color(0xFFF2F2F7)
        val segmentTrack = Color(0xFFE5E5EA)
        val segmentThumb = Color.White
        val searchFill = Color(0xFFF2F2F7)
        val filterActiveBlue = Color(0xFF007AFF)
        val filterInactiveFill = Color(0xFFE5E5EA)
        val filterSelectedFill = Color(0xFFDCDCE3)
        val labelPrimary = Color(0xFF000000)
        val labelSecondary = Color(0x993C3C43)
        val navBarIconTint = Color(0xFF111111)
        val navBarButtonFill = Color(0xFFF2F2F7)
        /** iOS system blue for map UI */
        val mapAccent = Color(0xFF007AFF)
        val segmentShadowAmbient = Color(0x1A000000)
        val segmentShadowSpot = Color(0x33000000)
    }

    object Profile {
        val pageBackground = Color(0xFFF2F2F7)
        val groupedCard = Color.White
        val groupedSurface = Color.White
        val secondaryLabel = Color(0x993C3C43)
        val linkBlue = Color(0xFF007AFF)
    }

    object DarkChrome {
        val pageBackground = Color(0xFF121212)
        val groupedSurface = Color(0xFF1E1E1E)
        val groupedCard = Color(0xFF1E1E1E)
        val secondaryLabel = Color(0xFFAEAEB2)
        val systemBlue = Color(0xFF0A84FF)
        val segmentTrack = Color(0xFF2C2C2E)
    }

    object Chat {
        val bubbleIncoming = Color(0xFFE5E5EA)
        val bubbleOutgoing = Color(0xFF007AFF)
    }
}
