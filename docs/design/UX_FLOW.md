# Raahi: User Experience and Navigation Flows

**Date:** October 2026  
**Status:** MVP Specification  
**Version:** 1.0

---

## 1. Navigation Architecture

### 1.1 Screen Hierarchy and Bottom Bar

The app uses a bottom navigation bar with 5 primary destinations:

```
┌─────────────────────────────────────────┐
│         ▲ Map / Screen Content ▲        │
│                                         │
├────────────────────────────────────────┤
│ Explore │ Trips │ Offline│Saved│Profile│
└─────────────────────────────────────────┘
```

**Bottom bar visibility:**
- **Visible on:** Explore, Trips, Offline, Saved, Profile, Settings, Language, About.
- **Hidden on:** Onboarding, Search, PlaceDetail, Nearby, Guidance (Drive Mode), OfflineRegionPicker.

### 1.2 Navigation Graph (Compose Navigation)

```
Onboarding ──→ (no bottom bar)
    ↓
    └──→ LocationPermission ──→ NotificationPermission
            ↓
          Explore (bottom bar shown) ────→ Search (hidden bottom bar)
            ↓                                  ↓
          Trips ────────────────────→ TripEditor
            ↓                            ↓
         Offline ─────────────────→ OfflineRegionPicker (hidden bottom)
            ↓                            ↓
         Saved ────────────────────→ SavedPlaceEditor
            ↓
         Profile ─────────────────→ Settings
                                     Language
                                     About
                                     EmergencyContacts
         
         (Deep links: raahi://place?lat=&lng=&name= and raahi://trip/{id})
         (From any screen → PlaceDetail, Guidance)
```

---

## 2. Launch and Onboarding

### 2.1 Cold Start / First Run (Onboarding)

**Preconditions:** App is installed and launched for the first time.

**Flow:**

1. **Onboarding Pager (3 pages, full-screen):**
   - Page 1: App intro ("Your journey, even when the signal isn't.")
   - Page 2: Feature highlights (maps, trips, offline).
   - Page 3: Safety disclaimer (no real-time data, use with caution).
   - Bottom: "Skip" button, dots (page indicators), "Next" button on pages 1–2, "Start" button on page 3.
   - Bottom bar is hidden.

2. **After page 3 "Start":**
   - Navigate to LocationPermissionScreen.

3. **LocationPermissionScreen:**
   - Title: "Enable Location for Better Routes"
   - Explanation: "Raahi needs your location to show where you are on the map and calculate routes from your current position."
   - Buttons:
     - "Allow precise location" → requests ACCESS_FINE_LOCATION.
     - "Allow approximate location" → requests ACCESS_COARSE_LOCATION (fallback).
     - "Ask me later" → skip to next screen (but location features will degrade).
   - Result handling:
     - Granted → show checkmark, proceed.
     - Denied once → show "Ask me later" option.
     - Permanently denied → show "Go to Settings" link.
   - Bottom bar is hidden.

4. **NotificationPermissionScreen (Android 13+):**
   - Title: "Stay Updated"
   - Explanation: "Notifications help you stay on top of your trips and offline map updates."
   - Buttons:
     - "Allow notifications" → requests POST_NOTIFICATIONS (Android 13+).
     - "Skip" → skip to home.
   - Bottom bar is hidden.

5. **Home Screen (Explore):**
   - Now show bottom bar.
   - Map is centered on user's current location (if permission granted).
   - Show search bar, category chips, recent searches.

**State Persistence:**
- Onboarding state is saved in DataStore (key: "onboarding_completed").
- Once onboarding is complete, app always launches to Explore.

**Edge Cases:**
- Location permanently denied: show "You'll miss location features. Enable in Settings?" button on map.
- Notification permanently denied: no button; app continues (non-critical permission).

---

## 3. Explore Screen

### 3.1 Overview

**Purpose:** Map-first exploration and discovery of nearby places.

