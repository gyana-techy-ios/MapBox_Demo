# Mapbox Capabilities Research (Android, Kotlin + Compose + Material 3)

Research date: 2026-10-08. Sources are official Mapbox docs, the Mapbox Maven repository (probed live with curl), and mapbox.com/pricing.
Items marked **[UNVERIFIED]** were not confirmed against an official page in this research pass and must be checked before relying on them.

---

## 1. Dependency / version matrix

Mapbox aligns minor versions across products: Maps `11.N` / Search `2.N` / Nav `3.N` (docs: keep sub-versions aligned, https://docs.mapbox.com/android/search/guides/install/ and https://docs.mapbox.com/android/navigation/guides/install/).
Verified in the POMs on the Mapbox Maven repo: `navigationcore:ui-maps-ndk27:3.32.0` depends on `com.mapbox.maps:android-ndk27:11.32.0`; Search `2.32.0` depends on `com.mapbox.common:common-ndk27:24.32.0`.

| Product | Gradle coordinate (16 KB page-size safe, "ndk27") | Version |
|---|---|---|
| Maps SDK | `com.mapbox.maps:android-ndk27` | **11.32.0** |
| Maps Compose extension | `com.mapbox.extension:maps-compose-ndk27` | **11.32.0** |
| Search - core (SearchEngine, reverse geocoding) | `com.mapbox.search:mapbox-search-android-ndk27` | **2.32.0** |
| Search - Place Autocomplete | `com.mapbox.search:place-autocomplete-ndk27` | 2.32.0 |
| Search - Discover (category/POI) | `com.mapbox.search:discover-ndk27` | 2.32.0 |
| Search - Address Autofill | `com.mapbox.search:autofill-ndk27` | 2.32.0 |
| Search - Offline | `com.mapbox.search:offline-ndk27` | 2.32.0 (premium, see section 8) |
| Search - UI widgets | `com.mapbox.search:mapbox-search-android-ui-ndk27` | 2.32.0 |
| Navigation SDK v3 core | `com.mapbox.navigationcore:android-ndk27` | **3.32.0** |
| Nav optional modules | `com.mapbox.navigationcore:{ui-maps,voice,tripdata,copilot,ui-components,navigation,ui-utils,android-auto-components}-ndk27` | 3.32.0 |
| Android Auto (Maps only) | `com.mapbox.extension:maps-androidauto-ndk27` | 11.32.0 (exists on Maven; only if Android Auto is in scope) |

Version caveats:
- The Maps install page snippet still shows 11.31.1, but the Compose guide, examples (tag v11.32.0) and Maven show 11.32.0. Maven has no 11.33+ at probe time. Use 11.32.0.
- The Search API reference landing page lists `2.33.0-rc.1` (pre-release). Stable on Maven is 2.32.0 (no 2.33.0).
- Nav docs show 3.32.0 on install/pricing/API links and 3.34.0 on one overview page. Maven has 3.32.0 and **no** 3.33.0/3.34.0 at probe time. Use 3.32.0.
- Non-ndk27 artifacts (`android`, `maps-compose`, ...) lack 16 KB page-size support. NDK 27 artifacts exist since Maps 11.7.0 / Search 2.14.0 / Nav 3.11.0. Use ndk27 everywhere (do not mix).
- Since Maps 11.8.0 the SDK pulls Google Play Services transitively (HTTP/3); it can be excluded.
- Compose extension POM depends on `compose-bom:2023.01.00` and `androidx.compose.material:material` (Material 2) transitively. It works in a Material 3 app but pin your own Compose BOM.

Gradle:
```kotlin
// settings.gradle.kts  - must be in dependencyResolutionManagement, NOT pluginManagement
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven { url = uri("https://api.mapbox.com/downloads/v2/releases/maven") }   // NO credentials
    }
}
// app/build.gradle.kts
buildFeatures { compose = true }
dependencies {
    implementation("com.mapbox.maps:android-ndk27:11.32.0")
    implementation("com.mapbox.extension:maps-compose-ndk27:11.32.0")
    implementation("com.mapbox.search:place-autocomplete-ndk27:2.32.0")    // optional
    implementation("com.mapbox.search:discover-ndk27:2.32.0")              // optional
    implementation("com.mapbox.search:mapbox-search-android-ndk27:2.32.0") // optional (reverse geocode)
    implementation("com.mapbox.navigationcore:android-ndk27:3.32.0")       // only if Nav SDK used
    implementation("com.mapbox.navigationcore:ui-maps-ndk27:3.32.0")
    implementation("com.mapbox.navigationcore:voice-ndk27:3.32.0")
    implementation("com.mapbox.navigationcore:tripdata-ndk27:3.32.0")
}
```
Sources: https://docs.mapbox.com/android/maps/guides/install/ , https://docs.mapbox.com/android/search/guides/install/ , https://docs.mapbox.com/android/navigation/guides/install/ , https://docs.mapbox.com/android/maps/guides/using-jetpack-compose/

---

## 2. Maven repository and authentication (VERIFIED with curl, 2026-10-08)

Repository: `https://api.mapbox.com/downloads/v2/releases/maven`

**No secret token (DOWNLOADS:READ) is required.** The v11 Maps, Search and Nav v3 install pages show the `maven {}` block with only a `url`. (The Nav *v2* install page still describes a secret token + basic auth; stale for v3.)

| Request | Anonymous (no auth) | Fake basic auth (`mapbox:pk.abc`) |
|---|---|---|
| `.../com/mapbox/maps/android-ndk27/11.31.1/android-ndk27-11.31.1.pom` | **200** | 401 |
| `.../com/mapbox/maps/android-ndk27/11.31.1/android-ndk27-11.31.1.aar` | **200** | 401 |
| `.../com/mapbox/maps/android/11.31.1/android-11.31.1.pom` | **200** | 401 |
| `.../com/mapbox/maps/android/11.5.0/android-11.5.0.pom` | **200** | 401 |
| `.../com/mapbox/maps/android-ndk27/11.32.0/android-ndk27-11.32.0.aar` | **200** | - |
| `.../com/mapbox/extension/maps-compose-ndk27/11.32.0/*.pom` | **200** | 401 |
| `.../com/mapbox/extension/maps-androidauto-ndk27/11.32.0/*.pom` | **200** | - |
| `.../com/mapbox/search/{place-autocomplete,discover}-ndk27/2.32.0/*.aar` | **200** | - |
| `.../com/mapbox/navigationcore/android-ndk27/3.32.0/*.aar` | **200** | - |
| `.../com/mapbox/maps/android/maven-metadata.xml` | 404 (directory metadata is not served; probe versions directly) | - |
| Maven Central `com/mapbox/maps/android/maven-metadata.xml` | 404 (not on Central) | - |

Consequences:
- Do NOT configure `credentials {}` for this repo. Wrong basic auth returns 401 (it does not fall back to anonymous). Never leave a placeholder `MAPBOX_DOWNLOADS_TOKEN` in the build.
- Only a **public token (`pk.`)** is needed, at runtime.
- Declare the repo in `dependencyResolutionManagement`, not `pluginManagement`.
- CI needs outbound access to api.mapbox.com.

---

## 3. Build requirements

| Item | Requirement | Source / note |
|---|---|---|
| minSdk | **21** | Search install, Maps v11 migration, Nav |
| compileSdk / targetSdk | Docs minimum 33; use latest (35/36). Nothing in docs forbids newer | migrate-to-v11 |
| Kotlin | Built with 1.7.20; compiler >= 1.6.0 (Maps), >= 1.7.0 (Nav). Binary-compatible claim in docs; Kotlin 2.x not explicitly stated **[UNVERIFIED]** | migrate-to-v11, nav get-started |
| AGP | Not specified by Mapbox | - |
| Java | Source/target compat Java 8 minimum | nav install |
| NDK | Built with NDK 23.2.8568313 (default) or 27.0.12077973 (`-ndk27`). Prebuilt .so ships in AAR; app does not need NDK | migrate-to-v11 |
| 16 KB page size | Supported only by `-ndk27` artifacts | install pages |
| Graphics | OpenGL ES 3.0 | migrate-to-v11 |
| Compose | `buildFeatures { compose = true }` + Compose compiler plugin | Compose guide |
| ProGuard/R8 | No manual rules documented in Maps/Search/Nav guides (consumer rules ship in AARs). **[UNVERIFIED]**: smoke-test a minified release build | docs index search |

Manifest permissions: the Nav SDK manifest merges `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `INTERNET`, `ACCESS_NETWORK_STATE`. Request location + notification permissions at runtime yourself. (https://docs.mapbox.com/android/navigation/guides/install/)

---

## 4. Token configuration

- Public token starts with `pk.`. Secret tokens (`sk.`) must never ship in the app.
- **Option A (resource):** `res/values/mapbox_access_token.xml` with `<string name="mapbox_access_token" translatable="false" tools:ignore="UnusedResources">pk...</string>`. Picked up automatically when a map is created. Leaving the placeholder yields a blank map.
- **Option B (runtime):** `com.mapbox.common.MapboxOptions.accessToken = "pk...."` **before** creating `MapView`/`MapboxMap`, `Snapshotter`, `OfflineManager`, Search/Nav engines. Docs: otherwise "the app will crash". v11 has exactly one global token (`ResourceOptions` removed). Set in `Application.onCreate()`.
- Missing token: crash per docs. Invalid/unauthorized token: requests return 401, map renders empty, Search/Directions return an error result ("Not authorized"). Surface via map load-error listeners and search error results.
- Inject from build config: `local.properties`/CI env var -> `BuildConfig.MAPBOX_ACCESS_TOKEN` -> `MapboxOptions.accessToken`.
- Scopes: default public token scopes cover Maps, Search, Directions, Geocoding, Static Images. Do not add secret scopes to the app token.
- URL restrictions: documented for web; no Android package-name restriction was found in fetched pages. Mitigate with a dedicated app token, usage monitoring, rotation. Guidance: https://docs.mapbox.com/help/troubleshooting/private-access-token-android-and-ios/ **[not fetched]**.
- Rate limits are counted per access token (https://docs.mapbox.com/api/overview/).

---

## 5. Compose API (Maps SDK 11.32.0), taken from official example sources

Sources: https://docs.mapbox.com/android/maps/guides/using-jetpack-compose/ and https://docs.mapbox.com/android/maps/examples/compose/ (code from tag v11.32.0).

```kotlin
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
```

### Map + viewport state
```kotlin
val viewport = rememberMapViewportState {
    setCameraOptions { center(Point.fromLngLat(77.2090, 28.6139)); zoom(9.0); pitch(0.0) }  // lng, lat
}
MapboxMap(Modifier.fillMaxSize(), mapViewportState = viewport) { /* map content */ }
```

### Standard style with lightPreset
```kotlin
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue   // DAY, DAWN, DUSK, NIGHT
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.ThemeValue          // DEFAULT, FADED, MONOCHROME
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState

MapboxMap(
    Modifier.fillMaxSize(), mapViewportState = viewport,
    style = {
        MapboxStandardStyle(
            standardStyleState = rememberStandardStyleState {
                configurationsState.apply {
                    lightPreset = LightPresetValue.NIGHT
                    theme = ThemeValue.DEFAULT
                    showPointOfInterestLabels = BooleanValue(true)
                    showPlaceLabels = BooleanValue(true)
                    showRoadLabels = BooleanValue(true)
                    showTransitLabels = BooleanValue(true)
                    show3dObjects = BooleanValue(true)
                }
            }
        )
    }
)
```
Satellite: `MapboxStandardSatelliteStyle(standardSatelliteStyleState = rememberStandardSatelliteStyleState { configurationsState.apply { lightPreset = ... } })` (same package). Changing `lightPreset` from Compose state recomposes live. Optional `styleTransition = transition { duration(1_000) }` (`com.mapbox.maps.extension.style.utils.transition`).

### Point annotation with custom icon
```kotlin
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage

MapboxMap(Modifier.fillMaxSize(), mapViewportState = viewport) {
    val marker = rememberIconImage(key = R.drawable.ic_pin, painter = painterResource(R.drawable.ic_pin))
    PointAnnotation(point = Point.fromLngLat(77.2090, 28.6139)) {
        iconImage = marker
        textField = "India Gate"
        interactionsState.onClicked { /* handle */ true }
    }
}
```
Clustering: `PointAnnotationGroup(annotations = points.map { PointAnnotationOptions().withPoint(it) }, annotationConfig = AnnotationConfig(annotationSourceOptions = AnnotationSourceOptions(clusterOptions = ClusterOptions(textSize = 20.0, colorLevels = listOf(Pair(100, c1), Pair(50, c2), Pair(0, c3)))))) { iconImage = marker; interactionsState.onClusterClicked { ... } }`
Imports: `...compose.annotation.generated.PointAnnotationGroup`, `com.mapbox.maps.plugin.annotation.{AnnotationConfig, AnnotationSourceOptions, ClusterOptions}`, `com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions`. `ClusterOptions` colors are `@ColorInt Int`; derive from theme (`MaterialTheme.colorScheme.primary.toArgb()`), never literals. Also `CircleAnnotation(Group)` and `PolygonAnnotation(Group)` in `...compose.annotation.generated`.

### Polyline
```kotlin
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation

PolylineAnnotation(points = routePoints /* List<Point> */) {
    lineColor = MaterialTheme.colorScheme.primary   // Compose Color from theme token
    lineWidth = 5.0
}
```
Also `PolylineAnnotationGroup(annotations = List<PolylineAnnotationOptions>)`. For long Directions routes (thousands of vertices) prefer GeoJsonSource + LineLayer via the Compose style composition (example "Add layers and sources in runtime"); exact import paths not re-verified.

### View annotation
```kotlin
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions

ViewAnnotation(options = viewAnnotationOptions {
    geometry(point); annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }; allowOverlap(false)
}) { PlaceCard() }
```

### Location puck + follow
```kotlin
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.ViewportStatus

