package com.raahi.app.feature.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.R
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.result.toSearchMessageRes
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.RecentSearch
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.repository.PlaceSearchRepository
import com.raahi.app.domain.repository.RecentSearchRepository
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

enum class SearchPhase { Idle, Loading, Results, Empty, Error }

data class SearchUiState(
    val query: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
    val suggestions: List<SearchSuggestion> = emptyList(),
    val recents: List<RecentSearch> = emptyList(),
    val error: AppError? = null,
    val connectivity: ConnectivityState = ConnectivityState.Online,
    val isRetrieving: Boolean = false,
) {
    val isQueryLongEnough: Boolean get() = query.trim().length >= SearchViewModel.MIN_QUERY_LENGTH
}

sealed interface SearchEvent {
    data class OpenPlace(val place: Place) : SearchEvent
    data class ShowMessage(@StringRes val messageRes: Int) : SearchEvent
}

private sealed interface SearchOutcome {
    data object Idle : SearchOutcome
    data object Loading : SearchOutcome
    data class Done(val result: AppResult<List<SearchSuggestion>>) : SearchOutcome
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val placeSearch: PlaceSearchRepository,
    private val recentSearches: RecentSearchRepository,
    private val locationRepository: LocationRepository,
    private val connectivity: ConnectivityMonitor,
    private val newSessionToken: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val mutableState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<SearchEvent>(Channel.BUFFERED)
    val events: Flow<SearchEvent> = eventChannel.receiveAsFlow()

    private val retryTick = MutableStateFlow(0)
    private var sessionToken: String = newSessionToken()
    private var isSelecting = false

    init {
        recentSearches.observeRecent()
            .onEach { recents -> mutableState.update { it.copy(recents = recents) } }
            .launchIn(viewModelScope)
        connectivity.state
            .onEach { state -> mutableState.update { it.copy(connectivity = state) } }
            .launchIn(viewModelScope)
        combine(
            mutableState.map { it.query.trim() }
                .distinctUntilChanged()
                .debounce { query -> if (query.length < MIN_QUERY_LENGTH) 0L else DEBOUNCE_MILLIS }
                .distinctUntilChanged(),
            retryTick,
        ) { query, _ -> query }
            .flatMapLatest { query -> outcomesFor(query) }
            .onEach(::applyOutcome)
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        mutableState.update {
            if (query.trim().length < MIN_QUERY_LENGTH) {
                it.copy(query = query, phase = SearchPhase.Idle, suggestions = emptyList(), error = null)
            } else {
                it.copy(query = query)
            }
        }
    }

    fun retry() {
        retryTick.update { it + 1 }
    }

    fun onImeSearch() {
        mutableState.value.suggestions.firstOrNull()?.let(::select)
    }

    fun select(suggestion: SearchSuggestion) {
        if (isSelecting) return
        isSelecting = true
        mutableState.update { it.copy(isRetrieving = true) }
        viewModelScope.launch {
            when (val result = placeSearch.retrieve(suggestion, sessionToken)) {
                is AppResult.Success -> openRetrieved(suggestion, result.value)
                is AppResult.Failure -> eventChannel.send(SearchEvent.ShowMessage(result.error.toSearchMessageRes()))
            }
            isSelecting = false
            mutableState.update { it.copy(isRetrieving = false) }
        }
    }

    fun selectRecent(recent: RecentSearch) {
        val place = recent.place
        if (place == null) {
            onQueryChange(recent.query)
            return
        }
        viewModelScope.launch {
            recentSearches.record(recent.query, place)
            eventChannel.send(SearchEvent.OpenPlace(place))
        }
    }

    fun deleteRecent(id: Long) {
        viewModelScope.launch { recentSearches.delete(id) }
    }

    fun clearRecents() {
        viewModelScope.launch { recentSearches.clear() }
    }

    private suspend fun openRetrieved(suggestion: SearchSuggestion, place: Place) {
        if (!place.hasUsableCoordinates()) {
            eventChannel.send(SearchEvent.ShowMessage(R.string.search_place_unavailable))
            return
        }
        recentSearches.record(suggestion.name, place)
        sessionToken = newSessionToken()
        eventChannel.send(SearchEvent.OpenPlace(place))
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
                SearchOutcome.Idle -> state.copy(phase = SearchPhase.Idle, suggestions = emptyList(), error = null)
                SearchOutcome.Loading -> state.copy(phase = SearchPhase.Loading, error = null)
                is SearchOutcome.Done -> when (val result = outcome.result) {
                    is AppResult.Success -> state.copy(
                        phase = if (result.value.isEmpty()) SearchPhase.Empty else SearchPhase.Results,
                        suggestions = result.value,
                        error = null,
                    )
                    is AppResult.Failure -> state.copy(phase = SearchPhase.Error, suggestions = emptyList(), error = result.error)
                }
            }
        }
    }

    private fun Place.hasUsableCoordinates(): Boolean =
        point.lat in -90.0..90.0 && point.lng in -180.0..180.0 && !(point.lat == 0.0 && point.lng == 0.0)

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val DEBOUNCE_MILLIS = 300L
    }
}
