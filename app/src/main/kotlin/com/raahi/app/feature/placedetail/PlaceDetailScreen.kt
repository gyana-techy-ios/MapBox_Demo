package com.raahi.app.feature.placedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddRoad
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appViewModel
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.PlaceSource
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.SavedPlaceType
import com.raahi.app.domain.model.Trip
import com.raahi.app.map.MapMarker
import com.raahi.app.map.MapMarkerKind
import com.raahi.app.map.RaahiMap
import com.raahi.app.map.rememberRaahiMapState
import com.raahi.app.ui.components.ConnectivityBanner
import com.raahi.app.ui.components.PrimaryButton
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SecondaryButton
import com.raahi.app.ui.components.icon
import com.raahi.app.ui.components.labelRes
import com.raahi.app.ui.format.formatDistance
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes
import com.raahi.app.ui.util.PlaceLinks
import com.raahi.app.ui.util.copyToClipboard
import com.raahi.app.ui.util.shareText

private val HeaderMapHeight = 220.dp

@Composable
fun PlaceDetailRoute(
    placeId: String,
    name: String,
    lat: Double,
    lng: Double,
    address: String?,
    category: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDirections: (Long) -> Unit = {},
) {
    val initialPlace = remember(placeId, name, lat, lng, address, category) {
        Place(
            id = placeId,
            name = name,
            address = address,
            point = GeoPoint(lat, lng),
            category = PlaceCategory.fromName(category),
            distanceMeters = null,
            source = PlaceSource.SEARCH,
        )
    }
    val viewModel = appViewModel(key = "place_${lat}_${lng}_$placeId") {
        PlaceDetailViewModel(
            initialPlace = initialPlace,
            savedPlaces = it.savedPlaceRepository,
            trips = it.tripRepository,
            preferences = it.preferencesRepository,
            placeSearch = it.placeSearchRepository,
            locationRepository = it.locationRepository,
            connectivity = it.connectivityMonitor,
            clock = it.clock,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaceDetailEvent.ShowMessage -> snackbarHost.showSnackbar(event.message.resolve(context))
                is PlaceDetailEvent.OpenRoutePreview -> onDirections(event.tripId)
            }
        }
    }

    PlaceDetailContent(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onDirections = viewModel::startDirections,
        onToggleFavorite = viewModel::toggleFavorite,
        onToggleLabel = viewModel::toggleLabel,
        onAddToTrip = viewModel::addToTrip,
        onAddToNewTrip = viewModel::addToNewTrip,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlaceDetailContent(
    state: PlaceDetailUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onDirections: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLabel: (SavedPlaceType) -> Unit,
    onAddToTrip: (Long) -> Unit,
    onAddToNewTrip: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val place = state.place
    val displayName = place.name.ifBlank { stringResource(R.string.placedetail_unnamed) }
    var showLabelSheet by rememberSaveable { mutableStateOf(false) }
    var showTripSheet by rememberSaveable { mutableStateOf(false) }
    var coordinatesJustCopied by remember { mutableStateOf(false) }
    val coordinates = PlaceLinks.coordinatesText(place.point)
    val shareChooserTitle = stringResource(R.string.placedetail_share_chooser)
    val coordinatesCopied = stringResource(R.string.placedetail_coordinates_copied)
    val coordinatesLabel = stringResource(R.string.placedetail_coordinates)
    val newTripName = stringResource(R.string.placedetail_new_trip_name, displayName)

    Scaffold(
        modifier = modifier.testTag("screen_placedetail"),
        topBar = { RaahiTopBar(title = stringResource(R.string.placedetail_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ConnectivityBanner(state = state.connectivity)
            PlaceHeaderMap(place = place, displayName = displayName)

            Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }.testTag("placedetail_name"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(place.category.labelRes())) },
                        leadingIcon = {
                            Icon(
                                imageVector = place.category.icon(),
                                contentDescription = null,
                                tint = LocalRaahiColors.current.categoryColor(place.category),
                            )
                        },
                    )
                    if (state.isFavorite) SavedBadge(Icons.Outlined.Bookmark, stringResource(R.string.placedetail_badge_favorite))
                    if (state.isHome) SavedBadge(Icons.Outlined.Home, stringResource(R.string.placedetail_badge_home))
                    if (state.isWork) SavedBadge(Icons.Outlined.Work, stringResource(R.string.placedetail_badge_work))
                }
                AddressRow(state = state)
                InfoRow(
                    icon = Icons.Outlined.LocationOn,
                    text = coordinates,
                    description = coordinatesLabel,
                    trailing = {
                        IconButton(
                            onClick = {
                                context.copyToClipboard(coordinatesLabel, coordinates)
                                coordinatesJustCopied = true
                            },
                            modifier = Modifier.heightIn(min = Sizes.minTouchTarget).testTag("placedetail_copy"),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.placedetail_copy_coordinates))
                        }
                    },
                )
                state.distanceMeters?.let { distance ->
                    InfoRow(
                        icon = Icons.Outlined.NearMe,
                        text = stringResource(R.string.placedetail_distance_away, formatDistance(distance)),
                        description = stringResource(R.string.placedetail_distance),
                    )
                }

                PrimaryButton(
                    text = stringResource(R.string.placedetail_directions),
                    onClick = onDirections,
                    icon = Icons.Outlined.Directions,
                    enabled = !state.isCreatingTrip,
                    modifier = Modifier.fillMaxWidth().testTag("placedetail_directions"),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    SecondaryButton(
                        text = stringResource(if (state.isFavorite) R.string.placedetail_saved else R.string.placedetail_save),
                        onClick = onToggleFavorite,
                        icon = if (state.isFavorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        modifier = Modifier.testTag("placedetail_save"),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.placedetail_add_to_trip),
                        onClick = { showTripSheet = true },
                        icon = Icons.Outlined.AddRoad,
                        modifier = Modifier.testTag("placedetail_addtrip"),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.placedetail_share),
                        onClick = {
                            context.shareText(PlaceLinks.placeShareText(place, displayName), shareChooserTitle)
                        },
                        icon = Icons.Outlined.Share,
                        modifier = Modifier.testTag("placedetail_share"),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.placedetail_home_work),
                        onClick = { showLabelSheet = true },
                        icon = Icons.Outlined.Home,
                        modifier = Modifier.testTag("placedetail_label"),
                    )
                }
            }
        }
    }

    if (showLabelSheet) {
        LabelSheet(
            state = state,
            onToggleFavorite = onToggleFavorite,
            onToggleLabel = onToggleLabel,
            onDismiss = { showLabelSheet = false },
        )
    }
    if (showTripSheet) {
        TripSheet(
            trips = state.tripsForAdding,
            onPick = { tripId ->
                showTripSheet = false
                onAddToTrip(tripId)
            },
            onNewTrip = {
                showTripSheet = false
                onAddToNewTrip(newTripName)
            },
            onDismiss = { showTripSheet = false },
        )
    }
    if (coordinatesJustCopied) {
        LaunchedEffect(Unit) {
            snackbarHost.showSnackbar(coordinatesCopied)
            coordinatesJustCopied = false
        }
    }
}

