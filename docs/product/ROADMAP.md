# Raahi: Product Roadmap

**Date:** October 2026  
**Planning Horizon:** 18 months (MVP through v1.2)  

---

## MVP (Launch: Q4 2026)

### Goals
- Establish Raahi as a trip-first, offline-friendly companion for Indian highway travellers.
- Validate core use cases: trip planning, offline maps, emergency access.
- Build a freemium subscription base for recurring revenue.
- Achieve 50k downloads and 10k MAU by end of Q4 2027 (12 months post-launch).

### Features Shipped

#### Search and Exploration
- **Explore home screen:** map with location puck, search bar, category chips.
- **Category chips:** Fuel, EV Charging, Hospital, Pharmacy, Police, ATM, Restaurant, Hotel, Parking.
- **Autocomplete search:** text input with place suggestions (debounced).
- **Place details:** address, phone, distance, directions, save/share options.
- **Recent searches:** persistent list, long-press to delete.
- **Add a Place UGC link:** direct users to Mapbox feedback for rural gaps.

#### Trip Planning and Navigation
- **Trip editor:** add/remove/reorder up to 25 stops with drag-and-drop.
- **Route profiles:** Driving-Traffic, Driving, Walking, Cycling.
- **Route comparison:** up to 3 alternatives (2-waypoint trips only, per Directions API constraint).
- **Route preview:** full geometry on map; distance, ETA, congestion per route.
- **Save/duplicate trips:** persist in Room; load and edit later.
- **Share trips:** static image + text summary via WhatsApp, SMS, email, or geo link.
- **Drive Mode (custom on-device guidance):**
  - Next maneuver, remaining distance/ETA.
  - Off-route detection (>10m for >5s) with "Recalculate" option (online only).
  - Android TextToSpeech + text fallback for voice guidance.
  - Screen stays on; foreground-only (no background service in MVP).

#### Offline Maps
- **Region picker:** draggable polygon on map to define download area.
- **Zoom presets:** Standard (11–15) or Detailed (15–16).
- **Size/tile estimate:** display before download.
- **Storage check:** verify available space; suggest deletions if needed.
- **Download manager:** progress bar, cancel button, WorkManager background task.
- **Resume downloads:** cancel + re-request same region ID fetches only missing tiles.
- **Offline availability:** map rendering works in downloaded regions without internet.
- **Region expiry:** marked "outdated after 30 days"; update reminders at 7 days.
- **Honest offline matrix:** map rendering YES; search NO; reverse geocoding NO; new routing NO; cached route guidance YES; POI search NO.

#### My Places and Emergency
- **Saved places:** home, work, favorites (up to 10 free; unlimited in Premium).
- **Parked car:** save location with optional photo and walk-back note.
- **Emergency contacts:** add name, phone (local storage, no sync).
- **Emergency screen:** one-tap buttons for Call 112, Call 108, Share Location.
- **Cached hospital search:** show nearest hospitals from last online search, marked "cached N hours ago" when offline.
- **Cyclone preparedness:** checklist (free for all), save shelters, offline access.

#### Monetization
- **Free tier:**
  - 10 searches/day limit.
  - 2 saved trips (max 3 stops).
  - 1 offline region download.
  - 10 saved places.
  - Emergency mode (always free).
  - Category chip limit (3 taps/day).

- **Raahi Plus (INR 49/month or INR 399/year):**
  - Unlimited searches.
  - Unlimited trips (up to 25 stops).
  - Unlimited offline regions.
  - Unlimited saved places.
  - Unlimited Drive Mode trips.
  - Route comparison (all alternatives, save for comparison).
  - Export trips (PDF itinerary, GeoJSON stops).
  - Curated Yatra/corridor packs (included, once available).

#### Settings and Preferences
- **Theme:** system / light / dark (Material 3 color tokens).
- **Language:** English, Hindi, Odia (per-app language; locales_config).
- **Map style:** Standard (light/dark presets), Satellite (preview).
- **Route defaults:** preferred profile, exclude tolls/motorways/ferries toggles.
- **Download settings:** WiFi-only toggle (default ON), disk quota management.
- **Voice guidance:** enable/disable TTS, volume control.
- **History management:** clear recent searches, clear trip history.

#### Onboarding
- **3-page animated pager:** app intro, safety disclaimers, feature overview.
- **Location permission flow:** precise, approximate, denied, permanently denied handling.
- **Notification permission (Android 13+):** request for Drive Mode alerts.
- **Deep link handling:** `raahi://place?lat=&lng=&name=` and `raahi://trip/{id}`.

