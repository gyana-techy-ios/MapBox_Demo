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

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── kotlin/com/example/raahi/
│   │   ├── Application.kt                   # App initialization, DI
│   │   ├── AppContainer.kt                  # Manual DI (no Hilt)
│   │   │
│   │   ├── core/
│   │   │   ├── config/                      # Build config, constants
│   │   │   │   └── Config.kt
│   │   │   ├── di/                          # Service locator / DI
│   │   │   │   └── AppContainer.kt          # Single DI entry point
│   │   │   ├── network/                     # HTTP clients
│   │   │   │   ├── OkHttpFactory.kt
│   │   │   │   ├── RetrofitFactory.kt
│   │   │   │   └── SearchBoxClient.kt       # HTTP fallback for Search
│   │   │   ├── result/                      # Result<T> sealed type
│   │   │   │   └── Result.kt
│   │   │   ├── error/                       # AppError, ErrorMapper
│   │   │   │   ├── AppError.kt
│   │   │   │   └── ErrorMapper.kt
│   │   │   ├── location/                    # Location utilities
│   │   │   │   └── PermissionUtils.kt
│   │   │   └── util/                        # General utilities
│   │   │       └── Extensions.kt
│   │   │
│   │   ├── domain/                          # Business logic
│   │   │   ├── model/                       # Domain models (immutable)
│   │   │   │   ├── Trip.kt
│   │   │   │   ├── Stop.kt
│   │   │   │   ├── Place.kt
│   │   │   │   ├── SavedPlace.kt
│   │   │   │   ├── Route.kt
│   │   │   │   ├── EmergencyContact.kt
│   │   │   │   ├── OfflineRegion.kt
│   │   │   │   └── GuidanceState.kt
│   │   │   ├── repository/                  # Interface (contract)
│   │   │   │   ├── PlaceSearchRepository.kt
│   │   │   │   ├── DirectionsRepository.kt
│   │   │   │   ├── TripRepository.kt
│   │   │   │   ├── SavedPlaceRepository.kt
│   │   │   │   ├── OfflineRegionRepository.kt
│   │   │   │   ├── LocationRepository.kt
│   │   │   │   ├── ConnectivityRepository.kt
│   │   │   │   └── EmergencyContactRepository.kt
│   │   │   └── usecase/                     # Use case orchestration
│   │   │       ├── SearchPlaceUseCase.kt
│   │   │       ├── PlanTripUseCase.kt
│   │   │       ├── CompareRoutesUseCase.kt
│   │   │       ├── StartGuidanceUseCase.kt
│   │   │       ├── DownloadOfflineRegionUseCase.kt
│   │   │       ├── SavePlaceUseCase.kt
│   │   │       ├── CallEmergencyUseCase.kt
│   │   │       └── ...
│   │   │
│   │   ├── data/                            # Data layer
│   │   │   ├── remote/                      # Network
│   │   │   │   ├── api/
│   │   │   │   │   ├── SearchBoxApi.kt      # Retrofit interface
│   │   │   │   │   ├── DirectionsApi.kt
│   │   │   │   │   └── StaticImagesApi.kt
│   │   │   │   └── model/                   # Network DTOs
│   │   │   │       ├── SearchBoxDto.kt
│   │   │   │       └── DirectionsDto.kt
│   │   │   ├── local/                       # Room persistence
│   │   │   │   ├── database/
│   │   │   │   │   ├── RaahiDatabase.kt
│   │   │   │   │   └── migration/
│   │   │   │   │       └── Migration_1_2.kt
│   │   │   │   ├── entity/                  # Room @Entity
│   │   │   │   │   ├── TripEntity.kt
│   │   │   │   │   ├── StopEntity.kt
│   │   │   │   │   ├── SavedPlaceEntity.kt
│   │   │   │   │   ├── EmergencyContactEntity.kt
│   │   │   │   │   ├── OfflineRegionMetadataEntity.kt
│   │   │   │   │   └── RecentSearchEntity.kt
│   │   │   │   ├── dao/                     # Room @Dao
│   │   │   │   │   ├── TripDao.kt
│   │   │   │   │   ├── SavedPlaceDao.kt
│   │   │   │   │   ├── OfflineRegionMetadataDao.kt
│   │   │   │   │   ├── RecentSearchDao.kt
│   │   │   │   │   └── EmergencyContactDao.kt
│   │   │   │   ├── preferences/             # DataStore
│   │   │   │   │   └── AppPreferences.kt
│   │   │   │   └── cache/                   # In-memory
│   │   │   │       └── PlaceCache.kt
│   │   │   └── repository/                  # Repository implementations
│   │   │       ├── PlaceSearchRepositoryImpl.kt
│   │   │       ├── DirectionsRepositoryImpl.kt
│   │   │       ├── TripRepositoryImpl.kt
│   │   │       ├── SavedPlaceRepositoryImpl.kt
│   │   │       ├── ConnectivityRepositoryImpl.kt
│   │   │       └── ...
│   │   │
│   │   ├── location/                        # Location abstraction
│   │   │   ├── LocationRepository.kt
│   │   │   ├── LocationRepositoryImpl.kt
│   │   │   └── PermissionState.kt
│   │   │
│   │   ├── offline/                         # Offline maps management
│   │   │   ├── OfflineRegionRepository.kt
│   │   │   ├── MapboxOfflineMapRepository.kt
│   │   │   ├── TileEstimator.kt             # Compute tile size
│   │   │   ├── OfflineDownloadManager.kt    # WorkManager task
│   │   │   └── OfflineMapState.kt
│   │   │
│   │   ├── map/                             # Mapbox integration
│   │   │   ├── RaahiMap.kt                  # Main composable
│   │   │   ├── RaahiMapState.kt             # State holder
│   │   │   ├── MapboxStyler.kt              # Style config (light/dark)
│   │   │   ├── ClusteringManager.kt         # Annotation clusters
│   │   │   ├── RoutePolylineManager.kt      # Draw routes
│   │   │   └── LocationPuckManager.kt
│   │   │
│   │   ├── guidance/                        # Custom guidance engine
│   │   │   ├── GuidanceEngine.kt            # Step-by-step maneuvers
│   │   │   ├── GuidanceState.kt             # Maneuver, ETA, distance
│   │   │   ├── OffRouteDetector.kt          # >10m, >5s logic
│   │   │   ├── Polyline6Decoder.kt          # Decode Directions polyline6
│   │   │   ├── TtsVoiceGuidance.kt          # Android TextToSpeech
│   │   │   └── ManeuverFormatter.kt
│   │   │
│   │   ├── connectivity/                    # Network state
│   │   │   ├── ConnectivityMonitor.kt       # Online/Limited/Offline
│   │   │   └── ConnectivityRepositoryImpl.kt
│   │   │
│   │   ├── ui/                              # UI theme + components
│   │   │   ├── theme/
│   │   │   │   ├── Color.kt                 # Design tokens (NO hardcoded colors)
│   │   │   │   ├── Type.kt                  # Typography
│   │   │   │   ├── Theme.kt                 # CompositionLocal overrides
│   │   │   │   └── Shapes.kt
│   │   │   ├── components/                  # Reusable composables
│   │   │   │   ├── RaahiSearchBar.kt
│   │   │   │   ├── CategoryChipRow.kt
│   │   │   │   ├── PlaceCard.kt
│   │   │   │   ├── PlaceListItem.kt
│   │   │   │   ├── RouteComparisonCard.kt
│   │   │   │   ├── EmptyState.kt
│   │   │   │   ├── ErrorState.kt
│   │   │   │   ├── ConnectivityBanner.kt
│   │   │   │   ├── LoadingSkeletonList.kt
│   │   │   │   ├── RaahiTopBar.kt
│   │   │   │   ├── ConfirmDialog.kt
│   │   │   │   ├── PermissionRationale.kt
│   │   │   │   ├── MapUnavailablePanel.kt
│   │   │   │   └── MapButton.kt (locate-me, etc.)
│   │   │   └── util/
│   │   │       └── AccessibilityExt.kt
│   │   │
│   │   ├── navigation/                      # Navigation routing
│   │   │   ├── NavGraph.kt                  # Compose navigation
│   │   │   └── RaahiNavHost.kt
│   │   │
│   │   └── feature/                         # Feature screens
│   │       ├── onboarding/
│   │       │   ├── OnboardingScreen.kt
│   │       │   ├── OnboardingViewModel.kt
│   │       │   └── OnboardingUiState.kt
│   │       ├── explore/
│   │       │   ├── ExploreScreen.kt
│   │       │   ├── ExploreViewModel.kt
│   │       │   ├── ExploreUiState.kt
│   │       │   ├── SearchScreen.kt
│   │       │   ├── PlaceDetailScreen.kt
│   │       │   └── NearbyScreen.kt
│   │       ├── trips/
│   │       │   ├── TripsScreen.kt
│   │       │   ├── TripEditorScreen.kt
│   │       │   ├── TripEditorViewModel.kt
│   │       │   ├── TripDetailScreen.kt
│   │       │   ├── RoutePreviewScreen.kt
│   │       │   └── RouteComparisonViewModel.kt
│   │       ├── guidance/
│   │       │   ├── GuidanceScreen.kt
│   │       │   ├── GuidanceViewModel.kt
│   │       │   └── GuidanceUiState.kt
│   │       ├── offline/
│   │       │   ├── OfflineScreen.kt
│   │       │   ├── OfflineRegionPickerScreen.kt
│   │       │   ├── OfflineViewModel.kt
│   │       │   └── OfflineUiState.kt
│   │       ├── saved/
│   │       │   ├── SavedPlacesScreen.kt
│   │       │   ├── SavedPlacesViewModel.kt
│   │       │   └── ParkedCarScreen.kt
│   │       ├── emergency/
│   │       │   ├── EmergencyScreen.kt
│   │       │   ├── EmergencyViewModel.kt
│   │       │   ├── EmergencyContactsScreen.kt
│   │       │   └── EmergencySosActionHandler.kt
│   │       ├── profile/
│   │       │   ├── ProfileScreen.kt
│   │       │   ├── SettingsScreen.kt
│   │       │   ├── LanguageScreen.kt
│   │       │   ├── AboutScreen.kt
│   │       │   └── PreferencesViewModel.kt
│   │       └── ...
│   │
│   └── res/
│       ├── values/
│       │   ├── colors.xml              # NOT USED; use theme tokens
│       │   ├── strings.xml             # en_US
│       │   ├── strings-hi.xml          # Hindi
│       │   ├── strings-or.xml          # Odia
│       │   ├── dimens.xml              # NOT USED; use spacing tokens
│       │   ├── styles.xml              # Material 3 theme
│       │   └── locales_config.xml
│       ├── drawable/
│       │   ├── ic_fuel.xml             # Vector icons
│       │   ├── ic_hospital.xml
│       │   └── ...
│       ├── mipmap/
│       │   └── ic_launcher.png
│       └── raw/
│           └── (no embedded data files)
│
├── build.gradle.kts                  # Single module
├── proguard-rules.pro
└── local.properties                  # .gitignore: MAPBOX_ACCESS_TOKEN
```

### Key Design Decisions

1. **No Hilt:** Manual DI via AppContainer in Application.onCreate(). Reduces build time, makes test containers trivial.
2. **No separate `lib` modules:** Single `app` module keeps the codebase shallow; refactor to modules only if specific layers need to be swapped (e.g., offline map provider).
3. **Package-by-layer, not package-by-feature:** top-level organization by architectural layer (domain, data, ui) makes dependency rules clear; screens organized under `feature/`.
4. **Offline layer isolation:** `MapboxOfflineMapRepository` is the only place that touches TileStore; all other code sees the domain-level `OfflineRegion` model.
5. **Guidance engine isolation:** `GuidanceEngine` is pure Kotlin (no Android deps except for GPS/time); testable in JVM tests.

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
    val photoUri: String? = null,    // local file URI
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
    val photoUri: String? = null,
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

```kotlin
// SearchBoxApi: Mapbox Search Box API
interface SearchBoxApi {
    @GET("search/searchbox/v1/suggest")
    suspend fun suggest(
        @Query("q") query: String,
        @Query("session_token") sessionToken: String,
        @Query("proximity") proximity: String? = null,  // lng,lat
        @Query("limit") limit: Int = 5,
        @Query("access_token") token: String
    ): SearchBoxSuggestResponse

