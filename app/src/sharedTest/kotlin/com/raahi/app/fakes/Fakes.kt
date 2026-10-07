package com.raahi.app.fakes

import com.raahi.app.core.config.MapboxConfig
import com.raahi.app.core.dispatchers.DispatcherProvider
import com.raahi.app.core.di.AppContainer
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.time.Clock
import com.raahi.app.domain.model.CachedPlaces
import com.raahi.app.domain.model.DownloadProgress
import com.raahi.app.domain.model.EmergencyContact
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.OfflineEstimate
import com.raahi.app.domain.model.OfflineRegion
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.PlaceSource
import com.raahi.app.domain.model.RecentSearch
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.domain.model.ThemeMode
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.model.UserLocation
import com.raahi.app.domain.model.UserPreferences
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeClock(var now: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = now
}

class TestDispatcherProvider(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

class FakeConnectivityMonitor(initial: ConnectivityState = ConnectivityState.Online) : ConnectivityMonitor {
    val mutableState = MutableStateFlow(initial)
    var metered = false
    override val state: StateFlow<ConnectivityState> = mutableState.asStateFlow()
    override fun isActiveNetworkMetered(): Boolean = metered
}

fun samplePlace(
    id: String = "poi.1",
    name: String = "Test Fuel Station",
    category: PlaceCategory? = PlaceCategory.FUEL,
    point: GeoPoint = GeoPoint(28.61, 77.21),
    distance: Double? = 450.0,
) = Place(id, name, "1 Test Road, Delhi", point, category, distance, PlaceSource.CATEGORY)

class FakePlaceSearchRepository : PlaceSearchRepository {
    var suggestResult: AppResult<List<SearchSuggestion>> = AppResult.Success(emptyList())
    var retrieveResult: AppResult<Place> = AppResult.Success(samplePlace())
    var categoryResult: AppResult<List<Place>> = AppResult.Success(listOf(samplePlace()))
    var reverseResult: AppResult<Place> = AppResult.Success(samplePlace())
    val categoryCalls = mutableListOf<Pair<PlaceCategory, GeoPoint>>()

    override suspend fun suggest(query: String, proximity: GeoPoint?, sessionToken: String) = suggestResult
    override suspend fun retrieve(suggestion: SearchSuggestion, sessionToken: String) = retrieveResult
    override suspend fun categorySearch(category: PlaceCategory, near: GeoPoint, limit: Int): AppResult<List<Place>> {
        categoryCalls += category to near
        return categoryResult
    }
    override suspend fun reverseGeocode(point: GeoPoint) = reverseResult
}

class FakeDirectionsRepository : DirectionsRepository {
    var result: AppResult<List<Route>> = AppResult.Failure(AppError.NoRoute)
    var lastWaypoints: List<GeoPoint> = emptyList()
    override suspend fun getRoutes(waypoints: List<GeoPoint>, profile: RouteProfile, options: RouteOptions): AppResult<List<Route>> {
        lastWaypoints = waypoints
        return result
    }
}

class FakeTripRepository : TripRepository {
    val trips = MutableStateFlow<List<Trip>>(emptyList())
    private var nextId = 1L
    override fun observeTrips(): Flow<List<Trip>> = trips
    override fun observeTrip(id: Long): Flow<Trip?> = trips.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun getTrip(id: Long): Trip? = trips.value.firstOrNull { it.id == id }
    override suspend fun upsert(trip: Trip): Long {
        val id = if (trip.id == Trip.NEW_ID) nextId++ else trip.id
        trips.update { list -> list.filterNot { it.id == id } + trip.copy(id = id) }
        return id
    }
    override suspend fun delete(id: Long) = trips.update { list -> list.filterNot { it.id == id } }
    override suspend fun reorder(tripId: Long, orderedStopIds: List<Long>) = Unit
}

class FakeSavedPlaceRepository : SavedPlaceRepository {
    val items = MutableStateFlow<List<SavedPlace>>(emptyList())
    private var nextId = 1L
    override fun observeAll(): Flow<List<SavedPlace>> = items
    override suspend fun save(saved: SavedPlace): Long {
        val id = if (saved.id == 0L) nextId++ else saved.id
        items.update { list -> list.filterNot { it.id == id } + saved.copy(id = id) }
        return id
    }
    override suspend fun delete(id: Long) = items.update { list -> list.filterNot { it.id == id } }
}

class FakeEmergencyContactRepository : EmergencyContactRepository {
    val items = MutableStateFlow<List<EmergencyContact>>(emptyList())
    private var nextId = 1L
    override fun observeAll(): Flow<List<EmergencyContact>> = items
    override suspend fun upsert(contact: EmergencyContact): Long {
        val id = if (contact.id == 0L) nextId++ else contact.id
        items.update { list -> list.filterNot { it.id == id } + contact.copy(id = id) }
        return id
    }
    override suspend fun delete(id: Long) = items.update { list -> list.filterNot { it.id == id } }
}

class FakeRecentSearchRepository : RecentSearchRepository {
    val items = MutableStateFlow<List<RecentSearch>>(emptyList())
    private var nextId = 1L
    override fun observeRecent(limit: Int): Flow<List<RecentSearch>> = items
    override suspend fun record(query: String, place: Place?) {
        items.update { list -> listOf(RecentSearch(nextId++, query, place, 0L)) + list }
    }
    override suspend fun delete(id: Long) = items.update { list -> list.filterNot { it.id == id } }
    override suspend fun clear() = items.update { emptyList() }
}

class FakeLocationRepository(
    initialPermission: LocationPermissionState = LocationPermissionState.DENIED,
) : LocationRepository {
    val mutablePermission = MutableStateFlow(initialPermission)
    var locationEnabled = true
    var locationResult: AppResult<UserLocation> = AppResult.Success(sampleUserLocation())
    var permissionAfterRefresh: LocationPermissionState? = null
    override val permissionState: StateFlow<LocationPermissionState> = mutablePermission.asStateFlow()
    override fun refreshPermissionState() {
        permissionAfterRefresh?.let { mutablePermission.value = it }
    }
    override fun isLocationEnabled(): Boolean = locationEnabled
    override suspend fun lastKnown(): AppResult<UserLocation> = locationResult
    override suspend fun currentLocation(): AppResult<UserLocation> = locationResult
    override fun locationUpdates(intervalMillis: Long): Flow<UserLocation> = emptyFlow()
}

fun sampleUserLocation(point: GeoPoint = GeoPoint(28.6139, 77.2090)) =
    UserLocation(point, 8f, 0L, null, null, isStale = false, isLowAccuracy = false)

class FakeOfflineMapRepository : OfflineMapRepository {
    override val regions: Flow<List<OfflineRegion>> = MutableStateFlow(emptyList())
    override suspend fun reconcile() = Unit
    override fun estimate(bounds: GeoBounds, minZoom: Int, maxZoom: Int) = OfflineEstimate(0, 0)
    override fun download(region: OfflineRegion): Flow<DownloadProgress> = emptyFlow()
    override fun resume(regionId: String): Flow<DownloadProgress> = emptyFlow()
    override suspend fun cancel(regionId: String) = Unit
    override suspend fun delete(regionId: String): AppResult<Unit> = AppResult.Success(Unit)
}

class FakePreferencesRepository(initial: UserPreferences = UserPreferences()) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = state
    override suspend fun setOnboardingDone(done: Boolean) = state.update { it.copy(onboardingDone = done) }
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setMapStyle(style: MapStyleMode) = state.update { it.copy(mapStyle = style) }
    override suspend fun setRouteDefaults(profile: RouteProfile, options: RouteOptions) =
        state.update { it.copy(defaultProfile = profile, defaultRouteOptions = options) }
    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = state.update { it.copy(wifiOnlyDownloads = enabled) }
    override suspend fun setVoiceGuidance(enabled: Boolean) = state.update { it.copy(voiceGuidance = enabled) }
}

