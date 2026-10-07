# Raahi: Use Cases and Detailed Flows

**Date:** October 2026  
**Status:** MVP Specification

---

## Use Case 1: Explore and Nearby Essentials

### 1.1 Overview

The user opens the app and wants to find nearby places (fuel, hospital, food) without planning a full trip. The map-first interface shows the user's current location, a search bar, and category chips for quick discovery.

### 1.2 Actors

- **User:** Any person opening the app to explore.
- **Mapbox Search Box API:** provides autocomplete suggestions and category search.
- **Location service:** provides user's current position (GPS/network).
- **Local database (Room):** stores recent searches and saved places.

### 1.3 Main Flow: Category Discovery

**Preconditions:**
- App is open.
- Location permission is granted (precise or approximate).
- Internet connection is available (or cached results exist).

**Steps:**

1. User opens the app → Explore screen loads with the map centered on current location (blue puck).
2. User sees a search bar and category chips (Fuel, EV Charging, Hospital, Pharmacy, Police, ATM, Restaurant, Hotel, Parking).
3. User taps the "Fuel" chip.
4. App searches for `DiscoverQuery.Category.GAS_STATION` within 50 km of current location.
5. Search Box API returns up to 25 results.
6. App clusters results by zoom level:
   - If < 1 km: show individual markers.
   - If 1–10 km: show clusters with count badges.
   - If > 10 km: aggregate into one cluster.
7. User scrolls the list or taps a cluster on the map → cluster expands to show individual places.
8. User taps a place card → details open (name, address, phone, distance, navigate button).
9. User taps "Navigate" → navigates to the place (starts Drive mode or shares to Google Maps if offline is unavailable).

**Alternate Flow: Search by Name**

1. User taps the search bar.
2. Keyboard opens; user types "Agra".
3. App calls `PlaceAutocomplete.suggestions()` with debounce (>300 ms between requests).
4. Search Box API returns up to 5 suggestions (places, addresses, regions).
5. User taps a suggestion.
6. App calls `PlaceAutocomplete.select()` to get coordinates.
7. Map center moves to the suggestion; details card appears.

**Alternate Flow: Recent Searches**

1. User taps the search bar (empty state).
2. App displays a list of recent searches (up to 10).
3. User taps a recent search → map navigates to that place.
4. User long-presses a recent search → delete confirmation appears.

**Failure Flow: No Internet**

1. User is offline; taps a category chip.
2. App shows a toast: "Search requires internet. View your saved places instead?"
3. User taps "Saved Places" → app shows locally stored places (home, car, favorites).

**Failure Flow: Search Returns No Results**

1. User searches for a very specific/rural location.
2. API returns 0 results.
3. App shows an "Add a Place" card: "Don't see what you're looking for? Help us improve the map by adding it."
4. Card contains a deep link to Mapbox map feedback form (external).

### 1.4 Acceptance Criteria

| Criterion | Details |
|---|---|
| **Search latency** | Autocomplete response < 500 ms; results appear instantly. |
| **Cluster correctness** | Clusters are accurate at each zoom level; no double-counting of places. |
| **Category coverage** | All 8 categories (Fuel, EV, Hospital, Pharmacy, Police, ATM, Food, Hotel) are available and function. |
| **Offline behavior** | When offline, recent searches and saved places load from local DB within 200 ms. |
| **List/Map sync** | Tapping a result in the list highlights the marker on the map; panning the map does not de-highlight. |
| **Place details accuracy** | Phone numbers, addresses, and coordinates match the Mapbox place record. |

---

## Use Case 2: Multi-Stop Trip Planner and Route Comparison

### 2.1 Overview

The user plans a multi-stop journey (e.g., Delhi → Mathura → Agra → Gwalior) and compares up to 3 route alternatives by time, distance, and congestion. The user can then share the itinerary or start driving with in-app guidance.

### 2.2 Actors

- **User:** traveller planning a journey.
- **Mapbox Directions API v5:** computes routes, alternatives, steps.
- **Mapbox Search Box:** provides place suggestions for stops.
- **Local database (Room):** stores trips and waypoints.
- **Drive Mode Engine:** custom on-device guidance system (custom implementation, not Navigation SDK).

