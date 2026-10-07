# Raahi: Product Requirements Document

**Date:** October 2026  
**Status:** MVP Specification  
**Version:** 1.0  

## 1. Executive Summary

**Name:** Raahi  
**Tagline:** "Your journey, even when the signal isn't."  
**Category:** Offline-first Road Trip Companion  
**Target Audience:** Indian self-drive and two-wheeler travellers (families, weekend road-trippers, pilgrims' families) aged 25–55 in metros and Tier-2 cities; secondary: coastal households preparing for cyclones.

**Value Proposition:** Plan the whole journey, compare routes, download dead-zone stretches in advance, and know where the nearest fuel, hospital and safe place are—even with zero network. Trip-first and offline-first, built for Indian highways and pilgrim routes.

**Business Model:** Free core (2 trips, 1 region, rate-limited searches) + Raahi Plus subscription (INR 49–99/month, INR 399–599/year) + seasonal one-time packs (INR 99–199 per Yatra/corridor).

**MVP Scope:** 4 use cases (Explore & Nearby, Trip Planner, Offline Packs, My Places & Emergency); no backend required; Map rendering and turn-by-turn guidance are foreground-only (no background service in MVP; Navigation SDK deferred to v1.1).

---

## 2. Problem Statement

### 2.1 The Core Problem

Indian highway trips cross multi-hour dead zones (ghat ranges, Northeast corridors, rural stretches). Travellers lose navigation, cannot find fuel or emergency hospitals, compare routes by guesswork, and rely on cellular networks that drop unpredictably. The existing Google Maps + offline doesn't solve the journey-planning problem (map viewing alone isn't enough; route comparison, corridor-aware essentials, and integrated trip storage are missing). For coastal households, cyclones bring network outages when they most need to reach shelters.

### 2.2 Why Existing Solutions Fall Short

- **Google Maps offline:** downloads are for a city, not a corridor; no trip planning or multi-stop comparison.
- **Dedicated navigation (Apple Maps, Waze):** require active internet for re-routing; no offline packs, no trip storage.
- **Hiking apps (AllTrails):** designed for trekking, not highway trips.
- **No India-native app:** combines offline maps + trip planning + corridor-aware essentials.

---

## 3. Target Users and Jobs to Be Done

### 3.1 Personas

**Priya, Weekend Self-Drive Planner (28, Delhi)**
- Plans 4–5 trips a year: Delhi-Jaipur, Delhi-Mussoorie, Goa highway.
- Pain: compares routes manually, worries about fuel on unfamiliar roads, loses connectivity in ghat ranges.
- Job: plan the route, find fuel/food ahead of time, share itinerary with family, navigate offline.

**Arun, Pilgrimage Organizer (55, Bangalore)**
- Organises annual family trips to Char Dham, Puri Rath Yatra.
- Pain: multi-day itineraries are hard to coordinate; elderly family members need clear routes and nearby hospitals; seasonal crowd info is unreliable.
- Job: build an itinerary, save places to visit, pre-download maps, share with family, find emergency services.

**Meera, Solo Two-Wheeler Traveller (32, Chennai)**
- Monthly bike trips across Tamil Nadu, Kerala coasts.
- Pain: signal drops, map freezes mid-navigation, fuel gaps are scary on a bike.
- Job: pre-plan the ride, know fuel stops, have directions and emergency contacts offline.

**Vikram, Coastal Resident (40, Odisha)**
- Lives in a cyclone-prone area (Odisha coast).
- Pain: during Cyclone Fani/Yaas, power and mobile networks died; didn't know how to reach shelters.
- Job: pre-download maps, save shelter locations, access them offline when networks fail.

### 3.2 Jobs to Be Done

1. **Plan a multi-stop journey:** define start, up to 25 stops, choose a vehicle profile (driving-traffic, driving, cycling), avoid tolls/motorways/ferries, compare 2–3 alternatives.
2. **Explore nearby essentials:** find fuel, hospitals, ATMs, restaurants, hotels without typing addresses; see results on a map.
3. **Download offline maps:** download a corridor or region in advance so navigation and maps work offline.
4. **Know where I am in an emergency:** one-tap call 112/108, share location, find nearest hospital/pharmacy offline.
5. **Save places I care about:** home, parked car, emergency contacts, so I can return to them or share with family.

---

## 4. Features and Use Cases

### 4.1 Use Case 1: Explore and Nearby Essentials

