package com.raahi.app.core.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.raahi.app.core.config.MapboxConfig
import com.raahi.app.core.dispatchers.DispatcherProvider
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.time.Clock
import com.raahi.app.domain.repository.DirectionsRepository
import com.raahi.app.domain.repository.EmergencyContactRepository
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.repository.NearbyCacheRepository
import com.raahi.app.domain.repository.OfflineMapRepository
import com.raahi.app.domain.repository.PlaceSearchRepository
import com.raahi.app.domain.repository.PreferencesRepository
import com.raahi.app.domain.repository.RecentSearchRepository
import com.raahi.app.domain.repository.SavedPlaceRepository
import com.raahi.app.domain.repository.TripRepository
import com.raahi.app.domain.usecase.FindNearestEmergencyUseCase
import com.raahi.app.domain.usecase.PlanRoutesUseCase
import com.raahi.app.domain.usecase.TripValidator

interface AppContainer {
    val mapboxConfig: MapboxConfig
    val dispatchers: DispatcherProvider
    val clock: Clock
    val connectivityMonitor: ConnectivityMonitor
    val placeSearchRepository: PlaceSearchRepository
    val directionsRepository: DirectionsRepository
    val tripRepository: TripRepository
    val savedPlaceRepository: SavedPlaceRepository
    val emergencyContactRepository: EmergencyContactRepository
    val recentSearchRepository: RecentSearchRepository
    val locationRepository: LocationRepository
    val offlineMapRepository: OfflineMapRepository
    val preferencesRepository: PreferencesRepository
    val nearbyCacheRepository: NearbyCacheRepository
    val tripValidator: TripValidator
    val planRoutesUseCase: PlanRoutesUseCase
    val findNearestEmergencyUseCase: FindNearestEmergencyUseCase
}

val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("AppContainer not provided. Wrap content in CompositionLocalProvider(LocalAppContainer provides container).")
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}