### Technical Implementation

- **Single app module** layered by package (core, domain, data, location, offline, map, ui, navigation, feature).
- **MVVM** with immutable UiState in StateFlow.
- **Persistence:** Room (trips, stops, places, contacts, searches, offline metadata) + DataStore (preferences).
- **DI:** manual constructor DI via AppContainer (no Hilt).
- **Mapbox:** Maps SDK 11.32.0 + Compose + Search SDK 2.32.0 (PlaceAutocomplete, Discover, SearchEngine).
- **HTTP:** Retrofit + OkHttp + kotlinx.serialization for Directions v5.
- **Location:** FusedLocationProvider behind LocationRepository.
- **Connectivity:** ConnectivityMonitor exposing Online / Limited / Offline states.
- **Error handling:** sealed AppError type mapped to localized, actionable messages.
- **Localization:** en, hi, or (no backend translation; static resource strings).
- **Design system:** Material 3, zero hardcoded colors, edge-to-edge, 48dp touch targets.

### Release Gates (Must-Pass Tests)

1. **Functional:** all 4 use cases pass UAT with no critical bugs.
2. **Performance:** cold start < 3s, map load < 2s, search latency < 500ms, crash rate < 0.5%.
3. **Security:** no secret tokens in APK, location stored only locally, no analytics backend.
4. **Compliance:** Play Store policy check, Play Data Safety form filled, offline ToS adherence (no bundling).
5. **Accessibility:** WCAG AA on key screens (Explore, Trip, Emergency), TalkBack tested.

### Known Limitations

- No offline search (premium Mapbox feature).
- No offline reverse geocoding (premium).
- No offline new-route calculation (requires internet Directions API or Nav SDK).
- No turn-by-turn with Navigation SDK (deferred; foreground-only on-device guidance for MVP).
- No multi-user or family sharing (no backend).
- No curated Yatra packs in MVP (editorial effort; post-launch as one-time packs).
- Search Box POI coverage is incomplete in rural areas (honest communication: "nearest known").
- No push notifications (no backend).
- Offline regions limited to 750 packs by Mapbox; no way to combine regions.

---

## v1.1 (Q1–Q2 2027)

### Goals
- Professional turn-by-turn navigation via Mapbox Navigation SDK v3.
- Offline routing for downloaded regions.
- Background guidance (foreground service, no screen required).
- Curated Yatra packs (launch with Char Dham, Puri Rath Yatra).
- Grow MAU from 10k to 25k; retain 20% of Premium subscribers.

### New Features

#### Navigation SDK Integration
- **Professional turn-by-turn UI:** courtesy of Mapbox Navigation SDK v3.
- **Turn-by-turn in foreground service:** guidance continues in background; notification shows current maneuver.
- **Automatic reroute:** when user deviates > 50m for > 10s, auto-fetch new route (online only).
- **Route refresh:** periodically refresh current route for updated ETAs and traffic.
- **Voice guidance:** Mapbox voice API for professional audio (language selection based on app language).
- **Android Auto integration:** steering wheel control, voice-only UI for safe in-car experience.
- **Lane guidance and junction views:** Mapbox-provided views for complex intersections.

#### Offline Routing
- **Navigation tileset regions:** download routing tiles alongside map tiles (OfflineManager + TileStore).
- **Offline A-to-B routing:** request routes within downloaded regions without internet.
- **Offline reroute limitation:** reroute only within downloaded region; if user leaves region, re-route on next online opportunity.

#### Curated Yatra Packs

| Pack | Region | Contents | Price |
|---|---|---|---|
| **Char Dham** | Haridwar, Auli, Yamunotri, Kedarnath, Badrinath | Curated stops (temples, rest points), facilities (dharamshalas, medical camps, ATMs), stage-wise itinerary | INR 199 (one-time) |
| **Puri Rath Yatra** | Puri, Konark, surrounding coastal villages | Darshan timing, mela logistics, nearby lodging, medical points, parking guides | INR 149 (one-time, seasonal) |
| **Delhi-Goa Corridor** | NH 48 highway: Delhi, MP, MH, KA, TN, KL | Fuel stops, food landmarks, hotel chains, scenic viewpoints, typical rest points | INR 99 (one-time) |
| **Bengal-Northeast Highway** | NH 31, 54, 37: WB, AS, MG, TR | Fuel, medical, lodging for low-infrastructure areas; regional cuisine spots | INR 99 (one-time, seasonal) |

