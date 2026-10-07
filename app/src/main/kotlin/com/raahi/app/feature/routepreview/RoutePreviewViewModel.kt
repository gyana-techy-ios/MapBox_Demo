package com.raahi.app.feature.routepreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.time.Clock
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.repository.TripRepository
import com.raahi.app.domain.usecase.ActiveRouteStore
import com.raahi.app.domain.usecase.PlanRoutesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PreviewPhase { Loading, Ready, NoRoute, NeedsLocationPermission, Offline, Failed, TripMissing }

data class RoutePreviewUiState(
    val phase: PreviewPhase = PreviewPhase.Loading,
    val trip: Trip? = null,
    val routes: List<Route> = emptyList(),
    val selectedIndex: Int = 0,
    val error: AppError? = null,
    val connectivity: ConnectivityState = ConnectivityState.Online,
) {
    val fastestIndex: Int? get() = routes.indices.minByOrNull { routes[it].durationSeconds }
    val selectedRoute: Route? get() = routes.getOrNull(selectedIndex)
}

class RoutePreviewViewModel(
    private val tripId: Long,
    private val trips: TripRepository,
    private val planRoutes: PlanRoutesUseCase,
    private val locationRepository: LocationRepository,
    private val connectivity: ConnectivityMonitor,
    private val routeStore: ActiveRouteStore,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(RoutePreviewUiState())
    val uiState: StateFlow<RoutePreviewUiState> = mutableState.asStateFlow()

    private var planJob: Job? = null
    private var lastRequestAt: Long? = null

    init {
        connectivity.state
            .onEach { state -> mutableState.update { it.copy(connectivity = state) } }
            .map { it != ConnectivityState.Offline }
            .distinctUntilChanged()
            .onEach { online -> if (online && mutableState.value.phase == PreviewPhase.Offline) requestRoutes(force = true) }
            .launchIn(viewModelScope)
        requestRoutes(force = true)
    }

    fun retry() = requestRoutes(force = false)

    fun onPermissionResult() {
        locationRepository.refreshPermissionState()
        requestRoutes(force = true)
    }

    fun selectRoute(index: Int) {
        mutableState.update { state -> if (index in state.routes.indices) state.copy(selectedIndex = index) else state }
    }

    private fun requestRoutes(force: Boolean) {
        if (planJob?.isActive == true) return
        val now = clock.nowMillis()
        val previous = lastRequestAt
        if (!force && previous != null && now - previous < RETRY_DEBOUNCE_MILLIS) return
        lastRequestAt = now
        planJob = viewModelScope.launch { plan() }
    }

    private suspend fun plan() {
        mutableState.update { it.copy(phase = PreviewPhase.Loading, error = null) }
        val trip = trips.getTrip(tripId)
        if (trip == null) {
            mutableState.update { it.copy(phase = PreviewPhase.TripMissing, trip = null) }
            return
        }
        mutableState.update { it.copy(trip = trip) }
        if (trip.stops.any { it.useCurrentLocation } &&
            locationRepository.permissionState.value == LocationPermissionState.DENIED
        ) {
            mutableState.update { it.copy(phase = PreviewPhase.NeedsLocationPermission) }
            return
        }
        if (connectivity.state.value == ConnectivityState.Offline) {
            mutableState.update { it.copy(phase = PreviewPhase.Offline, error = AppError.NoInternet) }
            return
        }
        when (val result = planRoutes(trip)) {
            is AppResult.Success -> onRoutes(result.value)
            is AppResult.Failure -> mutableState.update { it.copy(phase = phaseFor(result.error), error = result.error) }
        }
    }

    private fun onRoutes(routes: List<Route>) {
        if (routes.isEmpty()) {
            mutableState.update { it.copy(phase = PreviewPhase.NoRoute, routes = emptyList(), error = AppError.NoRoute) }
            return
        }
        routeStore.put(tripId, routes)
        mutableState.update { it.copy(phase = PreviewPhase.Ready, routes = routes, selectedIndex = 0, error = null) }
    }

    private fun phaseFor(error: AppError): PreviewPhase = when (error) {
        AppError.NoRoute -> PreviewPhase.NoRoute
        AppError.NoInternet -> PreviewPhase.Offline
        AppError.PermissionDenied -> PreviewPhase.NeedsLocationPermission
        else -> PreviewPhase.Failed
    }

    companion object {
        const val RETRY_DEBOUNCE_MILLIS = 1_000L
    }
}
