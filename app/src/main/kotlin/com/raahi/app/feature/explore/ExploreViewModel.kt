package com.raahi.app.feature.explore

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.R
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.result.toMessageRes
import com.raahi.app.core.time.Clock
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.PlaceSource
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.SavedPlaceType
import com.raahi.app.domain.model.UserLocation
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.repository.NearbyCacheRepository
import com.raahi.app.domain.repository.PlaceSearchRepository
import com.raahi.app.domain.repository.PreferencesRepository
import com.raahi.app.domain.repository.SavedPlaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val selectedCategory: PlaceCategory? = null,
    val results: List<Place> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val cachedAtMillis: Long? = null,
    val mapStyle: MapStyleMode = MapStyleMode.STANDARD,
    val userLocation: UserLocation? = null,
    val locationPermission: LocationPermissionState = LocationPermissionState.DENIED,
    val connectivity: ConnectivityState = ConnectivityState.Online,
    val parkingSpots: List<SavedPlace> = emptyList(),
    val needsLocation: Boolean = false,
)

sealed interface ExploreEvent {
    data class FlyToUser(val point: GeoPoint, val zoom: Double = ExploreViewModel.MY_LOCATION_ZOOM) : ExploreEvent
    data class FitToResults(val points: List<GeoPoint>) : ExploreEvent
    data class ShowMessage(@StringRes val messageRes: Int) : ExploreEvent
    data object ShowPermissionRationale : ExploreEvent
    data object ShowLocationDisabled : ExploreEvent
}