**Layout:**
```
┌──────────────────────────────────┐
│ 🔍 Search ▼           [Settings]  │  ← Search bar + top bar
├──────────────────────────────────┤
│                                  │
│                                  │
│         🗺️ MAP                   │  ← Full-screen map
│         🔵 (user puck)           │
│                                  │
│                                  │
│                                  │
├──────────────────────────────────┤
│ [Fuel] [EV] [Hospital] [Pharmacy]│  ← Category chips (scrollable)
│ [Police] [ATM] [Food] [Hotel]    │
├──────────────────────────────────┤
│ Explore │ Trips │ Offline│Saved  │  ← Bottom bar
└──────────────────────────────────┘
```

### 3.2 Interactions

**Search Bar:**
- Tap → navigate to SearchScreen.
- If user has recent searches, show them.
- Debounce autocomplete typing to 300 ms.

**Category Chips:**
- Tap a chip → search for that category (e.g., fuel, hospital).
- Results appear as clustered markers on the map.
- Clusters are colored and show count badges.
- Results also appear in a side panel (scrollable list, if space allows).

**Map:**
- Show user location (blue puck) and heading (if bearing available).
- Cluster markers by zoom level.
- Tap a marker → PlaceDetail sheet appears.
- Long-press → show "Save as Home / Work / Favorite / Parked Car" menu.

**Toolbar:**
- Left: "Map" / "List" toggle (switch between map and list view of results).
- Right: Gear icon → SettingsScreen.
- Right: Locate-me button (center map on user).

---

## 4. Search Screen

### 4.1 Overview

**Purpose:** Full-screen autocomplete search with history and suggestions.

**Entry:** Tap search bar on Explore screen.

**Exit:** Select a result → PlaceDetail, or back arrow → Explore.

**Bottom bar:** Hidden.

### 4.2 Layout

```
┌──────────────────────────────────┐
│ ← [Search input]         ✕      │  ← Back arrow, search field, clear
├──────────────────────────────────┤
│                                  │
│  Recent Searches:                │
│  • Delhi                          │
│  • Agra (5 days ago)              │
│  • Mathura                        │
│                                  │
│  Suggestions (as user types):   │
│  • Delhi Gate, Delhi (5.2 km)    │
│  • Delhi Haat, Delhi (3.1 km)    │
│                                  │
└──────────────────────────────────┘
```

### 4.3 Interactions

**Typing:**
- Debounce: 300 ms before sending autocomplete request.
- Show spinner while loading.
- Suggestions refresh as user types.

**Recent Search:**
- Long-press → delete option.
- Tap → navigate to PlaceDetail.

**Autocomplete Suggestion:**
- Tap → PlaceDetail.
- Show:
  - Place name
  - Full address
  - Distance from user (if available)
  - Icon (place type)

**Clear Button (✕):**
- Clears search field.
- Shows recent searches again.

---

## 5. Place Detail Screen

### 5.1 Overview

**Purpose:** Show full information about a place and actions (save, share, directions).

**Entry:** Search result tap, map marker tap, or deep link (`raahi://place?lat=28.6&lng=77.2&name=Delhi Gate`).

**Exit:** Back arrow → previous screen.

**Bottom sheet:** Typically 50–70% of screen height, draggable.

### 5.2 Layout

```
┌──────────────────────────────────┐
│ ← [Name: Delhi Gate]             │  ← Top bar with title + back
├──────────────────────────────────┤
│                                  │
│       📍 Map (small preview)      │  ← Map showing place
│                                  │
├──────────────────────────────────┤
│ Address: Red Fort Complex...     │
│ Phone: +91 XXXX XXXX            │
│ Distance: 2.3 km                │
│                                  │
│ [Directions] [Call] [Share] [Save]  ← Actions
│                                  │
│ Add a Place feedback link        │
│                                  │
└──────────────────────────────────┘
```

### 5.3 Actions

**Directions:**
- Button: "Get Directions"
- Tap → navigate to RoutePreviewScreen (one-stop trip: current location → this place).

**Call:**
- Button: "Call"
- Tap → ACTION_DIAL intent (open phone dialer with phone number).

**Share:**
- Button: "Share"
- Tap → share sheet (SMS, WhatsApp, email, copy link).
- Content: "Check out [Place Name] at [Geo Link]" or static image.

**Save:**
- Button: "Save to My Places"
- Tap → SavePlaceDialog (choose type: Home, Work, Favorite, Parked Car).
- If Parked Car, ask for optional photo and note.

---

## 6. Trip Planner

