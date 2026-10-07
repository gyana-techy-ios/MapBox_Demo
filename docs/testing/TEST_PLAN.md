# Raahi: Test Plan

**Date:** October 2026  
**Status:** MVP Specification  
**Version:** 1.0

---

## 1. Testing Strategy

### 1.1 Test Pyramid

```
        ╱╲
       ╱  ╲        End-to-End (Manual, few)
      ╱────╲
     ╱      ╲       Integration (UI tests, some)
    ╱────────╲
   ╱          ╲     Unit Tests (many, fast)
  ╱────────────╲
```

- **Unit Tests (70%):** Pure Kotlin logic (repositories, use cases, domain models, GuidanceEngine, TileEstimator).
- **Integration Tests (20%):** ViewModel + Repository interactions, Room persistence, Compose composables.
- **End-to-End / Manual (10%):** Full user flows on real devices, network edge cases, map rendering.

### 1.2 Test Environment

- **Unit tests:** JVM (Robolectric for Android APIs).
- **Integration tests:** Android emulator or test device (Android 8.0+, API 26+).
- **Manual tests:** Real devices (low-end: Redmi 6A; mid: Redmi Note 9; flagship: Samsung S21).

---

## 2. Test Coverage by Layer

### 2.1 Domain Layer (Use Cases, Repositories)

| Scenario | Expected Behavior | Test Type | Automated? |
|---|---|---|---|
| **SearchPlaceUseCase:** query "fuel" → results returned | Returns up to 5 places with names, addresses, distances | Unit | Yes |
| **SearchPlaceUseCase:** empty query "" | Returns empty list without calling API | Unit | Yes |
| **SearchPlaceUseCase:** query fails with 401 (invalid token) | Returns AppError.TokenInvalid | Unit | Yes |
| **SearchPlaceUseCase:** network timeout (> 30 s) | Returns AppError.NetworkError with retry action | Unit | Yes |
| **DiscoveryCategoryUseCase:** search category "hospital" → results clustered | Results sorted by distance; no duplicates | Unit | Yes |
| **DirectionsRepositoryImpl:** 2-waypoint route → up to 3 alternatives | All 3 routes have valid geometry, distance > 0, duration > 0 | Unit | Yes |
| **DirectionsRepositoryImpl:** > 2 waypoints → 1 route returned | Alternatives not available; message shown to user | Unit | Yes |
| **DirectionsRepositoryImpl:** coordinates out of bounds (e.g., ocean) → no route | Returns AppError.RoutingFailed("NoRoute") | Unit | Yes |
| **TripRepository:** save trip → load trip | Trip loaded from Room matches saved trip (all fields) | Integration | Yes |
| **TripRepository:** delete trip → trip removed | Trip no longer found by ID | Integration | Yes |
| **SavedPlaceRepository:** save place + photo → retrieve | Photo URI is correct; data persisted | Integration | Yes |
| **OfflineRegionRepository:** estimate region size (10x10 km, zoom 11-15) | Estimate within ±15% of actual download | Unit | Yes |
| **GuidanceEngine:** compute next maneuver (500 m away) | Maneuver text matches step instruction; distance/time correct | Unit | Yes |
| **GuidanceEngine:** detect off-route (> 10 m for > 5 s) | isOffRoute() returns true | Unit | Yes |
| **LocationRepository:** requestLocation() → get position | Returns valid LatLng; timestamp ≤ 5 s old | Integration | Yes |
| **ConnectivityRepository:** online → isOnline() true | Detects network state correctly | Integration | Yes |
| **ConnectivityRepository:** offline (airplane mode) → isOnline() false | Detects network loss correctly | Integration | Yes |

### 2.2 Data Layer (Persistence, Network)

