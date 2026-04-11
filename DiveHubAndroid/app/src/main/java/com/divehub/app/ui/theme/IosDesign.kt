package com.divehub.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object IosDesign {
    val ScreenPadding = 16.dp
    val SectionSpacing = 12.dp
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
}
