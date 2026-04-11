package com.divehub.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.divehub.app.R

@Composable
fun DiveHubLogoMark(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF0077C8),
) {
    Image(
        painter = painterResource(id = R.drawable.brand_logo_mask),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
