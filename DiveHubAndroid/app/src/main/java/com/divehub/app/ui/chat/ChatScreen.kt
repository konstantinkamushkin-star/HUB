package com.divehub.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.remote.dto.ChatConversationDto
import com.divehub.app.data.remote.dto.ChatMessageDto
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.ui.social.SocialUiState
import com.divehub.app.ui.social.SocialViewModel
import com.divehub.app.ui.theme.IosDesign
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoute(graph: AppGraph, openFriendId: String? = null, onOpenFriendConsumed: () -> Unit = {}) {
    val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(graph))
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showNewChat by remember { mutableStateOf(false) }
    val socialVm: SocialViewModel = viewModel(factory = SocialViewModel.factory(graph))
    val socialState by socialVm.state.collectAsState()

    LaunchedEffect(showNewChat) {
        if (showNewChat) socialVm.refresh()
    }

    LaunchedEffect(state.openConversationError) {
        val msg = state.openConversationError ?: return@LaunchedEffect
        snack.showSnackbar(msg)
        vm.clearOpenConversationError()
    }

    LaunchedEffect(Unit) {
        val json = graph.consumePendingChatConversationJson() ?: return@LaunchedEffect
        runCatching { graph.gson.fromJson(json, ChatConversationDto::class.java) }
            .getOrNull()
            ?.let { vm.selectConversation(it) }
    }

    if (!openFriendId.isNullOrBlank()) {
        androidx.compose.runtime.LaunchedEffect(openFriendId) {
            vm.openOrCreateConversation(openFriendId)
            onOpenFriendConsumed()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (showNewChat) {
            ModalBottomSheet(onDismissRequest = { showNewChat = false }) {
                NewChatWithFriendsSheet(
                    state = socialState,
                    onFriendClick = { friend ->
                        vm.openOrCreateConversation(friend.id)
                        showNewChat = false
                    },
                )
            }
        }

        val selected = state.selectedConversation
        if (selected == null) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showNewChat = true }) {
                        Text(stringResource(R.string.chat_new_message))
                    }
                }
                when {
                    state.loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    state.error != null -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(state.error ?: "Error") }
                    else -> ChatList(
                        conversations = state.conversations,
                        onOpen = vm::selectConversation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        } else {
            ChatDetail(
                title = selected.peerDisplayName ?: stringResource(R.string.chat_title_fallback),
                meId = state.meId,
                messages = state.messages,
                loadingMessages = state.loadingMessages,
                loadingOlder = state.loadingOlder,
                hasMore = state.hasMore,
                detailError = state.detailError,
                onBack = vm::backToList,
                onSend = vm::send,
                onLoadOlder = vm::loadOlder,
                onRetry = vm::retryFailedMessage,
            )
        }

        SnackbarHost(
            snack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
        )
    }
}

@Composable
private fun ChatList(
    conversations: List<ChatConversationDto>,
    onOpen: (ChatConversationDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conversations.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.chat_no_chats_yet), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(conversations, key = { it.id }) { conv ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = IosDesign.CardCorner,
                elevation = CardDefaults.cardElevation(defaultElevation = IosDesign.CardElevation),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (conv.peerDisplayName ?: stringResource(R.string.chat_user_fallback)).take(1).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(conv.peerDisplayName ?: stringResource(R.string.chat_conversation_fallback), style = MaterialTheme.typography.titleMedium)
                        Text(conv.lastMessage?.content.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val lastTime = formatMessageTime(conv.lastMessage?.createdAt)
                        if (lastTime.isNotBlank()) {
                            Text(lastTime, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (conv.unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    "${conv.unreadCount}",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { onOpen(conv) },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                        shape = IosDesign.CardCorner,
                    ) {
                        Text(stringResource(R.string.chat_open))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatDetail(
    title: String,
    meId: String?,
    messages: List<ChatMessageDto>,
    loadingMessages: Boolean,
    loadingOlder: Boolean,
    hasMore: Boolean,
    detailError: String?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onLoadOlder: () -> Unit,
    onRetry: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var showJumpToLatest by remember { mutableStateOf(false) }
    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId, loadingOlder) {
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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back)) }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        if (loadingMessages && messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (!detailError.isNullOrBlank() && messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(detailError, color = MaterialTheme.colorScheme.error)
            }
        } else if (messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.chat_no_messages_yet), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = IosDesign.ScreenPadding),
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
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = IosDesign.BubbleMaxWidth),
                            shape = IosDesign.BubbleCorner,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMine) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0xFFF2F2F7)
                                },
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                if (showName) {
                                    Text(
                                        msg.senderName ?: stringResource(R.string.chat_user_fallback),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isMine) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Spacer(Modifier.height(2.dp))
                                }
                                Text(
                                    msg.content.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isMine) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                val timeText = formatMessageTime(msg.createdAt)
                                if (timeText.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMine) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        },
                                    )
                                }
                                if (msg.localSending || msg.localFailed) {
                                    if (msg.localSending) {
                                        Text(
                                            stringResource(R.string.chat_sending),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isMine) {
                                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                    }
                }
            }
        }
        if (loadingMessages && messages.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (showJumpToLatest && messages.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledIconButton(onClick = {
                    showJumpToLatest = false
                    scope.launch {
                        listState.animateScrollToItem(messages.lastIndex)
                    }
                }) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.chat_scroll_to_latest))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(IosDesign.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 50.dp),
                label = { Text(stringResource(R.string.chat_message_label)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                ),
            )
            Button(
                onClick = {
                    onSend(input)
                    input = ""
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.defaultMinSize(minHeight = 50.dp),
                shape = IosDesign.CardCorner,
            ) { Text(stringResource(R.string.chat_send)) }
        }
    }
}

@Composable
private fun NewChatWithFriendsSheet(
    state: SocialUiState,
    onFriendClick: (UserDto) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.chat_new_message_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null -> Text(
                state.error ?: stringResource(R.string.chat_error_generic),
                color = MaterialTheme.colorScheme.error,
            )
            state.friends.isEmpty() -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.chat_new_message_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.chat_new_message_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.friends, key = { it.id }) { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onFriendClick(friend) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                friend.displayName().take(1).uppercase(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(friend.displayName(), style = MaterialTheme.typography.titleMedium)
                            friend.role?.let { r ->
                                Text(r, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun formatMessageTime(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return ""
    return runCatching {
        val instant = java.time.Instant.parse(createdAt)
        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
        java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(zoned)
    }.getOrElse {
        if (createdAt.length >= 16) createdAt.substring(11, 16) else ""
    }
}

private fun formatMessageDateLabel(
    createdAt: String,
    todayLabel: String,
    yesterdayLabel: String,
): String {
    return runCatching {
        val instant = java.time.Instant.parse(createdAt)
        val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val today = java.time.LocalDate.now()
        when (date) {
            today -> todayLabel
            today.minusDays(1) -> yesterdayLabel
            else -> date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
        }
    }.getOrElse { "" }
}
