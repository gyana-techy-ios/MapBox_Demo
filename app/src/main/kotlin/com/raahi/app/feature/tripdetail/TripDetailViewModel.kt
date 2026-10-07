package com.raahi.app.feature.tripdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.repository.TripRepository
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

data class TripDetailUiState(
    val isLoading: Boolean = true,
    val trip: Trip? = null,
    val straightLineMeters: Double? = null,
    val isDeleting: Boolean = false,
) {
    val isMissing: Boolean get() = !isLoading && trip == null
}

sealed interface TripDetailEvent {
    data object Deleted : TripDetailEvent
}

class TripDetailViewModel(
    private val tripId: Long,
    private val trips: TripRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<TripDetailEvent>(Channel.BUFFERED)
    val events: Flow<TripDetailEvent> = eventChannel.receiveAsFlow()

    init {
        trips.observeTrip(tripId)
            .onEach { trip ->
                mutableState.update { state ->
                    if (state.isDeleting) state else state.copy(isLoading = false, trip = trip, straightLineMeters = trip?.straightLineMeters())
                }
            }
            .launchIn(viewModelScope)
    }

    fun deleteTrip() {
        if (mutableState.value.isDeleting) return
        mutableState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            trips.delete(tripId)
            eventChannel.send(TripDetailEvent.Deleted)
        }
    }

    private fun Trip.straightLineMeters(): Double? {
        val points = stops.sortedBy { it.order }.mapNotNull { it.place?.point }
        if (points.size < MIN_POINTS) return null
        return points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
    }

    private companion object {
        const val MIN_POINTS = 2
    }
}
