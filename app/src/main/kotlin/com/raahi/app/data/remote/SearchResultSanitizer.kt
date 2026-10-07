package com.raahi.app.data.remote

import com.raahi.app.domain.model.GeoPoint

/**
 * Search Box returns some features geo-coded to the India centroid fallback point.
 * Those are placeholder geocodes, not real locations, so they are dropped unless the
 * query origin is itself near that point.
 */
object SearchResultSanitizer {
    const val PLACEHOLDER_RADIUS_METERS = 200.0
    const val ORIGIN_EXEMPTION_RADIUS_METERS = 5_000.0

    fun isPlaceholder(point: GeoPoint, origin: GeoPoint?): Boolean {
        if (origin != null && origin.distanceTo(PLACEHOLDER_POINT) <= ORIGIN_EXEMPTION_RADIUS_METERS) return false
        return point.distanceTo(PLACEHOLDER_POINT) <= PLACEHOLDER_RADIUS_METERS
    }

    private val PLACEHOLDER_POINT = GeoPoint.INDIA_CENTER
}