**Implementation:**
- Packs are static GeoJSON layers (stops, facilities as point/polygon features).
- Pre-sized offline tile packs included with each (e.g., Char Dham = 300 MB tiles).
- Itinerary as a PDF (generated once, bundled).
- No backend; packs are bundled in the APK or downloaded as separate assets.
- One-time purchase via Google Play Billing (SKU per pack).

#### Enhanced Trip Planner
- **Curated Yatra integration:** user can template a trip from a pack (e.g., start with Char Dham stops, customize).
- **Estimated fuel breaks:** along a route, app suggests fuel stops based on fuel consumption heuristic (distance / 10 km per liter).
- **Along-route essentials:** show fuel, food, lodging in a vertical scrollable list during trip preview.

### Deprecations and Changes

- **Drive Mode:** renamed to "In-App Navigation" or kept as "Drive Mode" but now refers to the Navigation SDK integration.
- **Custom on-device guidance (MVP Drive Mode):** deprecated once Nav SDK is integrated; existing trips still supported but transition to Nav SDK UI.

### Release Gates

1. All v1 features stable and tested with Navigation SDK v3.
2. Offline routing validated for sample corridors (Delhi-Manali, Bhubaneswar-Puri, Rishikesh-Badrinath).
3. Yatra packs curated, data verified, and legal approval (ASI / Wikidata licenses) obtained.
4. Android Auto compliance verified.
5. Crash rate < 0.3%; battery usage measured for background foreground service.

---

## v1.2 (Q3–Q4 2027)

### Goals
- Stop optimization and advanced trip planning.
- Reachability visualization (Isochrone API).
- Trip optimization solver for multi-stop efficiency.
- Personal dead-zone heatmap from signal history.
- Grow MAU to 40k; Premium conversion to 8%.

### New Features

#### Optimization API Integration
- **Stop reordering:** user with 3+ stops and a Premium subscription can tap "Optimize Order" → app calculates the shortest total distance by calling Optimization API v1 (limited to 12 stops; for >12, use local solver).
- **Cost-based optimization:** optionally weight stops by estimated fuel cost (for budget-conscious trips).
- **Drag-to-reorder fallback:** users can still manually reorder stops if they prefer (optimization is optional).

#### Isochrone (Reachability)
- **"How far can I get in 30 minutes?" overlay:** tap "Reachability" on the planner → show a polygon of all places reachable in 15, 30, 45, 60 minutes from current location.
- **Filter amenities by reachability:** "Show fuel within 30-min drive" chip.
- **Depart-time sensitivity:** show isochrone for different departure times (e.g., 10 am vs 6 pm, accounting for traffic).

#### Personal Dead-Zone Heatmap
- **Background signal logging (opt-in):** if user enables, app periodically logs signal strength (0–5 bars) and GPS location (during Drive Mode or background location tracking if enabled).
- **Heatmap visualization:** Personal Dead Zone map shows areas with weak signal as a colored heatmap overlay (red = no signal, yellow = weak, green = strong).
- **Dead-zone alert:** before a trip, if planned route passes through a known dead zone, show a warning: "This route has weak signal for 45 km (Ghat range). Download offline maps?"

#### Enhanced Sharing and Export
- **GPX export:** save a trip as a GPX file; share with other GPS apps (e.g., Komoot, AllTrails, other offline navigation tools).
- **PDF itinerary:** enhanced with photos of key stops, elevation profile, fuel budget, phone numbers of saved contacts.
- **QR code trip link:** generate a QR code that others can scan to load your trip (deep link `raahi://trip/{id}`).

#### Advanced Trip Features
- **Multi-day trips:** save trip segments and chain them (v1 MVP trips are single-leg; v1.2 allows multi-day with rest stop suggestions).
- **Vehicle fuel/range profile:** user sets vehicle type (Petrol, Diesel, EV, Bike) and efficiency; app auto-calculates fuel stops and range warnings.
- **Elevation profile:** show elevation gain/loss along the route (from DEM tiles, if available offline).
- **Scenic route suggestion:** "Prefer scenic over fast" option → use Optimization API or heuristic to select a more visually interesting route.

#### Data Export and Sync (Local)
- **Backup trips to file:** user can export all trips, saved places, and offline metadata as a JSON file and save to Downloads folder (manual backup, no cloud).
- **Restore from backup:** load previously exported backup to restore trips and places (useful after app reinstall).