### 2.3 Main Flow: Create and Compare Routes

**Preconditions:**
- App is open on the Explore screen or a new trip is initiated.
- Internet is available (at minimum for route computation).

**Steps:**

1. User taps "Create Trip" button.
2. Trip Editor opens; origin is pre-filled with current location (or user searches for a starting point).
3. User adds stops:
   - Tap "Add Stop" → search bar appears.
   - Type place name → autocomplete suggestions appear.
   - Tap a suggestion → stop is added; origin OR previous stop becomes the start point.
   - Repeat up to 25 stops.
4. User sets route profile (Driving-Traffic, Driving, Walking, Cycling).
5. User (optional) sets exclusions (avoid Tolls, Motorways, Ferries).
6. User taps "Compare Routes".
7. App requests 3 routes from Directions API with parameters:
   - `alternatives=true`
   - `steps=true`
   - `annotations=duration,distance,congestion_numeric`
   - `overview=full`
   - `exclude=toll` (if user selected).
8. API returns 3 routes (limited to 3 by API for ≥2 waypoints; ≥3 waypoints allow only 2 alternatives or none).
9. App displays routes side-by-side on the map:
   - Route 1 (fastest): green.
   - Route 2 (short): blue.
   - Route 3 (scenic/least tolls): purple.
10. Each route shows:
    - Distance (e.g., "487 km").
    - ETA (e.g., "7 h 23 min").
    - Congestion summary (e.g., "Light traffic for 45 km").
11. User taps a route to select it; the selected route is highlighted.
12. User can:
    - **Edit:** drag stops to reorder (updates all routes in real-time).
    - **Save:** tap "Save Trip" → dialog asks for name → trip is stored in Room.
    - **Share:** tap "Share" → static image of route + summary text (e.g., "Delhi → Agra, 487 km, 7h 23m") is generated via Static Images API and shared via WhatsApp, SMS, email, or copied as a geo link.
    - **Drive:** tap "Start Driving" → Drive Mode starts.

**Alternate Flow: Multi-Stop Optimization (Deferred to v1.2)**

In v1.2, if the user has > 2 stops and a Premium subscription, they can tap "Optimize Order" → app calls Optimization API (or local solver) to reorder stops for shortest total distance.

**Alternate Flow: Save and Reuse**

1. User has a saved trip "Delhi-Agra".
2. User taps "Edit Trip" → load the trip from Room.
3. User can modify stops, profile, or exclusions; re-compare routes.
4. User taps "Save" → update is saved (overwrite) or "Save As" → new trip is created.

**Failure Flow: Route Not Found**

1. User creates a trip Delhi → Falkland Islands (hypothetical).
2. API returns `"code": "NoRoute"` (no road connectivity).
3. App shows an error: "No route found between these points. Check the locations and try again."

**Failure Flow: Too Many Stops for Alternatives**

1. User has 5 stops and requests alternatives.
2. API returns only 1 route (alternatives not available for > 2 waypoints in v1).
3. App shows a message: "Alternatives are available for 2-stop routes. You can view and compare them with a trip upgrade (coming soon)."

### 2.4 Drive Mode Flow

**Preconditions:**
- Trip is created and a route is selected.
- User taps "Start Driving".
- Location permission is granted.

**Steps:**

1. Drive Mode screen opens; map is shown with the full route (polyline) and the next 3 maneuvers in a list.
2. App center the map on the user's current location; the puck is always visible.
3. User location is tracked every 1 second (or GPS update frequency, whichever is higher).
4. App compares the user's location against the route geometry:
   - If on-route (< 10 m error): show "On route" indicator.
   - If off-route (> 10 m for > 5 s): show "Off Route, Recalculate?" button.
5. For each step in the cached route:
   - When user approaches the turn (500 m ahead), announce the maneuver: "In 500 m, turn right towards Mathura."
   - Use Android TextToSpeech with fallback to text display.
   - As user gets closer (100 m), update to: "In 100 m, turn right."
   - At the turn, next maneuver is prepared.
