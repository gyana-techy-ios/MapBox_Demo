package com.raahi.app.feature.placedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.R
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.time.Clock
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.SavedPlaceType
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.model.TripStop
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.repository.PlaceSearchRepository
import com.raahi.app.domain.repository.PreferencesRepository
import com.raahi.app.domain.repository.SavedPlaceRepository
import com.raahi.app.domain.repository.TripRepository
import com.raahi.app.ui.util.UiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaceDetailUiState(
    val place: Place,
    val isResolvingAddress: Boolean = false,
    val savedEntries: List<SavedPlace> = emptyList(),
    val userDistanceMeters: Double? = null,
    val tripsForAdding: List<Trip> = emptyList(),
    val connectivity: ConnectivityState = ConnectivityState.Online,
    val isCreatingTrip: Boolean = false,
) {
    val isFavorite: Boolean get() = savedEntries.any { it.type == SavedPlaceType.FAVORITE }
    val isHome: Boolean get() = savedEntries.any { it.type == SavedPlaceType.HOME }
    val isWork: Boolean get() = savedEntries.any { it.type == SavedPlaceType.WORK }
    val distanceMeters: Double? get() = userDistanceMeters ?: place.distanceMeters
}

sealed interface PlaceDetailEvent {
    data class ShowMessage(val message: UiMessage) : PlaceDetailEvent
    data class OpenRoutePreview(val tripId: Long) : PlaceDetailEvent
}

