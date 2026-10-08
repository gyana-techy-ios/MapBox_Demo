package com.raahi.app.core.di

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale
import android.content.Context
import com.raahi.app.BuildConfig
import com.raahi.app.core.config.MapboxConfig
import com.raahi.app.core.dispatchers.DefaultDispatcherProvider
import com.raahi.app.core.dispatchers.DispatcherProvider
import com.raahi.app.core.network.AndroidConnectivityMonitor
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.time.Clock
import com.raahi.app.core.time.SystemClock
import com.raahi.app.data.local.RaahiDatabase
import com.raahi.app.data.remote.DirectionsApi
import com.raahi.app.data.remote.HttpClientFactory
import com.raahi.app.data.remote.MapboxDirectionsRepository
import com.raahi.app.data.remote.MapboxPlaceSearchRepository
import com.raahi.app.data.remote.RetrofitFactory
import com.raahi.app.data.remote.SearchBoxApi
import com.raahi.app.data.repository.DataStorePreferencesRepository
import com.raahi.app.data.repository.RoomEmergencyContactRepository
import com.raahi.app.data.repository.RoomNearbyCacheRepository
import com.raahi.app.data.repository.RoomRecentSearchRepository
import com.raahi.app.data.repository.RoomSavedPlaceRepository
import com.raahi.app.data.repository.RoomTripRepository
import com.raahi.app.data.repository.raahiDataStore
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
import com.raahi.app.domain.usecase.ActiveRouteStore
import com.raahi.app.domain.usecase.FindNearestEmergencyUseCase
import com.raahi.app.domain.usecase.PlanRoutesUseCase
import com.raahi.app.domain.usecase.TripValidator
import com.raahi.app.location.FusedLocationRepository
import com.raahi.app.domain.voice.InstructionLanguage
import com.raahi.app.domain.voice.VoiceGuide
import com.raahi.app.offline.DeviceStorageProbe
import com.raahi.app.offline.DownloadCoordinator
import com.raahi.app.offline.MapboxOfflineMapRepository
import com.raahi.app.offline.StorageProbe
import com.raahi.app.voice.TextToSpeechVoiceGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + DefaultDispatcherProvider.default)

    override val mapboxConfig: MapboxConfig = MapboxConfig(BuildConfig.MAPBOX_ACCESS_TOKEN)
    override val dispatchers: DispatcherProvider = DefaultDispatcherProvider
    override val clock: Clock = SystemClock
    override val connectivityMonitor: ConnectivityMonitor by lazy { AndroidConnectivityMonitor(appContext) }

    private val database: RaahiDatabase by lazy { RaahiDatabase.create(appContext) }
    private val httpClient by lazy {
        HttpClientFactory.create(tokenProvider = { mapboxConfig.accessToken }, enableLogging = BuildConfig.DEBUG)
    }

    override val placeSearchRepository: PlaceSearchRepository by lazy {
        MapboxPlaceSearchRepository(
            api = RetrofitFactory.create(RetrofitFactory.SEARCH_BOX_BASE_URL, httpClient, SearchBoxApi::class.java),
            isConfigured = { mapboxConfig.isMapAvailable },
        )
    }
    override val directionsRepository: DirectionsRepository by lazy {
        MapboxDirectionsRepository(
            api = RetrofitFactory.create(RetrofitFactory.DIRECTIONS_BASE_URL, httpClient, DirectionsApi::class.java),
            isConfigured = { mapboxConfig.isMapAvailable },
            language = { InstructionLanguage.apiTagFor(AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()) },
        )
    }
    override val tripRepository: TripRepository by lazy { RoomTripRepository(database.tripDao(), clock) }
    override val savedPlaceRepository: SavedPlaceRepository by lazy { RoomSavedPlaceRepository(database.savedPlaceDao()) }
    override val emergencyContactRepository: EmergencyContactRepository by lazy {
        RoomEmergencyContactRepository(database.emergencyContactDao())
    }
    override val recentSearchRepository: RecentSearchRepository by lazy {
        RoomRecentSearchRepository(database.recentSearchDao(), clock)
    }
    override val locationRepository: LocationRepository by lazy { FusedLocationRepository(appContext, clock) }
    override val preferencesRepository: PreferencesRepository by lazy {
        DataStorePreferencesRepository(appContext.raahiDataStore)
    }
    override val nearbyCacheRepository: NearbyCacheRepository by lazy {
        RoomNearbyCacheRepository(database.nearbyCacheDao(), clock)
    }
    override val offlineMapRepository: OfflineMapRepository by lazy {
        MapboxOfflineMapRepository(
            context = appContext,
            config = mapboxConfig,
            regionDao = database.offlineRegionDao(),
            connectivity = connectivityMonitor,
            preferences = preferencesRepository,
            clock = clock,
            scope = applicationScope,
        )
    }
    override val tripValidator: TripValidator = TripValidator()
    override val planRoutesUseCase: PlanRoutesUseCase by lazy {
        PlanRoutesUseCase(locationRepository, directionsRepository, tripValidator)
    }
    override val findNearestEmergencyUseCase: FindNearestEmergencyUseCase by lazy {
        FindNearestEmergencyUseCase(placeSearchRepository, nearbyCacheRepository)
    }
    override val activeRouteStore: ActiveRouteStore = ActiveRouteStore()
    override val voiceGuide: VoiceGuide by lazy { TextToSpeechVoiceGuide(appContext) }
    override val downloadCoordinator: DownloadCoordinator by lazy {
        DownloadCoordinator(offlineMapRepository, CoroutineScope(SupervisorJob() + DefaultDispatcherProvider.default))
    }
    override val storageProbe: StorageProbe by lazy { DeviceStorageProbe(appContext) }
}
