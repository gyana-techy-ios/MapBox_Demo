# Raahi: Final QA Report

Date: 2026-10-08. Scope: full automated suite, manual screen-flow walkthrough on a real build with a live Mapbox token, fixes, static gates, release build.

## 1. Build results and toolchain

| Item | Result |
|------|--------|
| `:app:assembleDebug` | Pass |
| `:app:testDebugUnitTest` | Pass, 214 tests, 0 failures |
| `:app:lintDebug` | Pass, 0 errors |
| `:app:connectedDebugAndroidTest` | Pass, 31 tests, 0 failures (emulator API 34, arm64) |
| `:app:assembleRelease` (R8 minify + shrink resources) | Pass; unsigned APK re-signed with the debug key and smoke-tested |

Toolchain: Gradle 8.14.3, AGP 8.13.0, Kotlin 2.2.20, KSP 2.2.20-2.0.4, Compose BOM 2025.09.00, Navigation 2.9.4, Lifecycle 2.9.4, Room 2.8.4, Retrofit 3.0.0, OkHttp 4.12.0, JDK 21 (Android Studio JBR), compileSdk/targetSdk 36, minSdk 26. Host macOS 26.2.

## 2. Test counts per class

Unit (JVM), 214 total: AppResultTest 1, DirectionsRepositoryTest 11, DownloadCoordinatorTest 4, EmergencyContactsViewModelTest 10, EmergencyViewModelTest 6, ExploreViewModelTest 19, FindNearestEmergencyUseCaseTest 4, GuidanceEngineTest 10, GuidanceViewModelTest 12, HttpErrorMapperTest 4, InstructionLanguageTest 3 (new), MapboxConfigTest 5, NearbyViewModelTest 7, OfflineFeatureTest 7, OfflineLiveSizeTest 2 (new), OfflineRegionPickerViewModelTest 7, OnboardingViewModelTest 5, PlaceDetailViewModelTest 12, Polyline6Test 4, SavedViewModelTest 4, SearchBoxRepositoryTest 13, SearchResultSanitizerTest 5, SearchViewModelTest 16, SettingsViewModelTest 3, StopPickerViewModelTest 2, TileEstimatorTest 7, TokenRedactorTest 1, TripDetailAndPreviewTest 8, TripEditorViewModelTest 10, TripValidatorTest 8, TripsViewModelTest 4.

Instrumented (device), 31 total: BottomNavigationTest 3, ExploreTokenMissingTest 4, NavigationGraphTest 5, OnboardingFlowTest 3, PlacesFeatureUiTests 9, TripsOfflineUiTests 7.

## 3. Screen-flow walkthrough

Environment: Pixel 3a API 34 arm64 emulator, 1080x2220, live token, `adb emu geo fix 85.8245 20.2961` (Bhubaneswar), fresh install. Legend: Pass = worked as designed, Fixed = defect found and fixed in this pass, Known = documented limitation, n/a = not applicable, `-` = not exercised.

| Screen | Arrive | Leave | Back | Cancel | Fail | No internet | No permission | Rotation | Process death |
|--------|--------|-------|------|--------|------|-------------|---------------|----------|---------------|
| Onboarding (3 pages) | Pass | Pass (Get started / Skip) | Pass | n/a | n/a | n/a | Pass | - | - |
| Location permission step | Pass | Pass | Pass | Pass (Continue without location) | Pass (Try again) | n/a | Pass (Allow and Deny both reach Explore) | - | - |
| Explore | Pass | Pass | Pass (closes results, then exits) | Pass (close results) | Pass | Pass (banner, cached map) | Pass (FAB asks again, Find nearby CTA) | Pass | Pass |
| Nearby | Pass | Pass | Pass | n/a | Pass | Pass | Pass | - | - |
| Search | Pass | Pass | Pass (IME first, then screen) | Pass | Pass | Pass (offline message) | n/a | Pass (query kept) | - |
| PlaceDetail | Pass; deep link Pass | Pass | Pass | Pass (share sheet) | n/a | Pass | n/a | - | - |
| RoutePreview | Pass | Pass | Pass | n/a | Pass | Pass (routing offline message) | Pass (Location needed state) | Fixed (Start was unreachable in landscape) | - |
| Guidance | Pass | Pass (Exit, confirm dialog, back to preview) | Pass (asks to confirm) | Pass (Keep navigating) | n/a | - | - | - | - |
| Trips list | Pass | Pass | Pass | Pass | Pass | Pass | n/a | - | - |
| TripEditor | Pass | Pass | Pass (discard dialog) | Pass | Pass (validation) | n/a | n/a | Pass (draft kept) | - |
| TripDetail | Pass; deep link Pass | Pass | Pass | Pass (delete confirm) | Pass (missing trip screen) | Pass | n/a | - | Pass |
| Offline list | Pass | Pass | Pass | Pass (Cancel download) | Pass | Pass | n/a | - | - |
| Offline region picker | Pass | Pass | Pass | Pass | Pass | Pass | n/a | - | - |
| Saved | Pass | Pass | Pass | Pass | n/a | Pass | n/a | - | - |
| Emergency | Pass | Pass (dialer opens, return works) | Pass | n/a | n/a | Pass | Pass | - | - |
| Emergency contacts | Pass | Pass | Pass | Pass | Pass (invalid phone rejected) | Pass | n/a | - | - |
| Profile / Settings / Language / About | Pass | Pass | Pass | n/a | n/a | n/a | n/a | - | - |