**Scope:** Home screen map with search, category discovery, place details, recent searches, saved places.

**MVP Features:**
- **Search Box with autocomplete:** type place names; results are autofilled with maps/location images.
- **Category chips:** Fuel, EV Charging, Hospital, Pharmacy, Police, ATM, Restaurant, Hotel, Parking (labelled "nearest known" to set expectations).
- **Clustered markers:** results are clustered by zoom; tap to see individual places.
- **List/Map toggle:** switch between map and scrollable list.
- **Place details:** tap a place to see name, address, phone, directions, option to save/share.
- **Recent searches:** auto-populated; long-press to remove.
- **Add/Correct a place:** UGC fallback for rural gaps; link to contact Mapbox (no backend).

**Acceptance Criteria:**
- Given I open the app, When the map loads, Then I see my current location (blue puck) and a search bar.
- Given I search "fuel", When suggestions appear, Then I see up to 5 ranked results with icons.
- Given I tap "Fuel" chip, When I'm in a region (e.g. Delhi), Then I see all known fuel stations within 50 km, clustered by zoom.
- Given I tap a fuel station, When details open, Then I see name, address, navigate option, and save option.
- Given I'm offline, When I tap a saved place, Then directions work (if the route is pre-cached) or navigate to the cached location.

**Not in MVP:**
- Real-time fuel prices, opening hours, user reviews (would require backend).
- Offline search (premium Mapbox feature, deferred).

---

### 4.2 Use Case 2: Multi-Stop Trip Planner and Route Comparison

**Scope:** Create, edit, compare, and preview routes with up to 25 stops.

**MVP Features:**
- **Trip creation:** set origin (current location or search), add up to 25 stops, drag-reorder stops.
- **Route profiles:** driving-traffic, driving, walking, cycling.
- **Exclude options:** tolls, motorways, ferries.
- **Route comparison:** request up to 3 alternatives (Directions API constraint for 2-waypoint trips only); see distance, ETA, congestion for each.
- **Route preview:** full route geometry on the map; tap a stop to see details.
- **Trip details:** total distance, ETA, estimated fuel stops, summary of stops.
- **Save/duplicate trips:** save a trip to the device; load and reuse later.
- **Share itinerary:** static image of the map or text summary (name, stops, distance, ETA).
- **Drive mode:** in-app turn-by-turn computed on-device from Directions steps, not the Navigation SDK. Features:
  - Next maneuver and remaining distance/ETA
  - Off-route detection with throttled reroute (no auto-reroute, user requests manually)
  - Android TextToSpeech voice guidance with fallback to text
  - Screen stays on
  - Foreground-only (background guidance is v1.1+)

**Acceptance Criteria:**
- Given I create a trip, When I set origin and add 3 stops, Then the app calculates all routes and shows the fastest.
- Given I request route comparison, When I see 3 alternatives, Then each shows distance, ETA, and color-coded by time.
- Given I start Drive mode, When maneuvers appear, Then I hear the next turn and see distance remaining.
- Given I deviate from the route, When I am >50m off-track, Then the app offers to recalculate and shows a "Recalculate" button.
- Given I am offline, When Drive mode is active, Then guidance continues using cached Directions data; no rerouting without internet.

**Not in MVP:**
- Route optimization (stop reordering for efficiency; Optimization API deferred).
- Turn-by-turn with navigation SDK (v1.1).
- Background guidance or Android Auto (v1.2).

---

### 4.3 Use Case 3: Offline Journey Packs

**Scope:** Download and manage offline maps for routes and regions.

**MVP Features:**
- **Region picker:** tap on the map, drag to define a polygon or use preset zoom bounds.
- **Zoom presets:** zoom 11–15 (standard), zoom 15–16 (detailed).
- **Size and tile-count estimate:** compute and display before download.
- **Storage check:** verify available device storage before starting.
- **Download:** show progress (tiles downloaded, % complete, time remaining).
- **Cancel and retry:** pause is not available (Mapbox limitation); cancel and retry re-requests missing tiles.
- **Delete:** remove a region to free space; warn of affected trips.
- **Expiry management:** regions marked "outdated after 30 days"; offer refresh.
- **Wi-Fi-only downloads:** enabled by default; toggle in settings.
- **Pre-cached essentials:** when a trip is planned, cache 1 result per category (fuel, hospital, ATM) along the route into the local database.

**Honest Offline Matrix (MVP):**