### 6.1 Trips Screen (List)

**Purpose:** View saved trips, start a new trip.

**Entry:** Tap "Trips" on bottom bar.

**Layout:**
```
┌──────────────────────────────────┐
│ [+ Create Trip]                  │  ← Floating action button
├──────────────────────────────────┤
│                                  │
│ Saved Trips:                     │
│ ┌────────────────────────────┐   │
│ │ Delhi-Agra                 │   │
│ │ 487 km • 7h 23m            │   │
│ │ Last modified: 2 days ago  │   │
│ │ [Resume] [Edit] [Delete]   │   │
│ └────────────────────────────┘   │
│                                  │
│ ┌────────────────────────────┐   │
│ │ Bangalore-Mysore           │   │
│ │ 145 km • 2h 15m            │   │
│ │ Last modified: 1 week ago  │   │
│ │ [Resume] [Edit] [Delete]   │   │
│ └────────────────────────────┘   │
│                                  │
│ Trips │ Offline│Saved│Profile   │  ← Bottom bar
└──────────────────────────────────┘
```

**Actions on Card:**
- **Resume:** Start this trip in Guidance mode immediately.
- **Edit:** Navigate to TripEditorScreen.
- **Delete:** Show confirmation, then delete from Room.

**Create Trip Button:**
- Navigate to TripEditorScreen (new trip mode).

### 6.2 Trip Editor Screen

**Purpose:** Create or edit a multi-stop trip.

**Entry:** "Create Trip" button from Trips screen, or "Edit" from trip card.

**Layout:**
```
┌──────────────────────────────────┐
│ ← [Trip Name] ↑ [Compare Routes] │  ← Back, title edit, action button
├──────────────────────────────────┤
│ Profile: [Driving-Traffic ▼]    │
│ Exclude: [Tolls] [Motorways] [Ferries]
├──────────────────────────────────┤
│ Stops:                           │
│ 1. 📍 [Search or current loc] ▼  │
│ 2. 📍 [Stop name] ☰ ✕           │  ← Drag handle, delete
│ 3. 📍 [Stop name] ☰ ✕           │
│                                  │
│ [+ Add Stop]                     │
│                                  │
│ [Save Trip] [Clear]              │
│                                  │
└──────────────────────────────────┘
```

**Interactions:**

- **Profile dropdown:** Driving-Traffic (default), Driving, Walking, Cycling.
- **Exclude toggles:** Show/hide by tapping "Show Options" expand button.
- **Stop rows:**
  - Drag ☰ handle to reorder.
  - Tap name to edit or search.
  - Tap ✕ to delete.
- **Add Stop:** Tap → search dialog appears; select → stop added.
- **Compare Routes:** Tap → request routes from Directions API and navigate to RouteComparisonScreen.

### 6.3 Route Comparison Screen

**Purpose:** Compare up to 3 route alternatives.

**Entry:** "Compare Routes" button from TripEditorScreen (after adding origin + destination + optional stops).

**Layout:**
```
┌──────────────────────────────────┐
│ ← [Route Comparison]  [Open Map] │  ← Back, title, open full map
├──────────────────────────────────┤
│      🗺️ MAP (small)              │
│   Shows all 3 routes             │
├──────────────────────────────────┤
│ [Route 1] 🟢 Fastest             │
│ 487 km • 7h 23m • Light traffic  │
│ [Details] [Select]               │
│                                  │
│ [Route 2] 🔵 Shortest            │
│ 432 km • 8h 12m • Moderate       │
│ [Details] [Select]               │
│                                  │
│ [Route 3] 🟣 Avoid Tolls         │
│ 521 km • 9h 5m • Few tolls       │
│ [Details] [Select]               │
├──────────────────────────────────┤
│ [Start Driving] [Save Trip]      │
│                                  │
└──────────────────────────────────┘
```

**Interactions:**

- **Select button:** Mark route as selected (visual highlight).
- **Details button:** Show steps/maneuvers as a list.
- **Map:** Tap "Open Map" → full-screen RoutePreviewScreen.
- **Start Driving:** Begin Guidance for selected route.
- **Save Trip:** Save the trip (with selected route) to Room.

### 6.4 Route Preview Screen (Full Map)

