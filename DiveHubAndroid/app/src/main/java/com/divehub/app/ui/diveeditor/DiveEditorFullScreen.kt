package com.divehub.app.ui.diveeditor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.services.PhotoEnhancementJob
import com.divehub.app.services.PhotoEnhancementQueue
import com.divehub.app.ui.localization.localizedString
import com.divehub.app.ui.theme.iosChromePageBackground
import kotlinx.coroutines.launch

/** Full-screen editor — iOS `DiveEditorEditorView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiveEditorFullScreen(
    graph: AppGraph,
    jobId: String,
    sourceUri: Uri,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jobs by graph.photoEnhancementJobStore.jobs.collectAsState(initial = emptyList())
    val job = jobs.find { it.id == jobId }
    var compareMode by remember { mutableIntStateOf(0) }
    var splitRatio by remember { mutableFloatStateOf(0.5f) }

    val isRunning = job?.state == PhotoEnhancementJob.State.PENDING ||
        job?.state == PhotoEnhancementJob.State.RUNNING
    val resultUri = job?.resultUri?.let(Uri::parse)
    val hasResult = job?.state == PhotoEnhancementJob.State.COMPLETED && resultUri != null

    val compareAfter = localizedString("imageEditing", "diveEditorCompareAfter", R.string.dive_editor_compare_after)
    val compareBefore = localizedString("imageEditing", "diveEditorCompareBefore", R.string.dive_editor_compare_before)
    val compareSplit = localizedString("imageEditing", "diveEditorCompareSplit", R.string.dive_editor_compare_split)
    val bgHint = localizedString("imageEditing", "photoEnhancementBackgroundHint", R.string.dive_editor_processing_engine)
    val retryLabel = localizedString("common", "retry", R.string.common_retry)
    val doneLabel = localizedString("common", "done", R.string.common_ok)
    val saveLabel = stringResource(R.string.dive_editor_save)

    Scaffold(
        containerColor = iosChromePageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dive_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isRunning -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(bgHint, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    hasResult && compareMode == 2 -> {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { change, drag ->
                                        change.consume()
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        splitRatio = (splitRatio + drag / w).coerceIn(0.15f, 0.85f)
                                    }
                                },
                        ) {
                            AsyncImage(
                                model = sourceUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(splitRatio).fillMaxHeight(),
                            )
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
                            )
                            AsyncImage(
                                model = resultUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f - splitRatio).fillMaxHeight(),
                            )
                        }
                    }
                    hasResult && compareMode == 1 -> {
                        AsyncImage(
                            model = sourceUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    hasResult -> {
                        AsyncImage(
                            model = resultUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        AsyncImage(
                            model = sourceUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            if (job?.state == PhotoEnhancementJob.State.FAILED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    job.errorMessage ?: stringResource(R.string.common_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        PhotoEnhancementQueue.enqueue(context, sourceUri, graph.photoEnhancementJobStore)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(retryLabel) }
            }

            if (hasResult) {
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = compareMode == 0,
                        onClick = { compareMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    ) { Text(compareAfter) }
                    SegmentedButton(
                        selected = compareMode == 1,
                        onClick = { compareMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    ) { Text(compareBefore) }
                    SegmentedButton(
                        selected = compareMode == 2,
                        onClick = { compareMode = 2 },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    ) { Text(compareSplit) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) { Text(doneLabel) }
                    Button(
                        onClick = {
                            scope.launch {
                                resultUri?.let { graph.diveEditorRecentStore.prepend(it.toString()) }
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(saveLabel) }
                }
            } else if (!isRunning) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(doneLabel)
                }
            }
        }
    }
}