| Scenario | Expected Behavior | Test Type | Automated? |
|---|---|---|---|
| **Room tripDao:** insert + query 100 trips | All trips retrieved in order (lastModifiedAt DESC) | Integration | Yes |
| **Room stopDao:** insert stops, query by tripId | Stops returned in sequence order | Integration | Yes |
| **Room savedPlaceDao:** save home + 3 favorites → total 4 | Correct types and counts | Integration | Yes |
| **Room recentSearchDao:** insert 50 searches, query last 10 | Most recent first | Integration | Yes |
| **Room trip delete cascade:** delete trip → stops auto-deleted | Foreign key cascade works; no orphaned stops | Integration | Yes |
| **DataStore preferences:** set theme=DARK → read | Returns DARK correctly | Integration | Yes |
| **HTTP Directions API:** call with valid params → response | Response code 200, routes[] non-empty, geometry decoded | Integration | Yes |
| **HTTP Directions API:** request exceeds rate limit (300 rpm) → 429 | Returns 429; app shows "too many requests" message | Manual | No |
| **Polyline6 decoder:** decode valid polyline → LatLng list | Decoded points match expected path | Unit | Yes |
| **Search SDK:** PlaceAutocomplete with empty token → error | Returns meaningful error, not crash | Integration | Yes |

### 2.3 UI Layer (Compose Screens, ViewModels)

| Scenario | Expected Behavior | Test Type | Automated? |
|---|---|---|---|
| **ExploreViewModel:** onSearchQueryChanged("fuel") | uiState.searchResults updated with results | Integration | Yes |
| **ExploreViewModel:** search fails (no internet) → error state | uiState.searchError is set; retry button shown | Integration | Yes |
| **ExploreViewModel:** onCategoryChipTapped("hospital") | Nearby hospitals list appears in uiState | Integration | Yes |
| **TripEditorViewModel:** addStop() x 5 → trip.stops.size == 5 | Stops added in correct order | Integration | Yes |
| **TripEditorViewModel:** deleteStop(2) → stops reordered | Sequence numbers updated; no gaps | Integration | Yes |
| **TripEditorViewModel:** compareRoutes() → 3 routes | All 3 available; selected route highlighted | Integration | Yes |
| **OfflineViewModel:** downloadRegion() → progress updates | Progress flow emits completion with region in list | Integration | Yes |
| **EmergencyViewModel:** onCallEmergency(112) → ACTION_DIAL | Intent launched with "112" pre-filled | Integration | Yes |
| **SavedPlacesViewModel:** savePlace(home) → place in list | Place retrieved; photo (if taken) stored correctly | Integration | Yes |
| **ExploreScreen (Compose):** render on map ready | Map visible, puck shown, chips tappable | UI | Yes |
| **SearchScreen (Compose):** type query → suggestions appear | List updates as user types; debounce respected | UI | Yes |
| **PlaceCard (Compose):** tap navigate button → navigation starts | Trip created or sheet closes; correct destination | UI | Yes |
| **RouteComparisonScreen (Compose):** 3 routes visible → tap "Select" | Route highlighted; start driving available | UI | Yes |
| **GuidanceScreen (Compose):** screen stays on during guidance | WakeLock prevents sleep | UI | Manual |

---

## 3. Android Lifecycle and Edge Case Matrix

### 3.1 Lifecycle Scenarios

| Scenario | Device State | Expected Behavior | Test Type | Automated? |
|---|---|---|---|---|
| **Launch app (cold start)** | App not running | Onboarding shown (first run only); location/notification permissions requested | Manual | No |
| **App in background, killed by OS** | Memory pressure; task killer | On resume, last screen restored; no crash; data intact | Integration | Yes |
| **Configuration change** | Rotate device | ViewModel state preserved; UI redraws correctly | Integration | Yes |
| **App paused (user switches app)** | User minimizes app | Location updates pause; guidance notification shown (v1.1+) | Manual | No |
| **App resumed** | User returns to app | State restored; no blank screens; location updated if permission granted | Integration | Yes |
| **Locale change** | User changes system language | App UI switches language; no strings missing | Integration | Yes |
| **Theme change (dark mode toggle)** | User toggles dark mode in Settings | App theme switches immediately; map style updates | Integration | Yes |

