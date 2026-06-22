package com.divehub.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.FeedRepository
import com.divehub.app.data.remote.dto.FeedPostDto
import com.divehub.app.util.FeedHashtagParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HashtagFeedUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val posts: List<FeedPostDto> = emptyList(),
    val hasMore: Boolean = false,
    val cursor: String? = null,
    val imageApiRoot: String = "",
)

class HashtagFeedViewModel(
    private val graph: AppGraph,
    private val repo: FeedRepository,
    private val hashtag: String,
) : ViewModel() {
    private val _state = MutableStateFlow(HashtagFeedUiState())
    val state: StateFlow<HashtagFeedUiState> = _state.asStateFlow()

    private val normalizedTag = FeedHashtagParser.normalize(hashtag)

    fun load() {
        viewModelScope.launch {
            val imgRoot = graph.tokenStore.getRootBaseUrl()
            _state.value = HashtagFeedUiState(loading = true, imageApiRoot = imgRoot)
            runCatching { repo.list(cursor = null, hashtag = normalizedTag) }
                .onSuccess { res ->
                    _state.value = HashtagFeedUiState(
                        loading = false,
                        posts = res.items,
                        hasMore = res.hasMore,
                        cursor = res.nextCursor,
                        imageApiRoot = imgRoot,
                    )
                }
                .onFailure {
                    _state.value = HashtagFeedUiState(loading = false, imageApiRoot = imgRoot)
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loadingMore || s.cursor.isNullOrBlank()) return
        viewModelScope.launch {
            _state.value = s.copy(loadingMore = true)
            runCatching { repo.list(cursor = s.cursor, hashtag = normalizedTag) }
                .onSuccess { res ->
                    _state.value = s.copy(
                        loadingMore = false,
                        posts = s.posts + res.items,
                        hasMore = res.hasMore,
                        cursor = res.nextCursor,
                    )
                }
                .onFailure {
                    _state.value = s.copy(loadingMore = false)
                }
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

    companion object {
        fun factory(graph: AppGraph, hashtag: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HashtagFeedViewModel(graph, FeedRepository(graph), hashtag) as T
            }
        }
    }
}
