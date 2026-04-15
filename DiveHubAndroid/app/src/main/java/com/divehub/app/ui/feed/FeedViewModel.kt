package com.divehub.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.divehub.app.AppGraph
import com.divehub.app.data.FeedRepository
import com.divehub.app.data.remote.dto.DiveLogDto
import com.divehub.app.data.remote.dto.FeedCommentDto
import com.divehub.app.data.remote.dto.FeedPostDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val posts: List<FeedPostDto> = emptyList(),
    val hasMore: Boolean = false,
    val cursor: String? = null,
    val error: String? = null,
    /** Resolves relative `/api/media/…` paths for Coil (matches TokenStore base URL). */
    val imageApiRoot: String = "",
)

class FeedViewModel(
    private val graph: AppGraph,
    private val repo: FeedRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private val _comments = MutableStateFlow<List<FeedCommentDto>>(emptyList())
    val comments: StateFlow<List<FeedCommentDto>> = _comments.asStateFlow()
    private val _diveLogs = MutableStateFlow<List<DiveLogDto>>(emptyList())
    val diveLogs: StateFlow<List<DiveLogDto>> = _diveLogs.asStateFlow()
    private val _diveLogsLoading = MutableStateFlow(false)
    val diveLogsLoading: StateFlow<Boolean> = _diveLogsLoading.asStateFlow()
    private val _diveLogsLoadError = MutableStateFlow<String?>(null)
    val diveLogsLoadError: StateFlow<String?> = _diveLogsLoadError.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val imgRoot = graph.tokenStore.getRootBaseUrl()
            val prev = _state.value
            _state.value = prev.copy(loading = true, error = null, imageApiRoot = imgRoot)
            runCatching { repo.list(cursor = null) }
                .onSuccess { res ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = null,
                        posts = res.items,
                        hasMore = res.hasMore,
                        cursor = res.nextCursor,
                        imageApiRoot = imgRoot,
                    )
                }
                .onFailure { e ->
                    _state.value = prev.copy(
                        loading = false,
                        error = e.message ?: "Feed load error",
                        imageApiRoot = imgRoot,
                    )
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loadingMore || s.cursor.isNullOrBlank()) return
        viewModelScope.launch {
            _state.value = s.copy(loadingMore = true)
            runCatching { repo.list(cursor = s.cursor) }
                .onSuccess { res ->
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        posts = _state.value.posts + res.items,
                        hasMore = res.hasMore,
                        cursor = res.nextCursor,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(loadingMore = false)
                }
        }
    }

    fun createPost(
        context: Context,
        text: String,
        photoUris: List<Uri>,
        diveLogId: String?,
        onDone: () -> Unit,
    ) {
        if (text.isBlank() && photoUris.isEmpty() && diveLogId.isNullOrBlank()) return
        viewModelScope.launch {
            runCatching {
                val uploaded = photoUris.map { repo.uploadMedia(context, it) }
                val type = when {
                    diveLogId != null -> "dive"
                    uploaded.isNotEmpty() -> "photo"
                    else -> "text"
                }
                repo.create(
                    content = text.trim().ifBlank { null },
                    type = type,
                    photos = uploaded,
                    diveLogId = diveLogId,
                )
            }
                .onSuccess {
                    refresh()
                    onDone()
                }
        }
    }

    fun loadDiveLogs() {
        viewModelScope.launch {
            _diveLogsLoading.value = true
            _diveLogsLoadError.value = null
            runCatching { repo.diveLogs() }
                .onSuccess { list ->
                    _diveLogs.value = list.sortedByDescending { it.date }
                }
                .onFailure { e ->
                    _diveLogs.value = emptyList()
                    _diveLogsLoadError.value = e.message?.takeIf { it.isNotBlank() } ?: "Load failed"
                }
            _diveLogsLoading.value = false
        }
    }

    fun toggleLike(postId: String) {
        val old = _state.value.posts
        _state.value = _state.value.copy(
            posts = old.map {
                if (it.id == postId) {
                    if (it.isLiked) it.copy(isLiked = false, likes = (it.likes - 1).coerceAtLeast(0))
                    else it.copy(isLiked = true, likes = it.likes + 1)
                } else it
            },
        )
        viewModelScope.launch {
            runCatching { repo.toggleLike(postId) }.onFailure {
                _state.value = _state.value.copy(posts = old)
            }
        }
    }

    fun loadComments(postId: String) {
        viewModelScope.launch {
            _comments.value = runCatching { repo.comments(postId) }.getOrDefault(emptyList())
        }
    }

    fun addComment(postId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.addComment(postId, text.trim()) }
            loadComments(postId)
        }
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FeedViewModel(graph, FeedRepository(graph)) as T
            }
        }
    }
}
