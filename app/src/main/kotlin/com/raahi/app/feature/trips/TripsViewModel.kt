package com.raahi.app.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.core.result.AppError
import com.raahi.app.core.time.Clock
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.repository.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripsUiState(
    val isLoading: Boolean = true,
    val trips: List<Trip> = emptyList(),
    val recent: List<Trip> = emptyList(),
    val error: AppError? = null,
    val nowMillis: Long = 0L,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && trips.isEmpty() && recent.isEmpty()
}

sealed interface TripsEvent {
    data class TripDeleted(val trip: Trip) : TripsEvent
    data object RecentCleared : TripsEvent
}

private sealed interface TripsLoad {
    data class Loaded(val trips: List<Trip>) : TripsLoad
    data object Failed : TripsLoad
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModel(
    private val trips: TripRepository,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(TripsUiState())
    val uiState: StateFlow<TripsUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<TripsEvent>(Channel.BUFFERED)
    val events: Flow<TripsEvent> = eventChannel.receiveAsFlow()

    private val retryTick = MutableStateFlow(0)
    private var lastDeleted: Trip? = null

    init {
        retryTick
            .flatMapLatest {
                trips.observeTrips()
                    .map<List<Trip>, TripsLoad> { TripsLoad.Loaded(it) }
                    .catch { emit(TripsLoad.Failed) }
            }
            .onEach(::applyLoad)
            .launchIn(viewModelScope)
    }

    fun retry() {
        mutableState.update { it.copy(isLoading = true, error = null) }
        retryTick.update { it + 1 }
    }

    fun deleteTrip(trip: Trip) {
        viewModelScope.launch {
            lastDeleted = trip
            trips.delete(trip.id)
            eventChannel.send(TripsEvent.TripDeleted(trip))
        }
    }

    fun undoDelete() {
        val trip = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            trips.upsert(trip.copy(stops = trip.stops.map { it.copy(id = 0) }))
        }
    }

    fun clearRecent() {
        val recent = mutableState.value.recent
        viewModelScope.launch {
            recent.forEach { trips.delete(it.id) }
            eventChannel.send(TripsEvent.RecentCleared)
        }
    }

    private fun applyLoad(load: TripsLoad) {
        mutableState.update { state ->
            when (load) {
                is TripsLoad.Loaded -> {
                    val sorted = load.trips.sortedByDescending { it.updatedAt }
                    state.copy(
                        isLoading = false,
                        error = null,
                        trips = sorted.filterNot { it.isQuick },
                        recent = sorted.filter { it.isQuick }.take(MAX_RECENT),
                        nowMillis = clock.nowMillis(),
                    )
                }
                TripsLoad.Failed -> state.copy(isLoading = false, error = AppError.Unknown())
            }
        }
    }

    companion object {
        const val MAX_RECENT = 5
    }
}