// only after ACCESS_FINE/COARSE_LOCATION granted
MapboxMap(Modifier.fillMaxSize(), mapViewportState = viewport) {
    MapEffect(Unit) { mapView ->
        mapView.location.updateSettings {
            locationPuck = createDefault2DPuck(withBearing = true)
            puckBearingEnabled = true
            puckBearing = PuckBearing.HEADING
            enabled = true            // pulsingEnabled = true optional
        }
        viewport.transitionToFollowPuckState()
    }
}
// show "locate me" button when viewport.mapViewportStatus == ViewportStatus.Idle
```
There is no dedicated puck composable in the examples; the supported pattern is `MapEffect` + `mapView.location`. Never touch `mapView.lifecycle/compass/scalebar/logo/attribution` inside `MapEffect` (crash); use the Compose ornament parameters.

### Camera
```kotlin
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.compose.animation.*
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.plugin.viewport.data.OverviewViewportStateOptions
import com.mapbox.maps.plugin.viewport.data.DefaultViewportTransitionOptions

viewport.flyTo(cameraOptions { center(p); zoom(14.0); pitch(40.0) }, MapAnimationOptions.mapAnimationOptions { duration(3000) })
viewport.easeTo(cameraOptions = cameraOptions { center(p) })
viewport.transitionToOverviewState(   // fit a route
    overviewViewportStateOptions = OverviewViewportStateOptions.Builder()
        .geometry(MultiPoint.fromLngLats(points)).padding(EdgeInsets(100.0, 100.0, 100.0, 100.0))
        .animationDurationMs(600L).build(),
    defaultTransitionOptions = DefaultViewportTransitionOptions.Builder().maxDurationMs(5000L).build())