### Deprecations

- **Static trip sharing (MVP):** superseded by richer PDF + GPX export and QR code.

### Release Gates

1. Optimization API integration stress-tested with 12-stop trips.
2. Isochrone rendering performance tested (polygon with traffic time weights).
3. Dead-zone heatmap privacy verified (data stored locally; no upload).
4. PDF export tested for readability and file size (< 5 MB per trip).
5. Backup/restore validated for no data loss.

---

## Backlog (Post-v1.2)

### High Priority

#### Crowd-Sourced Signal Map (BE required)
- Users opt-in to share anonymized signal measurements.
- Heatmap is computed on a backend and pushed to all users (live crowd view).
- Enables the "dead zone prediction" feature for everyone, not just personal history.

#### Offline Search (Premium Mapbox Feature)
- Once Mapbox releases OfflineSearchEngine out of beta and into GA, integrate it.
- Enable offline text search for places in downloaded regions.
- Likely requires licensing upgrade (sales negotiation).

#### EV Mode with Live Charger Availability
- Integrate with charger networks (e.g., Tata Power, Statiq, OlaCharge APIs).
- Show live charger availability and reservation (deep links or native integration).
- Estimate EV range based on battery % and elevation profile.

#### OSDMA/NDMA Shelter Integration (Regulatory Approval)
- Integrate official shelter dataset from Odisha State Disaster Management Authority (OSDMA) and National Disaster Management Authority (NDMA).
- Requires official partnership and data licensing.
- Enables automated shelter location for cyclone preparedness (no UGC needed).

### Medium Priority

#### Multi-Language Voice Guidance
- Mapbox voice API supports multiple languages; integrate based on app language setting.
- Regional languages: Hindi, Marathi, Tamil, Telugu, Kannada, Malayalam.

#### Trip Journal and Photos
- Users can attach photos to trip segments and add notes.
- Auto-populate with time and location.
- Export as a trip memory book (PDF or gallery format).

#### Wearable Integration (Wear OS)
- Smartwatch app for turn-by-turn guidance.
- Quick access to saved places from wrist.
- SOS button on wearable for emergency mode.

#### Android 15+ Predictable Launch
- Optimize cold start using Android 15 predictable launch (when available).
- Pre-warm Mapbox SDK if app is predicted to launch soon.

### Lower Priority

#### Live Traffic Replay
- Record traffic conditions during a trip; playback later to show how traffic evolved.
- Useful for understanding congestion patterns on favorite routes.

#### Community Recommendations (BE required)
- Users share favorite scenic stops, food places, scenic viewpoints along common routes.
- Others can see and rate recommendations.
- Requires backend for moderation and user trust.

#### Smart Offline Packing (ML)
- Based on user's past trips, ML model predicts which regions user is likely to travel to next.
- Suggests preemptive offline downloads.
- Minimizes user action; works only if personalization privacy is acceptable.

---

## Release Schedule Summary

| Version | Target Release | Key Features | MAU Target | Revenue Model |
|---|---|---|---|---|
| **MVP** | Q4 2026 | Trip planner, offline maps, Drive Mode, emergency | 10k | Free + INR 49/mo |
| **v1.1** | Q2 2027 | Nav SDK, offline routing, Yatra packs | 25k | + INR 99–199 packs |
| **v1.2** | Q4 2027 | Optimization, isochrone, heatmap, export | 40k | Same |
| **v1.3+** | 2028 | Crowd signal, offline search, EV, wearable | 100k+ | TBD (licensing, affiliate) |

---

## Success Criteria by Release

### MVP Success
- 50k downloads in first 3 months post-launch.
- 10k MAU by month 12.
- 500 Premium subscribers.
- < 0.5% crash rate.
- > 40% day-7 retention.
- 100+ positive reviews on Play Store (4.5+ rating).

### v1.1 Success
- 25k MAU (2.5x MVP).
- 1.5k Premium subscribers (3x MVP).
- Android Auto functional and featured in Play Store.
- Char Dham pack revenue > INR 50k in season.
- < 0.3% crash rate.

### v1.2 Success
- 40k MAU.
- 3k Premium subscribers.
- Optimization and isochrone use by > 20% of Premium users.
- Personal heatmap active in 5% of app sessions.
- > INR 100k/month from one-time pack sales (seasonal).

---

**Document Owner:** Product Team  
**Last Updated:** October 2026  
**Next Review:** Q1 2027