    @GET("search/searchbox/v1/retrieve/{mapbox_id}")
    suspend fun retrieve(
        @Path("mapbox_id") mapboxId: String,
        @Query("session_token") sessionToken: String,
        @Query("access_token") token: String
    ): SearchBoxRetrieveResponse

    @GET("search/searchbox/v1/category/{canonical_id}")
    suspend fun categorySearch(
        @Path("canonical_id") categoryId: String,
        @Query("proximity") proximity: String? = null,
        @Query("limit") limit: Int = 25,
        @Query("access_token") token: String
    ): SearchBoxCategoryResponse
}

// DirectionsApi: Mapbox Directions v5
interface DirectionsApi {
    @GET("directions/v5/mapbox/{profile}/{coordinates}")
    suspend fun getDirections(
        @Path("profile") profile: String,     // driving-traffic, driving, walking, cycling
        @Path("coordinates") coordinates: String,  // lng,lat;lng,lat;...
        @Query("alternatives") alternatives: Boolean = true,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "polyline6",
        @Query("steps") steps: Boolean = true,
        @Query("annotations") annotations: String = "duration,distance,congestion_numeric",
        @Query("exclude") exclude: String? = null,     // toll,motorway,ferry
        @Query("access_token") token: String
    ): DirectionsResponse