```
Other composable examples: style imports, style states (atmosphere/terrain/lights/projection), ornaments, query rendered features, featureset interactions (POI/building/landmark taps on Standard), multi-map, 3D models, Markers guide.

---

## 6. Search

Search SDK 2.32.0 wraps the **Search Box API**; no separate SDK billing (https://docs.mapbox.com/android/search/guides/pricing/).

| Need | SDK class | Notes |
|---|---|---|
| (a) Text autocomplete -> select -> coordinates | `com.mapbox.search.autocomplete.PlaceAutocomplete.create()`; `suspend suggestions(query=...)` then `select(suggestion)` | Coroutine API, `response.isValue/value/error`. Best Compose fit. https://docs.mapbox.com/android/search/examples/place-autocomplete/ |
| (b) Category/nearby | `com.mapbox.search.discover.Discover.create()`; `discover.search(query = DiscoverQuery.Category.COFFEE_SHOP_CAFE, region = BoundingBox)` | Suspend, `response.onValue{}.onError{}`. Category constants in `DiscoverQuery.Category` (list: https://docs.mapbox.com/android/search/api/discover/2.32.0/ - **not machine-readable here; verify RESTAURANT / GAS_STATION / HOSPITAL / PHARMACY / ATM / EV charging / PARKING / HOTEL exist**). Fallback: HTTP `/category/{canonical_id}` and `GET /list/category` for the canonical IDs |
| (c) Reverse geocode | `SearchEngine.createSearchEngineWithBuiltInDataProviders(ApiType.SEARCH_BOX, SearchEngineSettings())` then `searchEngine.search(ReverseGeoOptions(center = Point, limit = 1), SearchCallback)` returns cancelable `AsyncOperationTask` | https://docs.mapbox.com/android/search/examples/reverse-geocoding/ |
| Address forms | `autofill-ndk27` | Not needed |
| Ready UI | `mapbox-search-android-ui-ndk27` | View-based; skip for Compose |

Verified imports: `com.mapbox.search.autocomplete.PlaceAutocomplete`, `com.mapbox.search.discover.{Discover, DiscoverQuery}`, `com.mapbox.search.{ApiType, ReverseGeoOptions, SearchCallback, SearchEngine, SearchEngineSettings, ResponseInfo}`, `com.mapbox.search.result.SearchResult`, `com.mapbox.search.common.AsyncOperationTask`.

### HTTP alternative: Search Box API (https://docs.mapbox.com/api/search/search-box/)
Base `https://api.mapbox.com/search/searchbox/v1`
- `GET /suggest?q=&session_token=<uuid>&proximity=lng,lat&limit<=10&language=&country=&types=poi,address,place&bbox=&poi_category=&access_token=` -> `suggestions[]` (`mapbox_id`, `name`, `full_address`/`place_formatted`, `feature_type`, `poi_category`). `session_token` mandatory.
- `GET /retrieve/{mapbox_id}?session_token=&access_token=` -> GeoJSON FeatureCollection; `features[0].geometry.coordinates = [lng, lat]`.
- `GET /forward?q=` text search (no session).
- `GET /category/{canonical_category_id}?proximity=&limit<=25&bbox=&radius=` ; search-along-route: `sar_type=isochrone&route=<polyline6>&route_geometry=polyline6&time_deviation=<min>`.
- `GET /list/category` category catalog.
- `GET /reverse?longitude=&latitude=&limit<=10`.
- Limits: 10 requests/s default; session ends on retrieve, 180 s idle, or 50 suggests. `/category` and `/reverse` billed per request.
- ToS: results are for **temporary use**; storing coordinates needs sales agreement.

