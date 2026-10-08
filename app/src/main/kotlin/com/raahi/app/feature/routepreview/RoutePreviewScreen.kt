package com.raahi.app.feature.routepreview

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Traffic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.CongestionLevel
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.Trip
import com.raahi.app.map.LocalMapboxConfig
import com.raahi.app.map.MapMarker
import com.raahi.app.map.MapMarkerKind
import com.raahi.app.map.MapRoute
import com.raahi.app.map.RaahiMap
import com.raahi.app.map.rememberRaahiMapState
import com.raahi.app.ui.components.ConnectivityBanner
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.ErrorState
import com.raahi.app.ui.components.PrimaryButton
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SecondaryButton
import com.raahi.app.ui.components.displayName
import com.raahi.app.ui.format.formatDistance
import com.raahi.app.ui.format.formatDuration
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.util.rememberMapStyle
import com.raahi.app.ui.theme.RaahiShapes
import com.raahi.app.ui.theme.Sizes

private val SheetMaxHeight = 340.dp

@Composable
fun RoutePreviewRoute(
    tripId: Long,
    onBack: () -> Unit,
    onStartGuidance: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
    onEditTrip: (Long) -> Unit = {},
) {
    val viewModel = appViewModel(key = "route_preview_$tripId") {
        RoutePreviewViewModel(
            tripId = tripId,
            trips = it.tripRepository,
            planRoutes = it.planRoutesUseCase,
            locationRepository = it.locationRepository,
            connectivity = it.connectivityMonitor,
            routeStore = it.activeRouteStore,
            clock = it.clock,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mapStyle = rememberMapStyle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionResult()
    }

    Scaffold(
        modifier = modifier.testTag("screen_routepreview"),
        topBar = { RaahiTopBar(title = stringResource(R.string.routepreview_title), onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ConnectivityBanner(state = state.connectivity)
            Box(modifier = Modifier.weight(1f)) {
                PreviewBody(
                    state = state,
                    mapStyle = mapStyle,
                    onRetry = viewModel::retry,
                    onSelect = viewModel::selectRoute,
                    onStart = { onStartGuidance(tripId, state.selectedIndex) },
                    onEditTrip = { onEditTrip(tripId) },
                    onGrantLocation = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PreviewBody(
    state: RoutePreviewUiState,
    mapStyle: MapStyleMode,
    onRetry: () -> Unit,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
    onEditTrip: () -> Unit,
    onGrantLocation: () -> Unit,
) {
    when (state.phase) {
        PreviewPhase.Loading -> LoadingState()
        PreviewPhase.Ready -> ReadyState(state, mapStyle, onSelect, onStart)
        PreviewPhase.NoRoute -> MessageState(
            icon = Icons.Outlined.Directions,
            title = stringResource(R.string.routepreview_no_route_title),
            body = stringResource(R.string.routepreview_no_route_body),
            tag = "routepreview_no_route",
            primaryLabel = stringResource(R.string.routepreview_action_edit),
            onPrimary = onEditTrip,
        )
        PreviewPhase.NeedsLocationPermission -> MessageState(
            icon = Icons.Outlined.LocationOff,
            title = stringResource(R.string.routepreview_permission_title),
            body = stringResource(R.string.routepreview_permission_body),
            tag = "routepreview_permission",
            primaryLabel = stringResource(R.string.action_allow_location),
            onPrimary = onGrantLocation,
            secondaryLabel = stringResource(R.string.routepreview_action_edit),
            onSecondary = onEditTrip,
        )
        PreviewPhase.Offline -> MessageState(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.routepreview_offline_title),
            body = stringResource(R.string.routepreview_offline_body),
            tag = "routepreview_offline",
            primaryLabel = stringResource(R.string.action_retry),
            onPrimary = onRetry,
        )
        PreviewPhase.Failed -> ErrorState(
            error = state.error ?: com.raahi.app.core.result.AppError.Unknown(),
            modifier = Modifier.testTag("routepreview_error"),
            onRetry = onRetry,
        )
        PreviewPhase.TripMissing -> EmptyState(
            icon = Icons.Outlined.Map,
            title = stringResource(R.string.routepreview_missing_title),
            body = stringResource(R.string.routepreview_missing_body),
            modifier = Modifier.testTag("routepreview_missing"),
        )
    }
}

@Composable
private fun LoadingState() {
    val spacing = LocalSpacing.current
    val message = stringResource(R.string.routepreview_loading)
    Column(
        modifier = Modifier.fillMaxSize().testTag("routepreview_loading").semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.padding(spacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MessageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    tag: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Box(modifier = Modifier.fillMaxSize().testTag(tag)) {
        EmptyState(icon = icon, title = title, body = body, actionLabel = primaryLabel, onAction = onPrimary)
        if (secondaryLabel != null && onSecondary != null) {
            SecondaryButton(
                text = secondaryLabel,
                onClick = onSecondary,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = spacing.xl),
            )
        }
    }
}

@Composable
private fun ReadyState(
    state: RoutePreviewUiState,
    mapStyle: MapStyleMode,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val trip = state.trip
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sheetWidth = maxWidth / 2
        if (maxWidth > maxHeight) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    PreviewMap(state = state, mapStyle = mapStyle, onSelect = onSelect)
                }
                RouteSheet(
                    state = state,
                    trip = trip,
                    onSelect = onSelect,
                    onStart = onStart,
                    sideBySide = true,
                    modifier = Modifier.width(sheetWidth).fillMaxHeight(),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    PreviewMap(state = state, mapStyle = mapStyle, onSelect = onSelect)
                }
                RouteSheet(state = state, trip = trip, onSelect = onSelect, onStart = onStart, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PreviewMap(state: RoutePreviewUiState, mapStyle: MapStyleMode, onSelect: (Int) -> Unit) {
    val routes = state.routes
    val mapRoutes = remember(routes, state.selectedIndex) {
        routes.mapIndexed { index, route -> MapRoute(points = route.geometry, isPrimary = index == state.selectedIndex) }
    }
    val markers = remember(state.trip, state.selectedRoute) { stopMarkers(state.trip, state.selectedRoute) }
    val bounds = remember(routes) { GeoBounds.of(routes.flatMap { it.geometry }) }
    val mapState = rememberRaahiMapState(initialCenter = bounds?.center ?: GeoPoint.INDIA_CENTER, initialZoom = 8.0)
    val mapAvailable = LocalMapboxConfig.current.isMapAvailable
    LaunchedEffect(bounds, mapAvailable) {
        if (mapAvailable && bounds != null) mapState.fitBounds(bounds, paddingPx = 80.0)
    }
    val description = stringResource(R.string.routepreview_map_description, routes.size)
    Box(modifier = Modifier.fillMaxSize().semantics { contentDescription = description }.testTag("routepreview_map")) {
        RaahiMap(state = mapState, style = mapStyle, markers = markers, routes = mapRoutes, onRouteClick = onSelect)
    }
}

private fun stopMarkers(trip: Trip?, route: Route?): List<MapMarker> {
    val stops = trip?.stops?.sortedBy { it.order }.orEmpty()
    return stops.mapIndexedNotNull { index, stop ->
        val point = stop.place?.point ?: route?.geometry?.firstOrNull()?.takeIf { stop.useCurrentLocation && index == 0 }
        point?.let {
            MapMarker(
                id = "stop_$index",
                point = it,
                kind = if (index == stops.lastIndex) MapMarkerKind.Destination else MapMarkerKind.Stop,
                label = "${index + 1}",
            )
        }
    }
}

@Composable
private fun RouteSheet(
    state: RoutePreviewUiState,
    trip: Trip?,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    sideBySide: Boolean = false,
) {
    val spacing = LocalSpacing.current
    Surface(
        shape = RaahiShapes.large.copy(bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp), bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp)),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier.testTag("routepreview_sheet"),
    ) {
        Column {
            LazyColumn(
                modifier = (if (sideBySide) Modifier.weight(1f) else Modifier.heightIn(max = SheetMaxHeight)).selectableGroup(),
                contentPadding = PaddingValues(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                itemsIndexed(state.routes, key = { _, route -> route.id }) { index, route ->
                    RouteOptionCard(
                        index = index,
                        route = route,
                        selected = index == state.selectedIndex,
                        isFastest = index == state.fastestIndex && state.routes.size > 1,
                        trip = trip,
                        onSelect = { onSelect(index) },
                    )
                }
            }
            PrimaryButton(
                text = stringResource(R.string.routepreview_action_start),
                onClick = onStart,
                icon = Icons.Outlined.Directions,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg).padding(bottom = spacing.lg).testTag("routepreview_start"),
            )
        }
    }
}

@Composable
private fun RouteOptionCard(
    index: Int,
    route: Route,
    selected: Boolean,
    isFastest: Boolean,
    trip: Trip?,
    onSelect: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val duration = formatDuration(route.durationSeconds)
    val distance = formatDistance(route.distanceMeters)
    val congestion = route.congestionLevel.labelRes()?.let { stringResource(it) }
    val via = route.summary.takeIf { it.isNotBlank() }?.let { stringResource(R.string.routepreview_via, it) }
    val fastest = stringResource(R.string.routepreview_fastest)
    val description = buildString {
        append(stringResource(R.string.routepreview_option_description, index + 1, duration, distance, via.orEmpty()))
        if (isFastest) append(" $fastest.")
        if (congestion != null) append(" $congestion.")
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("route_option_$index"),
    ) {
        Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(duration, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("route_duration_$index"))
                Spacer(Modifier.width(spacing.sm))
                Text(distance, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (isFastest) FastestBadge(fastest)
            }
            Text(
                text = via ?: stringResource(R.string.routepreview_option_title, index + 1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (congestion != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Traffic,
                        contentDescription = null,
                        tint = if (route.congestionLevel >= CongestionLevel.HEAVY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(spacing.xs))
                    Text(congestion, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (selected && route.legs.size > 1) LegRows(route = route, trip = trip)
        }
    }
}

@Composable
private fun FastestBadge(label: String) {
    val spacing = LocalSpacing.current
    Surface(shape = RaahiShapes.small, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bolt, contentDescription = null)
            Spacer(Modifier.width(spacing.xs))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun LegRows(route: Route, trip: Trip?) {
    val spacing = LocalSpacing.current
    val stops = trip?.stops?.sortedBy { it.order }.orEmpty()
    Column(modifier = Modifier.padding(top = spacing.sm).testTag("route_legs"), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(stringResource(R.string.routepreview_legs_title), style = MaterialTheme.typography.labelLarge)
        route.legs.forEachIndexed { index, leg ->
            val from = stops.getOrNull(index)?.displayName().orEmpty()
            val to = stops.getOrNull(index + 1)?.displayName().orEmpty()
            val stats = stringResource(
                R.string.stats_separator,
                formatDistance(leg.steps.sumOf { it.distanceMeters }),
                formatDuration(leg.steps.sumOf { it.durationSeconds }),
            )
            val label = stringResource(R.string.trip_first_to_last, from, to)
            val description = stringResource(R.string.routepreview_leg_description, index + 1, "$label, $stats")
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.minTouchTarget / 2).semantics(mergeDescendants = true) { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(stats, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun CongestionLevel.labelRes(): Int? = when (this) {
    CongestionLevel.UNKNOWN -> null
    CongestionLevel.LOW -> R.string.routepreview_congestion_light
    CongestionLevel.MODERATE -> R.string.routepreview_congestion_moderate
    CongestionLevel.HEAVY -> R.string.routepreview_congestion_heavy
    CongestionLevel.SEVERE -> R.string.routepreview_congestion_severe
}
