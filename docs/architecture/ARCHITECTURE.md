# Raahi: System Architecture

**Date:** October 2026  
**Status:** MVP Specification  
**Version:** 1.0

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer (Jetpack Compose)               │
│  Screens: Explore, TripEditor, TripDetail, RoutePreview,        │
│  Guidance, Offline, Saved, Emergency, Profile, Settings, etc.   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
        ┌──────────────────┴──────────────────┐
        ▼                                      ▼
┌─────────────────────┐            ┌──────────────────┐
│   Feature Modules   │            │   Core / Utils   │
│  (MVVM, ViewModel)  │            │  (DI, Config)    │
└──────────────────────────────────────────────────┘
        │                                      │
        ▼────────────────────────┬─────────────▼
┌──────────────────────────────────────────────────────────┐
│              Domain Layer (Use Cases)                     │
│  Repositories (interface), UseCase classes               │
└──────────────────────────────────────────────────────────┘
        │
        ▼────────────────────────┬─────────────────────────────┐
┌──────────────────────────┐  ┌────────────────────┐  ┌─────────────┐
│  Data Layer              │  │  Location Layer    │  │ Offline Mgr │
│ (Repository impl,        │  │  LocationRepository│  │ (TileStore) │
│  Remote + Local)         │  │  FusedLocationPrvdr│  │ OfflineMgr  │
└──────────────────────────┘  └────────────────────┘  └─────────────┘
        │                             │                      │
        ▼                             ▼                      ▼
┌──────────────────┐         ┌─────────────────┐  ┌──────────────┐
│ Mapbox APIs      │         │ GPS Provider    │  │ Mapbox       │
│ Search, Directions│         │ Android Location│  │ TileStore    │
│ Maps, Static Img │         │ Services        │  │ OfflineMgr   │
└──────────────────┘         └─────────────────┘  └──────────────┘
```

### Layer Responsibilities

**UI Layer:** Jetpack Compose screens, state holders, Material 3 design tokens, accessibility.

**Feature Modules:** MVVM screens + ViewModels that hold immutable UiState (StateFlow), use cases, navigation state.

**Domain Layer:** Business logic (use cases), repository interfaces, domain models (Trip, Stop, Place, SavedPlace, etc.).

**Data Layer:** Repository implementations, network (Retrofit/OkHttp), local persistence (Room), in-memory caches.

**Location Layer:** Abstraction over FusedLocationProviderClient, one-off and continuous location requests, permission handling.

**Offline Layer:** TileStore region download/management, offline map metadata, OfflineManager style pack handling.

**Map Layer:** Mapbox Maps SDK integration, RaahiMap composable, RaahiMapState, map-specific state management (viewport, clustering).

---

## 2. Package Structure

Single Gradle module (`app`), package `com.raahi.app`, source root `app/src/main/kotlin`.

```
com/raahi/app/
├── MainActivity.kt                    # Single activity, hosts RaahiApp
├── RaahiApplication.kt                # Builds DefaultAppContainer, initialises Mapbox token
│
├── core/
│   ├── config/        MapboxConfig, MapboxTokenBootstrap      # token parsing and status
│   ├── di/            AppContainer, DefaultAppContainer       # manual DI + appViewModel helpers
│   ├── dispatchers/   DispatcherProvider
│   ├── network/       ConnectivityMonitor, AndroidConnectivityMonitor
│   ├── result/        AppResult, AppError, HttpErrorMapper, AppErrorMessages
│   └── time/          Clock
│
├── domain/                            # Pure Kotlin
│   ├── model/         Geo, Place, Route, Trip, Offline
│   ├── repository/    Repositories.kt (all repository contracts)
│   ├── usecase/       ActiveRouteStore, ContactValidator, FindNearestEmergencyUseCase,
│   │                  GuidanceEngine, PlanRoutesUseCase, TileEstimator, TripValidator
│   └── voice/         VoiceGuide, InstructionLanguage
│
├── data/
│   ├── remote/        SearchBoxApi, DirectionsApi, RetrofitFactory, HttpClientFactory,
│   │                  MapboxPlaceSearchRepository, MapboxDirectionsRepository,
│   │                  DirectionsMapper, Polyline6, SearchResultSanitizer, ApiCall
│   ├── local/         RaahiDatabase, Entities, Daos, Mappers (Room)
│   └── repository/    RoomRepositories, DataStorePreferencesRepository
│
├── location/          FusedLocationRepository
├── offline/           MapboxOfflineMapRepository (the only TileStore/OfflineManager user),
│                      DownloadCoordinator, StorageProbe
├── voice/             TextToSpeechVoiceGuide
├── map/               RaahiMap, RaahiMapState, MapModels, MarkerIcons, MapUnavailablePanel
├── navigation/        RaahiApp (scaffold, bottom bar, deep-link intents), RaahiNavHost,
│                      Routes, TopLevelTab
├── ui/
│   ├── theme/         Color, Theme, Type, Dimensions (tokens only; the single place with colour literals)
│   ├── components/    Buttons, Dialogs, StateViews, RaahiTopBar, RaahiSearchBar, CategoryChipRow, ...
│   ├── format/        Formatters, TimeFormatters
│   └── util/          ContextExt, MapStyleState, PlaceLinks, UiMessage
└── feature/           one package per screen: onboarding, explore, search, nearby, placedetail,
                       trips, tripeditor, tripdetail, routepreview, guidance, offline,
                       offlineregionpicker, saved, emergency, emergencycontacts, profile,
                       settings, language, about
