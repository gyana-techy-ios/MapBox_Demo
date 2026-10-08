# Raahi

*Your journey, even when the signal isn't.*

Raahi is an offline-first road-trip and journey companion for India, built with Kotlin, Jetpack Compose (Material 3) and Mapbox. Find fuel, hospitals, ATMs and food around you, plan multi-stop trips, follow turn-by-turn guidance with voice, download map areas for use without signal, keep saved places and emergency contacts on the phone, and reach emergency numbers in one tap. English, Hindi and Odia. No account, no backend.

## Features

- Explore: Mapbox Standard and Satellite maps, category chips (fuel, EV, hospitals, pharmacies, ATMs, food, hotels, parking), nearby results sheet, my-location, save parking.
- Search: Search Box API suggestions, recent searches, place details with share, save, Home or Work.
- Trips: multi-stop editor (search or saved places, reorder), car, traffic, walk and bike profiles, avoid tolls, motorways and ferries, route alternatives, trip detail, recent directions.
- Guidance: on-device step-by-step engine with off-route detection, ETA, voice (Android TextToSpeech).
- Offline: download map areas (Mapbox TileStore), live progress and size, Wi-Fi-only option, delete.
- Emergency: 112 and 108 dialer shortcuts, nearest hospitals, pharmacies and police, share location, personal emergency contacts.
- Settings: light, dark or system theme, default travel mode, avoid options, language, privacy controls.

## Setup

1. Install Android Studio (JDK 17+ for Gradle; Studio's bundled JBR works) and the Android SDK (platform 36).
2. Copy `local.properties.example` to `local.properties` (it is gitignored) and fill it in:

   ```properties
   sdk.dir=/path/to/Android/sdk
   MAPBOX_ACCESS_TOKEN=pk.your_public_token_here
   ```

   Use a **public** token (`pk.`). Secret tokens (`sk.`) are rejected at runtime and must never ship in the app.
   `MAPBOX_ACCESS_TOKEN` may also be supplied as an environment variable (CI).
   Without a valid token the app still runs: the map is replaced by a "Map unavailable" panel and
   network features report a configuration error.
3. No Mapbox downloads token is needed. The Maven repository is read anonymously; do not add credentials.

## Build and test

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # or any JDK 17+
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest      # needs an emulator or device
./gradlew :app:assembleRelease                # minified, unsigned
```

Current status: 214 unit tests and 31 instrumented tests pass, lint has 0 errors. See `docs/FINAL_QA_REPORT.md`.

## Architecture overview

Single `app` module layered by package under `com.raahi.app`:

| Package | Responsibility |
|---|---|
| `core/` | config (token), DI container, connectivity, `AppResult`/`AppError`, dispatchers, clock |
| `domain/` | models, repository interfaces, use cases (`GuidanceEngine`, `TileEstimator`, ...) |
| `data/` | Retrofit/OkHttp clients (Search Box, Directions), Room, DataStore, repository implementations |
| `location/` | Fused location repository |
| `voice/` | Text-to-speech implementation of `VoiceGuide` |
| `offline/` | Mapbox TileStore based offline maps |
| `map/` | The only UI-side Mapbox code (`RaahiMap`) |
| `ui/` | Design system (theme, components) |
| `navigation/` | Type-safe Navigation Compose graph and bottom bar |
| `feature/` | One package per screen |

Rules: `com.mapbox.*` imports only in `map/`, `offline/`, `core/config` and `RaahiApplication`; all colors come
from `ui/theme`; all user-facing strings are resources with `values-hi` and `values-or` translations.

Search and routing use the HTTP Search Box API and Directions v5 through Retrofit; there is no Mapbox Search or Navigation SDK.

## Documentation index

| Document | Content |
|----------|---------|
| `docs/FINAL_QA_REPORT.md` | Final QA pass: results, issues fixed, known issues, gates |
| `docs/architecture/ARCHITECTURE.md` | Architecture, data model, offline design, DI, ADRs |
| `docs/design/UX_FLOW.md` | Screens, routes, callbacks, quality-gate matrix |
| `docs/design/DESIGN_SYSTEM.md` | Colour, type and component tokens |
| `docs/product/` | PRD, ideation, roadmap, use cases |
| `docs/mapbox/MAPBOX_CAPABILITIES.md` | Mapbox research |
| `docs/security/SECURITY.md` | Token handling, logging, privacy |
| `docs/testing/TEST_PLAN.md` | Test strategy |
