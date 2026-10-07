package com.raahi.app.feature.tripeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.core.time.Clock
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.model.TripStop
import com.raahi.app.domain.repository.PreferencesRepository
import com.raahi.app.domain.repository.TripRepository
import com.raahi.app.domain.usecase.TripIssue
import com.raahi.app.domain.usecase.TripValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorStop(val key: Long, val place: Place?, val useCurrentLocation: Boolean)

data class TripDraft(
    val name: String,
    val start: EditorStop,
    val destinations: List<EditorStop>,
    val profile: RouteProfile,
    val options: RouteOptions,
) {
    val allStops: List<EditorStop> get() = listOf(start) + destinations
}

data class TripEditorUiState(
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val isEditing: Boolean = false,
    val draft: TripDraft? = null,
    val issues: List<TripIssue> = emptyList(),
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
) {
    val canSave: Boolean get() = draft != null && issues.isEmpty() && !isSaving
}

sealed interface TripEditorEvent {
    data class Saved(val tripId: Long) : TripEditorEvent
    data class StopMoved(val name: String?, val position: Int) : TripEditorEvent
    data object SaveFailed : TripEditorEvent
}

class TripEditorViewModel(
    private val tripId: Long,
    private val trips: TripRepository,
    private val preferences: PreferencesRepository,
    private val validator: TripValidator,
    private val clock: Clock,
    private val defaultName: (Long) -> String,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val mutableState = MutableStateFlow(TripEditorUiState())
    val uiState: StateFlow<TripEditorUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<TripEditorEvent>(Channel.BUFFERED)
    val events: Flow<TripEditorEvent> = eventChannel.receiveAsFlow()

    private var original: TripDraft? = null
    private var nextKey = 1L
    private var existing: Trip? = null

    init {
        viewModelScope.launch { load() }
    }

    fun setName(name: String) {
        savedState[KEY_NAME] = name
        edit { it.copy(name = name) }
    }

    fun setProfile(profile: RouteProfile) = edit { it.copy(profile = profile) }

    fun setOptions(options: RouteOptions) = edit { it.copy(options = options) }

    fun useMyLocationAsStart() = edit { it.copy(start = EditorStop(nextKey++, null, useCurrentLocation = true)) }

    fun setStart(place: Place) = edit { it.copy(start = EditorStop(nextKey++, place, useCurrentLocation = false)) }

    fun addStop(place: Place) = edit { draft ->
        draft.copy(destinations = draft.destinations + EditorStop(nextKey++, place, useCurrentLocation = false))
    }

    fun removeStop(index: Int) = edit { draft ->
        if (index !in draft.destinations.indices) draft else draft.copy(destinations = draft.destinations.filterIndexed { i, _ -> i != index })
    }

    fun moveStop(from: Int, to: Int) = edit { draft ->
        val list = draft.destinations
        if (from !in list.indices || to !in list.indices || from == to) {
            draft
        } else {
            draft.copy(destinations = list.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun moveStopUp(index: Int) = moveAnnounced(index, index - 1)

    fun moveStopDown(index: Int) = moveAnnounced(index, index + 1)

    fun save() {
        val state = mutableState.value
        val draft = state.draft ?: return
        if (!state.canSave) return
        mutableState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val now = clock.nowMillis()
            val base = existing
            val trip = Trip(
                id = if (base == null) Trip.NEW_ID else base.id,
                name = draft.name.trim().ifEmpty { defaultName(now) },
                stops = draft.allStops.mapIndexed { index, stop ->
                    TripStop(id = 0, place = stop.place, useCurrentLocation = stop.useCurrentLocation, order = index)
                },
                profile = draft.profile,
                createdAt = base?.createdAt ?: now,
                updatedAt = now,
                isQuick = false,
                options = draft.options,
            )
            runCatching { trips.upsert(trip) }
                .onSuccess { id ->
                    original = draft
                    mutableState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                    eventChannel.send(TripEditorEvent.Saved(id))
                }
                .onFailure {
                    mutableState.update { it.copy(isSaving = false) }
                    eventChannel.send(TripEditorEvent.SaveFailed)
                }
        }
    }

    private fun moveAnnounced(from: Int, to: Int) {
        val destinations = mutableState.value.draft?.destinations ?: return
        if (from !in destinations.indices || to !in destinations.indices) return
        val moved = destinations[from]
        moveStop(from, to)
        viewModelScope.launch {
            eventChannel.send(TripEditorEvent.StopMoved(moved.place?.name, to + 2))
        }
    }

    private suspend fun load() {
        val prefs = preferences.preferences.first()
        if (tripId == NEW_TRIP) {
            val draft = TripDraft(
                name = defaultName(clock.nowMillis()),
                start = EditorStop(nextKey++, null, useCurrentLocation = true),
                destinations = emptyList(),
                profile = prefs.defaultProfile,
                options = prefs.defaultRouteOptions,
            )
            original = draft
            mutableState.value = stateFor(restoreName(draft), isEditing = false)
            return
        }
        val trip = trips.getTrip(tripId)
        if (trip == null) {
            mutableState.value = TripEditorUiState(isLoading = false, isNotFound = true, isEditing = true)
            return
        }
        existing = trip
        val stops = trip.stops.sortedBy { it.order }.map { EditorStop(nextKey++, it.place, it.useCurrentLocation) }
        val draft = TripDraft(
            name = trip.name,
            start = stops.firstOrNull() ?: EditorStop(nextKey++, null, useCurrentLocation = true),
            destinations = stops.drop(1),
            profile = trip.profile,
            options = trip.options,
        )
        original = draft
        mutableState.value = stateFor(restoreName(draft), isEditing = true)
    }

    private fun restoreName(draft: TripDraft): TripDraft =
        savedState.get<String>(KEY_NAME)?.let { draft.copy(name = it) } ?: draft

    private fun edit(transform: (TripDraft) -> TripDraft) {
        mutableState.update { state ->
            val draft = state.draft ?: return@update state
            stateFor(transform(draft), state.isEditing, state.isSaving)
        }
    }

    private fun stateFor(draft: TripDraft, isEditing: Boolean, isSaving: Boolean = false) = TripEditorUiState(
        isLoading = false,
        isEditing = isEditing,
        draft = draft,
        issues = validator.validate(draft.toTrip()).issues,
        hasUnsavedChanges = draft.signature() != original?.signature(),
        isSaving = isSaving,
    )

    private fun TripDraft.toTrip() = Trip(
        id = tripId,
        name = name,
        stops = allStops.mapIndexed { index, stop ->
            TripStop(id = 0, place = stop.place, useCurrentLocation = stop.useCurrentLocation, order = index)
        },
        profile = profile,
        createdAt = 0L,
        updatedAt = 0L,
        isQuick = false,
        options = options,
    )

    private fun TripDraft.signature(): List<Any?> = listOf(
        name,
        profile,
        options,
        allStops.map { it.useCurrentLocation to it.place?.id },
    )

    companion object {
        const val NEW_TRIP = -1L
        private const val KEY_NAME = "draft_name"
    }
}