### 3.2 Location Permission Scenarios

| Scenario | Permission State | Expected Behavior | Test Type | Automated? |
|---|---|---|---|---|
| **First launch, grant precise** | Precise granted | Blue puck shown; current location used for searches | Manual | No |
| **First launch, grant approximate** | Approximate only | Puck shown with reduced accuracy; searches use approximate | Manual | No |
| **First launch, deny** | Denied | Puck not shown; map centered on India default; search unavailable | Manual | No |
| **Grant location after denying** | Denied → Granted (via Settings) | Puck appears on next location update | Manual | No |
| **Revoke location permission mid-app** | Revoke while app running | Location updates stop; UI shows "Location unavailable" | Manual | No |

### 3.3 Network Edge Cases

| Scenario | Network State | Expected Behavior | Test Type | Automated? |
|---|---|---|---|---|
| **Cold start, no internet** | Offline from launch | Map offline (no tiles); saved places shown; search disabled | Manual | No |
| **Internet drops during search** | Online → Offline mid-request | In-flight request times out after 30 s; error shown; fallback to local | Integration | Yes |
| **Reconnect after disconnect** | Offline → Online | ConnectivityMonitor notifies; UI updates to online state | Integration | Yes |
| **WiFi → Mobile data switch** | WiFi lost, mobile available | Connectivity maintained; no user-visible interruption | Manual | No |
| **Weak signal (high latency)** | 100 ms+ latency | API calls slow but succeed; user sees spinner; no premature timeout | Integration | Yes |
| **Rate limit hit (300 rpm)** | Directions API 429 | App shows "too many requests"; user retries later | Manual | No |

### 3.4 Offline Map Scenarios

| Scenario | Offline State | Expected Behavior | Test Type | Automated? |
|---|---|---|---|---|
| **Download region, WiFi available** | WiFi ON, Wi-Fi-only toggle ON | Download starts; progresses to 100%; region listed in Offline | Integration | Yes |
| **Download region, WiFi unavailable** | WiFi OFF, mobile only | Download paused; notification shows "WiFi required"; resume when WiFi returns | Manual | No |
| **Download region, storage full** | < 100 MB free, region 450 MB | Download fails; app suggests delete old regions; storage manager opens | Integration | Yes |
| **Cancel download mid-way** | 50% downloaded | Download stops; partial tiles kept; resume available | Integration | Yes |
| **Resume download** | 50% done, resumed | Only missing tiles fetched; no re-download of completed tiles | Integration | Yes |
| **Delete offline region** | Region 450 MB | Region removed from list; storage freed; trip still accessible (but offline routing unavailable) | Integration | Yes |
| **Region expires** | 30+ days after download | Expiry warning shown at day 25; update button available; old auto-deleted after update | Integration | Yes |

### 3.5 Navigation and Guidance Scenarios

| Scenario | State | Expected Behavior | Test Type | Automated? |
|---|---|---|---|---|
| **Start guidance with valid route** | Route loaded, user at start | Next maneuver shown; distance, ETA, voice guidance active | Manual | No |
| **Guidance: approach turn (500 m away)** | En route, 500 m from next turn | Maneuver announced via TTS + text; arrow shown on map | Manual | No |
| **Guidance: off-route (15 m dev)** | Off-track > 10 m for > 5 s | "Off Route, Recalculate?" button shown; online = enabled, offline = disabled | Manual | No |
| **Guidance: recalculate (online)** | Off-route, WiFi available | New route computed; guidance switches to new route; no instruction gaps | Integration | Yes |
| **Guidance: app minimized** | Guidance active, user presses home | Guidance pauses; notification shows current maneuver; resume when user returns | Manual | No |
| **Guidance: screen lock** | Guidance active, screen locks | Screen stays on (WakeLock held); guidance continues in background (v1.1+) | Manual | No |
| **Guidance: reach destination** | User arrives within 50 m of final stop | Arrival confirmed; "Trip Complete" message; guidance ends | Integration | Yes |

