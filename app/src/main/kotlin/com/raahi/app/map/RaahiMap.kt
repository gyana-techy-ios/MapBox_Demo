package com.raahi.app.map

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.withTextColor
import com.mapbox.maps.extension.compose.annotation.generated.withTextHaloColor
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardSatelliteStyle
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.rememberStandardSatelliteStyleState
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.AnnotationSourceOptions
import com.mapbox.maps.plugin.annotation.ClusterOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.toCameraOptions
import com.raahi.app.core.config.MapboxConfig
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.ui.theme.LocalIsDarkTheme
import com.raahi.app.ui.theme.LocalRaahiColors

val LocalMapboxConfig = staticCompositionLocalOf { MapboxConfig(null) }

private const val CLUSTER_THRESHOLD = 50
private const val PRIMARY_LINE_WIDTH = 8.0
private const val ALTERNATE_LINE_WIDTH = 6.0
private const val MARKER_TEXT_SIZE = 12.0
private const val CLUSTER_RADIUS = 50L
private val LOGO_WIDTH = 88.dp

@OptIn(MapboxExperimental::class)
@Composable
fun RaahiMap(
    modifier: Modifier = Modifier,
    state: RaahiMapState = rememberRaahiMapState(),
    style: MapStyleMode = MapStyleMode.STANDARD,
    markers: List<MapMarker> = emptyList(),
    routes: List<MapRoute> = emptyList(),
    showUserLocation: Boolean = false,
    interactive: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    onMapClick: (GeoPoint) -> Unit = {},
    onMapLongClick: (GeoPoint) -> Unit = {},
    onMarkerClick: (MapMarker) -> Unit = {},
    onRouteClick: (Int) -> Unit = {},
    onCameraIdle: (MapCameraInfo) -> Unit = {},
) {
    val config = LocalMapboxConfig.current
    if (!config.isMapAvailable) {
        MapUnavailablePanel(status = config.status, modifier = modifier)
        return
    }

    val layoutDirection = LocalLayoutDirection.current
    val logoPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 8.dp,
    )
    val attributionPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + 8.dp + LOGO_WIDTH + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 8.dp,
    )
    val compassPadding = PaddingValues(
        top = contentPadding.calculateTopPadding() + 8.dp,
        end = contentPadding.calculateEndPadding(layoutDirection) + 8.dp,
    )

    val lightPreset = if (LocalIsDarkTheme.current) LightPresetValue.NIGHT else LightPresetValue.DAY
    val latestMapClick by rememberUpdatedState(onMapClick)
    val latestMapLongClick by rememberUpdatedState(onMapLongClick)
    val latestCameraIdle by rememberUpdatedState(onCameraIdle)

    Box(modifier = modifier.fillMaxSize()) {
    MapboxMap(
        modifier = Modifier.fillMaxSize(),
        mapViewportState = state.viewport,
        scaleBar = {},
        compass = { Compass(contentPadding = compassPadding) },
        logo = { Logo(contentPadding = logoPadding) },
        attribution = { Attribution(contentPadding = attributionPadding, alignment = Alignment.BottomStart) },
        onMapClickListener = OnMapClickListener { point ->
            latestMapClick(GeoPoint(point.latitude(), point.longitude()))
            true
        },
        onMapLongClickListener = OnMapLongClickListener { point ->
            latestMapLongClick(GeoPoint(point.latitude(), point.longitude()))
            true
        },
        style = {
            when (style) {
                MapStyleMode.STANDARD -> {
                    val styleState = rememberStandardStyleState {}
                    SideEffect { styleState.configurationsState.lightPreset = lightPreset }
                    MapboxStandardStyle(standardStyleState = styleState)
                }
                MapStyleMode.SATELLITE -> {
                    val styleState = rememberStandardSatelliteStyleState {}
                    SideEffect { styleState.configurationsState.lightPreset = lightPreset }
                    MapboxStandardSatelliteStyle(standardSatelliteStyleState = styleState)
                }
            }
        },
    ) {
        MapEffect(Unit) { mapView ->
            mapView.mapboxMap.subscribeMapIdle {
                state.markReady()
                val mapboxMap = mapView.mapboxMap
                val camera = mapboxMap.cameraState
                val bounds = mapboxMap.coordinateBoundsForCamera(camera.toCameraOptions())
                latestCameraIdle(
                    MapCameraInfo(
                        center = GeoPoint(camera.center.latitude(), camera.center.longitude()),
                        zoom = camera.zoom,
                        bounds = GeoBounds(
                            south = bounds.southwest.latitude(),
                            west = bounds.southwest.longitude(),
                            north = bounds.northeast.latitude(),
                            east = bounds.northeast.longitude(),
                        ),
                    ),
                )
            }
        }

        MapEffect(interactive) { mapView ->
            mapView.gestures.updateSettings {
                scrollEnabled = interactive
                rotateEnabled = interactive
                pitchEnabled = interactive
                pinchToZoomEnabled = interactive
                doubleTapToZoomInEnabled = interactive
                doubleTouchToZoomOutEnabled = interactive
                quickZoomEnabled = interactive
                pinchScrollEnabled = interactive
            }
        }

        MapEffect(showUserLocation) { mapView ->
            mapView.location.updateSettings {
                enabled = showUserLocation
                locationPuck = createDefault2DPuck(withBearing = true)
                puckBearingEnabled = true
                puckBearing = PuckBearing.COURSE
            }
        }

        RouteLines(routes = routes, onRouteClick = onRouteClick)
        MarkerLayer(markers = markers, onMarkerClick = onMarkerClick)
    }
    if (!interactive) Box(Modifier.matchParentSize().pointerInput(Unit) {})
    }
}