```

Resources: `res/values` (en), `res/values-hi`, `res/values-or`, `res/values-night`, `res/xml/locales_config.xml`.

### Key Design Decisions

1. **No Hilt:** Manual DI via `DefaultAppContainer`, created in `RaahiApplication`. ViewModels are built with `appViewModel { }` factories.
2. **Single module:** refactor to modules only if a layer must be swapped.
3. **Mapbox isolation:** `com.mapbox` imports are allowed only in `map/`, `offline/`, `core/config` and `RaahiApplication`.
4. **Offline isolation:** `MapboxOfflineMapRepository` is the only code touching TileStore. All Mapbox load calls run on the main dispatcher because Mapbox delivers callbacks on the calling thread's Looper.
5. **Guidance engine isolation:** `GuidanceEngine` is pure Kotlin and unit-tested on the JVM.
6. **HTTP only for search and routing:** Search Box API and Directions v5 via Retrofit. No Search SDK, no Navigation SDK.

---

## 3. Data Model and Persistence

### Domain Models (Immutable Data Classes)

```kotlin
// Trip: multi-stop journey
data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Trip",
    val origin: Stop,
    val stops: List<Stop> = emptyList(),   // up to 25 stops
    val profile: RouteProfile = RouteProfile.DRIVING_TRAFFIC,
    val excludeTolls: Boolean = false,
    val excludeMotorways: Boolean = false,
    val excludeFerries: Boolean = false,
    val selectedRoute: Route? = null,       // last selected route
    val alternativeRoutes: List<Route> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
)

// Stop: waypoint in a trip
data class Stop(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val sequence: Int = 0        // order in trip
)

// Route: computed direction
data class Route(
    val id: String = UUID.randomUUID().toString(),
    val geometry: List<LatLng>,   // polyline6 decoded
    val distance: Double,         // meters
    val duration: Double,         // seconds
    val steps: List<DirectionStep>,
    val legs: List<RouteLeg>,
    val congestion: CongestionSummary? = null,
    val selectedAt: Long? = null  // when user selected this route
)

// DirectionStep: maneuver (turn, continue, etc.)
data class DirectionStep(
    val instruction: String,      // "Turn right towards..."
    val distance: Double,         // meters to next step
    val duration: Double,         // seconds to next step
    val geometry: String,         // polyline6
    val name: String? = null,     // street name
    val maneuver: ManeuverType = ManeuverType.TURN   // turn, depart, arrive, etc.
)

