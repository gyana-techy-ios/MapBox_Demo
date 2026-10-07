package com.raahi.app.feature.tripeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.repository.PlaceSearchRepository
import com.raahi.app.domain.repository.SavedPlaceRepository
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StopSearchPhase { Idle, Loading, Results, Empty, Error }

data class StopPickerUiState(
    val query: String = "",
    val phase: StopSearchPhase = StopSearchPhase.Idle,
    val suggestions: List<SearchSuggestion> = emptyList(),
    val saved: List<SavedPlace> = emptyList(),
    val error: AppError? = null,
    val connectivity: ConnectivityState = ConnectivityState.Online,
    val isRetrieving: Boolean = false,
)

sealed interface StopPickerEvent {
    data class Picked(val place: Place) : StopPickerEvent
    data class Failed(val error: AppError) : StopPickerEvent
}

private sealed interface SearchOutcome {
    data object Idle : SearchOutcome
    data object Loading : SearchOutcome
    data class Done(val result: AppResult<List<SearchSuggestion>>) : SearchOutcome
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class StopPickerViewModel(
    private val placeSearch: PlaceSearchRepository,
    private val savedPlaces: SavedPlaceRepository,
    private val locationRepository: LocationRepository,
    private val connectivity: ConnectivityMonitor,
) : ViewModel() {

    private val mutableState = MutableStateFlow(StopPickerUiState())
    val uiState: StateFlow<StopPickerUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<StopPickerEvent>(Channel.BUFFERED)
    val events: Flow<StopPickerEvent> = eventChannel.receiveAsFlow()

    private var sessionToken = newToken()
    private var isRetrieving = false

    init {
        savedPlaces.observeAll()
            .onEach { list -> mutableState.update { it.copy(saved = list) } }
            .launchIn(viewModelScope)
        connectivity.state
            .onEach { state -> mutableState.update { it.copy(connectivity = state) } }
            .launchIn(viewModelScope)
        mutableState.map { it.query.trim() }
            .distinctUntilChanged()
            .debounce { query -> if (query.length < MIN_QUERY_LENGTH) 0L else DEBOUNCE_MILLIS }
            .distinctUntilChanged()
            .flatMapLatest { query -> outcomesFor(query) }
            .onEach(::applyOutcome)
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        mutableState.update {
            if (query.trim().length < MIN_QUERY_LENGTH) {
                it.copy(query = query, phase = StopSearchPhase.Idle, suggestions = emptyList(), error = null)
            } else {
                it.copy(query = query)
            }
        }
    }

    fun reset() {
        sessionToken = newToken()
        mutableState.update { it.copy(query = "", phase = StopSearchPhase.Idle, suggestions = emptyList(), error = null) }
    }

    fun pickSaved(saved: SavedPlace) {
        viewModelScope.launch { eventChannel.send(StopPickerEvent.Picked(saved.place)) }
    }

    fun pickSuggestion(suggestion: SearchSuggestion) {
        if (isRetrieving) return
        isRetrieving = true
        mutableState.update { it.copy(isRetrieving = true) }
        viewModelScope.launch {
            when (val result = placeSearch.retrieve(suggestion, sessionToken)) {
                is AppResult.Success -> {
                    sessionToken = newToken()
                    eventChannel.send(StopPickerEvent.Picked(result.value))
                }
                is AppResult.Failure -> eventChannel.send(StopPickerEvent.Failed(result.error))
            }
            isRetrieving = false
            mutableState.update { it.copy(isRetrieving = false) }
        }
    }

    private fun outcomesFor(query: String): Flow<SearchOutcome> = flow {
        if (query.length < MIN_QUERY_LENGTH) {
            emit(SearchOutcome.Idle)
            return@flow
        }
        if (connectivity.state.value == ConnectivityState.Offline) {
            emit(SearchOutcome.Done(AppResult.Failure(AppError.NoInternet)))
            return@flow
        }
        emit(SearchOutcome.Loading)
        emit(SearchOutcome.Done(placeSearch.suggest(query, proximity(), sessionToken)))
    }

    private suspend fun proximity(): GeoPoint? {
        if (locationRepository.permissionState.value == LocationPermissionState.DENIED) return null
        return (locationRepository.lastKnown() as? AppResult.Success)?.value?.point
    }

    private fun applyOutcome(outcome: SearchOutcome) {
        mutableState.update { state ->
            when (outcome) {
                SearchOutcome.Idle -> state.copy(phase = StopSearchPhase.Idle, suggestions = emptyList(), error = null)
                SearchOutcome.Loading -> state.copy(phase = StopSearchPhase.Loading, error = null)
                is SearchOutcome.Done -> when (val result = outcome.result) {
                    is AppResult.Success -> state.copy(
                        phase = if (result.value.isEmpty()) StopSearchPhase.Empty else StopSearchPhase.Results,
                        suggestions = result.value,
                        error = null,
                    )
                    is AppResult.Failure -> state.copy(phase = StopSearchPhase.Error, suggestions = emptyList(), error = result.error)
                }
            }
        }
    }

    private fun newToken() = UUID.randomUUID().toString()

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val DEBOUNCE_MILLIS = 300L
    }
}
