package com.raahi.app.domain.usecase

import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.OfflineEstimate
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

object TileEstimator {

    const val MAX_LATITUDE = 85.0511
    const val AVERAGE_TILE_BYTES = 40_000L
    const val STYLE_PACK_OVERHEAD_BYTES = 6_000_000L

    fun estimate(bounds: GeoBounds, minZoom: Int, maxZoom: Int): OfflineEstimate {
        require(minZoom <= maxZoom) { "minZoom must be <= maxZoom" }
        val tiles = (minZoom..maxZoom).sumOf { tileCount(bounds, it) }
        return OfflineEstimate(
            tileCount = tiles,
            approximateBytes = tiles * AVERAGE_TILE_BYTES + STYLE_PACK_OVERHEAD_BYTES,
        )
    }

    fun tileCount(bounds: GeoBounds, zoom: Int): Long {
        val tilesPerAxis = 2.0.pow(zoom)
        val xMin = tileX(bounds.west, tilesPerAxis)
        val xMax = tileX(bounds.east, tilesPerAxis)
        val yMin = tileY(bounds.north, tilesPerAxis)
        val yMax = tileY(bounds.south, tilesPerAxis)
        return (xMax - xMin + 1) * (yMax - yMin + 1)
    }

    private fun tileX(lng: Double, tilesPerAxis: Double): Long {
        val x = floor((lng + 180.0) / 360.0 * tilesPerAxis).toLong()
        return x.coerceIn(0, tilesPerAxis.toLong() - 1)
    }

    private fun tileY(lat: Double, tilesPerAxis: Double): Long {
        val clamped = max(-MAX_LATITUDE, min(MAX_LATITUDE, lat))
        val latRad = Math.toRadians(clamped)
        val y = floor((1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * tilesPerAxis).toLong()
        return y.coerceIn(0, tilesPerAxis.toLong() - 1)
    }
}
