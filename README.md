# Raahi

*Your journey, even when the signal isn't.*

Offline-first road-trip and journey companion for India. Kotlin, Jetpack Compose (Material 3) and Mapbox.

## Setup

1. Install Android Studio (JDK 17+ for Gradle; Studio's bundled JBR 21 works) and the Android SDK (platform 36).
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
4. Build and test:

   ```sh
   ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
   ./gradlew :app:connectedDebugAndroidTest   # needs an emulator or device
   ```

## Architecture

Single `app` module layered by package under `com.raahi.app`:

| Package | Responsibility |
|---|---|
| `core/` | config (token), DI container, connectivity, `AppResult`/`AppError`, dispatchers, clock |
| `domain/` | models, repository interfaces, use cases (`GuidanceEngine`, `TileEstimator`, ...) |
| `data/` | Retrofit/OkHttp clients (Search Box, Directions), Room, DataStore, repository implementations |
| `location/` | Fused location repository |
| `offline/` | Mapbox TileStore based offline maps |
| `map/` | The only UI-side Mapbox code (`RaahiMap`) |
| `ui/` | Design system (theme, components) |
| `navigation/` | Type-safe Navigation Compose graph and bottom bar |
| `feature/` | One package per screen |

Rules: `com.mapbox.*` imports only in `map/`, `offline/`, `core/config` and `RaahiApplication`; all colors come
from `ui/theme`; all user-facing strings are resources with `values-hi` and `values-or` translations.