// SavedPlace: user's anchor (home, car, favorite)
data class SavedPlace(
    val id: String = UUID.randomUUID().toString(),
    val type: SavedPlaceType,    // HOME, WORK, FAVORITE, PARKED_CAR
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val note: String? = null,        // e.g., "5th floor, Block A"
    val savedAt: Long = System.currentTimeMillis()
)

// EmergencyContact: stored locally, no sync
data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val createdAt: Long = System.currentTimeMillis()
)

// OfflineRegion: metadata for a downloaded region
data class OfflineRegion(
    val id: String,                  // "delhi-agra-20261008"
    val name: String = "Region",
    val geometry: String,            // WKT or GeoJSON string
    val minZoom: Int = 11,
    val maxZoom: Int = 15,
    val sizeBytes: Long,
    val tileCount: Int,
    val downloadedAt: Long = System.currentTimeMillis(),
    val expiryAt: Long,              // +30 days
    val isExpired: Boolean = false
)

// GuidanceState: real-time in-app guidance
data class GuidanceState(
    val currentStepIndex: Int = 0,
    val nextManeuver: String = "",
    val remainingDistance: Double = 0.0,     // meters
    val remainingTime: Double = 0.0,         // seconds
    val remainingSteps: List<DirectionStep> = emptyList(),
    val isOffRoute: Boolean = false,
    val userLocation: LatLng? = null,
    val speedMps: Float = 0f
)
```

### Room Persistence

**TripEntity** (Trip ↔ Room):
```kotlin
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val originLat: Double,
    val originLng: Double,
    val profile: String,           // DRIVING_TRAFFIC, DRIVING, WALKING, CYCLING
    val excludeTolls: Boolean,
    val excludeMotorways: Boolean,
    val excludeFerries: Boolean,
    val selectedRouteJson: String? = null,    // serialized Route
    val alternativeRoutesJson: String? = null,// serialized List<Route>
    val createdAt: Long,
    val lastModifiedAt: Long
)

// StopEntity: separate table, foreign key to TripEntity
@Entity(tableName = "stops", foreignKeys = [
    ForeignKey(entity = TripEntity::class, parentColumns = ["id"], 
               childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)
])
data class StopEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val sequence: Int
)

// SavedPlaceEntity: standalone table
@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey val id: String,
    val type: String,              // HOME, WORK, FAVORITE, PARKED_CAR
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val note: String? = null,
    val savedAt: Long
)

// OfflineRegionMetadataEntity: track downloaded regions
@Entity(tableName = "offline_regions")
data class OfflineRegionMetadataEntity(
    @PrimaryKey val id: String,
    val name: String,
    val geometryWkt: String,
    val minZoom: Int,
    val maxZoom: Int,
    val sizeBytes: Long,
    val tileCount: Int,
    val downloadedAt: Long,
    val expiryAt: Long
)

// RecentSearchEntity: persist search history
@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val searchedAt: Long
)
```

**Daos:**
```kotlin
@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Query("SELECT * FROM trips ORDER BY lastModifiedAt DESC")
    fun getAllTripsFlow(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: String): TripEntity?

    @Delete
    suspend fun deleteTrip(trip: TripEntity)
}

@Dao
interface StopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<StopEntity>)

    @Query("SELECT * FROM stops WHERE tripId = :tripId ORDER BY sequence")
    suspend fun getStopsForTrip(tripId: String): List<StopEntity>

    @Delete
    suspend fun deleteStop(stop: StopEntity)
}
```

**DataStore for Preferences:**
```kotlin
class AppPreferences(private val dataStore: DataStore<Preferences>) {
    val theme: Flow<Theme> = dataStore.data
        .map { it[THEME_KEY] ?: Theme.SYSTEM.name }
        .map { Theme.valueOf(it) }
        .catch { emit(Theme.SYSTEM) }

    val appLanguage: Flow<Language> = dataStore.data
        .map { it[LANGUAGE_KEY] ?: Language.ENGLISH.code }
        .map { Language.fromCode(it) }
        .catch { emit(Language.ENGLISH) }