@Composable
private fun PlaceHeaderMap(place: Place, displayName: String) {
    val mapState = rememberRaahiMapState(initialCenter = place.point, initialZoom = 15.0)
    val description = stringResource(R.string.placedetail_map_description, displayName)
    val markers = remember(place.id, place.point, place.category) {
        listOf(
            MapMarker(
                id = "place",
                point = place.point,
                kind = place.category?.let { MapMarkerKind.Category(it) } ?: MapMarkerKind.Place,
                label = null,
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderMapHeight)
            .semantics { contentDescription = description }
            .testTag("placedetail_map"),
    ) {
        RaahiMap(state = mapState, markers = markers, interactive = false)
    }
}

@Composable
private fun AddressRow(state: PlaceDetailUiState) {
    val spacing = LocalSpacing.current
    val address = state.place.address?.takeIf { it.isNotBlank() }
    when {
        address != null -> InfoRow(
            icon = Icons.Outlined.LocationOn,
            text = address,
            description = stringResource(R.string.placedetail_address),
        )
        state.isResolvingAddress -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(spacing.md))
            Text(
                text = stringResource(R.string.placedetail_address_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> Text(
            text = stringResource(
                if (state.connectivity == ConnectivityState.Offline) R.string.placedetail_address_offline else R.string.placedetail_address_unknown,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    description: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$description: $text" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(spacing.md))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun SavedBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelSheet(
    state: PlaceDetailUiState,
    onToggleFavorite: () -> Unit,
    onToggleLabel: (SavedPlaceType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), modifier = Modifier.testTag("placedetail_label_sheet")) {
        Text(
            text = stringResource(R.string.placedetail_save_as),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        SheetOption(
            icon = if (state.isFavorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            text = stringResource(if (state.isFavorite) R.string.placedetail_remove_favorite else R.string.placedetail_add_favorite),
            onClick = { onToggleFavorite(); onDismiss() },
            tag = "placedetail_sheet_favorite",
        )
        SheetOption(
            icon = Icons.Outlined.Home,
            text = stringResource(if (state.isHome) R.string.placedetail_remove_home else R.string.placedetail_set_home),
            onClick = { onToggleLabel(SavedPlaceType.HOME); onDismiss() },
            tag = "placedetail_sheet_home",
        )
        SheetOption(
            icon = Icons.Outlined.Work,
            text = stringResource(if (state.isWork) R.string.placedetail_remove_work else R.string.placedetail_set_work),
            onClick = { onToggleLabel(SavedPlaceType.WORK); onDismiss() },
            tag = "placedetail_sheet_work",
        )
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripSheet(
    trips: List<Trip>,
    onPick: (Long) -> Unit,
    onNewTrip: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), modifier = Modifier.testTag("placedetail_trip_sheet")) {
        Text(
            text = stringResource(R.string.placedetail_add_to_trip),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        trips.forEach { trip ->
            SheetOption(
                icon = Icons.Outlined.AddRoad,
                text = trip.name,
                supporting = pluralStringResource(R.plurals.placedetail_trip_stops, trip.stops.size, trip.stops.size),
                onClick = { onPick(trip.id) },
                tag = "placedetail_trip_${trip.id}",
            )
        }
        SheetOption(
            icon = Icons.Outlined.AddRoad,
            text = stringResource(R.string.placedetail_new_trip),
            onClick = onNewTrip,
            tag = "placedetail_trip_new",
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    tag: String,
    supporting: String? = null,
) {
    ListItem(
        headlineContent = { Text(text) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .heightIn(min = Sizes.minTouchTarget)
            .testTag(tag)
            .semantics(mergeDescendants = true) {}
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
    )
}