class ExploreViewModel(
    private val placeSearch: PlaceSearchRepository,
    private val locationRepository: LocationRepository,
    private val preferences: PreferencesRepository,
    private val savedPlaces: SavedPlaceRepository,
    private val nearbyCache: NearbyCacheRepository,
    private val connectivity: ConnectivityMonitor,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<ExploreEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var lastCameraCenter: GeoPoint? = null
    private var lastCameraZoom: Double? = null
    private var initialCentreDone = false
    private var searchJob: Job? = null

    init {
        preferences.preferences.map { it.mapStyle }
            .onEach { style -> mutableState.update { it.copy(mapStyle = style) } }
            .launchIn(viewModelScope)
        connectivity.state
            .onEach { state -> mutableState.update { it.copy(connectivity = state) } }
            .launchIn(viewModelScope)
        locationRepository.permissionState
            .onEach { permission -> mutableState.update { it.copy(locationPermission = permission) } }
            .launchIn(viewModelScope)
        savedPlaces.observeAll()
            .map { list -> list.filter { it.type == SavedPlaceType.PARKING } }
            .onEach { parking -> mutableState.update { it.copy(parkingSpots = parking) } }
            .launchIn(viewModelScope)
        viewModelScope.launch { refreshLastKnownLocation() }
    }

    fun onCameraIdle(center: GeoPoint, zoom: Double) {
        lastCameraCenter = center
        lastCameraZoom = zoom
    }

    fun centerOnUserOnce() {
        if (initialCentreDone || locationRepository.permissionState.value == LocationPermissionState.DENIED) return
        initialCentreDone = true
        viewModelScope.launch {
            val location = (locationRepository.lastKnown() as? AppResult.Success)?.value
                ?: (locationRepository.currentLocation() as? AppResult.Success)?.value
            if (location == null) {
                initialCentreDone = false
                return@launch
            }
            onUserLocationFound(location)
            eventChannel.send(ExploreEvent.FlyToUser(location.point, INITIAL_ZOOM))
        }
    }

    fun onCategorySelected(category: PlaceCategory) {
        if (mutableState.value.selectedCategory == category) {
            clearResults()
            return
        }
        searchJob?.cancel()
        mutableState.update {
            it.copy(
                selectedCategory = category, results = emptyList(), isLoading = true, error = null,
                cachedAtMillis = null, needsLocation = false,
            )
        }
        searchJob = viewModelScope.launch { loadCategory(category) }
    }

    fun retry() {
        mutableState.value.selectedCategory?.let { category ->
            searchJob?.cancel()
            mutableState.update { it.copy(isLoading = true, error = null) }
            searchJob = viewModelScope.launch { loadCategory(category) }
        }
    }

    fun clearResults() {
        searchJob?.cancel()
        mutableState.update {
            it.copy(
                selectedCategory = null, results = emptyList(), isLoading = false, error = null,
                cachedAtMillis = null, needsLocation = false,
            )
        }
    }

    fun onMyLocationClicked() {
        locationRepository.refreshPermissionState()
        viewModelScope.launch {
            if (locationRepository.permissionState.value == LocationPermissionState.DENIED) {
                eventChannel.send(ExploreEvent.ShowPermissionRationale)
                return@launch
            }
            if (!locationRepository.isLocationEnabled()) {
                eventChannel.send(ExploreEvent.ShowLocationDisabled)
                return@launch
            }
            when (val result = locationRepository.currentLocation()) {
                is AppResult.Success -> {
                    onUserLocationFound(result.value)
                    eventChannel.send(ExploreEvent.FlyToUser(result.value.point))
                }
                is AppResult.Failure -> eventChannel.send(ExploreEvent.ShowMessage(result.error.toMessageRes()))
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        locationRepository.refreshPermissionState()
        if (granted) {
            onMyLocationClicked()
        } else {
            viewModelScope.launch { eventChannel.send(ExploreEvent.ShowPermissionRationale) }
        }
    }

    fun toggleMapStyle() {
        val next = if (mutableState.value.mapStyle == MapStyleMode.STANDARD) MapStyleMode.SATELLITE else MapStyleMode.STANDARD
        viewModelScope.launch { preferences.setMapStyle(next) }
    }

    fun saveParking(point: GeoPoint, name: String) {
        viewModelScope.launch {
            val now = clock.nowMillis()
            val place = Place(
                id = "parking_$now",
                name = name,
                address = null,
                point = point,
                category = PlaceCategory.PARKING,
                distanceMeters = null,
                source = PlaceSource.MAP,
            )
            savedPlaces.save(SavedPlace(id = 0, place = place, type = SavedPlaceType.PARKING, note = null, savedAt = now))
            eventChannel.send(ExploreEvent.ShowMessage(R.string.explore_parking_saved))
        }
    }

    fun parkingTarget(fallback: GeoPoint): GeoPoint = mutableState.value.userLocation?.point ?: fallback

    private suspend fun refreshLastKnownLocation() {
        if (locationRepository.permissionState.value == LocationPermissionState.DENIED) return
        val result = locationRepository.lastKnown()
        if (result is AppResult.Success) {
            onUserLocationFound(result.value)
        }
    }

    private fun onUserLocationFound(location: UserLocation) {
        mutableState.update { it.copy(userLocation = location) }
        if (mutableState.value.needsLocation) retry()
    }

    private suspend fun loadCategory(category: PlaceCategory) {
        val userPoint = mutableState.value.userLocation?.point
        val origin = selectSearchOrigin(lastCameraCenter, lastCameraZoom, userPoint)
        if (origin == null) {
            mutableState.update { it.copy(results = emptyList(), isLoading = false, error = null, needsLocation = true) }
            return
        }
        mutableState.update { it.copy(needsLocation = false) }
        val cacheKey = "explore_${category.name}"
        when (val result = placeSearch.categorySearch(category, origin)) {
            is AppResult.Success -> {
                val sorted = result.value.withDistances(userPoint, origin)
                if (sorted.isNotEmpty()) nearbyCache.put(cacheKey, sorted)
                mutableState.update { it.copy(results = sorted, isLoading = false, error = null, cachedAtMillis = null) }
                if (sorted.isNotEmpty()) eventChannel.send(ExploreEvent.FitToResults(sorted.map { it.point } + origin))
            }
            is AppResult.Failure -> {
                val cached = nearbyCache.get(cacheKey)
                if (cached != null && cached.places.isNotEmpty()) {
                    val sorted = cached.places.withDistances(userPoint, origin)
                    mutableState.update {
                        it.copy(results = sorted, isLoading = false, error = null, cachedAtMillis = cached.cachedAtMillis)
                    }
                } else {
                    mutableState.update { it.copy(results = emptyList(), isLoading = false, error = result.error) }
                }
            }
        }
    }

    companion object {
        const val MY_LOCATION_ZOOM = 15.0
        const val INITIAL_ZOOM = 13.0
        const val MIN_AREA_ZOOM = 10.0
    }
}

/** Camera centre when exploring an area, else the user, else null (caller must ask for location). */
internal fun selectSearchOrigin(cameraCenter: GeoPoint?, cameraZoom: Double?, user: GeoPoint?): GeoPoint? =
    if (cameraCenter != null && cameraZoom != null && cameraZoom >= ExploreViewModel.MIN_AREA_ZOOM) cameraCenter else user

/** Distance from the user when known, else the API distance, else from the search origin; nearest first. */
private fun List<Place>.withDistances(user: GeoPoint?, origin: GeoPoint): List<Place> =
    map { it.copy(distanceMeters = user?.distanceTo(it.point) ?: it.distanceMeters ?: origin.distanceTo(it.point)) }
        .sortedBy { it.distanceMeters }
