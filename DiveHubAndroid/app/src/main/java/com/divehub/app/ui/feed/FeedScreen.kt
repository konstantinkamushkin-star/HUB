package com.divehub.app.ui.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ScubaDiving
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.divehub.app.AppGraph
import com.divehub.app.diveHubApp
import com.divehub.app.R
import com.divehub.app.util.absoluteMediaUrl
import com.divehub.app.data.remote.dto.DiveLogLiteDto
import com.divehub.app.data.remote.dto.FeedPostDto
import com.divehub.app.ui.theme.IosDesign
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedRoute(graph: AppGraph) {
    val vm: FeedViewModel = viewModel(factory = FeedViewModel.factory(graph))
    val state by vm.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<FeedPostDto?>(null) }
    var diveDetail by remember { mutableStateOf<DiveLogLiteDto?>(null) }

    LaunchedEffect(showCreate) {
        if (showCreate) vm.loadDiveLogs()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, null)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.posts.isEmpty() -> EmptyFeed()
                else -> LazyColumn(
                    contentPadding = PaddingValues(IosDesign.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(IosDesign.ScreenPadding),
                ) {
                    itemsIndexed(state.posts, key = { _, it -> it.id }) { idx, post ->
                        FeedCard(
                            post = post,
                            imageApiRoot = state.imageApiRoot,
                            onLike = { vm.toggleLike(post.id) },
                            onComments = { commentsFor = post },
                            onOpenDiveLog = { diveDetail = it },
                        )
                        if (idx >= state.posts.size - 2) vm.loadMore()
                    }
                    if (state.loadingMore) {
                        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    }
                }
            }
        }
    }

    if (showCreate) {
        Dialog(
            onDismissRequest = { showCreate = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                CreatePostFullscreen(
                    vm = vm,
                    onClose = { showCreate = false },
                    onPost = { context, text, photos, diveLogId ->
                        vm.createPost(context, text, photos, diveLogId) { showCreate = false }
                    },
                )
            }
        }
    }
    if (commentsFor != null) {
        CommentsSheet(
            post = commentsFor!!,
            vm = vm,
            onDismiss = { commentsFor = null },
        )
    }
    diveDetail?.let { dive ->
        ModalBottomSheet(onDismissRequest = { diveDetail = null }) {
            FeedDiveLogDetailSheet(
                dive = dive,
                imageApiRoot = state.imageApiRoot,
                onClose = { diveDetail = null },
            )
        }
    }
}

/** Full width, natural aspect ratio (capped height) — avoids hard crop in a fixed square. */
@Composable
private fun FeedPostPhoto(model: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(min = 96.dp, max = 520.dp)
                .align(Alignment.Center),
        )
    }
}