    suspend fun setTheme(theme: Theme) {
        dataStore.edit { it[THEME_KEY] = theme.name }
    }

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        // ... other keys
    }
}
```

### In-Memory Caching

```kotlin
// PlaceCache: cache search results to avoid duplicate API calls
class PlaceCache {
    private val cache = mutableMapOf<String, CachedSearchResult>()
    private val mutex = Mutex()

    suspend fun get(query: String): CachedSearchResult? {
        mutex.withLock {
            val cached = cache[query]
            return if (cached?.isExpired() == false) cached else null
        }
    }

    suspend fun put(query: String, result: CachedSearchResult) {
        mutex.withLock { cache[query] = result }
    }

    data class CachedSearchResult(
        val places: List<Place>,
        val timestamp: Long = System.currentTimeMillis(),
        val ttlMs: Long = 5 * 60 * 1000  // 5 min
    ) {
        fun isExpired() = System.currentTimeMillis() > timestamp + ttlMs
    }
}
```

---

## 4. Network Layer

### HTTP APIs via Retrofit

Search and routing use plain HTTPS through Retrofit and OkHttp. The Mapbox Search SDK and Navigation SDK are not used.

```kotlin
interface SearchBoxApi {            // search/searchbox/v1/{suggest, retrieve, category, reverse}
    suspend fun suggest(query, sessionToken, proximity, language): SuggestResponseDto
    suspend fun retrieve(mapboxId, sessionToken, language): FeatureCollectionDto
    suspend fun category(canonicalId, proximity, language, limit): FeatureCollectionDto
    suspend fun reverse(lng, lat, language): FeatureCollectionDto
}

interface DirectionsApi {           // directions/v5/mapbox/{profile}/{coordinates}
    suspend fun getRoutes(profile, coordinates, alternatives, annotations, exclude, language): DirectionsResponseDto
}
```

`HttpClientFactory` adds the access token as a query parameter through an interceptor and redacts it from debug logging (`TokenRedactor`). Release builds never log HTTP.

### Repository Implementation

| Class | Responsibility |
|-------|----------------|
| `MapboxPlaceSearchRepository` | suggest / retrieve / category / reverse; maps DTOs to `Place`; passes results through `SearchResultSanitizer` (drops placeholder POIs); language from `apiLanguageFor(locale)` (hi or en) |
| `MapboxDirectionsRepository` | validates waypoint count (2 to 25), maps `RouteOptions` to `exclude`, decodes polyline6, maps API error codes; `language` comes from `InstructionLanguage.apiTagFor(appLocale)` |
| `DirectionsMapper` | DTO to domain `Route` with steps, legs and congestion |

Directions v5 does not support Hindi or Odia, so those locales fall back to `en`; `TextToSpeechVoiceGuide` resolves its locale through the same `InstructionLanguage` object so voice and text always agree.

---

## 5. State Management (MVVM)

### ViewModel + UiState

```kotlin
// ExploreViewModel: explore screen state
data class ExploreUiState(
    val mapReady: Boolean = false,
    val userLocation: LatLng? = null,
    val searchResults: List<Place> = emptyList(),
    val selectedPlace: Place? = null,
    val isSearching: Boolean = false,
    val searchError: AppError? = null,
    val categoryResults: Map<String, List<Place>> = emptyMap(),  // category -> results
    val nearestEssentials: Map<String, Place?> = emptyMap(),     // category -> nearest
    val recentSearches: List<String> = emptyList(),
    val isOnline: Boolean = true,
    val locationPermissionState: PermissionState = PermissionState.DENIED,
    val mapStyle: MapStyle = MapStyle.STANDARD
)