class FakeNearbyCacheRepository : NearbyCacheRepository {
    val entries = mutableMapOf<String, CachedPlaces>()
    var clock: Clock = FakeClock()
    override suspend fun get(key: String): CachedPlaces? = entries[key]
    override suspend fun put(key: String, places: List<Place>) {
        entries[key] = CachedPlaces(places, clock.nowMillis())
    }
}

class FakeAppContainer(
    token: String? = null,
    override val preferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
    override val locationRepository: FakeLocationRepository = FakeLocationRepository(),
    override val connectivityMonitor: FakeConnectivityMonitor = FakeConnectivityMonitor(),
    override val placeSearchRepository: FakePlaceSearchRepository = FakePlaceSearchRepository(),
    override val savedPlaceRepository: FakeSavedPlaceRepository = FakeSavedPlaceRepository(),
    override val nearbyCacheRepository: FakeNearbyCacheRepository = FakeNearbyCacheRepository(),
) : AppContainer {
    override val mapboxConfig: MapboxConfig = MapboxConfig(token)
    override val dispatchers: DispatcherProvider = TestDispatcherProvider()
    override val clock: Clock = FakeClock()
    override val directionsRepository: FakeDirectionsRepository = FakeDirectionsRepository()
    override val tripRepository: FakeTripRepository = FakeTripRepository()
    override val emergencyContactRepository: FakeEmergencyContactRepository = FakeEmergencyContactRepository()
    override val recentSearchRepository: FakeRecentSearchRepository = FakeRecentSearchRepository()
    override val offlineMapRepository: OfflineMapRepository = FakeOfflineMapRepository()
    override val tripValidator: TripValidator = TripValidator()
    override val planRoutesUseCase: PlanRoutesUseCase = PlanRoutesUseCase(locationRepository, directionsRepository, tripValidator)
    override val findNearestEmergencyUseCase: FindNearestEmergencyUseCase =
        FindNearestEmergencyUseCase(placeSearchRepository, nearbyCacheRepository)
}