Geocoding v6 (`GET /search/geocode/v6/forward?q=` and `/reverse?longitude=&latitude=`) exists **[not fetched this pass]**; Temporary Geocoding priced per request.

**Recommendation:** use the Search SDK (`PlaceAutocomplete` + `Discover` + `SearchEngine` reverse) behind an app-level `PlaceSearchRepository` interface; keep a Search Box HTTP implementation (OkHttp/Ktor + kotlinx.serialization) as a drop-in alternative. HTTP is simpler to unit test and avoids SDK RC churn; the SDK gives typed results and session handling. Either is robust; default to SDK, swap if issues.

---

## 7. Directions / routing / Navigation

### Directions API v5 (https://docs.mapbox.com/api/navigation/directions/)
`GET https://api.mapbox.com/directions/v5/{profile}/{lng,lat;lng,lat;...}?access_token=`
- Profiles: `mapbox/driving-traffic`, `mapbox/driving`, `mapbox/walking`, `mapbox/cycling`.
- Up to **25 coordinates** (12 for EV). 300 requests/min. Max total distance: 10,000 km driving, 1,500 km cycling, 1,000 km walking.
- Params: `alternatives=true`, `overview=full|simplified|false`, `geometries=polyline6|polyline|geojson`, `steps=true`, `banner_instructions=true`, `voice_instructions=true`, `voice_units`, `language`, `annotations=duration,distance,speed,congestion,congestion_numeric,maxspeed(beta),closure` (requires `overview=full`), `exclude=toll,motorway,ferry,unpaved,cash_only_tolls,...`, `depart_at`/`arrive_by`, `waypoints=0;2`, `waypoint_names`, `radiuses`, `approaches`, `continue_straight`, `engine=electric`. HTTP POST available for long coordinate lists.
- Response: `routes[]` (`geometry`, `distance` m, `duration` s, `legs[]` -> `steps[]`, `annotation`), `waypoints[]`, `code` ("Ok", "NoRoute"...). Decode polyline6 with `LineString.fromPolyline(str, 6)`.
- Recommended: `.../mapbox/driving-traffic/{coords}?alternatives=true&overview=full&geometries=polyline6&steps=true&annotations=duration,distance,congestion_numeric`.

