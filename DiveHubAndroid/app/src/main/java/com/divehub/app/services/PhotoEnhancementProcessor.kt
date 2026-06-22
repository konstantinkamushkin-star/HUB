package com.divehub.app.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.divehub.app.data.diveeditor.CardLookProfile
import com.divehub.app.data.diveeditor.DiveEditorPhotoRepository
import java.io.ByteArrayOutputStream

/**
 * Cloud underwater enhancement — same pipeline as iOS [PhotoEnhancementProcessor].
 */
object PhotoEnhancementProcessor {

    suspend fun process(jpeg: ByteArray, repo: DiveEditorPhotoRepository): ByteArray {
        val profile = CardLookProfile.default
        return runCatching {
            repo.processPhotoUnderwaterVisionModule(
                imageJpeg = jpeg,
                engine = profile.engine,
                mode = profile.mode,
            )
        }.getOrElse { first ->
            runCatching {
                val imageId = repo.uploadImageForProcessing(jpeg)
                val payload = DiveEditorPhotoRepository.ImageProcessParamsPayload(
                    depth = 25.0,
                    strength = 50.0,
                    dehaze = 0.0,
                    clarity = 0.0,
                    temperature = 0.0,
                    autoAi = false,
                    pipeline = "default",
                )
                val job = repo.createImageProcessJob(imageId, payload)
                repo.waitForImageProcessJob(jobId = job.jobId, maxWaitSeconds = 120)
            }.getOrElse {
                repo.processUnderwaterPhotoWithAI(
                    imageJpeg = jpeg,
                    depthMeters = 10.0,
                    strength = profile.strength ?: 0.7,
                    useAi = false,
                    pipeline = "default",
                )
            }
        }
    }

    fun jpegData(bitmap: Bitmap, maxSide: Int = 2048, quality: Int = 92): ByteArray {
        val scaled = scaleBitmap(bitmap, maxSide)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled != bitmap) scaled.recycle()
        return out.toByteArray()
    }

    fun jpegData(imageBytes: ByteArray, maxSide: Int = 2048, quality: Int = 92): ByteArray? {
        val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
        return try {
            jpegData(bmp, maxSide, quality)
        } finally {
            bmp.recycle()
        }
    }

    private fun scaleBitmap(source: Bitmap, maxSide: Int): Bitmap {
        val w = source.width
        val h = source.height
        val max = maxOf(w, h)
        if (max <= maxSide) return source
        val scale = maxSide.toFloat() / max.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }
}