Additional checks: theme Light, Dark, System checked on Explore, Trips and Settings (all legible); Satellite style renders; clear history works; Hindi and Odia strings render on tabs, Explore chips and Language, back to English works; About links open Chrome and return; deep links `raahi://place?...` and `raahi://trip/999` work cold and hot; back-stack check from Guidance returned PlaceDetail, Search, Explore, then exit with no duplicate destinations; no app crash or ANR in `logcat -b crash` over the whole session.

## 4. Issues found and resolved

| # | Issue | Root cause | Fix |
|---|-------|-----------|-----|
| 1 | Offline download stuck at 0 percent / 0 B forever (never completed) | Mapbox delivers `loadStylePack` / `loadTileRegion` callbacks on the calling thread's Looper; calls were made from `Dispatchers.Default` (no Looper) so no callback ever fired | All TileStore/OfflineManager calls now run on the main dispatcher (`MapboxOfflineMapRepository`). Verified: download reaches Ready, 23 MB |
| 2 | Offline region size stayed 0 B until completion | UI read the stored size; progress only updated bytes from the style pack | Tile phase now reports `completedResourceSize`; `OfflineRegionMapper` shows live bytes while downloading |
| 3 | Directions language always `en`; TTS language independent | Hardcoded value | `InstructionLanguage` maps app locale to a supported Directions language (hi and or fall back to en, verified with curl); TTS uses the same object |
| 4 | Route preview and trip detail maps did not fit the route (zoom stayed at initial) | `fitBounds` ran before the map attached to its viewport state and was dropped | `RaahiMapState` queues fits until the first idle |
| 5 | RoutePreview Start button and map unreachable in landscape | Sheet used a fixed 340 dp list height, taller than the viewport | Landscape layout: map and sheet side by side, list scrolls, Start always visible |
| 6 | Explore tab could not return to Explore root (re-opened stale Emergency or Contacts) | `popUpTo(saveState) + restoreState` restored the saved nested stack | Explore tab pops to root; other tabs open their root |
| 7 | Hot deep links ignored while the app was running | No `onNewIntent` handling | `RaahiApp` registers an `OnNewIntentListener` that calls `handleDeepLink` |
| 8 | PlaceDetail snackbars covered the action buttons | Buttons lived at the end of the scroll content | Directions and Save moved to a bottom action bar; Scaffold places the snackbar above it |
| 9 | PlaceDetail header map stole vertical scroll drags | Map view consumed touch events even with gestures disabled | A pointer-input overlay on non-interactive maps blocks the MapView; scrolling passes to the page |
| 10 | My-location FAB could cover fitted markers near the right edge | FAB column and fit padding used unrelated numbers | Shared `FabColumnReserve` is used as the right fit padding; FAB inset is a named constant |
| 11 | `git grep "pk.eyJ"` matched a unit-test fixture | Realistic-looking dummy token | Fixture changed to `pk.fake-test-token` |

## 5. Known issues and limitations

- Guidance works only while the app is on screen (no foreground service); stated on the Guidance screen.
- Directions v5 has no Hindi or Odia; spoken and written instructions stay in English for those locales. Search results use `hi` or `en`.
- Revoking location mid-guidance makes Android kill the app process; not testable beyond relaunch (Explore and RoutePreview handle missing permission gracefully).
- TripDetail map for a "start from my location" trip fits only the known stops (one point), so it zooms to the destination.
- The pre-download size estimate (6.6 MB for the test area) is about 3.5x lower than the actual 23 MB because it excludes style, glyph and sprite resources.
- Offline: search, routing and nearby results need internet (nearby has a cache). Downloaded areas cover map rendering only. Interrupted downloads resume manually (no WorkManager).
- Trips screen shows a bare "Plan a trip" text button above Recent directions when there are no saved trips but there is history.
- Emergency FAB on Explore is not exposed with a text label in the UI Automator tree (TalkBack reads it through the compose semantics; not verified with TalkBack).
- Parking photo and note from the original PRD were not built; parking stores a name and location only (PRD still lists it as a goal).
- Gradle debug APK is 124 MB (all ABIs, debug symbols); see performance notes.