class ExploreViewModel(
    private val placeSearchUseCase: SearchPlaceUseCase,
    private val discoveryUseCase: DiscoveryCategoryUseCase,
    private val locationRepository: LocationRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Monitor connectivity
            connectivityRepository.connectionState.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }

        viewModelScope.launch {
            // Monitor location updates
            locationRepository.locationUpdates.collect { location ->
                _uiState.update { it.copy(userLocation = location) }
            }
        }

        viewModelScope.launch {
            // Monitor preferences (theme, style)
            preferences.mapStyle.collect { style ->
                _uiState.update { it.copy(mapStyle = style) }
            }
        }

        loadRecentSearches()
    }

    fun onSearchQueryChanged(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        _uiState.update { it.copy(isSearching = true) }

        viewModelScope.launch {
            val result = placeSearchUseCase.searchAutocomplete(query)
            _uiState.update {
                it.copy(
                    searchResults = (result as? Result.Success)?.data ?: emptyList(),
                    searchError = (result as? Result.Error)?.error,
                    isSearching = false
                )
            }
        }
    }

    fun onCategoryChipTapped(category: String) {
        val currentLocation = _uiState.value.userLocation ?: return

        viewModelScope.launch {
            val result = discoveryUseCase.discoverNearby(category, currentLocation)
            _uiState.update {
                it.copy(
                    categoryResults = it.categoryResults + (category to ((result as? Result.Success)?.data ?: emptyList())),
                    searchError = (result as? Result.Error)?.error
                )
            }
        }
    }

    fun onPlaceSelected(place: Place) {
        _uiState.update { it.copy(selectedPlace = place) }
    }

    fun onMapReady() {
        _uiState.update { it.copy(mapReady = true) }
    }

    private fun loadRecentSearches() {
        // Load from Room
        // ...
    }
}

// In the composable:
fun ExploreScreen(viewModel: ExploreViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = { RaahiBottomBar(...) },
        content = { paddingValues ->
            RaahiMap(
                modifier = Modifier.padding(paddingValues),
                isOnline = uiState.isOnline,
                userLocation = uiState.userLocation,
                // ...
            )
            // Search bar, category chips, search results
            // ...
        }
    )
}
```

---

## 6. Offline Maps Architecture

### Download flow (as built)

```
OfflineRegionPickerViewModel --start--> DownloadCoordinator --collects--> OfflineMapRepository.download(region)
                                                                              |
                                              (main dispatcher) OfflineManager.loadStylePack(STANDARD)
                                                                              |
                                              TileStore.loadTileRegion(id, geometry + tileset descriptor)
                                                                              |
                                           Room offline_regions row updated: status, progress, sizeBytes
```

- `DownloadCoordinator` owns download jobs on an application-scoped `CoroutineScope`, exposes `progress: StateFlow<Map<String, DownloadProgress>>`, and survives screen changes. There is no WorkManager: downloads run while the process is alive and a region interrupted by process death is reconciled to `Partial` on the next Offline screen open, then resumed with the Resume action.
- `MapboxOfflineMapRepository` is the only user of `TileStore` and `OfflineManager`. Style pack accounts for the first 30 percent of progress, tiles for the remaining 70 percent. `sizeBytes` is updated from `completedResourceSize` during the download and is final on completion.
- Mapbox invokes load callbacks on the thread's Looper, so every Mapbox call is made on `Dispatchers.Main`.
- `StorageProbe` (`DeviceStorageProbe`) reports free space; a download is refused when free space is below the estimated size times a safety factor. `TileEstimator` (pure Kotlin) produces the pre-download estimate.
- `reconcile()` compares Room rows with `TileStore.getAllTileRegions` and downgrades rows whose tiles disappeared.
- Wi-Fi-only downloads are enforced from preferences; on metered networks the UI asks for confirmation (`MeteredDownloadGuard`).

---

## 7. Error Handling

### AppError Sealed Type

```kotlin
sealed class AppError : Serializable {
    abstract val message: String

    // Network errors
    data class NetworkError(override val message: String) : AppError()
    data class HttpError(val code: Int, override val message: String) : AppError()

    // Mapbox-specific
    data class SearchFailed(override val message: String) : AppError()
    data class RoutingFailed(val code: String = "NoRoute", override val message: String = "No route found") : AppError()
    data class TokenInvalid(override val message: String = "Mapbox token is invalid or unauthorized") : AppError()
    data class TokenMissing(override val message: String = "Mapbox token is not configured") : AppError()