| Capability | Offline? | How |
|---|---|---|
| Map rendering (vector, Standard style) | **Yes** | Style pack + tile region |
| Route preview / Line of sight | **Yes** | Cached Directions JSON |
| Location puck | **Yes** | GPS hardware |
| Place text search | **No** | Premium Mapbox feature; use saved places instead |
| Reverse geocoding | **No** | Only online via Mapbox |
| Turn-by-turn guidance | **Yes** | Cached Directions steps, on-device computation, Android TTS |
| A-to-B routing (new route calculation) | **No** | Directions API requires internet |
| POI category search (fuel, hospital) | **No** | Online only; use pre-cached results or saved places |
| Satellite imagery | **No** | Download size prohibitive for MVP |

**Acceptance Criteria:**
- Given I am on the map, When I tap "Download", Then a polygon appears and I can drag corners to adjust.
- Given I set a region, When I see the size estimate, Then I can confirm or cancel before download starts.
- Given a download is in progress, When the screen locks, Then download continues in a foreground service (WorkManager).
- Given a download completes, When I close the app and reopen it offline, Then the region is available and I can view the map.
- Given I have multiple regions, When storage is running low, Then the app prompts me to delete older regions or reduce zoom.

**Not in MVP:**
- Pause/resume (not supported by Mapbox; workaround is cancel + retry with same ID).
- Pre-bundled packs (Yatra, corridor packs deferred to post-MVP as bundled GeoJSON layers).
- Offline search.

---

### 4.4 Use Case 4: My Places and Emergency Mode

**Scope:** Save personal anchors (home, car, contacts, shelters) and one-tap emergency access.

**MVP Features:**
- **Saved places:** home, work, favorites (up to 10 free; unlimited in Premium).
- **Parked car:** save location with optional photo and walk-back note (e.g., "5th floor, Block A"); tap to see directions back.
- **Emergency contacts:** add name, phone number (no sync; local only).
- **Emergency screen:** one-tap access; shows:
  - Nearest known hospitals / pharmacies / police (from recent online search or cached results).
  - Call 112 (national emergency) or 108 (ambulance) buttons (ACTION_DIAL, no CALL permission).
  - Share my location via SMS/WhatsApp as a geo link.
  - Cached results marked "cached N hours ago" when offline.
- **Cyclone/Storm preparedness:** optional checklist (download home district offline pack, save shelters, add emergency contacts); free for all users.

**Acceptance Criteria:**
- Given I save home, When I long-press the map, Then a dialog appears to save the location.
- Given I'm in the Emergency screen, When I tap "Call 112", Then the phone dialer opens with 112 pre-filled.
- Given I'm offline, When I tap the Emergency screen, Then I see my cached hospital results (even if > 24 h old) with a timestamp.
- Given I save a parked car with a photo, When I return to the car, Then the photo and note help me remember the spot.
- Given the app is in background, When I ask to share my location, Then a geo link is copied to the clipboard or sent via share sheet.

**Not in MVP:**
- Live family tracking (requires backend).
- Real-time emergency dispatch (liability and regulatory complexity).
- Verified hospital capability data (regulatory approval needed).

---

## 5. Non-Goals

- **Live family location tracking:** deferred; requires backend, sync, and push.
- **Community reports / crowd-sourced hazards:** deferred; cold-start problem, backend needed.
- **Turn-by-turn with Navigation SDK:** MVP uses foreground-only guidance; Nav SDK is v1.1.
- **Background guidance / foreground service without screen:** MVP is foreground-only.
- **Curated Yatra packs in MVP:** bundled GeoJSON is post-MVP (editorial effort).
- **Analytics or telemetry backend:** no server, no data collection.
- **User authentication or accounts:** all data is local.
- **Offline search:** premium Mapbox feature.
- **EV live charger availability / real-time fuel prices:** requires external APIs (EXT).

---

## 6. Success Metrics

### 6.1 Product Metrics

| Metric | Goal | Rationale |
|---|---|---|
| Time to first working journey | < 3 minutes from app open | Low friction for trip planning |
| Offline map download success rate | > 95% | Reliability is critical for trust |
| Turn-by-turn guidance accuracy | 98% correct maneuvers | Safety in navigation |
| Saved places retention | > 50% users save ≥ 1 place at 30d | Engagement proxy |
| Offline route availability | 95% of downloaded regions have ≥ 1 cached trip | Usefulness of offline download |
| Emergency mode response time | < 500 ms to nearest hospital | Speed is critical in emergencies |

