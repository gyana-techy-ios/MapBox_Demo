package com.raahi.app.feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.R
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.ThemeMode
import com.raahi.app.domain.model.UserPreferences
import com.raahi.app.domain.repository.NearbyCacheRepository
import com.raahi.app.domain.repository.PreferencesRepository
import com.raahi.app.domain.repository.RecentSearchRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
)

sealed interface SettingsEvent {
    data class ShowMessage(@StringRes val messageRes: Int) : SettingsEvent
}

class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val recentSearches: RecentSearchRepository,
    private val nearbyCache: NearbyCacheRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    init {
        preferences.preferences
            .onEach { mutableState.value = SettingsUiState(isLoading = false, preferences = it) }
            .launchIn(viewModelScope)
    }

    fun setTheme(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }

    fun setMapStyle(style: MapStyleMode) = launch { preferences.setMapStyle(style) }

    fun setDefaultProfile(profile: RouteProfile) = launch {
        preferences.setRouteDefaults(profile, mutableState.value.preferences.defaultRouteOptions)
    }

    fun setDefaultOptions(options: RouteOptions) = launch {
        preferences.setRouteDefaults(mutableState.value.preferences.defaultProfile, options)
    }

    fun setWifiOnly(enabled: Boolean) = launch { preferences.setWifiOnlyDownloads(enabled) }

    fun setVoiceGuidance(enabled: Boolean) = launch { preferences.setVoiceGuidance(enabled) }

    fun clearSearchHistory() = launch {
        recentSearches.clear()
        eventChannel.send(SettingsEvent.ShowMessage(R.string.settings_history_cleared))
    }

    fun clearNearbyCache() = launch {
        nearbyCache.clear()
        eventChannel.send(SettingsEvent.ShowMessage(R.string.settings_cache_cleared))
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