    @POST("directions/v5/mapbox/{profile}")
    suspend fun getDirectionsPost(
        @Path("profile") profile: String,
        @Body request: DirectionsPostRequest,
        @Query("access_token") token: String
    ): DirectionsResponse
}

// StaticImagesApi: Mapbox Static Images
interface StaticImagesApi {
    @GET("styles/v1/{user}/{style_id}/static/{lon},{lat},{zoom}/{width}x{height}")
    suspend fun getStaticImage(
        @Path("user") user: String,
        @Path("style_id") styleId: String,
        @Path("lon") lon: Double,
        @Path("lat") lat: Double,
        @Path("zoom") zoom: Int,
        @Path("width") width: Int,
        @Path("height") height: Int,
        @Query("@2x") retina: String = "@2x",  // for 2x density
        @Query("access_token") token: String
    ): ResponseBody   // image bytes
}
```

### Repository Implementation (Data Layer)

```kotlin
class PlaceSearchRepositoryImpl(
    private val searchBoxApi: SearchBoxApi,
    private val searchBoxSdk: PlaceAutocomplete,
    private val discover: Discover,
    private val searchEngine: SearchEngine,
    private val placeCache: PlaceCache,
    private val appPreferences: AppPreferences,
    private val token: String
) : PlaceSearchRepository {

    override suspend fun searchAutocomplete(query: String): Result<List<Place>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.Success(emptyList())

        // Check cache first
        placeCache.get(query)?.let { return@withContext Result.Success(it.places) }

        return@withContext try {
            // Use Search SDK (preferred)
            val response = searchBoxSdk.suggestions(query)
            when {
                response.isValue -> {
                    val places = response.value!!.map { it.toPlace() }
                    placeCache.put(query, PlaceCache.CachedSearchResult(places))
                    Result.Success(places)
                }
                else -> Result.Error(AppError.SearchFailed(response.error?.message ?: "Unknown error"))
            }
        } catch (e: Exception) {
            // Fallback: HTTP Search Box API
            try {
                val httpResult = searchBoxApi.suggest(
                    query = query,
                    sessionToken = UUID.randomUUID().toString(),
                    limit = 5,
                    token = token
                )
                val places = httpResult.suggestions.map { it.toPlace() }
                placeCache.put(query, PlaceCache.CachedSearchResult(places))
                Result.Success(places)
            } catch (e: Exception) {
                Result.Error(AppError.NetworkError("Search failed: ${e.message}"))
            }
        }
    }

    override suspend fun discoverCategory(category: String, location: LatLng, radius: Double = 50.0): Result<List<Place>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val boundingBox = BoundingBox.fromPoint(location, radiusKm = radius)
            val response = discover.search(
                DiscoverQuery.Category(category),
                region = boundingBox
            )
            when {
                response.isValue -> Result.Success(response.value!!.map { it.toPlace() })
                else -> Result.Error(AppError.SearchFailed("Category search failed"))
            }
        } catch (e: Exception) {
            Result.Error(AppError.NetworkError(e.message ?: "Category search error"))
        }
    }

    override suspend fun reverseGeocode(location: LatLng): Result<Place> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = searchEngine.search(
                ReverseGeoOptions(center = Point.fromLngLat(location.lng, location.lat), limit = 1)
            ) { response ->
                // callback; this is callback-based, not suspend
            }
            // Note: the Search SDK's reverse geocode is callback-based; wrap in suspendCancellableCoroutine
            Result.Success(Place.UNKNOWN)  // simplified
        } catch (e: Exception) {
            Result.Error(AppError.NetworkError(e.message ?: "Reverse geocode failed"))
        }
    }
}

