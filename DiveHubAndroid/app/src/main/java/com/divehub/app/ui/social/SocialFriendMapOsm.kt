package com.divehub.app.ui.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.divehub.app.data.remote.dto.DiscoverNearbyDto
import com.divehub.app.data.remote.dto.FriendLocationDto
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

enum class SocialMapPinKind {
    Friend,
    Discover,
    Self,
}

data class SocialMapPin(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val kind: SocialMapPinKind,
    val userId: String? = null,
)

@Composable
fun SocialFriendMapOsm(
    pins: List<SocialMapPin>,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 220.dp,
    onPinTap: (SocialMapPin) -> Unit = {},
) {
    val context = LocalContext.current
    val friendIcon = remember(context) { createPinIcon(context, 0xFF2196F3.toInt()) }
    val discoverIcon = remember(context) { createPinIcon(context, 0xFFFF9800.toInt()) }
    val selfIcon = remember(context) { createPinIcon(context, 0xFF4CAF50.toInt()) }
    val mapRef = remember { arrayOfNulls<MapView>(1) }
    val lastSignature = remember { intArrayOf(0) }
    val viewportInitialized = remember { booleanArrayOf(false) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(mapHeight)
            .clipToBounds(),
        factory = {
            MapView(it).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setBuiltInZoomControls(false)
                setMultiTouchControls(true)
                setTilesScaledToDpi(true)
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                minZoomLevel = 3.0
                maxZoomLevel = 19.0
                controller.setZoom(12.0)
                controller.setCenter(GeoPoint(20.0, 0.0))
                mapRef[0] = this
            }
        },
        update = { map ->
            val signature = pins.fold(31) { acc, pin ->
                31 * acc + pin.id.hashCode() + pin.kind.ordinal
            }
            if (signature != lastSignature[0]) {
                lastSignature[0] = signature
                map.overlays.clear()
                pins.forEach { pin ->
                    val icon = when (pin.kind) {
                        SocialMapPinKind.Friend -> friendIcon
                        SocialMapPinKind.Discover -> discoverIcon
                        SocialMapPinKind.Self -> selfIcon
                    }
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(pin.latitude, pin.longitude)
                            title = pin.title
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            this.icon = icon
                            setOnMarkerClickListener { _, _ ->
                                onPinTap(pin)
                                true
                            }
                        },
                    )
                }
            }
            if (pins.isNotEmpty() && !viewportInitialized[0]) {
                if (pins.size == 1) {
                    map.controller.setZoom(13.0)
                    map.controller.setCenter(GeoPoint(pins.first().latitude, pins.first().longitude))
                } else {
                    val lats = pins.map { it.latitude }
                    val lngs = pins.map { it.longitude }
                    val box = BoundingBox(
                        lats.maxOrNull() ?: 0.0,
                        lngs.maxOrNull() ?: 0.0,
                        lats.minOrNull() ?: 0.0,
                        lngs.minOrNull() ?: 0.0,
                    )
                    map.post { map.zoomToBoundingBox(box, true, 80) }
                }
                viewportInitialized[0] = true
            }
            map.invalidate()
        },
    )

    LaunchedEffect(pins) {
        viewportInitialized[0] = false
    }
}

fun friendPinsForMap(locations: List<FriendLocationDto>): List<SocialMapPin> =
    locations.map { pin ->
        SocialMapPin(
            id = pin.userId,
            latitude = pin.latitude,
            longitude = pin.longitude,
            title = pin.displayName(),
            kind = SocialMapPinKind.Friend,
            userId = pin.userId,
        )
    }

fun discoverPinsForMap(users: List<DiscoverNearbyDto>): List<SocialMapPin> =
    users.map { user ->
        SocialMapPin(
            id = user.userId,
            latitude = user.latitude,
            longitude = user.longitude,
            title = user.displayName(),
            kind = SocialMapPinKind.Discover,
            userId = user.userId,
        )
    }

private fun createPinIcon(context: Context, colorArgb: Int): Drawable {
    val dp = context.resources.displayMetrics.density
    val size = (28 * dp).toInt().coerceAtLeast(24)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }
    val radius = size / 2f - dp
    canvas.drawCircle(size / 2f, size / 2f, radius, paint)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp
    }
    canvas.drawCircle(size / 2f, size / 2f, radius, stroke)
    return BitmapDrawable(context.resources, bitmap)
}