### 6.2 Business Metrics

| Metric | Target (12 months) | Note |
|---|---|---|
| Downloads | 50k | Organic + Play Store featuring |
| Monthly Active Users (MAU) | 10k | Tier-1/2 cities, word-of-mouth |
| Freemium conversion | 5% | INR 49/month is low friction |
| Raahi Plus subscribers | 500 | Recurring revenue anchor |
| One-time pack revenue | 10k INR/month | Seasonal Yatra packs (v1+) |
| Retention at day 7 | 40% | Typical for travel apps |
| Retention at day 30 | 15% | Repeat travellers |
| Support ticket volume | < 100/month | Keep automation high |

### 6.3 Technical Metrics

| Metric | Target |
|---|---|
| App size (Release APK) | < 80 MB |
| Map load time (cold start) | < 2 s |
| Search autocomplete latency | < 500 ms per keystroke |
| Offline region download speed | ≥ 2 Mbps over WiFi |
| Location puck update frequency | ≥ 1 Hz |
| Crash-free rate | > 98% |
| ANR rate | < 0.1% |

---

## 7. Monetization Strategy

### 7.1 Free Tier

- Map, explore, nearby essentials (10 searches/day limit).
- Saved places (up to 10).
- Trip planner (up to 2 saved trips, max 3 stops per trip).
- One offline region download (small, e.g., ~ 1 district).
- Emergency mode (always free).

### 7.2 Raahi Plus (Subscription)

**Price:** INR 49/month or INR 399/year (6-month equivalent + 1 free month; INR ~33/month).

**Includes:**
- Unlimited searches.
- Unlimited saved places.
- Unlimited trips (up to 25 stops per trip).
- Unlimited offline regions + larger corridor downloads.
- Turn-by-turn guidance unlimited (no trip-count cap).
- Route comparison (all alternatives, save comparisons).
- Export (GPX, GeoJSON, PDF itinerary).
- Curated Yatra/corridor packs (included).

### 7.3 One-Time Packs (v1+)

**Price:** INR 99–199 per pack (e.g., Char Dham, Delhi-Goa corridor).

**Includes:** Curated GeoJSON layer (stops, facilities, season notes) + pre-sized offline tile pack + itinerary PDF.

**Monetization note:** Packs are optional (free users can download any region); packs add structure and editorial value.

### 7.4 Affiliate and Sponsorship

- Deep links to fuel-card signup (e.g., CRED, ICICI, Jio).
- EV charger network links (e.g., Tata Power, Statiq, OlaCharge; no backend needed, just deep links).
- Hotel / dharamshala affiliates (e.g., MakeMyTrip, OYO).
- CSR sponsorship for Cyclone / disaster preparedness features.

**Ads policy:** Low tolerance. No interstitials or full-screen ads ever. Small, non-intrusive banner/native in the Explore home screen only (free tier). Never on Navigation, Emergency, or Download screens. Expect low eCPM in India; do not plan the business on ads.

---

## 8. Roadmap

### MVP (Shipped)
- Explore & Nearby Essentials
- Multi-Stop Trip Planner (up to 25 stops, 3 alternatives)
- Foreground Drive Mode (on-device guidance from cached Directions)
- Offline Map Downloads (TileStore regions)
- My Places (home, car, favorites)
- Emergency Mode (call, share location, cached hospitals)
- Raahi Plus subscription (INR 49–99/month)

### v1.1 (Q1–Q2 2027)
- Navigation SDK v3 turn-by-turn (official in-app turn-by-turn with UI)
- Offline routing (Nav SDK routing tiles)
- Background foreground service for guidance (no screen wake required)
- Android Auto support
- Curated Yatra packs (Char Dham, Puri Rath Yatra, 2 corridors)

### v1.2 (Q3–Q4 2027)
- Optimization API (stop reordering for efficiency)
- Isochrone "reach in 30 min" overlay
- Trip optimization solver
- GPX export
- Personal dead-zone map (heatmap of personal signal loss history)

### Backlog (Post-v1.2)
- Crew crowd-sourced signal map (requires backend).
- OSDMA/NDMA shelter integration (regulatory approval).
- Offline search engine (premium Mapbox beta).
- EV mode with charger live availability (EXT API partnership).
- Multi-language voice (Mapbox voice API varies by region).
- Wearable app (watch guidance).

---