**Purpose:** Full-screen map view of a selected route with preview details.

**Layout:**
```
┌──────────────────────────────────┐
│ ← [Trip Name] ↑ [Start Driving]  │
├──────────────────────────────────┤
│                                  │
│                                  │
│         🗺️ FULL MAP              │
│    Polyline shows full route     │
│    Stops marked with numbers     │
│                                  │
│                                  │
├──────────────────────────────────┤
│ Distance: 487 km                 │
│ ETA: 7h 23m (assuming 10am dep)  │
│ Stops: 3                         │
│ [Start Driving]                  │
│                                  │
└──────────────────────────────────┘
```

---

## 7. Drive Mode (Guidance)

### 7.1 Guidance Screen

**Purpose:** In-app turn-by-turn navigation (foreground-only in MVP).

**Entry:** "Start Driving" button from route comparison or trip detail.

**Exit:** "End Trip" button → back to TripDetail or Explore.

**Bottom bar:** Hidden.

**Fullscreen layout:**
```
┌──────────────────────────────────┐
│ ← [Trip Name] ⊗ [End Trip]       │
├──────────────────────────────────┤
│                                  │
│         🗺️ MAP (large)           │
│      🔵 User puck                │
│      ▶️ Next turn (arrow icon)   │
│                                  │
│                                  │
├──────────────────────────────────┤
│         500 m                    │
│    Turn Right Towards [Street]   │ ← Maneuver text
│         5 h 30 m                │ ← ETA
│                                  │
│  Upcoming Turns:                 │
│  1. Turn left (800 m)            │
│  2. Continue straight (1.2 km)   │
│  [...]                           │
├──────────────────────────────────┤
│ [Recalculate] [Call] [Settings] │
│                                  │
└──────────────────────────────────┘
```

### 7.2 Guidance Behavior

**Maneuver Announcement:**
- When user is ~500 m from turn: "In 500 meters, turn right."
- When user is ~100 m away: "In 100 meters, turn right."
- At the turn: "Turn right now" + visual arrow on map.
- Announcement via Android TextToSpeech + text display.

**Off-Route Detection:**
- If user is > 10 m off-track for > 5 seconds → show "Off Route, Recalculate?" button.
- If online: tap "Recalculate" → request new route from current location to destination.
- If offline: "Recalculate" is disabled (show "Go online to recalculate").

**Screen Stay-On:**
- Keep screen on while Guidance is active (use WakeLock or setKeepScreenOn()).

**Background (MVP limitation):**
- If app goes to background (user minimizes), guidance pauses.
- Show notification: "Navigation paused. Open app to resume."
- In v1.1, background guidance via foreground service is available.

---

## 8. Offline Maps

### 8.1 Offline Screen (List)

**Purpose:** Manage downloaded regions.

**Entry:** Tap "Offline" on bottom bar.

**Layout:**
```
┌──────────────────────────────────┐
│ [+ Download Region]              │
├──────────────────────────────────┤
│ Downloaded Regions:              │
│                                  │
│ ┌────────────────────────────┐   │
│ │ Delhi-Agra Corridor        │   │
│ │ 450 MB • 1,240 tiles       │   │
│ │ Downloaded: Oct 5, 2026    │   │
│ │ Expires: Nov 4, 2026       │   │
│ │ [Map] [Update] [Delete]    │   │
│ └────────────────────────────┘   │
│                                  │
│ ┌────────────────────────────┐   │
│ │ Bangalore City             │   │
│ │ 180 MB • 520 tiles         │   │
│ │ Downloaded: Oct 2, 2026    │   │
│ │ Expires: Nov 1, 2026       │   │
│ │ [Map] [Update] [Delete]    │   │
│ └────────────────────────────┘   │
│                                  │
│ Storage Used: 630 MB / 2.5 GB    │
│ (available space shown)          │
│                                  │
│ Offline │ Saved │ Profile        │
└──────────────────────────────────┘
```

### 8.2 Download Region Flow

**Entry:** Tap "+ Download Region" button.