    // Offline/storage
    data class OfflineUnavailable(override val message: String = "Offline region not available") : AppError()
    data class InsufficientStorage(val requiredBytes: Long, override val message: String) : AppError()
    data class EstimationFailed(override val message: String) : AppError()

    // Location
    data class LocationPermissionDenied(override val message: String = "Location permission denied") : AppError()
    data class LocationUnavailable(override val message: String = "Location service unavailable") : AppError()

    // Trip/routing
    data class InvalidTrip(override val message: String) : AppError()
    data class NoAlternatives(override val message: String = "No alternative routes available") : AppError()

    // Generic
    data class Unknown(override val message: String = "An unknown error occurred") : AppError()
}

// ErrorMapper: AppError → localized user message
class ErrorMapper(private val context: Context, private val preferences: AppPreferences) {

    suspend fun toUserMessage(error: AppError): String = when (error) {
        is AppError.NetworkError -> context.getString(R.string.error_network)
        is AppError.SearchFailed -> context.getString(R.string.error_search_failed)
        is AppError.RoutingFailed -> context.getString(R.string.error_no_route)
        is AppError.LocationPermissionDenied -> context.getString(R.string.error_location_denied)
        is AppError.TokenInvalid -> context.getString(R.string.error_mapbox_token_invalid)
        is AppError.InsufficientStorage -> context.getString(
            R.string.error_insufficient_storage,
            formatBytes(error.requiredBytes)
        )
        // ... etc
        else -> error.message
    }

    fun toUserAction(error: AppError): UserAction? = when (error) {
        is AppError.LocationPermissionDenied -> UserAction.RequestPermission(Permission.LOCATION)
        is AppError.TokenMissing, is AppError.TokenInvalid -> UserAction.ShowDeveloperPanel
        is AppError.NetworkError -> UserAction.Retry
        is AppError.InsufficientStorage -> UserAction.FreeStorage
        else -> null
    }
}

sealed class UserAction {
    data class RequestPermission(val permission: Permission) : UserAction()
    object Retry : UserAction()
    object ShowDeveloperPanel : UserAction()
    object FreeStorage : UserAction()
}

// Usage in ViewModel:
viewModelScope.launch {
    val result = placeSearchUseCase.search(query)
    if (result is Result.Error) {
        val userMessage = errorMapper.toUserMessage(result.error)
        val action = errorMapper.toUserAction(result.error)
        _uiState.update {
            it.copy(
                searchError = userMessage,
                suggestedAction = action
            )
        }
    }
}
```

---

## 8. Connectivity and Offline Strategy

### ConnectivityMonitor

```kotlin
interface ConnectivityRepository {
    val connectionState: Flow<Boolean>  // true = online, false = offline/limited
    suspend fun isOnline(): Boolean
}

class ConnectivityRepositoryImpl(context: Context) : ConnectivityRepository {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val connectionState: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            override fun onLost(network: Network) {
                trySend(false)
            }
            override fun onCapabilitiesChanged(network: Network, cap: NetworkCapabilities) {
                trySend(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback
        )

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.stateIn(scope = GlobalScope, started = SharingStarted.Lazily, initialValue = isOnline())

    override suspend fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val cap = connectivityManager.getNetworkCapabilities(network) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

// Usage:
class ExploreViewModel(..., private val connectivity: ConnectivityRepository) : ViewModel() {
    init {
        viewModelScope.launch {
            connectivity.connectionState.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        if (!_uiState.value.isOnline) {
            // Fall back to saved places or cached searches
            showLocalResults(query)
            return
        }
        // Proceed with online search
    }
}
```

---

## 9. Testing Strategy

### Unit Tests (Pure Kotlin)

```kotlin
class GuidanceEngineTest {
    private val engine = GuidanceEngine()

    @Test
    fun `calculateRemainingDistance returns zero when on final step`() {
        val steps = listOf(
            createDirectionStep(distance = 100.0),
            createDirectionStep(distance = 50.0)
        )
        val remaining = engine.calculateRemainingDistance(stepIndex = 1, steps = steps)
        assertEquals(50.0, remaining)
    }

    @Test
    fun `isOffRoute returns true when distance exceeds threshold`() {
        val routeGeometry = listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0))
        val userLocation = LatLng(0.5, 0.5)  // ~78 km from route, for this lat/lng
        val isOffRoute = engine.isOffRoute(userLocation, routeGeometry, threshold = 50.0)
        assertTrue(isOffRoute)
    }
}

class TileEstimatorTest {
    private val estimator = TileEstimator()

