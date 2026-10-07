package com.raahi.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.domain.repository.OfflineMapRepository
import com.raahi.app.domain.repository.SavedPlaceRepository
import com.raahi.app.domain.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class ProfileUiState(
    val savedPlaceCount: Int = 0,
    val tripCount: Int = 0,
    val offlineRegionCount: Int = 0,
)

class ProfileViewModel(
    savedPlaces: SavedPlaceRepository,
    trips: TripRepository,
    offlineMaps: OfflineMapRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = mutableState.asStateFlow()

    init {
        combine(savedPlaces.observeAll(), trips.observeTrips(), offlineMaps.regions) { places, allTrips, regions ->
            ProfileUiState(
                savedPlaceCount = places.size,
                tripCount = allTrips.count { !it.isQuick },
                offlineRegionCount = regions.size,
            )
        }
            .catch { }
            .onEach { mutableState.value = it }
            .launchIn(viewModelScope)
    }
}