**Step 1: Region Picker Screen**
```
┌──────────────────────────────────┐
│ ← [Download Region]  [Continue] │
├──────────────────────────────────┤
│                                  │
│         🗺️ MAP                   │
│   (draggable polygon)            │
│                                  │
├──────────────────────────────────┤
│ Zoom: [Standard (11-15) ▼]      │
│ (adjust to detailed if needed)   │
│                                  │
│ [Region name: ________]          │
│ [Save this region]               │
│                                  │
│ Estimate: Computing...          │
│                                  │
└──────────────────────────────────┘
```

**Interactions:**
- Drag polygon corners to adjust region.
- Zoom dropdown to change tier.
- Text field to name the region (e.g., "Delhi-Agra").
- Estimate updates as geometry/zoom changes.

**Step 2: Confirmation Screen**
```
┌──────────────────────────────────┐
│ ← [Confirm Download]             │
├──────────────────────────────────┤
│ Region: Delhi-Agra Corridor      │
│ Size: 450 MB (1,240 tiles)       │
│ Available: 2.5 GB ✓              │
│                                  │
│ [WiFi Only: ON] (toggle)         │
│ ℹ️ Recommended for low-data plans│
│                                  │
│ [Download] [Cancel]              │
│                                  │
└──────────────────────────────────┘
```

**Step 3: Progress Screen (During Download)**
```
┌──────────────────────────────────┐
│ Downloading: Delhi-Agra          │
├──────────────────────────────────┤
│ Progress:                        │
│ ████████░░░░ 340 MB / 450 MB    │
│ 75% complete • ~2 min remaining  │
│                                  │
│ [Cancel]                         │
│                                  │
│ Note: Download continues in the  │
│ background if you close the app. │
│                                  │
└──────────────────────────────────┘
```

**Interactions:**
- Progress bar updates in real-time.
- Cancel button stops the download (partial data kept for resume).
- App can be closed; download continues via WorkManager.

**Step 4: Completion**
```
✓ Region Downloaded
   Delhi-Agra Corridor
   450 MB, available for 30 days
```

Notification appears; download is now listed in Offline screen.

### 8.3 Region Expiry and Update

- Regions marked "Outdated after 30 days."
- At 7 days remaining, "Update" button appears.
- Tap "Update" → re-download the region (same process as new download).
- Old region is deleted once new download completes.

---

## 9. My Saved Places

### 9.1 Saved Places Screen

**Purpose:** View and manage home, work, favorites, and parked car.

**Entry:** Tap "Saved" on bottom bar.

**Layout:**
```
┌──────────────────────────────────┐
│ [+ Add Place]                    │
├──────────────────────────────────┤
│ Home                             │
│ 🏠 28.5 Market Lane, Lodhi Rd   │
│ [Navigate] [Edit] [Delete]       │
│                                  │
│ Work                             │
│ 🏢 Sector 62, Noida             │
│ [Navigate] [Edit] [Delete]       │
│                                  │
│ Parked Car                       │
│ 🚗 Ground Floor, Parking        │
│ [Photo] [Note: Block A, 5th fl] │
│ [Navigate] [Edit] [Delete]       │
│                                  │
│ Favorites                        │
│ ★ Taj Mahal, Agra               │
│ [Navigate] [Edit] [Delete]       │
│                                  │
│ Emergency Contacts               │
│ [View/Edit]                      │
│                                  │
│ Saved │ Profile                  │
└──────────────────────────────────┘
```

### 9.2 Add/Edit Place

**Dialog:**
```
╔═══════════════════════════════╗
║ Save Place As:                ║
║ [Home] [Work] [Favorite]      ║
║ [Parked Car]                  ║
║                               ║
║ Name: _____________           ║
║                               ║
║ (If Parked Car:)              ║
║ [📷 Take Photo] [photo shown]  ║
║ Note: ________________         ║
║                               ║
║ [Save] [Cancel]               ║
╚═══════════════════════════════╝
```

---

## 10. Emergency Mode

### 10.1 Emergency Screen

**Purpose:** One-tap SOS actions and cached emergency results.

**Entry:** Tap red SOS button (always visible on map or from Profile).