class PlaceDetailViewModel(
    initialPlace: Place,
    private val savedPlaces: SavedPlaceRepository,
    private val trips: TripRepository,
    private val preferences: PreferencesRepository,
    private val placeSearch: PlaceSearchRepository,
    private val locationRepository: LocationRepository,
    private val connectivity: ConnectivityMonitor,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(PlaceDetailUiState(place = initialPlace))
    val uiState: StateFlow<PlaceDetailUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<PlaceDetailEvent>(Channel.BUFFERED)
    val events: Flow<PlaceDetailEvent> = eventChannel.receiveAsFlow()

    private var addressLookupDone = false

    init {
        savedPlaces.observeAll()
            .map { all -> all.filter { it.type in labelledTypes && it.matches(mutableState.value.place) } }
            .onEach { entries -> mutableState.update { it.copy(savedEntries = entries) } }
            .launchIn(viewModelScope)
        trips.observeTrips()
            .map { all -> all.filterNot { it.isQuick } }
            .onEach { list -> mutableState.update { it.copy(tripsForAdding = list) } }
            .launchIn(viewModelScope)
        connectivity.state
            .onEach { state -> mutableState.update { it.copy(connectivity = state) } }
            .map { it != ConnectivityState.Offline }
            .distinctUntilChanged()
            .onEach { online -> if (online) resolveMissingAddress() }
            .launchIn(viewModelScope)
        viewModelScope.launch { resolveUserDistance() }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = mutableState.value
            if (current.isFavorite) {
                current.savedEntries.filter { it.type == SavedPlaceType.FAVORITE }.forEach { savedPlaces.delete(it.id) }
                eventChannel.send(PlaceDetailEvent.ShowMessage(UiMessage(R.string.placedetail_removed_favorite)))
            } else {
                savedPlaces.save(newSavedPlace(SavedPlaceType.FAVORITE))
                eventChannel.send(PlaceDetailEvent.ShowMessage(UiMessage(R.string.placedetail_saved_favorite)))
            }
        }
    }

    fun toggleLabel(type: SavedPlaceType) {
        require(type == SavedPlaceType.HOME || type == SavedPlaceType.WORK)
        viewModelScope.launch {
            val current = mutableState.value
            val own = current.savedEntries.filter { it.type == type }
            val isHome = type == SavedPlaceType.HOME
            if (own.isNotEmpty()) {
                own.forEach { savedPlaces.delete(it.id) }
                eventChannel.send(PlaceDetailEvent.ShowMessage(UiMessage(if (isHome) R.string.placedetail_home_removed else R.string.placedetail_work_removed)))
                return@launch
            }
            savedPlaces.observeAll().first().filter { it.type == type }.forEach { savedPlaces.delete(it.id) }
            savedPlaces.save(newSavedPlace(type))
            eventChannel.send(PlaceDetailEvent.ShowMessage(UiMessage(if (isHome) R.string.placedetail_home_set else R.string.placedetail_work_set)))
        }
    }

    fun startDirections() {
        if (mutableState.value.isCreatingTrip) return
        mutableState.update { it.copy(isCreatingTrip = true) }
        viewModelScope.launch {
            val place = mutableState.value.place
            val now = clock.nowMillis()
            val profile = preferences.preferences.first().defaultProfile
            val trip = Trip(
                id = Trip.NEW_ID,
                name = place.name,
                stops = listOf(currentLocationStop(order = 0), placeStop(place, order = 1)),
                profile = profile,
                createdAt = now,
                updatedAt = now,
                isQuick = true,
            )
            val tripId = trips.upsert(trip)
            mutableState.update { it.copy(isCreatingTrip = false) }
            eventChannel.send(PlaceDetailEvent.OpenRoutePreview(tripId))
        }
    }

    fun addToTrip(tripId: Long) {
        viewModelScope.launch {
            val trip = trips.getTrip(tripId) ?: return@launch
            if (trip.stops.size >= Trip.MAX_STOPS) {
                eventChannel.send(PlaceDetailEvent.ShowMessage(UiMessage(R.string.placedetail_trip_full)))
                return@launch
            }
            val stop = placeStop(mutableState.value.place, order = trip.stops.size)
            trips.upsert(trip.copy(stops = trip.stops + stop))
            eventChannel.send(PlaceDetailEvent.ShowMessage(UiMessage(R.string.placedetail_added_to_trip, listOf(trip.name))))
        }
    }

    fun addToNewTrip(tripName: String) {
        viewModelScope.launch {
            val place = mutableState.value.place
            val now = clock.nowMillis()
            val profile = preferences.preferences.first().defaultProfile
            val trip = Trip(
                id = Trip.NEW_ID,
                name = tripName,
                stops = listOf(currentLocationStop(order = 0), placeStop(place, order = 1)),
                profile = profile,
                createdAt = now,
                updatedAt = now,
                isQuick = false,
            )
            trips.upsert(trip)
            eventChannel.send(PlaceDetailEvent.ShowMessage(UiMessage(R.string.placedetail_added_to_trip, listOf(tripName))))
        }
    }

    private suspend fun resolveMissingAddress() {
        val place = mutableState.value.place
        val needsAddress = place.address.isNullOrBlank() || place.name.isBlank()
        if (addressLookupDone || !needsAddress) return
        addressLookupDone = true
        mutableState.update { it.copy(isResolvingAddress = true) }
        when (val result = placeSearch.reverseGeocode(place.point)) {
            is AppResult.Success -> mutableState.update {
                it.copy(
                    isResolvingAddress = false,
                    place = it.place.copy(
                        name = it.place.name.ifBlank { result.value.name },
                        address = it.place.address?.takeIf { address -> address.isNotBlank() } ?: result.value.address,
                    ),
                )
            }
            is AppResult.Failure -> {
                addressLookupDone = false
                mutableState.update { it.copy(isResolvingAddress = false) }
            }
        }
    }

    private suspend fun resolveUserDistance() {
        if (locationRepository.permissionState.value == LocationPermissionState.DENIED) return
        val location = (locationRepository.lastKnown() as? AppResult.Success)?.value ?: return
        val distance = location.point.distanceTo(mutableState.value.place.point)
        mutableState.update { it.copy(userDistanceMeters = distance) }
    }

    private fun newSavedPlace(type: SavedPlaceType) = SavedPlace(
        id = 0,
        place = mutableState.value.place,
        type = type,
        note = null,
        savedAt = clock.nowMillis(),
    )

    private fun currentLocationStop(order: Int) = TripStop(id = 0, place = null, useCurrentLocation = true, order = order)

    private fun placeStop(place: Place, order: Int) = TripStop(id = 0, place = place, useCurrentLocation = false, order = order)

    private fun SavedPlace.matches(other: Place): Boolean =
        (place.id.isNotBlank() && place.id == other.id) || place.point.distanceTo(other.point) < SAME_PLACE_METERS

    private companion object {
        val labelledTypes = setOf(SavedPlaceType.FAVORITE, SavedPlaceType.HOME, SavedPlaceType.WORK)
        const val SAME_PLACE_METERS = 15.0
    }
}
