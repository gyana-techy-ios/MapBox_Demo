package com.raahi.app.feature.nearby

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.result.toMessageRes
import com.raahi.app.domain.model.CachedPlaces
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.repository.NearbyCacheRepository
import com.raahi.app.domain.repository.PlaceSearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NearbyViewMode { List, Map }

data class NearbyUiState(
    val category: PlaceCategory,
    val results: List<Place> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val needsLocation: Boolean = false,
    val permission: LocationPermissionState = LocationPermissionState.DENIED,
    val origin: GeoPoint? = null,
    val cachedAtMillis: Long? = null,
    val viewMode: NearbyViewMode = NearbyViewMode.List,
    val connectivity: ConnectivityState = ConnectivityState.Online,
)

sealed interface NearbyEvent {
    data class ShowMessage(@StringRes val messageRes: Int) : NearbyEvent
    data object PermissionDenied : NearbyEvent
}

class NearbyViewModel(
    private val category: PlaceCategory,
    private val placeSearch: PlaceSearchRepository,
    private val locationRepository: LocationRepository,
    private val cache: NearbyCacheRepository,
    private val connectivity: ConnectivityMonitor,
) : ViewModel() {

    private val mutableState = MutableStateFlow(NearbyUiState(category = category))
    val uiState: StateFlow<NearbyUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<NearbyEvent>(Channel.BUFFERED)
    val events: Flow<NearbyEvent> = eventChannel.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        connectivity.state
            .onEach { state -> mutableState.update { it.copy(connectivity = state) } }
            .launchIn(viewModelScope)
        locationRepository.permissionState
            .onEach { permission -> mutableState.update { it.copy(permission = permission) } }
            .launchIn(viewModelScope)
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    fun retry() = load(isRefresh = false)

    fun onPermissionResult(granted: Boolean) {
        locationRepository.refreshPermissionState()
        if (granted) {
            load(isRefresh = false)
        } else {
            viewModelScope.launch { eventChannel.send(NearbyEvent.PermissionDenied) }
        }
    }

    fun setViewMode(mode: NearbyViewMode) {
        mutableState.update { it.copy(viewMode = mode) }
    }

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        mutableState.update {
            it.copy(isLoading = !isRefresh && it.results.isEmpty(), isRefreshing = isRefresh, error = null, needsLocation = false)
        }
        loadJob = viewModelScope.launch {
            val origin = resolveOrigin()
            if (origin == null) {
                showCachedOrNeedLocation()
            } else {
                loadFrom(origin)
            }
        }
    }

    private suspend fun resolveOrigin(): GeoPoint? {
        if (locationRepository.permissionState.value == LocationPermissionState.DENIED) return null
        val known = locationRepository.lastKnown()
        if (known is AppResult.Success) return known.value.point
        if (connectivity.state.value == ConnectivityState.Offline || !locationRepository.isLocationEnabled()) return null
        return (locationRepository.currentLocation() as? AppResult.Success)?.value?.point
    }

    private suspend fun loadFrom(origin: GeoPoint) {
        mutableState.update { it.copy(origin = origin) }
        if (connectivity.state.value == ConnectivityState.Offline) {
            showCachedOr(origin, AppError.NoInternet)
            return
        }
        when (val result = placeSearch.categorySearch(category, origin)) {
            is AppResult.Success -> {
                val sorted = result.value.sortedBy { it.distanceMeters ?: it.point.distanceTo(origin) }
                if (sorted.isNotEmpty()) cache.put(cacheKey(category), sorted)
                mutableState.update {
                    it.copy(results = sorted, isLoading = false, isRefreshing = false, cachedAtMillis = null, error = null)
                }
            }
            is AppResult.Failure -> {
                showCachedOr(origin, result.error)
                if (mutableState.value.isRefreshing) eventChannel.send(NearbyEvent.ShowMessage(result.error.toMessageRes()))
            }
        }
    }

    private suspend fun showCachedOr(origin: GeoPoint?, error: AppError) {
        val cached = readCache()
        if (cached != null) {
            val sorted = if (origin != null) cached.places.sortedBy { it.point.distanceTo(origin) } else cached.places
            val withDistance = sorted.map { place ->
                if (origin != null) place.copy(distanceMeters = place.point.distanceTo(origin)) else place.copy(distanceMeters = null)
            }
            mutableState.update {
                it.copy(
                    results = withDistance,
                    isLoading = false,
                    isRefreshing = false,
                    cachedAtMillis = cached.cachedAtMillis,
                    error = null,
                )
            }
        } else {
            mutableState.update {
                it.copy(results = emptyList(), isLoading = false, isRefreshing = false, cachedAtMillis = null, error = error)
            }
        }
    }

    private suspend fun showCachedOrNeedLocation() {
        val cached = readCache()
        if (cached != null) {
            showCachedOr(origin = null, error = AppError.LocationUnavailable)
        } else {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    needsLocation = true,
                    error = if (it.permission == LocationPermissionState.DENIED) null else AppError.LocationUnavailable,
                )
            }
        }
    }

    private suspend fun readCache(): CachedPlaces? =
        cache.get(cacheKey(category))?.takeIf { it.places.isNotEmpty() }
            ?: cache.get(EXPLORE_KEY_PREFIX + category.name)?.takeIf { it.places.isNotEmpty() }

    companion object {
        fun cacheKey(category: PlaceCategory): String = "nearby_${category.name}"
        private const val EXPLORE_KEY_PREFIX = "explore_"
    }
}