@Composable
private fun FeedCard(
    post: FeedPostDto,
    imageApiRoot: String,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onOpenDiveLog: (DiveLogLiteDto) -> Unit,
) {
    Card(
        shape = IosDesign.CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(IosDesign.ScreenPadding)) {
            Text(post.user?.displayName() ?: stringResource(R.string.feed_user_fallback), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(post.createdAt ?: "", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (!post.content.isNullOrBlank()) Text(post.content)
            if (post.photos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.photos.forEach { raw ->
                        val url = raw.trim()
                        if (url.isEmpty()) return@forEach
                        val model = absoluteMediaUrl(imageApiRoot, url)
                        if (model.isBlank()) return@forEach
                        FeedPostPhoto(model = model)
                    }
                }
            }
            val attachedDive = post.diveLog
            if (attachedDive != null) {
                Spacer(Modifier.height(8.dp))
                DiveLogPreview(
                    dive = attachedDive,
                    onOpen = { onOpenDiveLog(attachedDive) },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onLike) {
                    Icon(if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                    Spacer(Modifier.width(4.dp))
                    Text("${post.likes}")
                }
                TextButton(onClick = onComments) {
                    Icon(Icons.Default.ChatBubbleOutline, null)
                    Spacer(Modifier.width(4.dp))
                    Text("${post.comments}")
                }
            }
        }
    }
}

@Composable
private fun DiveLogPreview(dive: DiveLogLiteDto, onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(stringResource(R.string.feed_dive_log), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.feed_depth_format, dive.maxDepth?.toInt() ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.feed_time_format, dive.duration ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!dive.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(dive.notes ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.feed_dive_tap_detail),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FeedDiveLogDetailSheet(dive: DiveLogLiteDto, imageApiRoot: String, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(stringResource(R.string.logbook_dive_log_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        dive.date?.let { Text(stringResource(R.string.logbook_date, it)) }
        Text(stringResource(R.string.logbook_max_depth_value, dive.maxDepth?.toInt() ?: 0))
        Text(stringResource(R.string.logbook_avg_depth_value, dive.averageDepth?.toInt() ?: 0))
        Text(stringResource(R.string.logbook_duration_value, dive.duration ?: 0))
        dive.waterTemperature?.let { Text(stringResource(R.string.logbook_water_temp_value, it.toInt())) }
        dive.visibility?.let { Text(stringResource(R.string.logbook_visibility_value, it.toInt())) }
        dive.current?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.logbook_current_label) + ": $it")
        }
        dive.diveType?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.logbook_dive_type_label) + ": $it")
        }
        when {
            !dive.diveSiteName.isNullOrBlank() ->
                Text(
                    stringResource(R.string.feed_dive_site_name, dive.diveSiteName!!),
                    style = MaterialTheme.typography.bodySmall,
                )
            !dive.diveSiteId.isNullOrBlank() ->
                Text(stringResource(R.string.feed_dive_site_id, dive.diveSiteId!!), style = MaterialTheme.typography.bodySmall)
        }
        if (!dive.notes.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.logbook_notes_label), fontWeight = FontWeight.SemiBold)
            Text(dive.notes ?: "", style = MaterialTheme.typography.bodyMedium)
        }
        val divePhotos = dive.photoUrls.orEmpty()
        if (divePhotos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(divePhotos.size) { idx ->
                    val p = divePhotos[idx].trim()
                    if (p.isEmpty()) return@items
                    val model = absoluteMediaUrl(imageApiRoot, p)
                    if (model.isBlank()) return@items
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(160.dp, 120.dp).clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.common_close))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EmptyFeed() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.feed_no_posts_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.feed_no_posts_subtitle), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostFullscreen(
    vm: FeedViewModel,
    onClose: () -> Unit,
    onPost: (android.content.Context, String, List<Uri>, String?) -> Unit,
) {
    val context = LocalContext.current
    val diveLogs by vm.diveLogs.collectAsState()
    val diveLogsLoading by vm.diveLogsLoading.collectAsState()
    val diveLogsLoadError by vm.diveLogsLoadError.collectAsState()
    var showDivePicker by remember { mutableStateOf(false) }
    var showEmptyDivesDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var selectedPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedDiveLogId by remember { mutableStateOf<String?>(null) }
    val quickTags = listOf("#wreck", "#reef", "#nightdive", "#deep")

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris -> selectedPhotos = uris }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_create_post)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = IosDesign.ScreenPadding)
                    .padding(bottom = 24.dp),
            ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.feed_share_experience)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickTags.forEach { tag ->
                    AssistChip(
                        onClick = {
                            if (!text.contains(tag)) text = (text + " " + tag).trim() + " "
                        },
                        label = { Text(tag) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.feed_add_photo))
                }
                OutlinedButton(
                    onClick = {
                        when {
                            diveLogsLoading -> Unit
                            diveLogs.isEmpty() -> {
                                if (diveLogsLoadError != null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.feed_no_dives_for_post),
                                        )
                                    }
                                } else {
                                    showEmptyDivesDialog = true
                                }
                            }
                            else -> showDivePicker = true
                        }
                    },
                    enabled = !diveLogsLoading,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (diveLogsLoading) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.ScubaDiving, contentDescription = null)
                        }
                        Text(stringResource(R.string.feed_add_dive))
                    }
                }
            }
            diveLogsLoadError?.let { err ->
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.feed_dive_logs_load_error, err),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { vm.loadDiveLogs() }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
            if (!diveLogsLoading && diveLogs.isEmpty() && diveLogsLoadError == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.feed_no_dives_for_post),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectedPhotos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(selectedPhotos.size) { idx ->
                        AsyncImage(
                            model = selectedPhotos[idx],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(90.dp, 90.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
            if (!selectedDiveLogId.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                val dive = diveLogs.firstOrNull { it.id == selectedDiveLogId }
                Text(
                    stringResource(
                        R.string.feed_dive_attached,
                        dive?.date ?: selectedDiveLogId.orEmpty(),
                        dive?.maxDepth?.toInt() ?: 0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (diveLogs.isNotEmpty()) {
                            val currentIdx = diveLogs.indexOfFirst { it.id == selectedDiveLogId }
                            val next = diveLogs[(currentIdx + 1).coerceAtLeast(0) % diveLogs.size]
                            selectedDiveLogId = next.id
                        }
                    }) { Text(stringResource(R.string.feed_edit)) }
                    TextButton(onClick = { selectedDiveLogId = null }) { Text(stringResource(R.string.feed_remove)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onPost(context, text, selectedPhotos, selectedDiveLogId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotBlank() || selectedPhotos.isNotEmpty() || selectedDiveLogId != null,
            ) { Text(stringResource(R.string.feed_post)) }
            }
        }
    }

    if (showEmptyDivesDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyDivesDialog = false },
            title = { Text(stringResource(R.string.feed_empty_dives_dialog_title)) },
            text = { Text(stringResource(R.string.feed_empty_dives_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyDivesDialog = false
                        context.diveHubApp().emitDiverTab(2)
                        onClose()
                    },
                ) { Text(stringResource(R.string.feed_go_to_logbook)) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyDivesDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showDivePicker && diveLogs.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDivePicker = false },
            title = { Text(stringResource(R.string.feed_pick_dive_title)) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    diveLogs.forEach { log ->
                        TextButton(
                            onClick = {
                                selectedDiveLogId = log.id
                                showDivePicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${log.date} · ${log.maxDepth.toInt()} m · ${log.duration} min",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDivePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(post: FeedPostDto, vm: FeedViewModel, onDismiss: () -> Unit) {
    val comments by vm.comments.collectAsState()
    var text by remember { mutableStateOf("") }
    LaunchedEffect(post.id) { vm.loadComments(post.id) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.feed_comments), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            comments.forEach { c ->
                Text("${c.user?.displayName() ?: stringResource(R.string.feed_user_fallback)}: ${c.content}")
                Spacer(Modifier.height(6.dp))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.feed_add_comment)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    vm.addComment(post.id, text)
                    text = ""
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feed_post)) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