6. User can:
   - Tap "Recalculate" (off-route) → if online, request a new route from current position to destination.
   - Tap "End Trip" → Drive Mode closes; trip is marked complete.
   - Tap a stop in the list → map zooms to that stop.
7. Screen stays on (no sleep) while Drive Mode is active.
8. If app goes to background (user minimizes), guidance continues via a notification (v1+; foreground service). In MVP, background guidance is not available; guidance pauses if the app is backgrounded.

**Offline Drive Mode:**

1. User has pre-downloaded an offline region (e.g., Delhi-Agra corridor).
2. Directions steps are cached from the last online route computation.
3. User starts Drive Mode offline.
4. Guidance continues using cached steps and on-device GPS.
5. Reroute is not available offline; user must return to Explore screen to manually plan a new trip.

### 2.5 Acceptance Criteria

| Criterion | Details |
|---|---|
| **Stop management** | User can add/remove/reorder stops without lag; each change updates routes immediately. |
| **Route computation latency** | 3 routes return in < 5 s for normal connectivity. |
| **Alternative display** | Each alternative is distinct in color/style; user can toggle between them. |
| **Distance/ETA accuracy** | Match Mapbox Directions API response; not estimated values. |
| **Maneuver clarity** | Text maneuvers match the step description from Directions steps; TTS is natural. |
| **Off-route detection** | Deviations > 10 m for > 5 s trigger a notification; recovery from accidental deviation is possible. |
| **Offline guidance** | Cached routes work without internet; reroute is disabled offline. |
| **Trip save/load** | Saved trips load with all stops, profile, and last-selected route; no data loss on process death. |
| **Share format** | Shared image includes map, route, distance, ETA, all stops, and is readable at phone screen resolution. |

---

## Use Case 3: Offline Journey Packs

### 3.1 Overview

The user downloads an offline map region in advance for a planned trip. The app provides size estimates, download progress, resume capability (via cancel + retry), and management UI for deletions and expiry tracking.

### 3.2 Actors

- **User:** traveller preparing for a trip.
- **Mapbox OfflineManager / TileStore:** manages offline tile packs.
- **Local database (Room):** stores offline region metadata (ID, size, date, expiry).
- **WorkManager:** long-running download task (background, resumable).

### 3.3 Main Flow: Download a Region

**Preconditions:**
- App is open on the Explore screen.
- User has planned a trip (optional; download is independent of trips).
- Device has WiFi connection (default; user can override).

**Steps:**

1. User taps "Download" on the bottom bar → Offline screen opens.
2. Offline screen shows a list of downloaded regions and a "Download New Region" button.
3. User taps "Download New Region" → region picker opens (map with a draggable polygon).
4. User adjusts the polygon by dragging corners to cover the planned trip corridor (e.g., Delhi to Agra).
5. User sets zoom level: preset (Standard: 11–15, or Detailed: 15–16).
6. App computes tile count and size estimate:
   - Polygon geometry + zoom range → tile estimate (e.g., "1,240 tiles, ~120 MB").
   - Includes style pack (~5 MB).
7. App checks available device storage (e.g., "Available: 2.5 GB").
8. User reviews the estimate and taps "Download".
9. Confirmation dialog appears:
   - "Download 120 MB to [device]? Download over WiFi only? [Toggle: ON]."
   - User can toggle WiFi-only off (not recommended for low-data users).
10. User confirms → download starts.
11. WorkManager enqueues a long-running task to fetch tiles from Mapbox TileStore.
12. Download progress is shown:
    - "Downloaded 340 MB of 450 MB (75%), ~2 min remaining."
    - Progress bar updates every 100 tiles or 2 seconds.
13. Download can be interrupted:
    - User navigates away or closes the app; download continues in background.
    - If user taps "Cancel", download is aborted; partially downloaded tiles are kept (for resume).
14. Download completes:
    - Region is added to the local DB with metadata: ID, size, download date, auto-expiry date (+30 days).
    - Notification: "Region downloaded: Delhi-Agra, 450 MB. Available offline for 30 days."