class DirectionsRepositoryImpl(
    private val directionsApi: DirectionsApi,
    private val token: String
) : DirectionsRepository {

    override suspend fun getDirections(
        origin: LatLng,
        destination: LatLng,
        stops: List<Stop>,
        profile: RouteProfile,
        alternatives: Boolean,
        exclude: Set<String> = emptySet()
    ): Result<List<Route>> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Construct coordinates: origin; stops; destination
            val coords = listOf(origin) + stops.map { LatLng(it.lat, it.lng) } + destination
            val coordString = coords.joinToString(";") { "${it.lng},${it.lat}" }
            val excludeString = if (exclude.isNotEmpty()) exclude.joinToString(",") else null

            val response = directionsApi.getDirections(
                profile = profile.mapboxProfile,
                coordinates = coordString,
                alternatives = alternatives,
                exclude = excludeString,
                token = token
            )

            if (response.code != "Ok") {
                return@withContext Result.Error(AppError.RoutingFailed(response.code))
            }

            val routes = response.routes.map { routeDto ->
                Route(
                    geometry = Polyline6Decoder.decode(routeDto.geometry),
                    distance = routeDto.distance,
                    duration = routeDto.duration,
                    steps = routeDto.legs.flatMap { it.steps.map { s -> s.toDomain() } },
                    legs = routeDto.legs.map { it.toDomain() },
                    congestion = CongestionSummary.from(routeDto.annotations)
                )
            }
            Result.Success(routes)
        } catch (e: Exception) {
            Result.Error(AppError.NetworkError("Directions: ${e.message}"))
        }
    }
}
```

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

@HiltViewModel
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

### Offline Region Download Flow

```kotlin
// OfflineRegionRepository: interface
interface OfflineRegionRepository {
    suspend fun estimateRegionSize(geometry: String, minZoom: Int, maxZoom: Int): Result<TileSizeEstimate>
    suspend fun downloadRegion(id: String, name: String, geometry: String, minZoom: Int, maxZoom: Int): Flow<DownloadProgress>
    suspend fun listDownloadedRegions(): Result<List<OfflineRegion>>
    suspend fun deleteRegion(id: String): Result<Unit>
    suspend fun isRegionAvailable(id: String): Boolean
    fun getDownloadProgress(id: String): Flow<DownloadProgress>
}

