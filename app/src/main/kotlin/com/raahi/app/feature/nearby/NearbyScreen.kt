package com.raahi.app.feature.nearby

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.LocalAppContainer
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.map.LocalMapboxConfig
import com.raahi.app.map.MapMarker
import com.raahi.app.map.MapMarkerKind
import com.raahi.app.map.RaahiMap
import com.raahi.app.map.rememberRaahiMapState
import com.raahi.app.ui.components.ConnectivityBanner
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.ErrorState
import com.raahi.app.ui.components.PlaceListItem
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.components.labelRes
import com.raahi.app.ui.format.formatCachedAge
import com.raahi.app.ui.theme.Elevation
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.util.openAppSettings

@Composable
fun NearbyRoute(
    category: String,
    onBack: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedCategory = PlaceCategory.fromName(category) ?: PlaceCategory.FUEL
    val viewModel = appViewModel(key = "nearby_${resolvedCategory.name}") {
        NearbyViewModel(
            category = resolvedCategory,
            placeSearch = it.placeSearchRepository,
            locationRepository = it.locationRepository,
            cache = it.nearbyCacheRepository,
            connectivity = it.connectivityMonitor,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionNeeded = stringResource(R.string.nearby_location_needed_body)
    val settingsLabel = stringResource(R.string.action_settings)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NearbyEvent.ShowMessage -> snackbarHost.showSnackbar(context.getString(event.messageRes))
                NearbyEvent.PermissionDenied -> {
                    val result = snackbarHost.showSnackbar(permissionNeeded, settingsLabel, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) context.openAppSettings()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        viewModel.onPermissionResult(grants.values.any { it })
    }
    val requestLocation = {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    NearbyContent(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onOpenPlace = onOpenPlace,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onViewMode = viewModel::setViewMode,
        onGrantLocation = requestLocation,
        onOpenSettings = { context.openAppSettings() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbyContent(
    state: NearbyUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onViewMode: (NearbyViewMode) -> Unit,
    onGrantLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val categoryLabel = stringResource(state.category.labelRes())
    Scaffold(
        modifier = modifier.testTag("screen_nearby"),
        topBar = { RaahiTopBar(title = stringResource(R.string.nearby_title_format, categoryLabel), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ConnectivityBanner(state = state.connectivity)
            ViewModeToggle(
                mode = state.viewMode,
                onMode = onViewMode,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            )
            state.cachedAtMillis?.let { CachedLabel(it) }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> SkeletonList(modifier = Modifier.padding(top = spacing.md).testTag("nearby_loading"), itemCount = 6)
                    state.results.isNotEmpty() -> ResultsContent(state, onOpenPlace, onRefresh)
                    state.needsLocation -> LocationNeeded(state, onGrantLocation, onOpenSettings, onRetry)
                    state.error != null -> ErrorState(
                        error = state.error,
                        onRetry = onRetry,
                        modifier = Modifier.testTag("nearby_error"),
                    )
                    else -> EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.nearby_empty_title),
                        body = stringResource(R.string.nearby_empty_body, categoryLabel),
                        modifier = Modifier.testTag("nearby_empty"),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRetry,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeToggle(mode: NearbyViewMode, onMode: (NearbyViewMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = NearbyViewMode.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, item ->
            SegmentedButton(
                selected = mode == item,
                onClick = { onMode(item) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                modifier = Modifier.testTag("nearby_mode_${item.name}"),
            ) {
                Text(stringResource(if (item == NearbyViewMode.List) R.string.nearby_mode_list else R.string.nearby_mode_map))
            }
        }
    }
}

@Composable
private fun CachedLabel(cachedAtMillis: Long) {
    val spacing = LocalSpacing.current
    val clock = LocalAppContainer.current.clock
    Row(
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs).testTag("nearby_cached_label"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, tint = LocalRaahiColors.current.offline)
        Text(
            text = stringResource(R.string.nearby_cached_results, formatCachedAge(cachedAtMillis, clock.nowMillis())),
            modifier = Modifier.padding(start = spacing.sm),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalRaahiColors.current.offline,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsContent(state: NearbyUiState, onOpenPlace: (Place) -> Unit, onRefresh: () -> Unit) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.viewMode == NearbyViewMode.List) {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("nearby_list"), contentPadding = PaddingValues(bottom = LocalSpacing.current.xl)) {
                items(state.results, key = { it.id }) { place ->
                    PlaceListItem(place = place, onClick = { onOpenPlace(place) })
                }
            }
        } else {
            NearbyMap(state = state, onOpenPlace = onOpenPlace)
        }
    }
}

@Composable
private fun NearbyMap(state: NearbyUiState, onOpenPlace: (Place) -> Unit) {
    val mapState = rememberRaahiMapState()
    val mapAvailable = LocalMapboxConfig.current.isMapAvailable
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val markers = remember(state.results, selectedId) {
        state.results.map { place ->
            MapMarker(
                id = place.id,
                point = place.point,
                kind = MapMarkerKind.Category(place.category ?: state.category),
                label = place.name,
                selected = place.id == selectedId,
            )
        }
    }
    LaunchedEffect(state.results, mapAvailable) {
        if (mapAvailable && state.results.isNotEmpty()) {
            mapState.fitPoints(state.results.map { it.point } + listOfNotNull(state.origin))
        }
    }
    val selected = state.results.firstOrNull { it.id == selectedId }
    Box(Modifier.fillMaxSize().testTag("nearby_map")) {
        RaahiMap(
            state = mapState,
            markers = markers,
            showUserLocation = state.permission != LocationPermissionState.DENIED,
            onMarkerClick = { selectedId = it.id },
            onMapClick = { selectedId = null },
        )
        if (selected != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(LocalSpacing.current.lg),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = Elevation.medium,
            ) {
                PlaceListItem(place = selected, onClick = { onOpenPlace(selected) })
            }
        }
    }
}

@Composable
private fun LocationNeeded(
    state: NearbyUiState,
    onGrantLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val denied = state.permission == LocationPermissionState.DENIED
    EmptyState(
        icon = Icons.Outlined.LocationOff,
        title = stringResource(R.string.nearby_location_needed_title),
        body = stringResource(if (denied) R.string.nearby_location_needed_body else R.string.nearby_location_unavailable_body),
        modifier = Modifier.testTag("nearby_location_needed"),
        actionLabel = stringResource(if (denied) R.string.nearby_grant_location else R.string.action_retry),
        onAction = if (denied) onGrantLocation else onRetry,
    )
}