15. Region appears in the "Downloaded Regions" list.

**Alternate Flow: Resume a Cancelled Download**

1. User cancelled a download of the Delhi-Agra region (100 MB completed, 350 MB remaining).
2. User taps the region in the Offline screen → a "Resume" button appears.
3. User taps "Resume" → download resumes from where it left off.
4. Mapbox TileStore automatically fetches only missing tiles (no re-download of completed tiles).

**Alternate Flow: Delete a Region**

1. User taps a downloaded region in the Offline screen → options menu appears.
2. User taps "Delete" → confirmation: "Delete Delhi-Agra region (450 MB)?"
3. User confirms → region is removed from Room and TileStore; storage is freed.
4. If any trips reference the deleted region, they can still be used (user must re-download to use the trip offline).

**Failure Flow: Insufficient Storage**

1. User attempts to download a 200 MB region on a device with only 50 MB free.
2. App checks storage and shows an error: "Insufficient storage. Free up 150 MB and try again."
3. User sees an option to delete older regions.

**Failure Flow: Network Interruption**

1. Download is 50% complete; WiFi connection drops.
2. App detects the offline state (via ConnectivityManager).
3. If WiFi-only is enabled (default), download is paused (WorkManager suspends the task).
4. Notification appears: "Download paused: WiFi lost."
5. When WiFi returns, download resumes automatically.
6. If user was on mobile data (WiFi-only disabled), download continues on mobile data.

### 3.4 Offline Region Expiry

1. Region is downloaded on Oct 1, marked to expire on Oct 31.
2. On Oct 25, the user opens the Offline screen.
3. App checks expiry dates; if a region is within 7 days of expiry, it shows a "Update" button.
4. User taps "Update" on a region nearing expiry → the region is re-downloaded (new ID, new expiry).
5. Old region is automatically deleted once the new download completes.

### 3.5 Acceptance Criteria

| Criterion | Details |
|---|---|
| **Estimate accuracy** | Tile count and size estimates are within ±15% of actual download. |
| **Download speed** | ≥ 1 Mbps over fast WiFi; user can see remaining time. |
| **Resume reliability** | Cancelled downloads resume without re-downloading completed tiles; no data loss. |
| **Cancel responsiveness** | Download cancels within 2 seconds of user tapping "Cancel". |
| **Offline availability** | Region is immediately available for offline map viewing once download completes. |
| **Expiry tracking** | Regions are marked "outdated after 30 days"; update reminders appear at 7 days. |
| **Storage management** | User can see total downloaded size and delete regions to free space. |
| **Background persistence** | Download continues in background (WorkManager); app can be closed. |

---

## Use Case 4: My Places and Emergency Mode

### 4.1 Overview

The user saves personal anchors (home, car, favorites) and accesses emergency features (call 112, share location, find nearby hospitals) with a single tap. Emergency mode works offline using cached data or GPS location.

### 4.2 Actors

- **User:** traveller or emergency situation.
- **Location service:** provides GPS position.
- **Local database (Room):** stores saved places, emergency contacts, cached search results.
- **Android system:** handles ACTION_DIAL (call), share intent.
- **Mapbox Search Box API:** provides online hospital/pharmacy search (cached in Room for offline).

### 4.3 Main Flow: Save a Place

**Preconditions:**
- App is open.
- User is on the Explore screen or any map-visible screen.

**Steps:**

1. User long-presses the map at a desired location.
2. A context menu appears: "Save as Home / Work / Favorite / Parked Car".
3. User selects "Parked Car".
4. Dialog opens: "Save Parked Car Location".
5. User can optionally:
   - Take a photo of the parking spot (camera button).
   - Add a note (e.g., "Block A, 5th floor").
   - Confirm the location (map preview is shown).
6. User taps "Save" → location is stored in Room with:
   - Coordinates, name, photo (if taken), note, timestamp.
7. Confirmation: "Parked car saved. Tap 'My Places' to view all."

**Alternate Flow: Add Emergency Contact**