// MapboxOfflineMapRepository: implementation
class MapboxOfflineMapRepository(
    private val offlineManager: OfflineManager,
    private val tileStore: TileStore,
    private val tileEstimator: TileEstimator,
    private val database: RaahiDatabase
) : OfflineRegionRepository {

    override suspend fun downloadRegion(
        id: String,
        name: String,
        geometry: String,
        minZoom: Int,
        maxZoom: Int
    ): Flow<DownloadProgress> = flow {
        val polygon = geometryStringToPolygon(geometry)  // WKT string to Polygon

        val descriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI(com.mapbox.maps.Style.STANDARD)
                .minZoom(minZoom)
                .maxZoom(maxZoom)
                .pixelRatio(Resources.getSystem().displayMetrics.density)
                .build()
        )

        val loadOptions = TileRegionLoadOptions.Builder()
            .geometry(polygon)
            .descriptors(listOf(descriptor))
            .metadata(Value.valueOf(mapOf("name" to Value.valueOf(name))))
            .acceptExpired(false)
            .build()

        val cancelable = tileStore.loadTileRegion(
            id, loadOptions,
            { progress ->
                // Progress callback on background thread
                emit(DownloadProgress(
                    regionId = id,
                    completedResourceCount = progress.completedResourceCount,
                    requiredResourceCount = progress.requiredResourceCount,
                    loadedBytes = progress.loadedBytes
                ))
            }
        ) { expected ->
            // Completion callback
            expected.fold(
                onValue = { region ->
                    // Success
                    viewModelScope.launch {
                        database.offlineRegionMetadataDao().insertMetadata(
                            OfflineRegionMetadataEntity(
                                id = id,
                                name = name,
                                geometryWkt = geometry,
                                minZoom = minZoom,
                                maxZoom = maxZoom,
                                sizeBytes = region.completedResourceSize.toLong(),
                                tileCount = region.completedResourceCount,
                                downloadedAt = System.currentTimeMillis(),
                                expiryAt = System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000
                            )
                        )
                        emit(DownloadProgress(regionId = id, isComplete = true))
                    }
                },
                onError = { error ->
                    // Error handling
                    emit(DownloadProgress(regionId = id, error = error.type.toString()))
                }
            )
        }

        // Ensure cancellation is tracked (for potential resume logic)
    }

    override suspend fun estimateRegionSize(geometry: String, minZoom: Int, maxZoom: Int): Result<TileSizeEstimate> = withContext(Dispatchers.Default) {
        return@withContext try {
            val polygon = geometryStringToPolygon(geometry)
            val estimate = tileEstimator.estimate(polygon, minZoom, maxZoom)
            Result.Success(estimate)
        } catch (e: Exception) {
            Result.Error(AppError.EstimationFailed(e.message ?: "Estimation error"))
        }
    }
}

