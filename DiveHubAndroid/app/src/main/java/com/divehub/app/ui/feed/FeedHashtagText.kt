package com.divehub.app.ui.feed

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.divehub.app.util.FeedHashtagParser

@Composable
fun FeedHashtagText(
    content: String,
    onHashtagClick: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val segments = FeedHashtagParser.segments(content)
    val annotated = buildAnnotatedString {
        segments.forEach { segment ->
            if (segment.hashtag != null) {
                pushStringAnnotation(tag = "hashtag", annotation = segment.hashtag)
                withStyle(SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)) {
                    append(segment.text)
                }
                pop()
            } else {
                append(segment.text)
            }
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "hashtag", start = offset, end = offset)
                .firstOrNull()
                ?.let { onHashtagClick(it.item) }
        },
    )
}
