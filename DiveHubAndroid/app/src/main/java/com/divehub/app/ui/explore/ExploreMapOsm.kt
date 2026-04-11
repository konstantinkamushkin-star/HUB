package com.divehub.app.ui.explore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.divehub.app.R
import com.divehub.app.data.remote.dto.ExploreDiveSite
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class ExploreMapActions(
    val zoomIn: () -> Unit,
    val zoomOut: () -> Unit,
    val centerOn: (Double, Double) -> Unit,
)

@Composable
fun ExploreMapOsm(
    sites: List<ExploreDiveSite>,
    onSiteTap: (ExploreDiveSite) -> Unit,
    onActionsReady: (ExploreMapActions) -> Unit,
) {
    val context = LocalContext.current
    val markerIcon = remember(context) { createDiveHubMarker(context) }
    val mapRef = remember { arrayOfNulls<MapView>(1) }
    val viewportInitialized = remember { booleanArrayOf(false) }
    val lastSitesSignature = remember { intArrayOf(0) }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
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
                controller.setZoom(4.0)
                controller.setCenter(GeoPoint(25.7617, -80.1918))
                mapRef[0] = this
            }
        },
        update = { map ->
            // Avoid expensive full redraw on every recomposition.
            val signature = 31 * sites.size + sites.fold(0) { acc, site -> 31 * acc + site.id.hashCode() }
            if (signature != lastSitesSignature[0]) {
                lastSitesSignature[0] = signature
                map.overlays.clear()
                // Keep map responsive with large datasets.
                sites.take(400).forEach { site ->
                    map.overlays.add(site.asMarker(map, markerIcon, onSiteTap))
                }
            }
            if (sites.isNotEmpty() && !viewportInitialized[0]) {
                if (sites.size == 1) {
                    map.controller.setZoom(10.5)
                    map.controller.setCenter(GeoPoint(sites.first().latitude, sites.first().longitude))
                } else if (sites.size > 40) {
                    // Avoid global zoom-out when backend returns many worldwide points.
                    map.controller.setZoom(5.8)
                    map.controller.setCenter(GeoPoint(sites.first().latitude, sites.first().longitude))
                } else {
                    val lats = sites.map { it.latitude }
                    val lngs = sites.map { it.longitude }
                    val box = BoundingBox(
                        lats.maxOrNull() ?: 0.0,
                        lngs.maxOrNull() ?: 0.0,
                        lats.minOrNull() ?: 0.0,
                        lngs.minOrNull() ?: 0.0,
                    )
                    map.post { map.zoomToBoundingBox(box, true, 120) }
                }
                viewportInitialized[0] = true
            }
            map.invalidate()
        },
    )

    LaunchedEffect(mapRef[0]) {
        val map = mapRef[0] ?: return@LaunchedEffect
        onActionsReady(
            ExploreMapActions(
                zoomIn = { map.controller.setZoom(map.zoomLevelDouble + 1.0) },
                zoomOut = { map.controller.setZoom(map.zoomLevelDouble - 1.0) },
                centerOn = { lat, lng -> map.controller.animateTo(GeoPoint(lat, lng)) },
            ),
        )
    }
}

private fun createDiveHubMarker(context: Context): Drawable {
    val dp = context.resources.displayMetrics.density
    val w = (40 * dp).toInt().coerceAtLeast(36)
    val h = w
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val logo = ContextCompat.getDrawable(context, R.drawable.brand_logo_mask)
    if (logo != null) {
        logo.setBounds(0, 0, w, h)
        logo.draw(canvas)
    }
    return BitmapDrawable(context.resources, bitmap)
}

private fun ExploreDiveSite.asMarker(
    map: MapView,
    icon: Drawable?,
    onTap: (ExploreDiveSite) -> Unit,
): Marker {
    return Marker(map).apply {
        position = GeoPoint(latitude, longitude)
        title = name
        snippet = "${diveType} • ${difficulty}"
        setAnchor(Marker.ANCHOR_CENTER, 1f)
        this.icon = icon
        setOnMarkerClickListener { _, _ ->
            onTap(this@asMarker)
            true
        }
    }
}