// TileEstimator: pure Kotlin, no Android deps
class TileEstimator {
    fun estimate(geometry: Geometry, minZoom: Int, maxZoom: Int): TileSizeEstimate {
        var tileCount = 0
        for (zoom in minZoom..maxZoom) {
            tileCount += tilesInGeometry(geometry, zoom)
        }
        val avgBytes = 15_000  // avg tile size ~15 KB
        return TileSizeEstimate(
            tileCount = tileCount,
            estimatedBytes = tileCount * avgBytes
        )
    }

    private fun tilesInGeometry(geometry: Geometry, zoom: Int): Int {
        // Compute number of tiles for a given geometry and zoom level
        // (implementation depends on geometry type)
        return 0  // placeholder
    }
}

data class TileSizeEstimate(val tileCount: Int, val estimatedBytes: Long)
data class DownloadProgress(
    val regionId: String,
    val completedResourceCount: Int = 0,
    val requiredResourceCount: Int = 0,
    val loadedBytes: Long = 0L,
    val isComplete: Boolean = false,
    val error: String? = null
)
```

### WorkManager Task for Background Download

```kotlin
class OfflineDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val offlineRepository: OfflineRegionRepository by inject()

    override suspend fun doWork(): Result = try {
        val regionId = inputData.getString("region_id") ?: return Result.failure()
        val name = inputData.getString("name") ?: "Region"
        val geometry = inputData.getString("geometry") ?: return Result.failure()
        val minZoom = inputData.getInt("min_zoom", 11)
        val maxZoom = inputData.getInt("max_zoom", 15)

        offlineRepository.downloadRegion(
            id = regionId,
            name = name,
            geometry = geometry,
            minZoom = minZoom,
            maxZoom = maxZoom
        ).collect { progress ->
            // Update foreground notification with progress
            val notification = createProgressNotification(progress)
            setForeground(ForegroundInfo(1001, notification))

            // Also update progress to Room (for UI to observe)
            saveProgress(regionId, progress)
        }

        Result.success()
    } catch (e: Exception) {
        Result.retry()  // WorkManager will retry with exponential backoff
    }

    private fun createProgressNotification(progress: DownloadProgress): Notification {
        // Return a Notification with progress bar
        return NotificationCompat.Builder(applicationContext, "offline_download")
            .setContentTitle("Downloading ${progress.regionId}")
            .setProgress(
                progress.requiredResourceCount,
                progress.completedResourceCount,
                false
            )
            .setSmallIcon(R.drawable.ic_map)
            .build()
    }

    private suspend fun saveProgress(regionId: String, progress: DownloadProgress) {
        // Save to a DataStore or Room table so UI can observe and update
    }
}

// Enqueue from ViewModel:
fun downloadOfflineRegion(...) {
    val downloadRequest = OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
        .setInputData(
            workDataOf(
                "region_id" to "delhi-agra",
                "name" to "Delhi to Agra",
                "geometry" to "...",
                "min_zoom" to 11,
                "max_zoom" to 15
            )
        )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "offline_download_delhi-agra",
        ExistingWorkPolicy.REPLACE,
        downloadRequest
    )
}
```

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

```kotlin
class AppContainer(private val context: Context) {

