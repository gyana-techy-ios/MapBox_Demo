package com.raahi.app.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val lat: Double, val lng: Double) {

    fun distanceTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.lat - lat)
        val dLng = Math.toRadians(other.lng - lng)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    companion object {
        const val EARTH_RADIUS_METERS = 6_371_008.8
        val INDIA_CENTER = GeoPoint(lat = 22.9734, lng = 78.6569)
    }
}

@Serializable
data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val center: GeoPoint get() = GeoPoint((south + north) / 2, (west + east) / 2)

    fun contains(point: GeoPoint): Boolean =
        point.lat in south..north && point.lng in west..east

    companion object {
        fun around(center: GeoPoint, radiusMeters: Double): GeoBounds {
            val latDelta = Math.toDegrees(radiusMeters / GeoPoint.EARTH_RADIUS_METERS)
            val lngDelta = latDelta / max(cos(Math.toRadians(center.lat)), 0.01)
            return GeoBounds(
                south = center.lat - latDelta,
                west = center.lng - lngDelta,
                north = center.lat + latDelta,
                east = center.lng + lngDelta,
            )
        }

        fun of(points: List<GeoPoint>): GeoBounds? {
            if (points.isEmpty()) return null
            return GeoBounds(
                south = points.minOf { it.lat },
                west = points.minOf { it.lng },
                north = points.maxOf { it.lat },
                east = points.maxOf { it.lng },
            )
        }
    }
}
