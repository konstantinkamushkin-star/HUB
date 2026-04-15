package com.divehub.app.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.divehub.app.data.remote.dto.ReviewDto

@Composable
fun ReviewListRow(r: ReviewDto) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            r.userName?.trim().orEmpty().ifBlank { "—" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(r.rating.coerceIn(1, 5)) {
                Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color(0xFFF2C94C))
            }
        }
        Text(r.text, style = MaterialTheme.typography.bodyMedium)
    }
}