    // Network
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val newRequest = if (!BuildConfig.DEBUG) {
                    request  // no logging in release
                } else {
                    // Add access token to all API requests
                    val url = request.url.newBuilder()
                        .addQueryParameter("access_token", BuildConfig.MAPBOX_ACCESS_TOKEN)
                        .build()
                    request.newBuilder().url(url).build()
                }
                chain.proceed(newRequest)
            }
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.mapbox.com/")
            .client(okHttpClient)
            .addConverterFactory(KotlinxSerializationConverterFactory())
            .build()
    }

    val searchBoxApi: SearchBoxApi by lazy { retrofit.create(SearchBoxApi::class.java) }
    val directionsApi: DirectionsApi by lazy { retrofit.create(DirectionsApi::class.java) }

    // Database
    val database: RaahiDatabase by lazy {
        Room.databaseBuilder(
            context,
            RaahiDatabase::class.java,
            "raahi.db"
        ).build()
    }

    // DataStore
    val appPreferences: AppPreferences by lazy {
        AppPreferences(
            PreferenceDataStoreFactory.create { File(context.cacheDir, "app_prefs.pb") }
        )
    }

    // Repositories
    val placeSearchRepository: PlaceSearchRepository by lazy {
        PlaceSearchRepositoryImpl(
            searchBoxApi = searchBoxApi,
            token = BuildConfig.MAPBOX_ACCESS_TOKEN,
            // ... other deps
        )
    }

    val directionsRepository: DirectionsRepository by lazy {
        DirectionsRepositoryImpl(
            directionsApi = directionsApi,
            token = BuildConfig.MAPBOX_ACCESS_TOKEN
        )
    }

    val tripRepository: TripRepository by lazy {
        TripRepositoryImpl(database.tripDao(), database.stopDao())
    }

    val savedPlaceRepository: SavedPlaceRepository by lazy {
        SavedPlaceRepositoryImpl(database.savedPlaceDao())
    }

    val offlineRegionRepository: OfflineRegionRepository by lazy {
        MapboxOfflineMapRepository(
            offlineManager = OfflineManager(),
            tileStore = TileStore.create(),
            database = database
        )
    }

    val locationRepository: LocationRepository by lazy {
        LocationRepositoryImpl(context)
    }

    val connectivityRepository: ConnectivityRepository by lazy {
        ConnectivityRepositoryImpl(context)
    }

    // Use Cases
    val searchPlaceUseCase: SearchPlaceUseCase by lazy {
        SearchPlaceUseCase(placeSearchRepository)
    }

    val planTripUseCase: PlanTripUseCase by lazy {
        PlanTripUseCase(tripRepository, directionsRepository)
    }

    // ... more use cases and services
}

// In Application:
class RaahiApplication : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()

        // Initialize Mapbox
        MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN

        appContainer = AppContainer(this)
    }
}

// In composables:
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as RaahiApplication).appContainer

        setContent {
            RaahiApp(appContainer = appContainer)
        }
    }
}

@Composable
fun RaahiApp(appContainer: AppContainer) {
    val tripRepository = remember { appContainer.tripRepository }
    val placeSearchRepository = remember { appContainer.placeSearchRepository }

    RaahiTheme {
        NavHost(...) {
            composable("explore") {
                val viewModel = remember {
                    ExploreViewModel(
                        placeSearchUseCase = appContainer.searchPlaceUseCase,
                        discoveryUseCase = DiscoveryCategoryUseCase(placeSearchRepository),
                        locationRepository = appContainer.locationRepository,
                        connectivityRepository = appContainer.connectivityRepository,
                        preferences = appContainer.appPreferences
                    )
                }
                ExploreScreen(viewModel = viewModel)
            }
            // ... other routes
        }
    }
}
```

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

### ADR-003: HTTP Search Box + Search SDK Hybrid

**Decision:** Use Mapbox Search SDK for primary path; HTTP Search Box API as fallback.

**Rationale:**
- Search SDK is typed and handles session state.
- HTTP fallback avoids SDK version churn and gives control.
- Both work offline (SDK handles offline batching; HTTP is online-only).

**Consequence:** Maintain two implementations; testing requires both paths.

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