    @Test
    fun `estimateRegionSize returns reasonable bounds for Delhi`() {
        val delhiPolygon = createPolygonForDelhi()
        val estimate = estimator.estimate(delhiPolygon, minZoom = 11, maxZoom = 15)
        assertTrue(estimate.tileCount > 100)
        assertTrue(estimate.estimatedBytes < 500_000_000)  // < 500 MB
    }
}
```

### ViewModel Tests

```kotlin
@RunWith(RobolectricTestRunner::class)
class ExploreViewModelTest {
    private lateinit var viewModel: ExploreViewModel
    private val fakeSearchUseCase = FakeSearchPlaceUseCase()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        viewModel = ExploreViewModel(
            placeSearchUseCase = fakeSearchUseCase,
            discoveryUseCase = FakeDiscoveryUseCase(),
            locationRepository = FakeLocationRepository(),
            connectivityRepository = FakeConnectivityRepository(isOnline = true),
            preferences = FakeAppPreferences()
        )
    }

    @Test
    fun `onSearchQueryChanged updates uiState with results`() = runTest {
        fakeSearchUseCase.setResults(listOf(createPlace(name = "Fuel Station")))

        viewModel.onSearchQueryChanged("fuel")

        advanceUntilIdle()
        val uiState = viewModel.uiState.value
        assertEquals(1, uiState.searchResults.size)
        assertEquals("Fuel Station", uiState.searchResults[0].name)
    }

    @Test
    fun `onSearchQueryChanged when offline falls back to saved places`() = runTest {
        val offlineViewModel = ExploreViewModel(
            ...,
            connectivityRepository = FakeConnectivityRepository(isOnline = false)
        )
        offlineViewModel.onSearchQueryChanged("hospital")

        advanceUntilIdle()
        val uiState = offlineViewModel.uiState.value
        // Should show saved places or cached results instead
        assertTrue(uiState.searchResults.isEmpty())  // or populated from local DB
    }
}
```

### Integration Tests (Android Instrumented)

```kotlin
@RunWith(AndroidJUnit4::class)
class TripPlannerIntegrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: RaahiDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RaahiDatabase::class.java).build()
    }

    @Test
    fun `user can create, save, and load a trip`() {
        // Create trip
        val trip = Trip(
            name = "Delhi to Agra",
            origin = Stop(name = "Delhi", lat = 28.6, lng = 77.2),
            stops = listOf(Stop(name = "Agra", lat = 27.2, lng = 78.0))
        )

        // Save to Room
        runBlocking {
            val tripDao = database.tripDao()
            tripDao.insertTrip(trip.toEntity())

            // Load and verify
            val loaded = tripDao.getTripById(trip.id)
            assertNotNull(loaded)
            assertEquals("Delhi to Agra", loaded?.name)
        }
    }
}
```

---

## 10. Dependency Injection (Manual, No Hilt)

### AppContainer

`AppContainer` is an interface (so tests can supply fakes); `DefaultAppContainer` builds everything lazily.

```kotlin
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
    val activeRouteStore: ActiveRouteStore      // routes handed from RoutePreview to Guidance
    val voiceGuide: VoiceGuide                  // TextToSpeechVoiceGuide
    val downloadCoordinator: DownloadCoordinator
    val storageProbe: StorageProbe              // DeviceStorageProbe
}
```

The container is exposed to Compose through `LocalAppContainer`. ViewModels are created with `appViewModel { ViewModel(it.repository, ...) }` or `appStatefulViewModel { container, savedStateHandle -> ... }`.

### Navigation

`RaahiNavHost` uses type-safe Navigation Compose routes (`Routes.kt`): Onboarding, Explore, Search, PlaceDetail, Nearby, Emergency, EmergencyContacts, Trips, TripEditor, TripDetail, RoutePreview, Guidance, Offline, OfflineRegionPicker, Saved, Profile, Settings, Language, About. Deep links: `raahi://place?lat=&lng=&name=` and `raahi://trip/{id}`; `RaahiApp` forwards `onNewIntent` to `NavController.handleDeepLink` so links work on a running app. Bottom-bar tab selection pops to the Explore root and does not restore stale nested stacks.

