package com.divehub.app.data.diveeditor

import com.divehub.app.data.local.TokenStore
import com.divehub.app.util.mediaOriginBaseUrl
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * iOS parity: [NetworkService.processPhotoUnderwaterVisionModule], image job pipeline, [NetworkService.processUnderwaterPhotoWithAI].
 */
class DiveEditorPhotoRepository(
    private val okHttpClient: OkHttpClient,
    private val tokenStore: TokenStore,
    private val gson: Gson = Gson(),
) {

    private val longTimeoutClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .callTimeout(600, TimeUnit.SECONDS)
            .build()
    }

    suspend fun processPhotoUnderwaterVisionModule(
        imageJpeg: ByteArray,
        engine: String,
        mode: String?,
    ): ByteArray {
        val root = mediaOriginBaseUrl(tokenStore.getRootBaseUrl()).trimEnd('/')
        val base = UnderwaterVisionUrls.underwaterVisionModuleBaseUrl(root)
        val url = UnderwaterVisionUrls.processPhotoUrl(base, engine, mode)
        val imageBody = imageJpeg.toRequestBody("image/jpeg".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "photo.jpg", imageBody)
            .build()
        val req = Request.Builder()
            .url(url)
            .post(multipart)
            .header("Cache-Control", "no-cache")
            .build()
        return longTimeoutClient.executeSuspending(req).use { resp ->
            val body = resp.body?.use { it.bytes() } ?: throw IOException("empty body")
            if (!resp.isSuccessful) {
                throw IOException(uvmErrorMessage(body, resp.code))
            }
            val env = gson.fromJson(body.decodeToString(), UvmEnvelope::class.java)
                ?: throw IOException("invalid UVM JSON")
            hexDecodeJpeg(env.imageJpegBase64) ?: throw IOException("invalid image_jpeg_base64 hex")
        }
    }

    suspend fun processVideoUnderwaterVisionModule(
        videoMp4: ByteArray,
        engine: String = "ai2",
    ): ByteArray {
        val root = mediaOriginBaseUrl(tokenStore.getRootBaseUrl()).trimEnd('/')
        val base = UnderwaterVisionUrls.underwaterVisionModuleBaseUrl(root)
        val url = UnderwaterVisionUrls.processVideoUrl(base, engine)
        val videoBody = videoMp4.toRequestBody("video/mp4".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("video", "video.mp4", videoBody)
            .build()
        val req = Request.Builder()
            .url(url)
            .post(multipart)
            .header("Cache-Control", "no-cache")
            .build()
        return longTimeoutClient.executeSuspending(req).use { resp ->
            val body = resp.body?.use { it.bytes() } ?: throw IOException("empty body")
            if (!resp.isSuccessful) {
                throw IOException(uvmErrorMessage(body, resp.code))
            }
            body
        }
    }

    suspend fun uploadImageForProcessing(jpegData: ByteArray): String {
        val root = mediaOriginBaseUrl(tokenStore.getRootBaseUrl()).trimEnd('/')
        val url = "$root/api/v1/image/upload"
        val part = jpegData.toRequestBody("image/jpeg".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "photo.jpg", part)
            .build()
        val req = Request.Builder().url(url).post(multipart).build()
        return okHttpClient.executeSuspending(req).use { resp ->
            val body = resp.body?.use { it.bytes() } ?: throw IOException("empty body")
            if (!resp.isSuccessful) throw IOException("upload ${resp.code}: ${body.decodeToString().take(400)}")
            gson.fromJson(body.decodeToString(), ImageUploadResponse::class.java)?.imageId
                ?: throw IOException("upload parse error")
        }
    }

    suspend fun createImageProcessJob(imageId: String, params: ImageProcessParamsPayload): ImageProcessJobCreateResponse {
        val root = mediaOriginBaseUrl(tokenStore.getRootBaseUrl()).trimEnd('/')
        val url = "$root/api/v1/image/process"
        val bodyObj = ImageProcessRequestBody(
            imageId = imageId,
            pipeline = params.pipeline,
            params = params,
        )
        val json = gson.toJson(bodyObj)
        val req = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return okHttpClient.executeSuspending(req).use { resp ->
            val body = resp.body?.use { it.bytes() } ?: throw IOException("empty body")
            if (!resp.isSuccessful) throw IOException("process ${resp.code}: ${body.decodeToString().take(400)}")
            gson.fromJson(body.decodeToString(), ImageProcessJobCreateResponse::class.java)
                ?: throw IOException("job create parse error")
        }
    }

    suspend fun waitForImageProcessJob(jobId: String, pollMs: Long = 400L, maxWaitSeconds: Long = 120): ByteArray =
        withContext(Dispatchers.IO) {
            val root = mediaOriginBaseUrl(tokenStore.getRootBaseUrl()).trimEnd('/')
            val deadline = System.currentTimeMillis() + maxWaitSeconds * 1000L
            while (System.currentTimeMillis() < deadline) {
                coroutineContext.ensureActive()
                val stUrl = "$root/api/v1/image/status/$jobId"
                val stReq = Request.Builder().url(stUrl).get().build()
                okHttpClient.executeSuspending(stReq).use { resp ->
                    val body = resp.body?.use { it.bytes() } ?: throw IOException("empty status body")
                    if (!resp.isSuccessful) throw IOException("status ${resp.code}")
                    val st = gson.fromJson(body.decodeToString(), ImageProcessStatusResponse::class.java)
                        ?: throw IOException("status parse")
                    when (st.status) {
                        "done" -> {
                            val dl = "$root/api/v1/image/result/$jobId"
                            val dlReq = Request.Builder().url(dl).get().build()
                            okHttpClient.executeSuspending(dlReq).use { r2 ->
                                val bytes = r2.body?.use { it.bytes() } ?: throw IOException("empty result")
                                if (!r2.isSuccessful) throw IOException("result ${r2.code}")
                                return@withContext bytes
                            }
                        }
                        "failed" -> {
                            val msg = st.error?.takeIf { it.isNotBlank() } ?: "Image job failed"
                            throw IOException(msg)
                        }
                        else -> {
                            // suspend delay needs non-blocking — we're in IO dispatcher
                        }
                    }
                }
                delay(pollMs)
            }
            throw IOException("Processing timeout")
        }

    suspend fun processUnderwaterPhotoWithAI(
        imageJpeg: ByteArray,
        depthMeters: Double,
        strength: Double,
        useAi: Boolean,
        pipeline: String,
    ): ByteArray {
        val root = mediaOriginBaseUrl(tokenStore.getRootBaseUrl()).trimEnd('/')
        val url = "$root/api/v1/underwater-ai/process"
        val boundary = "----DiveHubForm${System.currentTimeMillis()}"
        val crlf = "\r\n"
        val sb = StringBuilder()
        sb.append("--").append(boundary).append(crlf)
        sb.append("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"").append(crlf)
        sb.append("Content-Type: image/jpeg").append(crlf).append(crlf)
        val head = sb.toString().encodeToByteArray()
        val tail = (
            "${crlf}--$boundary$crlf" +
                "Content-Disposition: form-data; name=\"depth_m\"$crlf$crlf$depthMeters$crlf" +
                "--$boundary$crlf" +
                "Content-Disposition: form-data; name=\"strength\"$crlf$crlf$strength$crlf" +
                "--$boundary$crlf" +
                "Content-Disposition: form-data; name=\"use_ai\"$crlf$crlf${if (useAi) "true" else "false"}$crlf" +
                "--$boundary$crlf" +
                "Content-Disposition: form-data; name=\"pipeline\"$crlf$crlf$pipeline$crlf" +
                "--$boundary--$crlf"
            ).encodeToByteArray()
        val full = head + imageJpeg + tail
        val body = full.toRequestBody("multipart/form-data; boundary=$boundary".toMediaType())
        val pl = pipeline.trim().lowercase()
        val heavy = pl == "jmse1820" || pl == "article3" || pl == "gpt"
        val client = if (heavy) {
            okHttpClient.newBuilder()
                .readTimeout(180, TimeUnit.SECONDS)
                .callTimeout(300, TimeUnit.SECONDS)
                .build()
        } else {
            okHttpClient.newBuilder()
                .readTimeout(90, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build()
        }
        val req = Request.Builder().url(url).post(body).build()
        return client.executeSuspending(req).use { resp ->
            val bytes = resp.body?.use { it.bytes() } ?: throw IOException("empty body")
            if (!resp.isSuccessful) throw IOException("underwater-ai ${resp.code}: ${bytes.decodeToString().take(400)}")
            bytes
        }
    }

    private suspend fun OkHttpClient.executeSuspending(request: Request): okhttp3.Response =
        withContext(Dispatchers.IO) {
            val call = newCall(request)
            coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            try {
                call.execute()
            } catch (e: IOException) {
                if (call.isCanceled()) throw CancellationException("Request canceled", e)
                throw e
            }
        }

    private data class UvmEnvelope(
        @SerializedName("image_jpeg_base64") val imageJpegBase64: String,
    )

    data class ImageProcessParamsPayload(
        val depth: Double,
        val strength: Double,
        val dehaze: Double,
        val clarity: Double,
        val temperature: Double,
        @SerializedName("auto_ai") val autoAi: Boolean,
        val pipeline: String = "default",
    )

    private data class ImageProcessRequestBody(
        @SerializedName("image_id") val imageId: String,
        val pipeline: String,
        val params: ImageProcessParamsPayload,
    )

    private data class ImageUploadResponse(
        @SerializedName("image_id") val imageId: String,
    )

    data class ImageProcessJobCreateResponse(
        @SerializedName("job_id") val jobId: String,
        val status: String,
    )

    private data class ImageProcessStatusResponse(
        @SerializedName("job_id") val jobId: String,
        val status: String,
        val progress: Int,
        val error: String?,
    )

    private fun uvmErrorMessage(data: ByteArray, code: Int): String {
        val text = runCatching { gson.fromJson(data.decodeToString(), Map::class.java) }.getOrNull()
        val detail = (text?.get("detail") as? String)?.takeIf { it.isNotEmpty() }
        val err = (text?.get("error") as? String)?.takeIf { it.isNotEmpty() }
        val message = (text?.get("message") as? String)?.takeIf { it.isNotEmpty() }
        val parsed = detail ?: err ?: message
        return parsed ?: "UVM HTTP $code — ${data.decodeToString().take(400)}"
    }
}

private fun hexDecodeJpeg(hex: String): ByteArray? {
    val h = hex.filter { !it.isWhitespace() }
    if (h.length % 2 != 0 || h.isEmpty()) return null
    val out = ByteArray(h.length / 2)
    var i = 0
    var o = 0
    while (i < h.length) {
        val byte = h.substring(i, i + 2).toIntOrNull(16) ?: return null
        out[o++] = byte.toByte()
        i += 2
    }
    return out
}
