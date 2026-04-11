package com.divehub.app.data

import android.content.Context
import android.net.Uri
import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.CreateFeedPostRequest
import com.divehub.app.data.remote.dto.DiveLogDto
import com.divehub.app.data.remote.dto.FeedCommentDto
import com.divehub.app.data.remote.dto.FeedCommentRequest
import com.divehub.app.data.remote.dto.FeedListResponse
import com.divehub.app.data.remote.dto.FeedPostDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class FeedRepository(private val graph: AppGraph) {
    suspend fun list(cursor: String?): FeedListResponse {
        return graph.feedApi().listPosts(limit = 20, cursor = cursor)
    }

    suspend fun create(content: String?, type: String, photos: List<String> = emptyList(), diveLogId: String? = null): FeedPostDto {
        return graph.feedApi().createPost(
            CreateFeedPostRequest(
                type = type,
                content = content,
                photos = photos.takeIf { it.isNotEmpty() },
                diveLogId = diveLogId,
            ),
        )
    }

    suspend fun toggleLike(postId: String): FeedPostDto {
        return graph.feedApi().toggleLike(postId)
    }

    suspend fun comments(postId: String): List<FeedCommentDto> {
        return graph.feedApi().comments(postId)
    }

    suspend fun addComment(postId: String, text: String): FeedCommentDto {
        return graph.feedApi().addComment(postId, FeedCommentRequest(content = text))
    }

    /** Same endpoint as logbook — use DiveLogsApi so parsing matches working logbook list. */
    suspend fun diveLogs(): List<DiveLogDto> = graph.diveLogsApi().list()

    suspend fun uploadMedia(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri) ?: error("Unable to read file")
        val temp = File.createTempFile("feed_upload_", ".jpg", context.cacheDir)
        temp.outputStream().use { out -> input.use { it.copyTo(out) } }
        val body = temp.asRequestBody("image/*".toMediaType())
        val part = MultipartBody.Part.createFormData("file", temp.name, body)
        val res = graph.feedApi().uploadMedia(part)
        temp.delete()
        return res.path ?: res.url ?: error("Upload failed")
    }
}
