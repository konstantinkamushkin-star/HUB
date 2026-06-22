package com.divehub.app.ui.diveeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

internal object DiveEditorBitmapUtils {

    fun decodeBitmapFromUri(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    fun bitmapToJpegMaxSide(bitmap: Bitmap, maxSide: Int, quality: Int): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val m = max(w, h).toFloat()
        val toEncode = if (m <= maxSide) {
            bitmap
        } else {
            val scale = maxSide / m
            val nw = max(1, (w * scale).roundToInt())
            val nh = max(1, (h * scale).roundToInt())
            Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        }
        val stream = java.io.ByteArrayOutputStream()
        toEncode.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        if (toEncode !== bitmap) toEncode.recycle()
        return stream.toByteArray()
    }

    private fun computeInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var inSampleSize = 1
        val longer = max(width, height)
        while (longer / inSampleSize > maxSide * 2) {
            inSampleSize *= 2
        }
        return max(1, inSampleSize)
    }

    fun openInputStream(context: Context, uri: Uri): InputStream? {
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> runCatching { java.net.URL(uri.toString()).openStream() }.getOrNull()
            else -> context.contentResolver.openInputStream(uri)
        }
    }
}
