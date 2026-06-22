package com.divehub.app.data.diveeditor

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Mirrors iOS [NetworkService.underwaterVisionProcessPhotoURL] / host detection for UVM vs Nest facade.
 */
internal object UnderwaterVisionUrls {

    fun underwaterVisionModuleBaseUrl(rootApiBase: String): String {
        val trimmed = rootApiBase.trim().trimEnd('/')
        return if (trimmed.isEmpty()) "https://api.dive-hub.ru" else trimmed
    }

    fun isDirectUnderwaterVisionHost(base: String): Boolean {
        val b = base.trim().trimEnd('/')
        if (b.endsWith(":8010")) return true
        val url = b.toHttpUrlOrNull() ?: return b.contains(":8010")
        return url.port == 8010
    }

    /**
     * Full URL for `POST` multipart `image` — path `/v1/process/photo/{engine}`.
     */
    fun processPhotoUrl(base: String, engine: String, mode: String?): String {
        val normalized = base.trim().trimEnd('/')
        val encEngine = java.net.URLEncoder.encode(engine, Charsets.UTF_8.name()).replace("+", "%20")
        val pathTail = "/v1/process/photo/$encEngine"
        val fullPath = if (isDirectUnderwaterVisionHost(normalized)) {
            "$normalized$pathTail"
        } else {
            val withApi = if (normalized.endsWith("/api", ignoreCase = true)) normalized else "$normalized/api"
            "$withApi$pathTail"
        }
        val http = fullPath.toHttpUrlOrNull() ?: return fullPath
        val b = http.newBuilder()
        if (!mode.isNullOrBlank()) {
            b.addQueryParameter("mode", mode.trim())
        }
        return b.build().toString()
    }

    /** `POST` multipart `video` — `/v1/process/video/{engine}` (iOS `processVideoUnderwaterVisionModule`). */
    fun processVideoUrl(base: String, engine: String): String {
        val normalized = base.trim().trimEnd('/')
        val encEngine = java.net.URLEncoder.encode(engine, Charsets.UTF_8.name()).replace("+", "%20")
        val pathTail = "/v1/process/video/$encEngine"
        val fullPath = if (isDirectUnderwaterVisionHost(normalized)) {
            "$normalized$pathTail"
        } else {
            val withApi = if (normalized.endsWith("/api", ignoreCase = true)) normalized else "$normalized/api"
            "$withApi$pathTail"
        }
        val http = fullPath.toHttpUrlOrNull() ?: return fullPath
        return http.newBuilder()
            .addQueryParameter("video_mode", "fast")
            .addQueryParameter("sample_frames", "12")
            .addQueryParameter("max_side", "1280")
            .build()
            .toString()
    }
}
