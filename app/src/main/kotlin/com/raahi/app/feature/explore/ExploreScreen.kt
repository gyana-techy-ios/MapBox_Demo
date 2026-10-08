package com.raahi.app.feature.explore

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.LocalAppContainer
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.map.MapMarker
import com.raahi.app.map.MapMarkerKind
import com.raahi.app.map.RaahiMap
import com.raahi.app.map.rememberRaahiMapState
import com.raahi.app.ui.components.CategoryChipRow
import com.raahi.app.ui.components.ConfirmDialog
import com.raahi.app.ui.components.ConnectivityBanner
import com.raahi.app.ui.components.RaahiSearchBar
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes
import com.raahi.app.ui.util.openAppSettings
import com.raahi.app.ui.util.openLocationSettings

private val exploreCategories = listOf(
    PlaceCategory.FUEL,
    PlaceCategory.EV_CHARGING,
    PlaceCategory.HOSPITAL,
    PlaceCategory.PHARMACY,
    PlaceCategory.ATM,
    PlaceCategory.RESTAURANT,
    PlaceCategory.HOTEL,
    PlaceCategory.PARKING,
    PlaceCategory.POLICE,
    PlaceCategory.TOILET,
)

private val ResultsPeekHeight = 232.dp
private val MapTopOverlay = 168.dp
private val FitPadding = 48.dp
private val FabEdgeInset = 16.dp
private val FabColumnReserve = Sizes.fab + FabEdgeInset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreRoute(
    onOpenSearch: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    onOpenEmergency: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenNearby: (PlaceCategory) -> Unit = {},
) {
    val viewModel = appViewModel {
        ExploreViewModel(
            placeSearch = it.placeSearchRepository,
            locationRepository = it.locationRepository,
            preferences = it.preferencesRepository,
            savedPlaces = it.savedPlaceRepository,
            nearbyCache = it.nearbyCacheRepository,
            connectivity = it.connectivityMonitor,
            clock = it.clock,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clock = LocalAppContainer.current.clock
    val mapState = rememberRaahiMapState()
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val snackbarHost = remember { SnackbarHostState() }
    val sheetState = rememberBottomSheetScaffoldState(snackbarHostState = snackbarHost)
    var pendingParkingPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var overflowOpen by rememberSaveable { mutableStateOf(false) }
    val parkingName = stringResource(R.string.explore_parking_default_name)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        viewModel.onPermissionResult(grants.values.any { it })
    }
    val rationaleMessage = stringResource(R.string.explore_location_permission_needed)
    val gpsOffMessage = stringResource(R.string.explore_location_disabled)
    val settingsLabel = stringResource(R.string.action_settings)
    val latestMapState by rememberUpdatedState(mapState)
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var centredOnUser by rememberSaveable { mutableStateOf(false) }
    val permissionGranted = state.locationPermission != LocationPermissionState.DENIED
    LaunchedEffect(permissionGranted) {
        if (permissionGranted && !centredOnUser) viewModel.centerOnUserOnce()
    }
    val requestLocation = {
        if (state.locationPermission == LocationPermissionState.DENIED) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        } else {
            viewModel.onMyLocationClicked()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExploreEvent.FlyToUser -> {
                    centredOnUser = true
                    latestMapState.flyTo(event.point, zoom = event.zoom)
                }
                is ExploreEvent.FitToResults -> latestMapState.fitPoints(
                    event.points,
                    paddingPx = with(density) { FitPadding.toPx().toDouble() },
                    topPx = with(density) { (statusBarTop + MapTopOverlay).toPx().toDouble() },
                    bottomPx = with(density) { (ResultsPeekHeight + FitPadding).toPx().toDouble() },
                    endPx = with(density) { (FabColumnReserve + FitPadding).toPx().toDouble() },
                )
                is ExploreEvent.ShowMessage -> snackbarHost.showSnackbar(context.getString(event.messageRes))
                ExploreEvent.ShowPermissionRationale -> {
                    val result = snackbarHost.showSnackbar(rationaleMessage, settingsLabel, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) context.openAppSettings()
                }
                ExploreEvent.ShowLocationDisabled -> {
                    val result = snackbarHost.showSnackbar(gpsOffMessage, settingsLabel, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) context.openLocationSettings()
                }
            }
        }
    }

    val hasResultsPanel = state.selectedCategory != null
    val peekHeight = if (hasResultsPanel) ResultsPeekHeight else 0.dp

    val markers = remember(state.results, state.parkingSpots, state.selectedCategory) {
        val resultMarkers = state.results.map { place ->
            MapMarker(
                id = place.id,
                point = place.point,
                kind = MapMarkerKind.Category(place.category ?: state.selectedCategory ?: PlaceCategory.FUEL),
                label = place.name,
            )
        }
        val parkingMarkers = state.parkingSpots.map { saved ->
            MapMarker(id = "saved_${saved.id}", point = saved.place.point, kind = MapMarkerKind.Parking, label = saved.place.name)
        }
        resultMarkers + parkingMarkers
    }

    BottomSheetScaffold(
        modifier = modifier.testTag("screen_explore"),
        scaffoldState = sheetState,
        sheetPeekHeight = peekHeight,
        sheetSwipeEnabled = hasResultsPanel,
        sheetContent = {
            ExploreResultsSheet(
                state = state,
                onClose = viewModel::clearResults,
                onRetry = viewModel::retry,
                onOpenPlace = onOpenPlace,
                onSeeAll = onOpenNearby,
                onEnableLocation = requestLocation,
                nowMillis = { clock.nowMillis() },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = Color.Transparent,
    ) {
        Box(Modifier.fillMaxSize()) {
            RaahiMap(
                state = mapState,
                style = state.mapStyle,
                markers = markers,
                showUserLocation = state.locationPermission != LocationPermissionState.DENIED,
                contentPadding = PaddingValues(top = statusBarTop + MapTopOverlay, bottom = peekHeight),
                onMarkerClick = { marker ->
                    state.results.firstOrNull { it.id == marker.id }?.let(onOpenPlace)
                },
                onMapLongClick = { pendingParkingPoint = it },
                onCameraIdle = { viewModel.onCameraIdle(it.center, it.zoom) },
            )

            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                ConnectivityBanner(state = state.connectivity)
                RaahiSearchBar(
                    placeholder = stringResource(R.string.explore_search_hint),
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.sm)
                        .testTag("explore_search_bar"),
                    trailing = {
                        Box(Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { overflowOpen = true }, modifier = Modifier.testTag("explore_overflow")) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                            }
                            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.explore_save_parking)) },
                                    onClick = {
                                        overflowOpen = false
                                        pendingParkingPoint = viewModel.parkingTarget(mapState.center)
                                    },
                                )
                            }
                        }
                    },
                )
                CategoryChipRow(
                    categories = exploreCategories,
                    selected = state.selectedCategory,
                    onCategoryClick = viewModel::onCategorySelected,
                    contentPadding = PaddingValues(horizontal = spacing.lg),
                )
                ExtendedFloatingActionButton(
                    onClick = onOpenEmergency,
                    modifier = Modifier
                        .padding(start = spacing.lg, top = spacing.sm)
                        .testTag("explore_emergency"),
                    containerColor = LocalRaahiColors.current.emergency,
                    contentColor = LocalRaahiColors.current.onEmergency,
                    icon = { Icon(Icons.Outlined.Emergency, contentDescription = null) },
                    text = { Text(stringResource(R.string.explore_emergency)) },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = FabEdgeInset, bottom = peekHeight + FabEdgeInset),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
                horizontalAlignment = Alignment.End,
            ) {
                FloatingActionButton(
                    onClick = viewModel::toggleMapStyle,
                    modifier = Modifier.testTag("explore_style_toggle"),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(
                        Icons.Outlined.Layers,
                        contentDescription = stringResource(
                            if (state.mapStyle == MapStyleMode.STANDARD) R.string.explore_style_to_satellite else R.string.explore_style_to_standard,
                        ),
                    )
                }
                FloatingActionButton(
                    onClick = requestLocation,
                    modifier = Modifier.testTag("explore_my_location"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = stringResource(R.string.explore_my_location))
                }
            }
        }
    }

    pendingParkingPoint?.let { point ->
        ConfirmDialog(
            title = stringResource(R.string.explore_save_parking),
            message = stringResource(R.string.explore_save_parking_message),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                viewModel.saveParking(point, parkingName)
                pendingParkingPoint = null
            },
            onDismiss = { pendingParkingPoint = null },
        )
    }
}