## 6. Mapbox dependencies

Mapbox Maps SDK for Android 11.32.0 with `maps-compose` extension (MapboxMap, annotations, viewport, location puck). Search Box API and Directions v5 are called over HTTP with Retrofit; no Search SDK or Navigation SDK. Tile loading uses `com.mapbox.common.TileStore` and `OfflineManager` from the Maps SDK. Maven repository is anonymous (no downloads token).

## 7. Offline limitations

- Style: Mapbox Standard, zoom 10 to 14 (Roads and places) or 10 to 16 (Street detail). Satellite is not downloaded.
- Verified: a 14-tile Bhubaneswar area downloaded to Ready (23 MB) and the map rendered with Wi-Fi and data off.
- Mapbox tile store has an ambient cache limit and an offline region quota; very large areas are rejected by the estimator or storage check.
- Only completed regions are shown on the offline map; partial regions need Resume.

## 8. Security checks

- Token: `local.properties` (gitignored, never committed) supplies `MAPBOX_ACCESS_TOKEN`; `git grep "pk\.eyJ"` returns nothing; `git ls-files | grep local.properties` lists only `local.properties.example`.
- Secret tokens (`sk.`) are rejected at runtime by `MapboxConfig`.
- Logging: HTTP logging is enabled only in debug and the access token is redacted by `TokenRedactor`; release builds do not log HTTP. No token is printed by the app.
- Release APK contains the public token in BuildConfig, which is expected for a public `pk.` token; restrict it by URL or package in the Mapbox account.
- Manifest: only `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS` and network state/internet permissions; deep-link activity is exported only for the `raahi` scheme.

## 9. Static gate results

| Gate | Result |
|------|--------|
| Colour literals (`Color(0x`, `#RRGGBB`) in `app/src/main/kotlin` | Hits only in `ui/theme/` (Color.kt) |
| Hardcoded `Text("...")` / `contentDescription = "..."` | None; the only literals are interpolated number labels (`"$count"`, `"${index + 1}"`) and composed descriptions built from string resources |
| `com.mapbox` imports outside `map/`, `offline/`, `core/config`, Application | None |
| `git grep "pk\.eyJ"` | No matches |
| `git ls-files \| grep local.properties` | `local.properties.example` only |
| TODO / FIXME in code and docs | None |
| Lint | 0 errors |
| Minified release (R8 + resource shrinking) | Builds; installed with the debug key; search, nearby, place detail, route preview and guidance work, so no extra keep rules were needed (existing rules cover serialization and Mapbox) |

## 10. Performance notes

- APK sizes: debug 124 MB, release unsigned 103 MB. Most of the size is Mapbox native libraries for all ABIs; an ABI split or app bundle would deliver roughly a quarter of that per device.
- Emulator observations: no formal benchmark was run; Explore was usable within about 5 s of launch, first map tiles took about 5 to 10 s on the emulator network, route preview 1 to 8 s depending on route length; no jank noticed in Explore, Nearby or Guidance; no ANR.

## 11. Screenshots

Stored outside the repository (about 38 MB) in `/private/tmp/claude-501/raahi-shots/`, prefix `qa_`:
01 onboarding page 1, 02 onboarding page 3, 03 and 04 and 05 permission step and dialog, 06 and 07 Explore with permission denied (stale DNS emulator), 08 and 09 Explore allowed, 10 hospitals results, 11 Nearby, 12 PlaceDetail, 13 save snackbar above action bar, 14 search, 15 share sheet, 16 route preview, 17 guidance, 18 exit dialog, 19 trips, 20 editor, 21 add stop, 22 two stops, 23 trip detail, 24 discard dialog, 25 delete dialog, 26 after delete, 27 undo snackbar, 28 offline list, 29 picker, 30 downloading, 31 ready, 32 label sheet, 33 saved, 34 and 35 emergency, 36 dialer, 37 contacts, 38 invalid phone, 39 profile, 40 settings, 41 settings dark, 42 trips dark, 43 explore dark, 44 clear history, 45 satellite, 46 Hindi, 47 Odia, 48 Odia explore, 49 Odia profile, 50 and 51 About, 52 settings light, 53 and 54 search landscape, 55 explore landscape, 56 and 58 route preview landscape (before and after), 57 route preview fit (before and after), 59 recents, 60 restored after process death, 61 explore offline, 62 and 63 and 64 permission revoked, 65 deep link place, 66 and 67 deep link trip, 68 release build explore. (Landscape captures from `adb emu screenrecord screenshot` are rotated 90 degrees; that is a capture artefact.)