---

## 11. Architecture Decision Records (ADRs)

### ADR-001: Single Module Over Multi-Module

**Decision:** Use a single `app` module rather than split into `core`, `domain`, `data`, `ui` modules.

**Rationale:**
- Reduces build time and complexity for MVP.
- Package-by-layer organization still provides clean boundaries.
- Can refactor to modules later if a specific layer (e.g., offline map provider) needs to be swapped.

**Consequence:** Larger module; careful package discipline required.

---

### ADR-002: Manual DI Over Hilt

**Decision:** Use manual constructor DI via AppContainer instead of Hilt.

**Rationale:**
- Smaller build footprint (no Hilt annotation processor).
- Faster compilation.
- AppContainer is explicit and easy to unit test (just create different instances).
- Team familiar with constructor injection.

**Consequence:** More boilerplate in AppContainer; no automatic dependency resolution.

---

### ADR-003: HTTP Search Box API and Directions v5 via Retrofit

**Decision:** Use the Mapbox Search Box HTTP API and Directions v5 through Retrofit and OkHttp. No Mapbox Search SDK, no Navigation SDK.

**Rationale:**
- Full control over request parameters, language and caching; no SDK version churn.
- Responses are plain JSON, easy to fake in unit tests with MockWebServer.
- Keeps `com.mapbox` imports confined to `map/`, `offline/`, `core/config` and the Application class.

**Consequence:** Search and routing are online-only; offline mode relies on cached nearby results, saved places and downloaded map tiles. Session tokens for Search Box billing are managed by the app.

---

### ADR-004: Custom Guidance Engine (MVP) Over Navigation SDK

**Decision:** Implement custom on-device guidance in MVP; defer Navigation SDK to v1.1.

**Rationale:**
- MVP ships faster without Nav SDK complexity.
- Custom engine is lightweight and testable.
- Nav SDK v3 is expensive (metered billing); validate value first.
- Can swap to Nav SDK in v1.1 without major refactor (same domain model).

**Consequence:** Implement maneuver, ETA, reroute logic manually; no Android Auto in MVP.

---

### ADR-005: No Colors Hardcoded; All Tokens

**Decision:** All colors come from Material 3 theme tokens (MaterialTheme.colorScheme.*); zero literals in code.

**Rationale:**
- Single source of truth for theme consistency.
- Light/dark mode switching is automatic.
- Annotation, line, and cluster colors use `toArgb()` from tokens.

**Consequence:** No direct use of hex values anywhere; slightly more boilerplate (e.g., MaterialTheme.colorScheme.primary).

---

## 12. Deployment and Release

### Build Configuration

```kotlin
// build.gradle.kts
android {
    compileSdk = 35
    minSdk = 26  // Android 8.0 (Mapbox requirement)
    targetSdk = 35

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    buildTypes {
        debug {
            debuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// local.properties (gitignored)
# MAPBOX_ACCESS_TOKEN=pk.your_token_here
```

### ProGuard Rules

```
# Mapbox
-keep class com.mapbox.** { *; }
-keep interface com.mapbox.** { *; }
-dontwarn com.mapbox.**

# Retrofit
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep, includedescriptorclasses class com.example.raahi.**$$serializer { *; }
-keepclassmembers class com.example.raahi.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.raahi.** {
    kotlinx.serialization.KSerializer serializer(...);
}
```

---

**Document Owner:** Architecture Team  
**Last Updated:** October 2026  
**Next Review:** Q1 2027
