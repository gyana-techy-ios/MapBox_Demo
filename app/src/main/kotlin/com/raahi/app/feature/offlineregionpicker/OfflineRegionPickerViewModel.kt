package com.raahi.app.feature.offlineregionpicker

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.R
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.OfflineEstimate
import com.raahi.app.domain.model.OfflineRegion
import com.raahi.app.domain.model.OfflineRegionStatus
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.repository.OfflineMapRepository
import com.raahi.app.domain.repository.PlaceSearchRepository
import com.raahi.app.domain.usecase.TileEstimator
import com.raahi.app.feature.offline.MeteredDownloadGuard
import com.raahi.app.map.MapCameraInfo
import com.raahi.app.offline.DownloadCoordinator
import com.raahi.app.offline.StorageProbe
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DetailPreset(val minZoom: Int, val maxZoom: Int, @StringRes val labelRes: Int) {
    Roads(10, 14, R.string.offlineregionpicker_preset_roads),
    Street(10, 16, R.string.offlineregionpicker_preset_street),
}

enum class PickerIssue(@StringRes val messageRes: Int) {
    TooLarge(R.string.offlineregionpicker_too_large),
    StorageLow(R.string.offlineregionpicker_storage_low),
    Offline(R.string.offlineregionpicker_offline),
}

object RegionFrame {
    const val HORIZONTAL_INSET = 0.10f
    const val VERTICAL_INSET = 0.16f

    fun shrink(visible: GeoBounds): GeoBounds {
        val latSpan = visible.north - visible.south
        val lngSpan = visible.east - visible.west
        return GeoBounds(
            south = visible.south + latSpan * VERTICAL_INSET,
            west = visible.west + lngSpan * HORIZONTAL_INSET,
            north = visible.north - latSpan * VERTICAL_INSET,
            east = visible.east - lngSpan * HORIZONTAL_INSET,
        )
    }
}

data class RegionPickerUiState(
    val name: String = "",
    val preset: DetailPreset = DetailPreset.Roads,
    val bounds: GeoBounds? = null,
    val estimate: OfflineEstimate? = null,
    val issue: PickerIssue? = null,
    val isStarting: Boolean = false,
    val connectivity: ConnectivityState = ConnectivityState.Online,
    val initialCenter: GeoPoint? = null,
    val hasLocationPermission: Boolean = false,
) {
    val canDownload: Boolean get() = bounds != null && estimate != null && issue == null && !isStarting
}

sealed interface RegionPickerEvent {
    data object DownloadStarted : RegionPickerEvent
    data object ConfirmMetered : RegionPickerEvent
}

@OptIn(FlowPreview::class)
class OfflineRegionPickerViewModel(
    private val offlineMaps: OfflineMapRepository,
    private val coordinator: DownloadCoordinator,
    private val placeSearch: PlaceSearchRepository,
    private val locationRepository: LocationRepository,
    private val connectivity: ConnectivityMonitor,
    private val storage: StorageProbe,
    private val meteredGuard: MeteredDownloadGuard,
    private val defaultName: (Int) -> String,
    private val newRegionId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val mutableState = MutableStateFlow(RegionPickerUiState())
    val uiState: StateFlow<RegionPickerUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<RegionPickerEvent>(Channel.BUFFERED)
    val events: Flow<RegionPickerEvent> = eventChannel.receiveAsFlow()

    private val cameraCenters = MutableStateFlow<GeoPoint?>(null)
    private var nameEdited = false
    private var nameResolved = false
    private var fallbackName = defaultName(1)

    init {
        mutableState.update { it.copy(name = fallbackName) }
        viewModelScope.launch {
            fallbackName = defaultName(offlineMaps.regions.first().size + 1)
            if (!nameEdited && !nameResolved) mutableState.update { it.copy(name = fallbackName) }
        }
        viewModelScope.launch { resolveStartLocation() }
        connectivity.state
            .onEach { network -> mutableState.update { recompute(it.copy(connectivity = network)) } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            cameraCenters.debounce(GEOCODE_DEBOUNCE_MILLIS).collectLatest { center -> if (center != null) resolveName(center) }
        }
    }

    fun onCameraIdle(info: MapCameraInfo) {
        val selected = RegionFrame.shrink(info.bounds)
        mutableState.update { recompute(it.copy(bounds = selected)) }
        cameraCenters.value = info.center
    }

    fun onNameChange(name: String) {
        nameEdited = true
        mutableState.update { it.copy(name = name) }
    }

    fun onPresetChange(preset: DetailPreset) {
        mutableState.update { recompute(it.copy(preset = preset)) }
    }

    fun download(allowMetered: Boolean = false) {
        val state = mutableState.value
        val bounds = state.bounds
        if (!state.canDownload || bounds == null) return
        mutableState.update { it.copy(isStarting = true) }
        viewModelScope.launch {
            if (!allowMetered && meteredGuard.requiresConfirmation()) {
                mutableState.update { it.copy(isStarting = false) }
                eventChannel.send(RegionPickerEvent.ConfirmMetered)
                return@launch
            }
            val region = OfflineRegion(
                id = newRegionId(),
                name = state.name.trim().ifEmpty { fallbackName },
                bounds = bounds,
                minZoom = state.preset.minZoom,
                maxZoom = state.preset.maxZoom,
                status = OfflineRegionStatus.Downloading,
                progress = 0f,
                sizeBytes = 0L,
                downloadedAt = null,
                isOutdated = false,
            )
            coordinator.start(region, allowMetered)
            mutableState.update { it.copy(isStarting = false) }
            eventChannel.send(RegionPickerEvent.DownloadStarted)
        }
    }

    private fun recompute(state: RegionPickerUiState): RegionPickerUiState {
        val bounds = state.bounds ?: return state.copy(estimate = null, issue = null)
        val estimate = TileEstimator.estimate(bounds, state.preset.minZoom, state.preset.maxZoom)
        val issue = when {
            estimate.tileCount > MAX_TILES -> PickerIssue.TooLarge
            estimate.approximateBytes * STORAGE_SAFETY_FACTOR > storage.availableBytes() -> PickerIssue.StorageLow
            state.connectivity == ConnectivityState.Offline -> PickerIssue.Offline
            else -> null
        }
        return state.copy(estimate = estimate, issue = issue)
    }

    private suspend fun resolveStartLocation() {
        if (locationRepository.permissionState.value == LocationPermissionState.DENIED) return
        val location = (locationRepository.lastKnown() as? AppResult.Success)?.value ?: return
        mutableState.update { it.copy(initialCenter = location.point, hasLocationPermission = true) }
    }

    private suspend fun resolveName(center: GeoPoint) {
        if (nameEdited || connectivity.state.value == ConnectivityState.Offline) return
        val place = (placeSearch.reverseGeocode(center) as? AppResult.Success)?.value ?: return
        val resolved = place.name.takeIf { it.isNotBlank() } ?: return
        if (nameEdited) return
        nameResolved = true
        mutableState.update { it.copy(name = resolved) }
    }

    companion object {
        const val MAX_TILES = 6_000L
        const val GEOCODE_DEBOUNCE_MILLIS = 800L
        private const val STORAGE_SAFETY_FACTOR = 1.25
    }
}
