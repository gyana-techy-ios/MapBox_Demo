package com.raahi.app.feature.tripdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.LocalAppContainer
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.Trip
import com.raahi.app.map.LocalMapboxConfig
import com.raahi.app.map.MapMarker
import com.raahi.app.map.MapMarkerKind
import com.raahi.app.map.RaahiMap
import com.raahi.app.map.rememberRaahiMapState
import com.raahi.app.ui.components.ConfirmDialog
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.PrimaryButton
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SectionHeader
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.components.displayName
import com.raahi.app.ui.components.icon
import com.raahi.app.ui.components.labelRes
import com.raahi.app.ui.format.formatDistance
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.util.rememberMapStyle
import com.raahi.app.ui.theme.Sizes
import com.raahi.app.ui.util.TripLinks
import com.raahi.app.ui.util.shareText

private val HeaderMapHeight = 240.dp

@Composable
fun TripDetailRoute(
    tripId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onPreviewRoute: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onBackToTrips: () -> Unit = onBack,
) {
    val viewModel = appViewModel(key = "trip_detail_$tripId") {
        TripDetailViewModel(tripId = tripId, trips = it.tripRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mapStyle = rememberMapStyle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val trip = state.trip

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TripDetailEvent.Deleted -> onBack()
            }
        }
    }

    val shareHeader = trip?.let { stringResource(R.string.tripdetail_share_header, it.name) }.orEmpty()
    val shareOpen = stringResource(R.string.tripdetail_share_open, TripLinks.deepLink(tripId))
    val shareChooser = stringResource(R.string.tripdetail_share_chooser)
    val shareStops = trip?.stops?.sortedBy { it.order }?.mapIndexed { index, stop ->
        stringResource(R.string.tripdetail_share_stop, index + 1, stop.displayName())
    }.orEmpty()

    Scaffold(
        modifier = modifier.testTag("screen_tripdetail"),
        topBar = {
            RaahiTopBar(
                title = trip?.name ?: stringResource(R.string.tripdetail_title),
                onBack = onBack,
                actions = {
                    if (trip != null) {
                        IconButton(onClick = { onEdit(tripId) }, modifier = Modifier.testTag("tripdetail_edit")) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(
                            onClick = { context.shareText(TripLinks.shareText(shareHeader, shareStops, shareOpen), shareChooser) },
                            modifier = Modifier.testTag("tripdetail_share"),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share))
                        }
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("tripdetail_delete")) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (trip != null) {
                Surface(tonalElevation = 3.dp) {
                    PrimaryButton(
                        text = stringResource(R.string.tripdetail_action_preview),
                        onClick = { onPreviewRoute(tripId) },
                        icon = Icons.Outlined.Directions,
                        modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.lg).testTag("tripdetail_preview"),
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            state.isLoading -> SkeletonList(modifier = contentModifier.padding(top = LocalSpacing.current.lg))
            trip == null -> EmptyState(
                icon = Icons.Outlined.Map,
                title = stringResource(R.string.tripdetail_missing_title),
                body = stringResource(R.string.tripdetail_missing_body),
                modifier = contentModifier.testTag("tripdetail_missing"),
                actionLabel = stringResource(R.string.tripdetail_back_to_trips),
                onAction = onBackToTrips,
            )
            else -> TripDetailBody(trip = trip, straightLineMeters = state.straightLineMeters, mapStyle = mapStyle, modifier = contentModifier)
        }
    }

    if (confirmDelete && trip != null) {
        ConfirmDialog(
            title = stringResource(R.string.tripdetail_delete_title),
            message = stringResource(R.string.tripdetail_delete_message, trip.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteTrip()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun TripDetailBody(
    trip: Trip,
    straightLineMeters: Double?,
    mapStyle: MapStyleMode,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val stops = remember(trip) { trip.stops.sortedBy { it.order } }
    LazyColumn(modifier = modifier.testTag("tripdetail_list"), contentPadding = PaddingValues(bottom = spacing.lg)) {
        item(key = "map") { TripMap(stops = stops, style = mapStyle) }
        item(key = "summary") {
            Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(trip.profile.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(spacing.sm))
                    Text(stringResource(trip.profile.labelRes()), style = MaterialTheme.typography.titleMedium)
                }
                if (straightLineMeters != null) {
                    Text(
                        text = stringResource(R.string.tripdetail_distance_hint, formatDistance(straightLineMeters)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("tripdetail_distance"),
                    )
                }
            }
        }
        item(key = "timeline_header") { SectionHeader(title = stringResource(R.string.tripdetail_timeline_title)) }
        itemsIndexed(stops, key = { index, _ -> "stop_$index" }) { index, stop ->
            val name = stop.displayName()
            val role = when (index) {
                0 -> stringResource(R.string.tripdetail_stop_start)
                stops.lastIndex -> stringResource(R.string.tripdetail_stop_destination)
                else -> null
            }
            val subtitle = stop.place?.address?.takeIf { it.isNotBlank() }
                ?: if (stop.useCurrentLocation) stringResource(R.string.tripdetail_start_my_location_hint) else null
            val description = stringResource(R.string.tripdetail_stop_description, index + 1, stops.size, name)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.minTouchTarget)
                    .padding(horizontal = spacing.lg, vertical = spacing.sm)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .testTag("tripdetail_stop_$index"),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(spacing.md))
                Column {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    role?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TripMap(stops: List<com.raahi.app.domain.model.TripStop>, style: MapStyleMode) {
    val points = remember(stops) { stops.mapNotNull { it.place?.point } }
    val markers = remember(stops) {
        stops.mapIndexedNotNull { index, stop ->
            stop.place?.let {
                MapMarker(
                    id = "stop_$index",
                    point = it.point,
                    kind = if (index == stops.lastIndex) MapMarkerKind.Destination else MapMarkerKind.Stop,
                    label = "${index + 1}",
                )
            }
        }
    }
    val mapState = rememberRaahiMapState(initialCenter = points.firstOrNull() ?: GeoPoint.INDIA_CENTER, initialZoom = if (points.isEmpty()) 4.2 else 10.0)
    val mapAvailable = LocalMapboxConfig.current.isMapAvailable
    LaunchedEffect(points, mapAvailable) {
        if (mapAvailable && points.isNotEmpty()) mapState.fitPoints(points, paddingPx = 100.0)
    }
    val description = stringResource(R.string.tripdetail_map_description, stops.size)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderMapHeight)
            .semantics { contentDescription = description }
            .testTag("tripdetail_map"),
    ) {
        RaahiMap(state = mapState, style = style, markers = markers, interactive = false)
    }
}