**Layout:**
```
┌──────────────────────────────────┐
│ ← [Emergency]                    │
├──────────────────────────────────┤
│ 🚨 EMERGENCY MODE               │
│                                  │
│ [☎️ Call 112] [☎️ Call 108]     │
│ [Share Location] [Find Hospital] │
│                                  │
├──────────────────────────────────┤
│ Nearest Hospitals:              │
│ (from online search or cached)   │
│                                  │
│ 1. Apollo Hospital Delhi        │
│    3.2 km away                  │
│    [Call] [Navigate]            │
│                                  │
│ 2. Max Healthcare               │
│    5.1 km away                  │
│    [Call] [Navigate]            │
│    (Cached 2 hours ago)         │
│                                  │
│ Cyclone Preparedness Checklist: │
│ ☑️ Download home district       │
│ ☐ Add emergency contacts       │
│ ☐ Save shelter locations        │
│                                  │
└──────────────────────────────────┘
```

### 10.2 Call Actions

- **Call 112:** ACTION_DIAL with "112" (national emergency).
- **Call 108:** ACTION_DIAL with "108" (ambulance).
- **Call Hospital:** ACTION_DIAL with hospital phone number.

### 10.3 Share Location

- Tap "Share Location" → geo link is generated (geo:lat,lng).
- Share sheet appears → SMS, WhatsApp, email, copy link.
- Recipient receives a clickable geo link (opens in Maps app).

---

## 11. Quality Gate Matrix

Every screen must handle these states:

| State | Behavior | Example Screen |
|---|---|---|
| **Navigate / Leave** | Back arrow or swipe-back clears state, leaves cleanly | Trip Editor |
| **Cancel** | User cancels operation (dismiss dialog, go back) | Download confirmation |
| **Data fails** | API error (no internet, 401, 500) → show ErrorState with retry | Search, Directions |
| **No internet** | ConnectivityMonitor.isOnline = false → fallback to local (offline mode) | Search, Route |
| **Location denied** | User rejected permission → disable location-dependent features, show rationale | Explore (no puck) |
| **Recreated** | Process death (task killer, memory pressure) → restore state from Room/DataStore | Trip Editor |
| **Process death** | App in background, killed by OS → restart to last known screen | Any screen, via deep link |

---

## 12. Navigation State Persistence

**Deep Links:**
```
raahi://place?lat=28.6139&lng=77.2090&name=Delhi%20Gate
    → Opens PlaceDetail for the given coordinates

raahi://trip/{trip_id}
    → Opens TripDetail for the saved trip

raahi://emergency
    → Opens Emergency screen (useful for shortcuts, widgets)
```

**SavedStateHandle (ViewModel Scoped):**
```kotlin
class TripEditorViewModel(..., private val savedStateHandle: SavedStateHandle) {
    private val tripId: String? = savedStateHandle["trip_id"]

    init {
        if (tripId != null) {
            // Load existing trip
        } else {
            // New trip
        }
    }

    // On configuration change or process death, ViewModel is recreated with savedStateHandle intact.
}
```

**Room Persistence:**
- All trips, stops, saved places, contacts are persisted in Room.
- On app restart, last-opened trip can be reloaded from Room and trip ID passed via deep link.

---

## 13. Accessibility

**Target:** WCAG AA on all primary screens.

**Key Principles:**
- **Touch targets:** All buttons, chips, and interactive elements are at least 48 dp.
- **Color:** Never rely on color alone; use text labels, icons, and patterns.
- **TalkBack:** All interactive elements have `contentDescription`.
- **Font scaling:** UI adapts up to 200% font scale (no hard-coded sizes; use `sp` units).
- **Contrast:** Text contrast ratio ≥ 4.5:1 for normal text, 3:1 for large text.

**Examples:**
```kotlin
Button(
    onClick = { ... },
    modifier = Modifier.size(48.dp)  // Minimum touch target
) {
    Text("Start Trip")  // Clear label
}

Icon(
    painter = painterResource(R.drawable.ic_navigate),
    contentDescription = "Navigate to destination",  // TalkBack
    tint = MaterialTheme.colorScheme.primary,  // No hardcoded color
    modifier = Modifier.size(24.dp)
)

Text(
    text = "Distance: 487 km",
    fontSize = 16.sp,  // Scalable unit; respects font size preference
    color = MaterialTheme.colorScheme.onSurface  // Theme token
)
```

---

**Document Owner:** Design Team  
**Last Updated:** October 2026  
**Next Review:** Q1 2027
