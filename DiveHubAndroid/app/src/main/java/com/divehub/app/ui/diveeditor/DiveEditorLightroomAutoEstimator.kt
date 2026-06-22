package com.divehub.app.ui.diveeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Port of iOS [DiveEditorLightroomAutoEstimator] — heuristic “Auto” slider positions from frame stats.
 */
object DiveEditorLightroomAutoEstimator {

    data class Result(
        val depth: Double,
        val colorStrength: Double,
        val dehaze: Double,
        val clarity: Double,
        val temperature: Double,
    ) {
        companion object {
            val fallback = Result(
                depth = 32.0,
                colorStrength = 62.0,
                dehaze = 48.0,
                clarity = 40.0,
                temperature = 12.0,
            )
        }
    }

    fun estimate(bitmap: Bitmap): Result {
        val stats = ThumbnailRgbAnalyzer.stats(bitmap, maxSide = 96) ?: return Result.fallback

        val r = stats.meanR
        val g = stats.meanG
        val b = stats.meanB
        val meanL = stats.meanLuma
        val contrast = min(1.0, max(0.0, stats.lumaStd / 0.2))

        val blueExcess = b - r
        val greenShift = g - (r + b) * 0.5
        val temperature = min(100.0, max(-100.0, blueExcess * 95 + greenShift * 35))

        val depth = min(100.0, max(6.0, 14.0 + (1.0 - meanL) * 58 + max(0.0, blueExcess) * 42))

        val chroma = max(r, max(g, b)) - min(r, min(g, b))
        val colorStrength = min(100.0, max(22.0, 45.0 + (0.32 - chroma) * 95))

        val flatness = 1.0 - contrast
        val dehaze = min(100.0, max(10.0, 22.0 + flatness * 62 + meanL * 0.22 * 35))
        val clarity = min(100.0, max(6.0, 18.0 + flatness * 68))

        return Result(
            depth = depth,
            colorStrength = colorStrength,
            dehaze = dehaze,
            clarity = clarity,
            temperature = temperature,
        )
    }
}

private object ThumbnailRgbAnalyzer {
    data class Stats(
        val meanR: Double,
        val meanG: Double,
        val meanB: Double,
        val meanLuma: Double,
        val lumaStd: Double,
    )

    fun stats(source: Bitmap, maxSide: Int): Stats? {
        val w0 = source.width
        val h0 = source.height
        if (w0 <= 1 || h0 <= 1) return null

        val scale = min(min(maxSide.toFloat() / w0, maxSide.toFloat() / h0), 1f)
        val tw = max(1, (w0 * scale).toInt())
        val th = max(1, (h0 * scale).toInt())

        val thumb = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(thumb)
        canvas.drawBitmap(source, null, android.graphics.Rect(0, 0, tw, th), null)

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumL = 0.0
        var sumL2 = 0.0
        var count = 0.0

        val pixels = IntArray(tw * th)
        thumb.getPixels(pixels, 0, tw, 0, 0, tw, th)
        for (px in pixels) {
            val a = Color.alpha(px) / 255.0
            if (a < 0.02) continue
            val rp = min(1.0, Color.red(px) / 255.0 / a)
            val gp = min(1.0, Color.green(px) / 255.0 / a)
            val bp = min(1.0, Color.blue(px) / 255.0 / a)
            sumR += rp
            sumG += gp
            sumB += bp
            val l = 0.2126 * rp + 0.7152 * gp + 0.0722 * bp
            sumL += l
            sumL2 += l * l
            count += 1.0
        }
        thumb.recycle()

        if (count <= 8.0) return null

        val meanR = sumR / count
        val meanG = sumG / count
        val meanB = sumB / count
        val meanL = sumL / count
        val variance = max(0.0, sumL2 / count - meanL * meanL)
        val lumaStd = sqrt(variance)
        return Stats(meanR = meanR, meanG = meanG, meanB = meanB, meanLuma = meanL, lumaStd = lumaStd)
    }
}