## 9. Constraints and Assumptions

### Technical Constraints

1. **No backend in MVP:** all data is local (Room, DataStore). Trips, places, contacts, offline metadata stored on device.
2. **Single module architecture:** no microservices; one `app` module.
3. **Mapbox Maps SDK 11.32.0 + Compose:** Jetpack Compose, Material 3, no Hilt (manual DI via AppContainer).
4. **Offline limitations:** 750 tile pack cap, no offline search, no offline reverse geocoding, no offline new-route calculation.
5. **Turn-by-turn in MVP:** custom on-device guidance, not Navigation SDK (SDK is v1.1).
6. **Location permissions:** precise + approximate + denied states; no background location in MVP.

### Data Assumptions

1. **Mapbox Search Box coverage in India:** POI coverage (fuel, hospital, ATM, EV) is good in metros/highways, thin in Tier-3+ towns. Rural gaps are expected; app must acknowledge via "nearest known" label and offer "add a place" UGC link.
2. **Worldview IN:** Map boundaries follow Mapbox worldview IN; must validate Kashmir/Arunachal rendering.
3. **Traffic:** traffic-aware routing (driving-traffic profile) works in metros but varies outside them. Do not promise real-time accuracy.
4. **Offline tile size:** ~1 district at zoom 11–15 = 50–200 MB (estimate; varies by geometry). Must provide pre-download estimates.

### User Assumptions

1. **Device:** Android 8.0+ (API 26+), 2GB+ RAM, 500MB available storage for one region.
2. **Network:** users have intermittent WiFi; all offline downloads must be retryable and resumable.
3. **Literacy:** in-app text in English + Hindi + Odia; simple icons for categories.
4. **Data plan:** most users have limited data; app must offer WiFi-only download by default and estimate sizes.

---

## 10. Glossary

| Term | Definition |
|---|---|
| **Raahi** | App name; Hindi/Urdu for "traveller". |
| **Dead zone** | Region with zero or very poor cellular/internet connectivity (e.g. ghat ranges, Northeast highways). |
| **Corridor** | A linear path along a highway or route; Raahi allows downloading maps along a corridor. |
| **Tile pack** | Mapbox TileStore region: a set of offline vector tiles for a polygon/line/point geometry and zoom range. |
| **Style pack** | Offline copy of the map style (fonts, glyphs, resources); one per style (e.g., Standard). |
| **Trip** | A multi-stop journey: origin + stops (up to 25) + chosen route profile + optional avoidances (tolls, motorways). |
| **Drive mode** | Foreground-only in-app turn-by-turn guidance computed from cached Directions steps. |
| **Directions** | Mapbox Directions API v5: routes A-to-B with alternatives, steps, distance, ETA. |
| **Search Box** | Mapbox Search Box API: autocomplete, reverse geocoding, category search. |
| **Offline-first** | App assumes no internet and caches/pre-downloads critical data (maps, routes, places). |
| **Mapbox Plus** | Raahi's subscription tier; called "Raahi Plus" in-app. |

---

## 11. Appendix: Open Questions and Validation Spikes

Before launch, these risks must be validated:

1. **Navigation SDK offline routing for India:** Do routing tiles from Mapbox Nav SDK v3 actually work offline for Delhi-Manali, Bhubaneswar-Puri, Rishikesh-Badrinath without internet?
2. **Search Box coverage gaps:** Quantify the POI coverage gap for fuel / hospital / ATM along 5 sample corridors. How many results are found online vs missing in rural areas?
3. **Offline tile budget:** Confirm cost-per-tile and cumulative MAU cost of offering large corridor downloads at scale (10k MAU downloading 1 region/year).
4. **Worldview IN correctness:** Validate that Mapbox renders Indian borders correctly (Kashmir/Arunachal) in the Standard style used by the app.
5. **Navigation SDK trip cost at 10k MAU:** Model cost of 10k MAU, each running 2 nav trips/month, at current Mapbox pricing. Ensure free tier caps don't blow the budget.
6. **Yatra pack licensing:** Research and licence data for Char Dham, Puri Rath Yatra, and 2 corridors. Verify ASI / Wikidata / Wikicommons license terms.
7. **Device compatibility:** Test on low-end devices (Redmi 6A, Poco X2) at 50MB offline region download. Ensure no OOM or ANR.

---

**Document Owner:** Product Team  
**Last Updated:** October 2026  
**Next Review:** November 2026
