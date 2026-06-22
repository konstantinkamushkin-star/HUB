package com.divehub.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Auto-crops certification card photos using ML Kit text block bounds (fallback: center ID-card aspect).
 */
object CertificateCardCropper {
    private const val maxSide = 2000
    private const val paddingFraction = 0.08f

    suspend fun cropAndSave(context: Context, sourceUri: Uri): Uri = withContext(Dispatchers.Default) {
        if (sourceUri.path?.contains("cert_card_cropped_") == true) return@withContext sourceUri
        val bitmap = decodeBitmap(context, sourceUri) ?: return@withContext sourceUri
        val cropped = cropToCard(bitmap)
        if (cropped === bitmap) return@withContext sourceUri
        val out = File(context.cacheDir, "cert_card_cropped_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { stream ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 88, stream)
        }
        if (cropped !== bitmap) cropped.recycle()
        bitmap.recycle()
        Uri.fromFile(out)
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        var sample = 1
        while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) sample *= 2
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decode)
        }
    }

    private suspend fun cropToCard(source: Bitmap): Bitmap {
        val textRect = detectTextUnion(source)
        val crop = when {
            textRect != null -> expand(textRect, source.width, source.height, 0.22f, 0.30f)
            else -> centerCardAspectRect(source.width, source.height)
        }
        val safe = clamp(crop, source.width, source.height)
        if (safe.width() < 48 || safe.height() < 48) return source
        return Bitmap.createBitmap(
            source,
            safe.left,
            safe.top,
            safe.width(),
            safe.height(),
        )
    }

    private suspend fun detectTextUnion(bitmap: Bitmap): RectF? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = client.process(image).await()
            var union: RectF? = null
            for (block in result.textBlocks) {
                union = union?.let { u ->
                    RectF(
                        min(u.left, block.boundingBox?.left?.toFloat() ?: u.left),
                        min(u.top, block.boundingBox?.top?.toFloat() ?: u.top),
                        max(u.right, block.boundingBox?.right?.toFloat() ?: u.right),
                        max(u.bottom, block.boundingBox?.bottom?.toFloat() ?: u.bottom),
                    )
                } ?: block.boundingBox?.let { b ->
                    RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                }
            }
            union
        } catch (_: Exception) {
            null
        } finally {
            client.close()
        }
    }

    private fun expand(rect: RectF, w: Int, h: Int, fx: Float, fy: Float): Rect {
        val padX = rect.width() * fx
        val padY = rect.height() * fy
        return clamp(
            Rect(
                (rect.left - padX).toInt(),
                (rect.top - padY).toInt(),
                (rect.right + padX).toInt(),
                (rect.bottom + padY).toInt(),
            ),
            w,
            h,
        )
    }

    private fun centerCardAspectRect(w: Int, h: Int): Rect {
        val targetRatio = 1.586f
        val imageRatio = w.toFloat() / h
        val cropW: Int
        val cropH: Int
        if (imageRatio > targetRatio) {
            cropH = h
            cropW = (h * targetRatio).toInt().coerceAtMost(w)
        } else {
            cropW = w
            cropH = (w / targetRatio).toInt().coerceAtMost(h)
        }
        val left = ((w - cropW) / 2f).toInt()
        val top = ((h - cropH) / 2f).toInt()
        return expand(
            RectF(left.toFloat(), top.toFloat(), (left + cropW).toFloat(), (top + cropH).toFloat()),
            w,
            h,
            paddingFraction,
            paddingFraction,
        )
    }

    private fun clamp(rect: Rect, w: Int, h: Int): Rect {
        val left = rect.left.coerceIn(0, w - 1)
        val top = rect.top.coerceIn(0, h - 1)
        val right = rect.right.coerceIn(left + 1, w)
        val bottom = rect.bottom.coerceIn(top + 1, h)
        return Rect(left, top, right, bottom)
    }
}
