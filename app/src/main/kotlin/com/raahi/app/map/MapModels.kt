package com.raahi.app.map

import androidx.compose.runtime.Immutable
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.PlaceCategory

sealed interface MapMarkerKind {
    data object Place : MapMarkerKind
    data object Parking : MapMarkerKind
    data object Stop : MapMarkerKind
    data object Destination : MapMarkerKind
    data object Emergency : MapMarkerKind
    data class Category(val category: PlaceCategory) : MapMarkerKind
}

@Immutable
data class MapMarker(
    val id: String,
    val point: GeoPoint,
    val kind: MapMarkerKind,
    val label: String? = null,
    val selected: Boolean = false,
)

@Immutable
data class MapRoute(
    val points: List<GeoPoint>,
    val isPrimary: Boolean,
)

@Immutable
data class MapCameraInfo(
    val center: GeoPoint,
    val zoom: Double,
    val bounds: GeoBounds,
)
