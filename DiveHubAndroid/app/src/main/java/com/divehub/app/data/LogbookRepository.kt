package com.divehub.app.data

import android.content.Context
import android.net.Uri
import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.CreateDiveLogRequest
import com.divehub.app.data.remote.dto.DiveLogDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class LogbookRepository(private val graph: AppGraph) {
    suspend fun list(): List<DiveLogDto> = graph.diveLogsApi().list()

    suspend fun create(
        date: LocalDate,
        durationMin: Int,
        maxDepth: Double,
        avgDepth: Double?,
        temp: Double?,
        visibility: Double?,
        current: String?,
        diveType: String?,
        notes: String?,
        photoUris: List<Uri>,
        context: Context,
    ): DiveLogDto {
        val photos = photoUris.map { uploadMedia(context, it) }
        return graph.diveLogsApi().create(
            CreateDiveLogRequest(
                date = date.format(DateTimeFormatter.ISO_DATE),
                duration = durationMin,
                maxDepth = maxDepth,
                averageDepth = avgDepth,
                waterTemperature = temp,
                visibility = visibility,
                current = current?.takeIf { it.isNotBlank() },
                diveType = diveType?.takeIf { it.isNotBlank() },
                notes = notes?.takeIf { it.isNotBlank() },
                photoUrls = photos.takeIf { it.isNotEmpty() },
                isPublished = false,
            ),
        )
    }

    private suspend fun uploadMedia(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri) ?: error("Cannot read image")
        val temp = File.createTempFile("dive_log_", ".jpg", context.cacheDir)
        temp.outputStream().use { out -> input.use { it.copyTo(out) } }
        val body = temp.asRequestBody("image/*".toMediaType())
        val part = MultipartBody.Part.createFormData("file", temp.name, body)
        val res = graph.diveLogsApi().uploadMedia(part)
        temp.delete()
        return res.path ?: res.url ?: error("Upload failed")
    }
}
