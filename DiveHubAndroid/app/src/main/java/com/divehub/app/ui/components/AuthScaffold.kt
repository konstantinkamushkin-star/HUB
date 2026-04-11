package com.divehub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divehub.app.ui.theme.IosDesign

@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(IosDesign.ScreenPadding),
            shape = IosDesign.CardCorner,
            tonalElevation = 0.dp,
            shadowElevation = IosDesign.CardElevation,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(IosDesign.ScreenPadding)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                )
                content()
            }
        }
    }
}
