package com.raahi.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraState
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.viewport.data.OverviewViewportStateOptions
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint

@Stable
class RaahiMapState internal constructor(
    initialCenter: GeoPoint,
    initialZoom: Double,
    initialBearing: Double = 0.0,
    initialPitch: Double = 0.0,
) {
    internal val viewport: MapViewportState = MapViewportState(
        CameraState(
            Point.fromLngLat(initialCenter.lng, initialCenter.lat),
            EdgeInsets(0.0, 0.0, 0.0, 0.0),
            initialZoom,
            initialBearing,
            initialPitch,
        ),
    )

    val center: GeoPoint
        get() = viewport.cameraState?.center?.let { GeoPoint(it.latitude(), it.longitude()) } ?: initialCenterValue

    val zoom: Double
        get() = viewport.cameraState?.zoom ?: initialZoomValue

    private var isReady = false
    private var pendingAction: (() -> Unit)? = null

    private val initialCenterValue = initialCenter
    private val initialZoomValue = initialZoom

    fun flyTo(point: GeoPoint, zoom: Double? = null, durationMillis: Long = DEFAULT_FLY_DURATION_MILLIS) {
        viewport.flyTo(
            cameraOptions {
                center(Point.fromLngLat(point.lng, point.lat))
                zoom(zoom ?: this@RaahiMapState.zoom.coerceAtLeast(MIN_FOCUS_ZOOM))
            },
            MapAnimationOptions.mapAnimationOptions { duration(durationMillis) },
        )
    }

    fun fitBounds(bounds: GeoBounds, paddingPx: Double = DEFAULT_FIT_PADDING_PX) {
        fitPoints(
            listOf(GeoPoint(bounds.south, bounds.west), GeoPoint(bounds.north, bounds.east)),
            paddingPx,
        )
    }

    fun fitPoints(
        points: List<GeoPoint>,
        paddingPx: Double = DEFAULT_FIT_PADDING_PX,
        topPx: Double = paddingPx,
        bottomPx: Double = paddingPx,
        startPx: Double = paddingPx,
        endPx: Double = paddingPx,
    ) {
        if (points.isEmpty()) return
        runWhenReady { applyFit(points, paddingPx, topPx, bottomPx, startPx, endPx) }
    }

    private fun applyFit(
        points: List<GeoPoint>,
        paddingPx: Double,
        topPx: Double,
        bottomPx: Double,
        startPx: Double,
        endPx: Double,
    ) {
        if (points.all { it.distanceTo(points.first()) < MIN_FIT_SPAN_METERS }) {
            flyTo(points.first(), zoom = MIN_FOCUS_ZOOM + 3)
            return
        }
        viewport.transitionToOverviewState(
            OverviewViewportStateOptions.Builder()
                .geometry(MultiPoint.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) }))
                .padding(EdgeInsets(topPx, startPx, bottomPx, endPx))
                .build(),
        )
    }

    internal fun markReady() {
        isReady = true
        pendingAction?.invoke()
        pendingAction = null
    }

    private fun runWhenReady(action: () -> Unit) {
        if (isReady) action() else pendingAction = action
    }

    fun followUser() {
        viewport.transitionToFollowPuckState()
    }

    internal fun snapshot(): List<Double> {
        val current = viewport.cameraState
        return if (current != null) {
            listOf(current.center.latitude(), current.center.longitude(), current.zoom, current.bearing, current.pitch)
        } else {
            listOf(initialCenterValue.lat, initialCenterValue.lng, initialZoomValue, 0.0, 0.0)
        }
    }

    companion object {
        const val DEFAULT_FLY_DURATION_MILLIS = 1_200L
        const val DEFAULT_FIT_PADDING_PX = 120.0
        const val MIN_FOCUS_ZOOM = 13.0
        private const val MIN_FIT_SPAN_METERS = 100.0
        const val DEFAULT_ZOOM = 4.2

        val Saver: Saver<RaahiMapState, Any> = listSaver<RaahiMapState, Double>(
            save = { it.snapshot() },
            restore = { RaahiMapState(GeoPoint(it[0], it[1]), it[2], it[3], it[4]) },
        )
    }
}

@Composable
fun rememberRaahiMapState(
    initialCenter: GeoPoint = GeoPoint.INDIA_CENTER,
    initialZoom: Double = RaahiMapState.DEFAULT_ZOOM,
): RaahiMapState = rememberSaveable(saver = RaahiMapState.Saver) {
    RaahiMapState(initialCenter, initialZoom)
}
