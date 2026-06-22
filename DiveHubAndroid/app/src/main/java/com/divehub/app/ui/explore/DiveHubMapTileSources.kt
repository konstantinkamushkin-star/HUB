package com.divehub.app.ui.explore

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * Как на iOS [DiveHubRasterTileOverlay]: светлая — OSM raster, тёмная — Carto Dark Matter.
 */
object DiveHubMapTileSources {
    /**
     * iOS [DiveHubRasterTileOverlay.url]: a/b/c subdomains for `tile.openstreetmap.org` (load spread + policy).
     * osmdroid [TileSourceFactory.MAPNIK] uses a single host — we mirror iOS.
     */
    val osmLight: ITileSource = XYTileSource(
        "DiveHub-OSM-Main",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://a.tile.openstreetmap.org/",
            "https://b.tile.openstreetmap.org/",
            "https://c.tile.openstreetmap.org/",
        ),
        "© OpenStreetMap contributors",
    )

    val cartoDark: ITileSource = XYTileSource(
        "DiveHub-Carto-Dark-Matter",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/",
        ),
        "© OpenStreetMap contributors, © CARTO",
    )
}
