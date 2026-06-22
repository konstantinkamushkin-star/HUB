package com.divehub.app.ui.chat

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.graphics.Color as AndroidColor
import coil.compose.AsyncImage
import com.divehub.app.R
import com.divehub.app.data.remote.dto.ChatMessageDto
import com.divehub.app.ui.theme.IosDesign
import com.divehub.app.ui.theme.LocalDiveHubDarkTheme
import com.divehub.app.ui.theme.iosChromePageBackground
import com.divehub.app.util.absoluteMediaUrl
import com.divehub.app.ui.util.fileProviderImageUri
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun ChatDetailPanel(
    title: String,
    meId: String?,
    meAvatarUrl: String?,
    apiRoot: String,
    messages: List<ChatMessageDto>,
    loadingMessages: Boolean,
    loadingOlder: Boolean,
    hasMore: Boolean,
    detailError: String?,
    sendingMedia: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onPickedImageOrVideo: (Uri) -> Unit,
    onPickedVoiceFile: (Uri) -> Unit,
    onLoadOlder: () -> Unit,
    onRetry: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val dark = LocalDiveHubDarkTheme.current
    val listBackground = iosChromePageBackground()
    val incomingBubbleBackground = if (dark) IosDesign.DarkChrome.groupedSurface else IosDesign.Chat.bubbleIncoming
    val incomingTextColor = if (dark) MaterialTheme.colorScheme.onSurface else IosDesign.Explore.labelPrimary
    val composerBackground = if (dark) IosDesign.DarkChrome.groupedSurface else MaterialTheme.colorScheme.surface
    val inputBackground = if (dark) IosDesign.DarkChrome.segmentTrack else Color(AndroidColor.WHITE)
    val listState = rememberLazyListState()
    var showJumpToLatest by remember { mutableStateOf(false) }
    var didInitialScroll by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    val voice = remember { ChatVoiceRecorder(ctx) }
    val lastMessageId = messages.lastOrNull()?.id

    DisposableEffect(Unit) { onDispose { voice.cancel() } }

    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { g -> audioGranted = g }

    val pickVisual = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { u: Uri? -> u?.let { onPickedImageOrVideo(it) } }

    var chatCameraTarget: Uri? by remember { mutableStateOf(null) }
    val takeChatPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) chatCameraTarget?.let { onPickedImageOrVideo(it) }
    }

    LaunchedEffect(loadingMessages, messages.size) {
        if (!didInitialScroll && !loadingMessages && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            didInitialScroll = true
            showJumpToLatest = false
        }
    }

    LaunchedEffect(lastMessageId, loadingOlder) {
        if (!didInitialScroll) return@LaunchedEffect
        if (!loadingOlder && messages.isNotEmpty()) {
            val nearBottom = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= (messages.lastIndex - 2)
            if (nearBottom) {
                listState.animateScrollToItem(messages.lastIndex)
                showJumpToLatest = false
            } else {
                showJumpToLatest = true
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.chat_back)) }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (loadingMessages && messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(listBackground),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        } else if (!detailError.isNullOrBlank() && messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(listBackground)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text(detailError, color = MaterialTheme.colorScheme.error) }
        } else if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(listBackground)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text(stringResource(R.string.chat_no_messages_yet), style = MaterialTheme.typography.bodyMedium) }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(listBackground)
                    .padding(horizontal = IosDesign.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(IosDesign.SectionSpacing),
            ) {
                if (hasMore) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onLoadOlder, enabled = !loadingOlder) {
                                Text(
                                    if (loadingOlder) stringResource(R.string.chat_loading)
                                    else stringResource(R.string.chat_load_older),
                                )
                            }
                        }
                    }
                }
                itemsIndexed(messages, key = { _, it -> it.id }) { index, msg ->
                    val isMine = msg.senderId == meId
                    val prev = messages.getOrNull(index - 1)
                    val showName = prev?.senderId != msg.senderId
                    val prevDateLabel = prev?.createdAt?.let {
                        formatMessageDateLabel(
                            createdAt = it,
                            todayLabel = stringResource(R.string.chat_today),
                            yesterdayLabel = stringResource(R.string.chat_yesterday),
                        )
                    }
                    val currentDateLabel = msg.createdAt?.let {
                        formatMessageDateLabel(
                            createdAt = it,
                            todayLabel = stringResource(R.string.chat_today),
                            yesterdayLabel = stringResource(R.string.chat_yesterday),
                        )
                    }
                    if (!currentDateLabel.isNullOrBlank() && currentDateLabel != prevDateLabel) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                currentDateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        if (!isMine) {
                            ChatMessageAvatar(
                                nameHint = msg.senderName,
                                avatarKey = msg.senderAvatarUrl,
                                apiRoot = apiRoot,
                            )
                            Spacer(Modifier.size(6.dp))
                        }
                        val bubbleBg = if (isMine) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            incomingBubbleBackground
                        }
                        val secondaryLabel = IosDesign.Explore.labelSecondary
                        Surface(
                            modifier = Modifier.widthIn(max = IosDesign.BubbleMaxWidth),
                            shape = IosDesign.BubbleCorner,
                            color = bubbleBg,
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                if (showName) {
                                    Text(
                                        msg.senderName ?: stringResource(R.string.chat_user_fallback),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMine) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                        } else {
                                            secondaryLabel
                                        },
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                ChatMessageBody(
                                    msg = msg,
                                    isMine = isMine,
                                    apiRoot = apiRoot,
                                    incomingTextColor = incomingTextColor,
                                )
                                val timeText = formatMessageTime(msg.createdAt)
                                if (timeText.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMine) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
                                        } else {
                                            secondaryLabel
                                        },
                                    )
                                }
                                if (msg.messageType == "text" && (msg.localSending || msg.localFailed)) {
                                    if (msg.localSending) {
                                        Text(
                                            stringResource(R.string.chat_sending),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isMine) {
                                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                            } else {
                                                secondaryLabel
                                            },
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                stringResource(R.string.chat_failed),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                            TextButton(onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onRetry(msg.id)
                                            }) {
                                                Text(stringResource(R.string.chat_retry))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (isMine) {
                            Spacer(Modifier.size(6.dp))
                            ChatMessageAvatar(
                                nameHint = null,
                                avatarKey = meAvatarUrl,
                                apiRoot = apiRoot,
                            )
                        }
                    }
                }
            }
        }
        if (sendingMedia) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (loadingMessages && messages.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (showJumpToLatest && messages.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledIconButton(onClick = {
                    showJumpToLatest = false
                    scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                }) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.chat_scroll_to_latest))
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(composerBackground)
                .imePadding()
                .padding(IosDesign.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = {
                    pickVisual.launch(
                        PickVisualMediaRequest(
                            mediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                        ),
                    )
                },
                enabled = !sendingMedia,
            ) { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = stringResource(R.string.chat_attach_media)) }
            IconButton(
                onClick = {
                    val f = File(ctx.cacheDir, "chat_cap_${System.currentTimeMillis()}.jpg")
                    f.parentFile?.mkdirs()
                    val u = fileProviderImageUri(ctx, f)
                    chatCameraTarget = u
                    runCatching { takeChatPhoto.launch(u) }
                },
                enabled = !sendingMedia,
            ) { Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.photo_choose_camera)) }
            val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 80.dp, minHeight = 48.dp)
                    .heightIn(min = 48.dp, max = 120.dp),
                // Label + narrow `weight(1f)` bar causes Russian "Сообщение" to wrap into two lines; use placeholder.
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                placeholder = { Text(stringResource(R.string.chat_message_label)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = inputBackground,
                    unfocusedContainerColor = inputBackground,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = placeholderColor,
                    unfocusedPlaceholderColor = placeholderColor,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            if (input.isBlank()) {
                IconButton(
                    onClick = {
                        if (isRecording) {
                            isRecording = false
                            val uri = voice.stopAndReset()
                            if (uri != null) onPickedVoiceFile(uri)
                        } else {
                            if (audioGranted) {
                                isRecording = voice.start()
                            } else {
                                requestAudio.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    enabled = !sendingMedia,
                ) {
                    Icon(
                        if (isRecording) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.chat_record_voice),
                    )
                }
            } else {
                FilledIconButton(
                    onClick = {
                        onSend(input)
                        input = ""
                    },
                    enabled = !sendingMedia,
                    modifier = Modifier.defaultMinSize(minWidth = 50.dp, minHeight = 50.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageAvatar(
    nameHint: String?,
    avatarKey: String?,
    apiRoot: String,
    size: Dp = 36.dp,
) {
    if (avatarKey.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (nameHint ?: "?").trim().ifEmpty { "?" }.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }
    val full = absoluteMediaUrl(apiRoot, avatarKey)
    if (full.startsWith("http", ignoreCase = true)) {
        AsyncImage(
            model = full,
            contentDescription = nameHint,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (nameHint ?: "?").trim().ifEmpty { "?" }.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ChatMessageBody(
    msg: ChatMessageDto,
    isMine: Boolean,
    apiRoot: String,
    incomingTextColor: Color,
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val t = (msg.messageType ?: "text").lowercase()
    when (t) {
        "photo" -> {
            val u = msg.attachments?.firstOrNull()?.url
            if (!u.isNullOrBlank()) {
                val full = absoluteMediaUrl(apiRoot, u)
                if (full.startsWith("http", ignoreCase = true)) {
                    AsyncImage(
                        model = full,
                        contentDescription = null,
                        // Crop in a fixed max box to avoid huge empty padding in the bubble
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 260.dp)
                            .heightIn(max = 220.dp)
                            .clip(IosDesign.BubbleCorner),
                    )
                } else {
                    Text("📷", color = if (isMine) onPrimary else incomingTextColor)
                }
            }
            val c = msg.content?.trim().orEmpty()
            if (c.isNotEmpty() && c != " " && c.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(c, color = if (isMine) onPrimary else incomingTextColor)
            }
        }
        "video" -> {
            val u = msg.attachments?.firstOrNull()?.url
            val vctx = LocalContext.current
            Text(
                stringResource(R.string.chat_tap_to_open_video),
                color = if (isMine) onPrimary else MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clip(IosDesign.BubbleCorner)
                    .clickable {
                        u?.let { rel ->
                            val open = absoluteMediaUrl(apiRoot, rel)
                            if (open.startsWith("http", ignoreCase = true)) {
                                runCatching {
                                    vctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(open))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        }
                    },
            )
        }
        "voice" -> {
            val u = msg.attachments?.firstOrNull()?.url
            val label = stringResource(R.string.chat_voice_tap_to_play)
            val vctx = LocalContext.current
            Text(
                "🎤 $label",
                color = if (isMine) onPrimary else incomingTextColor,
                modifier = Modifier.clickable {
                    u?.let { rel ->
                        val open = absoluteMediaUrl(apiRoot, rel)
                        if (open.startsWith("http", ignoreCase = true)) {
                            runCatching {
                                vctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(open))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    }
                },
            )
        }
        else -> {
            val trimmed = msg.content?.trim().orEmpty()
            if (trimmed.isNotEmpty()) {
                Text(
                    trimmed,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isMine) onPrimary else incomingTextColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
