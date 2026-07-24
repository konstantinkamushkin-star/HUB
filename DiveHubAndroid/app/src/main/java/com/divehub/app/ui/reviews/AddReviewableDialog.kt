package com.divehub.app.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.ReviewsRepository
import com.divehub.app.data.remote.dto.CreateReviewRequest
import com.divehub.app.data.remote.dto.ReviewDto
import com.divehub.app.data.remote.dto.UpdateReviewRequest
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.Locale

/**
 * Create or edit a review. One review per user per place — when [existing] is set, saves via PATCH
 * and offers delete.
 */
@Composable
fun AddReviewableDialog(
    reviewableType: String,
    reviewableId: String,
    graph: AppGraph,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    existing: ReviewDto? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val editing = existing != null
    var rating by remember(existing?.id) { mutableIntStateOf(existing?.rating?.coerceIn(1, 5) ?: 5) }
    var text by remember(existing?.id) { mutableStateOf(existing?.text.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text(stringResource(R.string.review_delete_title)) },
            text = { Text(stringResource(R.string.review_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { ReviewsRepository(graph).deleteReview(existing.id) }
                                .onSuccess {
                                    busy = false
                                    confirmDelete = false
                                    onSuccess()
                                }
                                .onFailure { e ->
                                    busy = false
                                    confirmDelete = false
                                    err = e.message ?: context.getString(R.string.review_failed)
                                }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.review_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !busy) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (editing) R.string.review_dialog_edit_title else R.string.review_dialog_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StarRatingPicker(rating = rating, onRating = { rating = it })
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.review_comment_hint)) },
                    minLines = 3,
                    maxLines = 6,
                )
                err?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.trim().isEmpty()) return@Button
                    busy = true
                    err = null
                    val lang = Locale.getDefault().language.takeIf { it.length == 2 } ?: "en"
                    scope.launch {
                        runCatching {
                            val repo = ReviewsRepository(graph)
                            if (existing != null) {
                                repo.updateReview(
                                    existing.id,
                                    UpdateReviewRequest(
                                        rating = rating.coerceIn(1, 5),
                                        text = text.trim(),
                                        language = lang,
                                    ),
                                )
                            } else {
                                repo.createReview(
                                    CreateReviewRequest(
                                        reviewableType = reviewableType,
                                        reviewableId = reviewableId,
                                        rating = rating.coerceIn(1, 5),
                                        text = text.trim(),
                                        language = lang,
                                    ),
                                )
                            }
                        }.onSuccess {
                            busy = false
                            onSuccess()
                        }.onFailure { e ->
                            busy = false
                            err = when (e) {
                                is HttpException -> when (e.code()) {
                                    401 -> context.getString(R.string.review_login_required)
                                    409 -> context.getString(R.string.review_already_exists)
                                    else -> context.getString(R.string.review_failed)
                                }
                                else -> e.message ?: context.getString(R.string.review_failed)
                            }
                        }
                    }
                },
                enabled = !busy && text.trim().isNotEmpty(),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(if (editing) R.string.review_save else R.string.review_send))
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (editing) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = !busy,
                    ) {
                        Text(
                            stringResource(R.string.review_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(stringResource(R.string.common_close))
                }
            }
        },
    )
}

@Composable
private fun StarRatingPicker(rating: Int, onRating: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { star ->
            IconButton(onClick = { onRating(star) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = if (star <= rating) Color(0xFFF2C94C) else Color(0x33000000),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
