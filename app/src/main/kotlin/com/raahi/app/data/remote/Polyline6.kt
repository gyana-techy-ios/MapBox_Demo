package com.raahi.app.data.remote

import com.raahi.app.domain.model.GeoPoint

object Polyline6 {

    private const val PRECISION = 1e6

    fun decode(encoded: String): List<GeoPoint> {
        val points = ArrayList<GeoPoint>(encoded.length / 4)
        var index = 0
        var lat = 0L
        var lng = 0L
        while (index < encoded.length) {
            val latResult = readDelta(encoded, index)
            lat += latResult.first
            index = latResult.second
            val lngResult = readDelta(encoded, index)
            lng += lngResult.first
            index = lngResult.second
            points += GeoPoint(lat / PRECISION, lng / PRECISION)
        }
        return points
    }

    private fun readDelta(encoded: String, start: Int): Pair<Long, Int> {
        var index = start
        var result = 0L
        var shift = 0
        var chunk: Int
        do {
            require(index < encoded.length) { "Truncated polyline" }
            chunk = encoded[index++].code - 63
            result = result or ((chunk and 0x1f).toLong() shl shift)
            shift += 5
        } while (chunk >= 0x20)
        val delta = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return delta to index
    }
}