### Optimization API
- **v1** (GA): `GET https://api.mapbox.com/optimized-trips/v1/{profile}/{coordinates}`; **max 12 coordinates**. 
- **v2** (`POST /optimized-trips/v2`): **public beta, sign-up required** (time windows, capacity, shifts); not generally available.
- For more than 12 stops: Matrix API + local solver.

### Matrix API (https://docs.mapbox.com/api/navigation/matrix/)
N x N durations/distances, billed per element. Coordinate limits per profile **[UNVERIFIED, check page]**.

### Navigation SDK v3 (3.32.0)
- Setup/route/trip: `MapboxNavigationApp.setup`/`attach`, `requestRoutes(RouteOptions...)`, `setNavigationRoutes`, `startTripSession()` **[snippets UNVERIFIED; see https://docs.mapbox.com/android/navigation/guides/initialization/]**.
- Modules: route line API, maneuver/trip progress (`tripdata`), route arrow, voice (`voice`), junction/lane views, `ui-components`, `copilot`, automatic reroute, route refresh, Free Drive, route replay.
- `startTripSession()` starts a foreground service with notification unless `startTripSession(false)`. Needs runtime location + (Android 13+) `POST_NOTIFICATIONS`; `FOREGROUND_SERVICE_LOCATION` is merged from the SDK manifest.
- **Usable with only a public token: YES** (v3 install docs require only a public token and anonymous Maven). Billing is metered (section 10).
- UI is View-based; use `AndroidView` interop or build Compose UI over data APIs.
- Android Auto: `com.mapbox.navigationcore:android-auto-components-ndk27:3.32.0`.

---

## 8. Offline

### Maps v11 offline API (https://docs.mapbox.com/android/maps/guides/offline/manage-offline-data/)
```kotlin
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TileStore
import com.mapbox.common.TileStoreOptions
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileRegionError
import com.mapbox.common.TileRegionErrorType
import com.mapbox.common.Cancelable
import com.mapbox.bindgen.Value   // docs snippet imports com.mapbox.common.Value; v11 Value lives in com.mapbox.bindgen - verify at compile
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.TilesetDescriptorOptions
import com.mapbox.maps.Style

MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN   // before OfflineManager()
val offlineManager = OfflineManager()
val tileStore = TileStore.create()   // or TileStore.create(path); then MapboxMapsOptions.tileStore = tileStore

// 1) Style pack (once per style, few MB)
val stylePackCancelable: Cancelable = offlineManager.loadStylePack(
    Style.STANDARD,
    StylePackLoadOptions.Builder()
        .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
        .acceptExpired(false).build(),
    { progress -> /* background thread */ }
) { expected -> expected.fold({ stylePack -> }, { error -> /* StylePackError type CANCELED etc. */ }) }

// 2) Tile region by geometry (Point / LineString / Polygon / MultiPolygon)
val descriptor = offlineManager.createTilesetDescriptor(
    TilesetDescriptorOptions.Builder().styleURI(Style.STANDARD).minZoom(11).maxZoom(15)
        .pixelRatio(resources.displayMetrics.density).build())
val loadOptions = TileRegionLoadOptions.Builder()
    .geometry(polygon)
    .descriptors(listOf(descriptor))
    .metadata(Value.valueOf(hashMapOf("name" to Value.valueOf("Delhi"))))
    .acceptExpired(false).build()
val regionCancelable: Cancelable = tileStore.loadTileRegion("delhi-core", loadOptions,
    { progress -> /* completed/required resource counts, loaded bytes */ }
) { expected -> expected.fold({ region -> }, { e -> if (e.type == TileRegionErrorType.CANCELED) {} else {} }) }

regionCancelable.cancel()                                          // cancel
tileStore.getAllTileRegions { it.fold({ regions -> }, { err -> }) } // list
tileStore.removeTileRegion("delhi-core")                           // remove
offlineManager.getAllStylePacks { /* list */ }; offlineManager.removeStylePack(Style.STANDARD)
tileStore.setOption(TileStoreOptions.DISK_QUOTA, Value.valueOf(500L * 1024 * 1024))
```
(The docs' own snippet passes `hashMapOf(...)` straight to `.metadata(...)`; confirm the accepted type at compile.)

Facts (https://docs.mapbox.com/android/maps/guides/offline/concepts/):
- Cumulative unique tile packs limit **750**; loads exceeding it are refused.
- Tile pack bands: zoom 0-5, 6-10, 11-14, 15-16 (minZoom 8 / maxZoom 15 downloads 6-16). Max offline zoom 16.
- Prefer polygons/lines over bounding boxes (data grows exponentially at high zoom).
- **Pause/resume: not documented.** Only `cancel()`. Re-calling `loadTileRegion` with the same region ID fetches missing/expired resources (resume by re-issue). A pending load for the same ID is cancelled by a new call.
- **Estimate API: none documented** in the Maps offline guide. Compute tile counts from geometry/zoom and use `TileRegionLoadProgress` after start.
- Offline data does not auto-refresh; reload with `acceptExpired(false)`.
- ToS: no bundling/redistribution; must download from Mapbox at runtime. Only Mapbox-hosted tilesets; disable source compositing for offline styles. "Some dynamic features may not work offline."
- No built-in UI.

### Navigation offline (https://docs.mapbox.com/android/navigation/guides/advanced/offline/)
- **Predictive caching** (`PredictiveCacheController`, `PredictiveCacheOptions`): automatic around user (20 km default), route (5 km buffer), destination (50 km). Example size: Berlin 15 km ~136 MB maps + 42 MB nav.
- **Offline regions for routing:** same `TileStore` instance for `MapboxMapsOptions.tileStore` and `RoutingTilesOptions`; load a region with Maps + Navigation tileset descriptors; routing and reroute work offline. The page has no sales gate, but contractual implications are **[UNVERIFIED - confirm with Mapbox]**.

### OFFLINE CAPABILITY MATRIX

| Capability | Offline? | How | Caveats |
|---|---|---|---|
| Map rendering (vector, Standard/Streets/Outdoors) | **Yes** | Style pack + tile region | 750 pack cap, zoom <= 16, manual refresh, pre-download |
| Satellite imagery | Possible (Mapbox-hosted raster) | Same | Very large; test before promising **[UNVERIFIED]** |
| Terrain / 3D | Partial | DEM tiles | Large; some dynamic features missing |
| Location puck | Yes (GPS) | - | No basemap outside regions |
| Place text search | **No (premium)**: `OfflineSearchEngine` (`offline-ndk27`) is a beta premium feature requiring Mapbox sales | Search offline guide | Treat as unavailable; search saved places in local DB |
| Reverse geocoding | Premium only (offline reverse geocoding example) | - | Treat as unavailable |
| A-to-B routing | **Yes with Nav SDK v3** + TileStore regions or predictive cache; Directions HTTP is online-only | Nav tileset descriptors | Only inside downloaded regions; no live traffic |
| Turn-by-turn | Yes (Nav SDK) | As above | Voice via Android TTS (Mapbox voice API is online) |
| POI category search | No | - | Online only |
| Static images | No | - | Online only |
| Download pause/resume | Not provided | cancel + re-issue same region ID | Build WorkManager/foreground-service download manager |

---

## 9. Location

- **Puck** (`mapView.location`): renders the user position; listener `addOnIndicatorPositionChangedListener` gives position.
- **Mapbox `LocationService`** (`com.mapbox.common.location`, in Maps SDK): `LocationServiceFactory.getOrCreate().getDeviceLocationProvider(LocationProviderRequest.Builder().interval(IntervalSettings.Builder().interval(1000L).minimumInterval(500L).maximumInterval(2000L).build()).displacement(0f).accuracy(AccuracyLevel.HIGHEST).build())` -> `result.value` is `DeviceLocationProvider`. `addLocationObserver(object : LocationObserver { override fun onLocationUpdateReceived(locations: MutableList<Location>) {} })`; `getLastLocation { }` returns `Cancelable`. Backed by Google Fused Location with Android GPS/Network fallback; starts with first observer, stops with last. Source: https://docs.mapbox.com/android/maps/guides/user-location/device-location/
- **Recommendation:** own `LocationRepository` (Flow) on FusedLocationProviderClient for non-map needs (proximity bias for search, distance sorting); let the Mapbox puck run its own provider; one app-level runtime permission flow (`PermissionsManager` helper exists: https://docs.mapbox.com/android/maps/guides/user-location/permissions/).

---

## 10. Pricing and cost drivers (https://www.mapbox.com/pricing, fetched 2026-10-08; verify before launch)

| Product | Unit | Free monthly | First paid band | Billing trigger |
|---|---|---|---|---|
| Maps SDK for Mobile | MAU | **25,000** | $4.00 / 1,000 MAU (25,001-125,000) | Unique install using Maps SDK (viewing a map or snapshot); reinstall = new MAU; covers vector + raster tile requests |
| Maps offline | see https://docs.mapbox.com/android/maps/guides/pricing/ and the pricing page's offline guide | - | - | **Confirm offline terms before shipping** |
| Search Box API sessions (`/suggest`+`/retrieve`) | session | **500** (introductory preview) ; standard rate card shows 2,500 | $3.00 / 1,000 (intro); $11.50 / 1,000 (standard) | One session per shared `session_token` (retrieve, 180 s idle, or 50 suggests) |
| Search Box API requests (`/category`, `/reverse`) | request | **50,000** (intro) / 25,000 (standard) | $1.00 / 1,000 (intro) / $1.70 / 1,000 (standard) | Each call |
| Temporary Geocoding | request | 100,000 | $0.75 / 1,000 | Each forward/reverse call |
| Directions API | request | **100,000** | $2.00 / 1,000 | One request regardless of waypoint count |
| Navigation SDK v3 (metered) | MAU + trip | **100 MAU, 1,000 trips** | $0.30 / MAU; $0.08 / trip (1,001-50,000) | Trip = `startTripSession()` with route (Active Guidance, max 12 h) or without (Free Drive, max 1 h). Route change >100 m or different waypoint count starts a new trip. In-session Directions/Map Matching/voice/refresh calls are free; requests outside a session (route preview) bill as normal Directions requests. Reinstall = new MAU |
| Navigation SDK v3 (unlimited trips) | MAU | 10 MAU | contact sales | Contract |
| Matrix API | element | 100,000 | $2.00 / 1,000 | Per origin-destination element |
| Isochrone API | request | 100,000 | $2.00 / 1,000 | Per request |
| Optimization API v1 | request | 100,000 | $2.00 / 1,000 | Per request |
| Map Matching API | request | 100,000 | $2.00 / 1,000 | Per request |
| Static Images API | request | 50,000 | $1.00 / 1,000 | Per image |
| Tilequery API | request | 100,000 | $1.00 / 1,000 | Per request |

The pricing page text shows two Search Box rate cards ("introductory preview" vs "standard"); confirm which applies to our account in the console.

Licensing: Mapbox logo and attribution must stay visible (repositioning allowed; "Custom Attribution" example exists). Search results are temporary-use only. Offline tiles must not be bundled/redistributed. **Telemetry opt-out for v11 was not found in the docs index [UNVERIFIED]**; check Mapbox privacy docs, and disclose in Play Data Safety regardless.

---

## 11. Other capabilities

| Capability | Notes | Source |
|---|---|---|
| Standard style config | `lightPreset` day/dawn/dusk/night, `theme` default/faded/monochrome, POI/place/road/transit labels, `show3dObjects`, `showLandmarkIcons`, `font`, `showAdminBoundaries`, color theme | https://docs.mapbox.com/map-styles/standard/ |
| Standard Satellite | `MapboxStandardSatelliteStyle`; roads/transit/pedestrian toggles | Compose Standard example |
| Terrain / 3D / atmosphere | Style states (atmosphere, terrain, lights, projection); DEM `mapbox-terrain-dem-v1` | Compose style-terrain / style-states examples |
| Clusters / heatmap | `PointAnnotationGroup` + `ClusterOptions`, or GeoJsonSource with `cluster=true` + CircleLayer/HeatmapLayer via style composition | Compose cluster / style-composition examples |
| Static Images API | `GET https://api.mapbox.com/styles/v1/{user}/{style_id}/static/{overlay}/{lon},{lat},{zoom}/{w}x{h}{@2x}` ; 50k free/month; thumbnails/share cards | https://docs.mapbox.com/api/maps/static-images/ |
| Isochrone API | `GET /isochrone/v1/mapbox/{profile}/{lng,lat}?contours_minutes=5,10,15&polygons=true` | https://docs.mapbox.com/api/navigation/isochrone/ |
| Matrix API | Travel-time matrix to rank nearby places / order stops | https://docs.mapbox.com/api/navigation/matrix/ |
| Map Matching | Snap GPS traces to roads | https://docs.mapbox.com/api/navigation/map-matching/ |
| Tilequery | Features at a point from a tileset | https://docs.mapbox.com/api/maps/tilequery/ |
| Geofencing | Maps SDK geofencing (separate pricing) | https://docs.mapbox.com/android/maps/guides/geofencing/ |
| Deep links | No Mapbox-provided Android deep-link scheme found; use our own App Links / `geo:` URIs | n/a |
| Android Auto | `navigationcore:android-auto-components-ndk27` or `extension:maps-androidauto-ndk27`; needs Car App Library + Play review | https://docs.mapbox.com/android/navigation/guides/androidauto/ |
| Docs MCP server | `claude mcp add mapbox-docs --transport http https://mcp-docs.mapbox.com/mcp` | docs llms.txt |

---

## 12. Capability matrix

| Capability | Mapbox Product | Android Support | Online | Offline | Complexity | Potential Use Cases |
|---|---|---|---|---|---|---|
| Interactive vector map | Maps SDK 11.32 + Compose ext | Yes (Compose-native) | Yes | Yes (regions, 750 pack cap) | Low | Home map, place detail, route preview |
| Day/night/3D Standard style | Standard style config | Yes | Yes | Mostly | Low | Auto light preset, themes |
| Markers / clusters | Point/Circle/Polygon/Polyline annotations + groups | Yes | Yes | Yes (local data) | Low-Med | Search results, saved places, nearby POIs |
| Composable callouts | ViewAnnotation | Yes | Yes | Yes | Low | Place cards on map |
| Route line / overlay | Annotation or GeoJson source + LineLayer | Yes | Yes | Yes | Med | Show Directions result |
| User location puck | Location component | Yes | Yes | Yes | Low | Follow user, heading |
| Camera animation | flyTo / easeTo / viewport states | Yes | Yes | Yes | Low | Locate-me, fit route |
| Autocomplete search | Search SDK PlaceAutocomplete / Search Box API | Yes | Yes | No (premium only) | Low | Search bar |
| Category / nearby POI | Discover / `/category` | Yes | Yes | No | Low-Med | Fuel, hospital, ATM, EV, parking, hotel |
| Reverse geocoding | SearchEngine / `/reverse` / Geocoding v6 | Yes | Yes | Premium only | Low | Long-press address |
| A-to-B routing + alternatives | Directions API v5 | HTTP | Yes | No (HTTP); Nav SDK offline yes | Low-Med | Route preview, ETA |
| Multi-stop planning | Directions (<=25) + Optimization v1 (<=12) | HTTP | Yes | No | Med | Itineraries |
| Turn-by-turn | Nav SDK v3 | Yes (View UI) | Yes | Yes | High | Driving navigation |
| Voice guidance | Nav voice / TTS | Yes | Yes | TTS only | Med | Spoken directions |
| Offline maps | OfflineManager + TileStore | Yes | - | Yes | Med-High | City downloads |
| Reachability | Isochrone API | HTTP | Yes | No | Low | "Within 15 min" overlay |
| Travel-time matrix | Matrix API | HTTP | Yes | No | Low | Rank by ETA |
| Trace snapping | Map Matching | HTTP | Yes | No | Low | Trip recording |
| Thumbnails | Static Images API | HTTP | Yes | No | Low | List thumbnails |
| Android Auto | Nav/Maps Auto ext | Yes | Yes | Yes | High | In-car nav |

---

## 13. Limitations and risks

1. **Token exposure:** public tokens are extractable from the APK; use a dedicated app token, minimal scopes, monitor usage, plan rotation. No Android package restriction documented.
2. **Offline search is premium** (sales-gated beta). Do not promise it; search saved places locally.
3. **No pause/resume or estimate** for offline downloads; build a download manager (WorkManager + foreground service), pre-compute sizes, enforce the 750-pack cap.
4. **Search ToS:** results are temporary-use; persisting coordinates from search needs a different product/contract. Legal decision needed for "saved places".
5. **Nav SDK UI is View-based**; foreground service, notification permission and Play foreground-service-location declaration add UX/compliance work.
6. **Billing surprises:** trips (12 h/1 h caps), route previews outside sessions, search sessions, Maps MAU on reinstall. Debounce autocomplete.
7. **Version drift in docs:** 11.31.1 / 3.34.0 / 2.33.0-rc appear in places; lock 11.32.0 / 3.32.0 / 2.32.0 and upgrade together.
8. **Compose extension pulls Compose Material 2 and an old BOM** transitively; control with own BOM.
9. **Telemetry/privacy:** opt-out mechanism unconfirmed; disclose in Play Data Safety.
10. **Rate limits:** Directions 300 rpm; Search Box 10 rps; Optimization v1 12 coordinates; Directions 25 waypoints.
11. **Maven availability:** artifacts only on api.mapbox.com (not Central); policy change (re-adding auth) or outage would break clean builds; keep Gradle cache/mirror.
12. **16 KB page size:** only ndk27 artifacts; do not mix with non-ndk27.

---

## 14. Recommended integration approach for our app

1. **Dependencies:** Maps 11.32.0 + Compose ext (ndk27) now; Search (autocomplete/discover/core, 2.32.0) behind `PlaceSearchRepository`; defer Nav SDK until in-app turn-by-turn is committed.
2. **Auth:** Maven with no credentials; public token via `MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN` in `Application.onCreate()` (token from `local.properties`/CI env, never committed). No secret token anywhere.
3. **Architecture (SOLID):** interfaces `PlaceSearchRepository` (autocomplete/select, nearby by category, reverse), `RoutingRepository` (Directions v5 via OkHttp/Ktor, `polyline6`), `OfflineRegionManager` (wraps OfflineManager/TileStore), `LocationRepository` (Fused Location Flow). Compose UI depends only on these. All colors from `MaterialTheme.colorScheme` tokens (annotation/line/cluster colors converted with `toArgb()`); no literals.
4. **Map UI:** `MapboxMap` + `rememberMapViewportState`; `MapboxStandardStyle` with `lightPreset` bound to app theme/time; `PointAnnotationGroup` with clustering for result sets; `ViewAnnotation` for the selected place card; `MapEffect` for puck; `transitionToFollowPuckState()` for locate button; `transitionToOverviewState` to frame routes.
5. **Search:** `PlaceAutocomplete` for the search bar (debounced; one session per typing flow), `Discover` for category chips (fuel, hospital, pharmacy, ATM, EV charging, parking, hotel, restaurant), `SearchEngine` reverse geocoding for long-press. Keep an HTTP Search Box implementation as fallback.
6. **Routing:** Directions v5 HTTP (`driving-traffic`, `alternatives=true`, `overview=full`, `geometries=polyline6`, `steps=true`, annotations) drawn as a line layer/polyline; multi-stop up to 25 waypoints; Optimization v1 (<=12) or Matrix + local solver for reordering. Add Nav SDK v3 later if guidance is required.
7. **Offline (v1 scope):** map rendering + local saved places + cached last route. Download city regions with polygon geometry, limited zoom, one Standard style pack; enforce 750-pack cap and disk quota; WorkManager download manager with cancel/retry (same region ID); show size/progress. Offline routing and search explicitly out of v1.
8. **Cost control:** debounce autocomplete; reuse session tokens; no persistent caching of search results; avoid Directions calls without user intent; monitor via console and alert near free-tier limits.
9. **Compliance checklist:** logo + attribution visible, Play Data Safety (location, telemetry), no persisted search results, offline data downloaded at runtime only.

## Sources
- https://docs.mapbox.com/android/maps/guides/install/ ; /using-jetpack-compose/ ; /migrate-to-v11/ ; /offline/ ; /offline/concepts/ ; /offline/manage-offline-data/ ; /user-location/device-location/ ; /user-location/location-on-map/ ; /pricing/
- https://docs.mapbox.com/android/maps/examples/compose/ (work-with-the-standard-style, add-point-annotations, add-polyline-annotations, add-cluster-symbol-annotations, add-view-annotations, location-component, map-viewport-animation)
- https://docs.mapbox.com/android/search/guides/install/ ; /pricing/ ; /search-engine/offline/ ; examples place-autocomplete, discover, reverse-geocoding
- https://docs.mapbox.com/android/navigation/guides/install/ ; /pricing/ ; /advanced/offline/ ; /androidauto/
- https://docs.mapbox.com/api/search/search-box/ ; /api/navigation/directions/ ; /optimization/ ; /optimization-v1/ ; /matrix/ ; /isochrone/ ; /map-matching/ ; /api/maps/static-images/ ; /api/maps/tilequery/ ; /api/overview/
- https://www.mapbox.com/pricing
- Mapbox Maven probes: https://api.mapbox.com/downloads/v2/releases/maven/com/mapbox/...