@Composable
private fun RouteLines(routes: List<MapRoute>, onRouteClick: (Int) -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val alternateColor = MaterialTheme.colorScheme.outline
    val indexed = routes.withIndex().sortedBy { it.value.isPrimary }
    indexed.forEach { (index, route) ->
        val points = remember(route.points) { route.points.map { Point.fromLngLat(it.lng, it.lat) } }
        PolylineAnnotation(points = points) {
            lineColor = if (route.isPrimary) primaryColor else alternateColor
            lineWidth = if (route.isPrimary) PRIMARY_LINE_WIDTH else ALTERNATE_LINE_WIDTH
            interactionsState.onClicked {
                onRouteClick(index)
                true
            }
        }
    }
}

@Composable
private fun MarkerLayer(markers: List<MapMarker>, onMarkerClick: (MapMarker) -> Unit) {
    if (markers.isEmpty()) return
    val context = LocalContext.current
    val raahiColors = LocalRaahiColors.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onSurface
    val haloColor = MaterialTheme.colorScheme.surface
    val clusterColor = MaterialTheme.colorScheme.primary
    val clusterTextColor = MaterialTheme.colorScheme.onPrimary
    val latestOnMarkerClick by rememberUpdatedState(onMarkerClick)

    val options = remember(markers, raahiColors, primary, secondary, textColor, haloColor) {
        val bitmaps = HashMap<Pair<MapMarkerKind, Boolean>, android.graphics.Bitmap>()
        markers.map { marker ->
            val bitmap = bitmaps.getOrPut(marker.kind to marker.selected) {
                MarkerIcons.render(context, marker.kind.tint(raahiColors, primary, secondary), marker.selected)
            }
            PointAnnotationOptions()
                .withPoint(Point.fromLngLat(marker.point.lng, marker.point.lat))
                .withIconImage(bitmap)
                .withIconAnchor(IconAnchor.BOTTOM)
                .withData(JsonPrimitive(marker.id))
                .apply {
                    marker.label?.let {
                        withTextField(it)
                        withTextSize(MARKER_TEXT_SIZE)
                        withTextOffset(listOf(0.0, 0.4))
                        withTextAnchor(TextAnchor.TOP)
                        withTextColor(textColor)
                        withTextHaloColor(haloColor)
                        withTextHaloWidth(1.5)
                    }
                }
        }
    }

    val clusterConfig = remember(markers.size, clusterColor, clusterTextColor) {
        if (markers.size > CLUSTER_THRESHOLD) {
            AnnotationConfig(
                annotationSourceOptions = AnnotationSourceOptions(
                    clusterOptions = ClusterOptions(
                        clusterRadius = CLUSTER_RADIUS,
                        colorLevels = listOf(0 to clusterColor.toArgb()),
                        textColor = clusterTextColor.toArgb(),
                        textSize = 14.0,
                    ),
                ),
            )
        } else {
            null
        }
    }

    PointAnnotationGroup(annotations = options, annotationConfig = clusterConfig) {
        interactionsState.onClicked { annotation ->
            val id = annotation.getData()?.asString
            markers.firstOrNull { it.id == id }?.let(latestOnMarkerClick)
            true
        }
    }
}
