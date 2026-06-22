package com.divehub.app.ui.components

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.divehub.app.R
import java.io.ByteArrayOutputStream

/** iOS parity: `SimpleSignaturePadView` — draw signature, export PNG base64. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePadSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!visible) return

    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.inventory_signature_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(top = 12.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                current = listOf(offset)
                                strokes.add(current)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                current = current + change.position
                                if (strokes.isNotEmpty()) {
                                    strokes[strokes.lastIndex] = current
                                }
                            },
                            onDragEnd = { current = emptyList() },
                        )
                    },
            ) {
                val strokeStyle = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                strokes.forEach { points ->
                    if (points.size < 2) return@forEach
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { p -> lineTo(p.x, p.y) }
                    }
                    drawPath(path, color = Color.Black, style = strokeStyle)
                }
            }
            OutlinedButton(
                onClick = { strokes.clear() },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.inventory_signature_clear))
            }
            Button(
                onClick = {
                    val b64 = encodeStrokesToPngBase64(strokes)
                    if (!b64.isNullOrBlank()) {
                        onConfirm(b64)
                        onDismiss()
                    }
                },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.common_ok))
            }
        }
    }
}

private fun encodeStrokesToPngBase64(strokes: List<List<Offset>>): String? {
    if (strokes.isEmpty()) return null
    val w = 900
    val h = 360
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 8f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { points ->
        if (points.size < 2) return@forEach
        val path = android.graphics.Path()
        path.moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { p -> path.lineTo(p.x, p.y) }
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
