package com.raahi.app.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.R
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.SavedPlaceType
import com.raahi.app.domain.repository.SavedPlaceRepository
import com.raahi.app.ui.util.UiMessage
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

data class SavedUiState(
    val isLoaded: Boolean = false,
    val home: SavedPlace? = null,
    val work: SavedPlace? = null,
    val parking: List<SavedPlace> = emptyList(),
    val favorites: List<SavedPlace> = emptyList(),
) {
    val isEmpty: Boolean get() = home == null && work == null && parking.isEmpty() && favorites.isEmpty()
}

sealed interface SavedEvent {
    data class ShowUndo(val message: UiMessage) : SavedEvent
}

class SavedViewModel(
    private val repository: SavedPlaceRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(SavedUiState())
    val uiState: StateFlow<SavedUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<SavedEvent>(Channel.BUFFERED)
    val events: Flow<SavedEvent> = eventChannel.receiveAsFlow()

    private var lastDeleted: SavedPlace? = null

    init {
        repository.observeAll()
            .onEach { all -> mutableState.update { group(all) } }
            .launchIn(viewModelScope)
    }

    fun delete(saved: SavedPlace) {
        viewModelScope.launch {
            repository.delete(saved.id)
            lastDeleted = saved
            eventChannel.send(SavedEvent.ShowUndo(UiMessage(R.string.saved_removed, listOf(saved.place.name))))
        }
    }

    fun undoDelete() {
        val saved = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { repository.save(saved) }
    }

    private fun group(all: List<SavedPlace>): SavedUiState {
        val newestFirst = all.sortedByDescending { it.savedAt }
        return SavedUiState(
            isLoaded = true,
            home = newestFirst.firstOrNull { it.type == SavedPlaceType.HOME },
            work = newestFirst.firstOrNull { it.type == SavedPlaceType.WORK },
            parking = newestFirst.filter { it.type == SavedPlaceType.PARKING },
            favorites = newestFirst.filter { it.type == SavedPlaceType.FAVORITE },
        )
    }
}