### 3.6 Emergency Mode Scenarios

| Scenario | State | Expected Behavior | Test Type | Automated? |
|---|---|---|---|---|
| **Call 112** | Emergency screen open, online | Phone dialer opens with "112" pre-filled; no app involvement after action | Manual | No |
| **Share location (online)** | Emergency screen open, WiFi available | Geo link (geo://28.6,77.2) shared via SMS/WhatsApp/email; recipient can open in Maps | Manual | No |
| **Find Hospital (online)** | Emergency screen, hospital search initiated | Up to 10 results shown; nearest first; distance calculated | Integration | Yes |
| **Find Hospital (offline)** | Emergency screen, offline, no prior search | "No cached hospitals. Go online to search." message | Integration | Yes |
| **Emergency contacts list (offline)** | Offline, emergency contacts saved | Contact list loaded from Room; phone numbers visible; call buttons work | Integration | Yes |

---

## 4. Device Tiers and Performance Testing

### 4.1 Device Classes

| Tier | Example Device | RAM | Storage | Processor | Testing Focus |
|---|---|---|---|---|---|
| **Low-end** | Redmi 6A | 2 GB | 16 GB eMMC | Snapdragon 439 | Cold start, memory pressure, ANR risk |
| **Mid-range** | Redmi Note 9 | 4 GB | 64 GB UFS 2.1 | Snapdragon 665 | Average user experience, offline download speed |
| **Flagship** | Samsung S21 | 8 GB | 256 GB UFS 3.1 | Snapdragon 888 | Baseline performance, GPU-heavy operations |

### 4.2 Performance Benchmarks

| Metric | Target | Tier | Pass Criteria |
|---|---|---|---|
| **Cold start** | < 3 s | Mid-range | Time to Explore screen visible |
| **Map load** | < 2 s | Mid-range | Map tiles render, puck visible |
| **Search latency** | < 500 ms | Mid-range | Autocomplete suggestions appear |
| **Route computation** | < 5 s | Mid-range | 3 routes returned from Directions API |
| **Offline download speed** | ≥ 2 Mbps | Mid-range | Over WiFi; assumes WiFi 5GHz, near AP |
| **Guidance update frequency** | ≥ 1 Hz | Low-end | Puck updates smoothly, maneuver timing accurate |
| **Memory (Guidance)** | < 300 MB | Low-end | No OOM during 2-hour guidance session |
| **APK size (Release)** | < 80 MB | All | fit on 128 MB phone after other apps |

### 4.3 Stress Testing

| Test | Scenario | Duration | Expected Result |
|---|---|---|---|
| **Long guidance session** | Drive 8-hour trip, screen on, location updates | 8 hours (or 1 hour compressed) | No memory leaks, no ANR, battery ~2% per hour |
| **Offline region spam** | Download 10 regions simultaneously | 30 min | App handles; WorkManager queues correctly; no crashes |
| **Cluster expansion** | Search returns 1000 places, cluster then expand | 2 min | UI responsive; clusters expand/collapse smoothly; no lag |
| **Large trip (25 stops)** | Create trip with max 25 stops, compare routes | 5 min | All routes computed; UI responsive; no lag in list/map |

---

## 5. Release Gates

App must pass the following quality criteria before release to Play Store:

### 5.1 Functional Gate

- [ ] All 4 use cases (Explore, Trip, Offline, Emergency) pass UAT.
- [ ] No critical bugs (crash, data loss, unrecoverable error).
- [ ] All screens handle offline state correctly.
- [ ] Location permission flow works on API 26, 33, 35+.
- [ ] Offline maps download and render without network.
- [ ] Trip guidance produces correct maneuvers (50 test routes).
- [ ] Emergency mode returns hospitals / pharmacies (with fallback to cached).
- [ ] Deep links (raahi://place, raahi://trip/{id}) functional.

### 5.2 Performance Gate

- [ ] Cold start < 3 s on mid-range device.
- [ ] Map load < 2 s after Explore screen enters.
- [ ] Crash rate < 0.5% (measured via Firebase Crashlytics or local logs).
- [ ] ANR rate < 0.1% (no jank on 60 Hz scroll or guidance update).
- [ ] Release APK < 80 MB.
- [ ] Battery drain < 3% per hour in Guidance mode.

### 5.3 Security Gate

- [ ] No secret tokens (sk.*) in APK.
- [ ] Public token correctly configured; token extraction plan documented.
- [ ] No hardcoded locations or personal data in code.
- [ ] Manifest permissions justify each requested permission.
- [ ] All API calls use HTTPS; no cleartext traffic.
- [ ] Logging redacts sensitive data (token, location) in release builds.
- [ ] Play Store Data Safety form filled accurately.
- [ ] Privacy policy linked and disclosed.

### 5.4 Compliance Gate

- [ ] Mapbox attribution visible on map.
- [ ] Mapbox ToS compliance (no offline bundling, no API abuse).
- [ ] Google Play policies (no malware, no privacy violations, no deceptive UX).
- [ ] Accessibility: WCAG AA on Explore, PlaceDetail, Emergency (48 dp targets, TalkBack tested, contrast ≥ 4.5:1).
- [ ] All strings localized (en, hi, or).
- [ ] App icon, screenshots, store listing completed.

---

## 6. Test Automation Tools

| Tool | Purpose | Usage |
|---|---|---|
| **JUnit 4** | Unit test framework | Test domain logic (use cases, repositories, GuidanceEngine) |
| **Robolectric** | Android unit tests on JVM | Test ViewModels, Room queries, Android Utils without emulator |
| **Mockk** | Mocking library | Mock Mapbox APIs, LocationRepository, ConnectivityRepository |
| **Truth** | Assertion library | Fluent assertions for lists, objects, flows |
| **Compose Testing** | UI component testing | Test ExploreScreen, SearchScreen, PlaceCard rendering |
| **Turbine** | Flow testing | Test StateFlow emission, order, and timing |
| **Espresso** | UI testing framework | Integration tests on emulator; click buttons, check text |
| **ActivityScenarioRule** | Activity testing | Test screen lifecycle, back navigation, state restoration |
| **Firebase Test Lab (Optional)** | Cloud device testing | Run tests on real devices (Google Cloud) |

---

## 7. Test Schedule

| Phase | Timeline | Deliverable |
|---|---|---|
| **Unit tests** | Week 2–4 (ongoing) | 70% code coverage for domain + data layers |
| **Integration tests** | Week 3–5 (ongoing) | ViewModel + Repository integration; Room persistence |
| **UI tests** | Week 4–6 (ongoing) | Compose component tests; navigation flow |
| **Manual testing** | Week 5–7 | Full flows on 3 device tiers; edge cases (offline, permissions, lifecycle) |
| **Stress testing** | Week 6 | Long guidance session, offline region spam, cluster expansion |
| **Play Protect scan** | Week 7 | Submit APK to Play Store; await malware scan |
| **Release readiness** | Week 7 (end) | All gates passed; launch approved |

---

## 8. Known Test Gaps and Mitigations

| Gap | Reason | Mitigation |
|---|---|---|
| **End-to-end Mapbox API tests** | Requires live API calls; quota costs; flaky due to API variance | Unit tests mock APIs; manual integration tests use real Directions/Search APIs with small payloads |
| **Real device Android 8.0 testing** | Low-end devices rare in team | Emulator with Android 8.0 API 26; test on one real low-end device (Redmi 6A) |
| **8-hour guidance session (real time)** | Time-prohibitive | Compressed test: 1 hour real time simulating 8-hour guidance via fake location updates |
| **Play Protect scan** | Requires actual upload to Play Store | Gate before GA; rescan if APK changes significantly |
| **Penetration testing** | Requires security expert; cost | Optional for MVP; recommended for v1.1 if budget allows |

---

**Document Owner:** QA Team  
**Last Updated:** October 2026  
**Next Review:** 1 week before GA launch
