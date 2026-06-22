package com.divehub.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.divehub.app.ui.diveeditor.DiveEditorBitmapUtils
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object ProfileAvatarCropMath {
    fun baseFillScale(iw: Float, ih: Float, side: Float): Float {
        val w = max(iw, 1f)
        val h = max(ih, 1f)
        return max(side / w, side / h)
    }

    fun clampOffset(
        o: Offset,
        iw: Float,
        ih: Float,
        side: Float,
        userScale: Float,
    ): Offset {
        val k0 = baseFillScale(iw, ih, side)
        val w = iw * k0 * userScale
        val h = ih * k0 * userScale
        val maxX = max(0f, (w - side) / 2f)
        val maxY = max(0f, (h - side) / 2f)
        return Offset(
            min(max(o.x, -maxX), maxX),
            min(max(o.y, -maxY), maxY),
        )
    }

    private fun imageBoundsFromUri(ctx: Context, uri: Uri): Pair<Float, Float>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return null
        return w.toFloat() to h.toFloat()
    }

    fun readImageBounds(ctx: Context, uri: Uri): Pair<Float, Float> {
        return imageBoundsFromUri(ctx, uri) ?: (1f to 1f)
    }

    /**
     * @param previewSidePx side of the round preview in **px** (must match the composable)
     * @param offset in the same space as [previewSidePx]
     */
    fun renderSquareJpeg(
        context: Context,
        sourceUri: Uri,
        previewSidePx: Float,
        userScale: Float,
        offset: Offset,
        outSide: Int = 1024,
        quality: Int = 85,
    ): ByteArray? {
        val bitmap = DiveEditorBitmapUtils.decodeBitmapFromUri(context, sourceUri, maxSide = 4096) ?: return null
        try {
            val iw = bitmap.width.toFloat()
            val ih = bitmap.height.toFloat()
            val L = outSide.toFloat()
            val k0 = baseFillScale(iw, ih, L)
            val w = iw * k0 * userScale
            val h = ih * k0 * userScale
            val m = L / max(previewSidePx, 1f)
            val ox = offset.x * m
            val oy = offset.y * m
            val out = Bitmap.createBitmap(outSide, outSide, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val left = L / 2f + ox - w / 2f
            val top = L / 2f + oy - h / 2f
            val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
            val dst = android.graphics.RectF(left, top, left + w, top + h)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(bitmap, src, dst, paint)
            return ByteArrayOutputStream().use { os ->
                out.compress(Bitmap.CompressFormat.JPEG, quality, os)
                os.toByteArray()
            }.also { out.recycle() }
        } finally {
            bitmap.recycle()
        }
    }
}

@Composable
fun ProfileAvatarCropEditor(
    imageUri: Uri,
    side: Dp,
    userScale: Float,
    onUserScaleChange: (Float) -> Unit,
    panOffset: Offset,
    onPanOffsetChange: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var iw by remember(imageUri) { mutableFloatStateOf(0f) }
    var ih by remember(imageUri) { mutableFloatStateOf(0f) }
    LaunchedEffect(imageUri) {
        val b = ProfileAvatarCropMath.readImageBounds(ctx, imageUri)
        iw = b.first
        ih = b.second
    }
    val density = LocalDensity.current
    val sidePx = with(density) { side.toPx() }
    val dm = ctx.resources.displayMetrics
    val acc = remember(imageUri) {
        object {
            var s: Float = 1f
            var o: Offset = Offset.Zero
        }
    }
    val onScaleCb = rememberUpdatedState(onUserScaleChange)
    val onPanCb = rememberUpdatedState(onPanOffsetChange)
    LaunchedEffect(userScale, panOffset) {
        acc.s = userScale
        acc.o = panOffset
    }
    if (iw <= 0f || ih <= 0f) {
        Box(modifier.size(side).clip(CircleShape))
        return
    }
    val k0 = ProfileAvatarCropMath.baseFillScale(iw, ih, sidePx)
    val wPx = iw * k0 * userScale
    val hPx = ih * k0 * userScale
    val wDp = (wPx / dm.density).dp
    val hDp = (hPx / dm.density).dp
    Box(
        modifier
            .size(side)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(imageUri, iw, ih, sidePx) {
                detectTransformGestures { _, pan, zoom, _ ->
                    acc.s = (acc.s * zoom).coerceIn(0.6f, 8f)
                    acc.o = ProfileAvatarCropMath.clampOffset(
                        Offset(acc.o.x + pan.x, acc.o.y + pan.y),
                        iw,
                        ih,
                        sidePx,
                        acc.s,
                    )
                    onScaleCb.value(acc.s)
                    onPanCb.value(acc.o)
                }
            },
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.Center)
                .size(wDp, hDp)
                .offset { IntOffset(panOffset.x.roundToInt(), panOffset.y.roundToInt()) },
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(side)
                .clip(CircleShape)
                .border(2.dp, Color(0xFF0A84FF), CircleShape),
        )
    }
}