1. User opens the Profile screen → taps "Emergency Contacts".
2. Contacts list is shown (initially empty).
3. User taps "Add Contact".
4. Dialog opens: "Name" (text field) and "Phone" (numeric field).
5. User enters "Mom: +91 98765 43210" and taps "Save".
6. Contact is stored in Room; no sync to cloud.

### 4.4 Emergency Mode Flow

**Preconditions:**
- App is open (or user has just launched it from emergency situation).
- Location permission is granted or cached location is available.

**Steps:**

1. User taps the SOS button (red icon) on the bottom bar.
2. Emergency Mode screen opens with 4 primary actions:
   - **Call 112:** taps a button → ACTION_DIAL intent with "112" pre-filled.
   - **Call 108:** taps a button → ACTION_DIAL intent with "108" pre-filled.
   - **Share Location:** taps a button → geo link is generated (e.g., `geo:28.6139,77.2090`) and shared via SMS/WhatsApp/email.
   - **Find Hospital:** taps a button → nearest hospital search (see below).
3. Below the action buttons, a "Nearby Hospitals" section shows:
   - If online: latest results from a Search Box category search (cached in Room on first retrieval).
   - If offline: cached results marked "cached 4 hours ago" (timestamp from last online search).
4. User taps a hospital in the list → details open (name, address, phone, distance, navigate button).
5. User taps "Call" → phone dialer opens with the hospital phone number.

**Offline Emergency Mode:**

1. User is offline (no WiFi, no cellular).
2. User opens Emergency Mode.
3. "Share Location" button still works (generates a geo link; user can copy it to clipboard or share when connectivity returns).
4. "Call 112" and "Call 108" buttons work (no internet needed; ACTION_DIAL uses the dialer).
5. "Find Hospital" section shows:
   - Cached results from the last online search, with a timestamp: "Cached 12 hours ago".
   - If no cached results exist, a message: "No cached hospitals. Go online to search."
6. User can still navigate to a saved place (e.g., saved home address) using offline maps and cached directions.

### 4.5 Cyclone Preparedness Mode

1. User opens the Emergency Mode screen.
2. A "Preparedness Checklist" section appears (free for all users):
   - [ ] Download home district offline map.
   - [ ] Add emergency contacts (name, phone).
   - [ ] Save shelter locations (or tap to find nearest shelters).
   - [ ] Share plan with family (copy checklist as text).
3. User can check each item as they complete it (state stored locally).
4. "Shelters" section shows:
   - Nearest known shelters (government, NGO, high ground) from a cached/curated list.
   - Distance and directions to each.
5. User taps a shelter → "Save as Safe Place" option appears; taps to save for offline access.

### 4.6 Acceptance Criteria

| Criterion | Details |
|---|---|
| **Save place latency** | Place is saved to Room within 200 ms; confirmation appears instantly. |
| **Emergency contact retrieval** | Contacts are loaded from Room in < 100 ms; lists are always available offline. |
| **Call intent behavior** | Tapping "Call 112" opens the dialer with 112 pre-filled; no delay. |
| **Share location format** | Geo link is correctly formatted (geo://lat,lng) and works in all share targets (SMS, WhatsApp, email, clipboard). |
| **Hospital search (online)** | Search completes in < 3 s; up to 10 results are shown, sorted by distance. |
| **Hospital search (offline)** | Cached results load instantly; timestamp shows freshness; results are clearly marked as cached. |
| **Photo storage** | Photos taken are stored locally and displayed on the saved place; no upload. |
| **Place edit** | User can edit a saved place (name, note, photo) and save changes without data loss. |

---

## Summary

| Use Case | Main Goal | Key Output |
|---|---|---|
| **Explore & Nearby** | Find places of interest without planning a trip | List of nearby places, clustered on map |
| **Trip Planner** | Plan and compare multi-stop journeys; navigate with in-app guidance | Saved trips, route comparison, Drive Mode guidance |
| **Offline Packs** | Download maps for offline navigation | Downloaded region, available offline for 30 days |
| **My Places & Emergency** | Save personal anchors and access emergency features | Saved places, emergency contacts, one-tap SOS actions |

---

**Document Owner:** Product Team  
**Last Updated:** October 2026
