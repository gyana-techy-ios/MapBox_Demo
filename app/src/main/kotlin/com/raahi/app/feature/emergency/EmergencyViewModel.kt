package com.raahi.app.feature.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.EmergencyContact
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.repository.EmergencyContactRepository
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.usecase.FindNearestEmergencyUseCase
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

val EmergencyServiceCategories = listOf(PlaceCategory.HOSPITAL, PlaceCategory.PHARMACY, PlaceCategory.POLICE)

sealed interface ServiceListState {
    data object Loading : ServiceListState
    data object NeedsLocation : ServiceListState
    data class Loaded(val places: List<Place>, val cachedAtMillis: Long?) : ServiceListState
    data class Failed(val error: AppError) : ServiceListState
}

data class EmergencyUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val permission: LocationPermissionState = LocationPermissionState.DENIED,
    val connectivity: ConnectivityState = ConnectivityState.Online,
    val selectedCategory: PlaceCategory = PlaceCategory.HOSPITAL,
    val services: Map<PlaceCategory, ServiceListState> = EmergencyServiceCategories.associateWith { ServiceListState.Loading },
    val isResolvingLocation: Boolean = false,
)

sealed interface EmergencyEvent {
    data class ShareLocation(val point: GeoPoint) : EmergencyEvent
    data object ShowLocationHelp : EmergencyEvent
}

class EmergencyViewModel(
    private val findNearest: FindNearestEmergencyUseCase,
    private val contacts: EmergencyContactRepository,
    private val locationRepository: LocationRepository,
    private val connectivity: ConnectivityMonitor,
) : ViewModel() {

    private val mutableState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<EmergencyEvent>(Channel.BUFFERED)
    val events: Flow<EmergencyEvent> = eventChannel.receiveAsFlow()

    init {
        contacts.observeAll()
            .onEach { list -> mutableState.update { it.copy(contacts = list) } }
            .launchIn(viewModelScope)
        connectivity.state
            .onEach { state -> mutableState.update { it.copy(connectivity = state) } }
            .launchIn(viewModelScope)
        locationRepository.permissionState
            .onEach { permission -> mutableState.update { it.copy(permission = permission) } }
            .launchIn(viewModelScope)
        reloadServices()
    }

    fun selectCategory(category: PlaceCategory) {
        mutableState.update { it.copy(selectedCategory = category) }
    }

    fun reloadServices() {
        mutableState.update { state ->
            state.copy(services = EmergencyServiceCategories.associateWith { ServiceListState.Loading })
        }
        viewModelScope.launch {
            val origin = knownLocation()
            if (origin == null) {
                mutableState.update { state ->
                    state.copy(services = EmergencyServiceCategories.associateWith { ServiceListState.NeedsLocation })
                }
                return@launch
            }
            EmergencyServiceCategories.forEach { category ->
                launch { loadCategory(category, origin) }
            }
        }
    }

    fun shareLocation() {
        if (mutableState.value.isResolvingLocation) return
        locationRepository.refreshPermissionState()
        mutableState.update { it.copy(isResolvingLocation = true) }
        viewModelScope.launch {
            val point = knownLocation() ?: currentLocationOrNull()
            mutableState.update { it.copy(isResolvingLocation = false) }
            if (point != null) {
                eventChannel.send(EmergencyEvent.ShareLocation(point))
            } else {
                eventChannel.send(EmergencyEvent.ShowLocationHelp)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        locationRepository.refreshPermissionState()
        if (granted) {
            reloadServices()
            shareLocation()
        } else {
            viewModelScope.launch { eventChannel.send(EmergencyEvent.ShowLocationHelp) }
        }
    }

    private suspend fun loadCategory(category: PlaceCategory, origin: GeoPoint) {
        val next = when (val result = findNearest(category, origin)) {
            is AppResult.Success -> ServiceListState.Loaded(result.value.places, result.value.cachedAtMillis)
            is AppResult.Failure -> ServiceListState.Failed(result.error)
        }
        mutableState.update { state -> state.copy(services = state.services + (category to next)) }
    }

    private suspend fun knownLocation(): GeoPoint? {
        if (locationRepository.permissionState.value == LocationPermissionState.DENIED) return null
        return (locationRepository.lastKnown() as? AppResult.Success)?.value?.point
    }

    private suspend fun currentLocationOrNull(): GeoPoint? {
        if (locationRepository.permissionState.value == LocationPermissionState.DENIED) return null
        if (!locationRepository.isLocationEnabled()) return null
        return (locationRepository.currentLocation() as? AppResult.Success)?.value?.point
    }
}
