# Product Ideation and Selection: Mapbox Consumer Android App (India)

**Date**: October 2026
**Constraint**: MVP has NO custom backend (Room/DataStore only).
**Stack**: Mapbox Maps SDK v11 (Compose), Search Box/Geocoding, Directions, Navigation SDK, TileStore offline regions, Isochrone, Static Images, GeoJSON layers, heatmaps, clustering.

> Pricing/quota numbers below are approximate and from memory of Mapbox's public pricing. Re-verify at mapbox.com/pricing before committing to a unit-economics model. Same for offline tile-pack limits and India POI coverage: run a spike (see Section 7).

## 0. Data-source legend (used throughout)

| Tag | Meaning |
|-----|---------|
| **MN** | Mapbox-native capability (works with only a Mapbox token) |
| **BND** | Bundled static asset we author/curate and ship in the APK (no server; needs editorial effort and licence checks) |
| **UGC** | User-generated data, stored locally in Room (no sharing across users) |
| **EXT** | External third-party API/dataset required (cost, ToS, key management) |
| **BE** | Needs our own backend (NOT in MVP) |

Cross-cutting facts that shape every idea:

- **Mapbox POI coverage in India is uneven.** Search Box categories (fuel, hospital, ATM, restaurant, EV charging) are decent in metros and on major highways, thin in Tier-3 towns and rural areas, and category tagging is inconsistent (e.g. "clinic", "PHC", "dhaba", "petrol pump" under varied names). EV charging coverage is especially unreliable. Never promise "all hospitals". Always show "nearest known" plus a UGC "add/correct a place" path.
- **Boundaries/worldview.** India has legal requirements about depicting its borders (Survey of India position). Use Mapbox worldview `IN` and verify the rendering of Kashmir/Arunachal on every style. Wrong borders in an India-targeted app is a real legal/store-risk, not a cosmetic issue.
- **No live traffic dependence for correctness**: traffic-aware routing works (MN) but quality varies outside metros.
- **Offline**: TileStore offline map tiles are MN. Offline routing needs Navigation SDK routing tiles (MN, but must be validated for Indian regions). Offline *search* is limited, so offline "find hospital" must be backed by data we cached earlier (UGC or pre-fetched results saved in Room at download time) or BND.
- **Live sharing, community reports, leaderboards, sync = BE.** Treat as post-MVP.

---

## 1. Twenty-two app ideas

Format: Problem / Audience / Why Mapbox / Core features / Monetization / Complexity / Offline / Differentiation / MVP scope / Expansion / Mapbox APIs / Data tags.

### 1. Raahi Road Trip Companion (offline-ready journey companion)
- **Problem**: Highway trips in India cross multi-hour dead zones; people lose navigation, can't find fuel/hospital/food, and compare routes by guesswork.
- **Audience**: Self-drive families, weekend travellers, bikers, 25-55, Tier-1/2 cities.
- **Why Mapbox**: TileStore offline regions + route comparison + clustering of nearby essentials + Compose map UI.
- **Core features**: multi-stop planner, alternative-route comparison (time/distance/tolls-unknown), offline corridor download, nearby essentials, parked-car/home/emergency places.
- **Monetization**: free core; premium offline packs/unlimited trips/trip export.
- **Complexity**: Medium-High.
- **Offline**: Very high.
- **Differentiation**: Trip-first and offline-first rather than destination search-first; corridor-aware "essentials ahead" list.
- **MVP**: planner (up to 3 stops free), compare 2-3 routes, download corridor, nearby chips, saved places.
- **Expansion**: pilgrimage packs, EV mode, storm mode, GPX export, trip journal.
- **APIs**: Maps, Search Box, Directions, Navigation, TileStore, GeoJSON, clustering, Static Images.
- **Data**: MN (map, routes, search), UGC (places, trips), BND (curated packs later). No BE. Rural essentials coverage is limited.

### 2. Yatra Offline (pilgrimage routes: Char Dham, Puri Rath Yatra, Kumbh, Amarnath)
- **Problem**: Pilgrim routes are crowded, seasonal, partly dead-zone (Kedarnath/Badrinath approach, Konark-Puri), with dharamshalas, medical camps and road closures that generic maps ignore.
- **Audience**: Families and elders on pilgrimage; tour groups.
- **Why Mapbox**: Offline regions + custom styled layers + GeoJSON of curated stops.
- **Core features**: curated route packs, stage-wise itinerary (Haridwar-Barkot-Yamunotri...), offline map per dham, dharamshala/medical/ATM layer, "darshan day" checklist.
- **Monetization**: per-yatra premium packs (INR 99-199), seasonal.
- **Complexity**: Medium (editorial heavy).
- **Offline**: Very high.
- **Differentiation**: Curated, culturally native, season-aware.
- **MVP**: 2 packs (Char Dham, Puri); static curated GeoJSON.
- **Expansion**: 12 Jyotirlinga, Shakti Peeth, Buddhist circuit, regional-language audio.
- **APIs**: Maps, TileStore, GeoJSON, Directions, Search Box.
- **Data**: BND (we must research and verify every point; stale data is a safety risk), MN, UGC. Live road closures/crowd = EXT or BE (not in MVP). Rath Yatra crowd routing requires official event data (EXT).

### 3. Cyclone Ready (Odisha/East Coast disaster preparedness)
- **Problem**: During Fani/Yaas/Amphan-type events, power and mobile networks fail; people don't know nearest cyclone shelter or evacuation direction.
- **Audience**: Coastal households in Odisha, AP, WB; community volunteers.
- **Why Mapbox**: Pre-downloaded offline maps + routing to saved shelters, no network at the moment of need.
- **Core features**: pre-season readiness checklist, download home-district offline pack, save shelters/high ground/hospitals, one-tap route to nearest saved shelter, family meeting point.
- **Monetization**: Free (public good) + sponsorship/CSR; or bundled as a mode of the main app.
- **Complexity**: Medium.
- **Offline**: Critical.
- **Differentiation**: Preparedness-first, works with dead network.
- **MVP**: district pack download, user-entered shelters, offline route to saved place.
- **Expansion**: OSDMA shelter dataset import, alerts, volunteer coordination.
- **APIs**: Maps, TileStore, Navigation (offline routes), GeoJSON.
- **Data**: UGC and BND. Official shelter locations (OSDMA/NDMA) = EXT, availability and licence unverified; do not assume. Alerts/IMD feeds = EXT; community coordination = BE.

### 4. ChargeGap EV Range Planner
- **Problem**: EV owners fear charger gaps on highways (Delhi-Jaipur okay, Odisha/NE sparse).
- **Audience**: EV 4W/2W owners.
- **Why Mapbox**: Route + range-aware corridor, isochrone as "reach on remaining battery".
- **Core features**: user-set battery/range, route with charge-stop suggestions, isochrone reach, saved chargers.
- **Monetization**: Premium planner; affiliate with charge networks.
- **Complexity**: High.
- **Offline**: Medium.
- **Differentiation**: Honest "gap warning".
- **MVP**: range circle/isochrone + search Box EV category + UGC chargers.
- **Expansion**: connector filters, live availability, OEM integrations.
- **APIs**: Directions, Isochrone, Search Box, Maps.
- **Data**: MN has Search Box EV category but India coverage unreliable. Reliable charger data = EXT (OCM/Statiq/Tata Power-type APIs; availability and terms unverified) or BE. Live availability = EXT. Mapbox driving profile has no EV consumption model; range maths is our heuristic.

### 5. SOS Nearest (emergency hospital finder)
- **Problem**: In emergencies, people don't know the nearest trauma-capable hospital; PHCs are poorly tagged.
- **Audience**: Everyone, especially travellers, elders' families.
- **Why Mapbox**: Search Box category + routing + offline cache.
- **Core features**: one-tap nearest hospitals, call 112/108 buttons, route, share location text, pre-cached nearby hospitals.
- **Monetization**: Mostly free; premium family profiles.
- **Complexity**: Low-Medium.
- **Offline**: Medium (cached results only).
- **Differentiation**: Speed to action.
- **MVP**: category search + call + navigate + cache.
- **Expansion**: trauma-level tags, blood banks, ambulance integration.
- **APIs**: Search Box, Directions, Navigation, Maps.
- **Data**: MN with rural gaps; hospital capability (ICU/trauma) = EXT/BE or UGC. Location sharing by SMS uses Android intents (no BE). Liability disclaimer needed.

### 6. TrekTrail Offline (trekking)
- **Problem**: Himalayan/Western Ghats treks have no signal and poor trail maps.
- **Audience**: Trekkers, 18-40.
- **Why Mapbox**: Terrain style, outdoors layers, offline regions, GeoJSON tracks.
- **Core features**: import GPX, offline terrain pack, record track, waypoints, elevation profile, off-route alert.
- **Monetization**: Premium packs/unlimited tracks.
- **Complexity**: Medium.
- **Offline**: Total.
- **Differentiation**: Indian treks bundle (Kedarkantha, Hampta, Valley of Flowers).
- **MVP**: GPX import, offline, record.
- **Expansion**: weather, permits.
- **APIs**: Maps (terrain/outdoors), TileStore, GeoJSON, location.
- **Data**: BND/UGC GPX; Mapbox trail data in India is thin; walking Directions don't route trails reliably. Weather = EXT.

### 7. Gig Rider Pro (delivery workers)
- **Problem**: Multi-drop planning, fuel cost, daily route logs.
- **Audience**: Delivery partners, couriers.
- **Why Mapbox**: Optimization-style multi-stop directions, navigation.
- **Core features**: stop list, ordering, per-km cost tracker, daily summary.
- **Monetization**: Low ARPU; ad/partnership.
- **Complexity**: Medium.
- **Offline**: Low.
- **Differentiation**: Earnings/expense lens.
- **MVP**: stop list + sequential route.
- **Expansion**: shift analytics.
- **APIs**: Directions (matrix/optimization are separate, extra cost), Navigation, Search Box.
- **Data**: MN; optimization may need Mapbox Optimization API (MN, extra cost); address quality in India is weak, so geocoding failures are common. Delivery platforms' own apps already navigate, so the value is small.

### 8. FieldSurvey Lite (field work)
- **Problem**: Surveyors/NGO/ASHA workers need offline pin-drop with notes and photos.
- **Audience**: Field staff, NGOs, small agribusiness.
- **Why Mapbox**: Offline map, GeoJSON export.
- **Core features**: pin/point/polygon capture, forms, photo attach, CSV/GeoJSON export.
- **Monetization**: B2B seats (needs BE for team sync).
- **Complexity**: Medium.
- **Offline**: Total.
- **Differentiation**: Simple compared with KoboToolbox/ODK.
- **MVP**: single-user capture/export.
- **Expansion**: team sync (BE).
- **APIs**: Maps, TileStore, GeoJSON, Static Images.
- **Data**: UGC. B2B needs BE. Weak consumer fit.

### 9. Reach (isochrone: "what's within 30 min")
- **Problem**: House/PG hunting and commute decisions.
- **Audience**: Students, job movers, renters.
- **Why Mapbox**: Isochrone is a differentiator.
- **Core features**: reach polygons per mode, compare 2 locations, pin amenities.
- **Monetization**: Ads/affiliate (property).
- **Complexity**: Low-Medium.
- **Offline**: None (isochrone is online).
- **Differentiation**: Visual.
- **MVP**: isochrone + search.
- **Expansion**: shortlist scoring.
- **APIs**: Isochrone, Search Box, Maps.
- **Data**: MN; rental listings = EXT/BE.

### 10. Heritage Walks (heritage trails)
- **Problem**: ASI monuments have thin signage and context.
- **Audience**: Tourists, history lovers, school groups.
- **Why Mapbox**: Custom-styled thematic maps, walking routes.
- **Core features**: curated trails, offline map, story cards, audio.
- **Monetization**: City packs.
- **Complexity**: Medium (editorial).
- **Offline**: High.
- **Differentiation**: Storytelling.
- **MVP**: 2-3 city trails.
- **Expansion**: AR, local guides (BE).
- **APIs**: Maps, TileStore, GeoJSON, Directions walking.
- **Data**: BND editorial; Wikipedia/Wikidata/ASI data = EXT (licence checks).

### 11. Dark Zone Map (connectivity mapping)
- **Problem**: Nobody knows where signal drops.
- **Audience**: Travellers, rural users.
- **Why Mapbox**: Heatmap layer of personal measurements.
- **Core features**: passive signal logging, personal heatmap, "dead stretch ahead" warning from own history.
- **Monetization**: Weak.
- **Complexity**: Medium.
- **Offline**: Yes.
- **Differentiation**: Novel; but the value is crowd-sourced, which requires BE.
- **MVP**: personal logging only (valuable only to repeat routes).
- **Expansion**: crowd map (BE), operator comparison.
- **APIs**: Maps (heatmap), GeoJSON.
- **Data**: UGC; crowd = BE. Cold-start problem. Good as a feature of #1, not an app.

### 12. Family Circle Safe (live family location)
- **Problem**: Family wants to know "reached safely?".
- **Audience**: Families, women travelling.
- **Why Mapbox**: Maps display.
- **Core features**: live sharing, geofences, SOS.
- **Monetization**: Subscription.
- **Complexity**: High.
- **Offline**: Low.
- **Differentiation**: Crowded space (Life360 etc).
- **MVP**: Not possible without BE.
- **APIs**: Maps, Navigation.
- **Data**: **BE required** (live sync, push). Excluded for MVP. SMS-based "share my ETA" is a no-backend subset.

### 13. Ghat and Night Driving Hazard Map
- **Problem**: Accident-prone curves, animal crossings, black spots.
- **Audience**: Highway drivers.
- **Why Mapbox**: Heatmap/clusters.
- **Core features**: hazard layer, alerts.
- **Monetization**: Premium alerts.
- **Complexity**: High.
- **Offline**: Medium.
- **Differentiation**: Safety.
- **MVP**: BND layer only if black-spot data exists.
- **Expansion**: crowd (BE).
- **APIs**: Maps, GeoJSON, Navigation.
- **Data**: EXT (MoRTH black-spot data, availability/licence unverified) or BE for crowd. Safe as a layer in #1 later.

### 14. ASHA/Health-Worker Visit Planner
- **Problem**: Rural visit scheduling with no signal.
- **Audience**: Frontline health workers.
- **Why Mapbox**: Offline multi-stop.
- **Core features**: household pins, visit order, notes.
- **Monetization**: Government/NGO (long sales cycle).
- **Complexity**: Medium.
- **Offline**: Total.
- **Differentiation**: Narrow niche.
- **MVP**: single user.
- **APIs**: Maps, TileStore, Directions (offline).
- **Data**: UGC; B2G sales; privacy (health data) risk. Not consumer.

### 15. Flood Watch (Assam/Bihar)
- **Problem**: Flood-prone villages need safe routes.
- **Audience**: Flood-belt residents.
- **Why Mapbox**: Layers + offline.
- **Core features**: flood layer, safe routes.
- **Monetization**: CSR.
- **Complexity**: High.
- **Offline**: Partial.
- **Differentiation**: Public good.
- **MVP**: UGC-marked safe points.
- **APIs**: Maps, GeoJSON, TileStore.
- **Data**: Flood extents = EXT (CWC/ISRO Bhuvan, licence/format uncertain) / BE. Overlaps #3.

### 16. Station-to-Stay (last-mile from railway stations)
- **Problem**: Arriving at unfamiliar stations at night.
- **Audience**: Rail travellers.
- **Why Mapbox**: Isochrone + Search.
- **Core features**: walk/auto reach map, nearby lodging/food/ATM.
- **Monetization**: Affiliate lodging.
- **Complexity**: Low.
- **Offline**: Medium.
- **Differentiation**: Rail-centric.
- **MVP**: station search + reach.
- **APIs**: Isochrone, Search Box, Directions.
- **Data**: Station dataset = BND/EXT; train timetable = EXT (IRCTC restricted). Fits as feature of #1.

### 17. Truck/Commercial Route Planner
- **Problem**: Truck restrictions, city entry times, weigh bridges.
- **Audience**: Truckers.
- **Why Mapbox**: Directions with truck-related exclusions.
- **Core features**: vehicle dims, restrictions.
- **Monetization**: B2B.
- **Complexity**: High.
- **Offline**: Medium.
- **Differentiation**: Underserved.
- **MVP**: weak.
- **APIs**: Directions, Navigation.
- **Data**: Mapbox lacks reliable Indian truck restriction data; EXT/BE. Not consumer.

### 18. Personal Heatmap and Activity Log (fitness)
- **Problem**: Runners/cyclists want a private "where I've been" map.
- **Audience**: Runners, cyclists, commuters.
- **Why Mapbox**: Heatmap layer + location tracking.
- **Core features**: record, personal heatmap, streaks, segments.
- **Monetization**: Premium stats.
- **Complexity**: Medium.
- **Offline**: Good.
- **Differentiation**: Privacy, local-only; but Strava/Garmin dominate.
- **MVP**: record + heatmap.
- **APIs**: Maps, GeoJSON, heatmap.
- **Data**: UGC. Social/segments = BE.

### 19. Festival/Mela Crowd Navigator (Kumbh, Rath Yatra)
- **Problem**: Mega-event wayfinding.
- **Audience**: Attendees.
- **Why Mapbox**: Offline custom layer.
- **Core features**: event map, ghats, medical camps, lost-and-found point.
- **Monetization**: Event sponsorship.
- **Complexity**: Medium.
- **Offline**: High.
- **Differentiation**: Hyper-local.
- **MVP**: one event.
- **APIs**: Maps, TileStore, GeoJSON.
- **Data**: Official event geodata = EXT (authority partnership); crowd density = BE. Seasonal and limited. Better as a Yatra pack.

### 20. Parked Car and Places (personal anchors)
- **Problem**: Forgetting where the car is parked; home/work/emergency anchors.
- **Audience**: Everyone.
- **Why Mapbox**: Maps + walking route back.
- **Core features**: save parking with photo/floor note, timer, walk-back.
- **Monetization**: Low standalone.
- **Complexity**: Low.
- **Offline**: Good.
- **Differentiation**: Weak standalone; strong as a sub-feature.
- **Data**: UGC.

### 21. Roadside Photo and Sunrise Spots (scenic drive discovery)
- **Problem**: Finding scenic stops en route.
- **Audience**: Travel enthusiasts.
- **Why Mapbox**: Clustering/custom markers.
- **Core features**: personal and curated spots.
- **Monetization**: Affiliate.
- **Complexity**: Low.
- **Offline**: Good.
- **Differentiation**: Value comes from community = BE.
- **Data**: UGC/BND; community = BE.

### 22. Campus/Student Safe-Route (night walking for students)
- **Problem**: Women students walking at night.
- **Audience**: College students.
- **Why Mapbox**: Walking routes + saved safe places.
- **Core features**: share ETA via SMS, safe places, fake call.
- **Monetization**: Institution licensing.
- **Complexity**: Medium.
- **Offline**: Medium.
- **Differentiation**: Safety UX; "safe route" claim is not supportable without safety data (EXT/BE), so risk of misleading users.
- **Data**: UGC; safety scoring = BE.

---

## 2. India analysis: real problems and data-dependency matrix

| India problem | Real situation | Best-fit ideas | Mapbox-native (MN) | Bundled/curated (BND) | User-generated (UGC) | External API (EXT) | Backend (BE) |
|---|---|---|---|---|---|---|---|
| Highway trips, patchy connectivity | Dead zones in ghats, Northeast, Himalaya, central highways | #1, #11 | Offline tiles, offline route tiles (validate in India), route alternatives | Pre-built corridor packs (e.g. Delhi-Manali) | Saved stops, personal signal log | Signal-coverage maps (operators do not offer open APIs) | Crowd signal map |
| Pilgrimage (Char Dham, Puri Rath Yatra) | Seasonal crowds, closures, elders, limited facilities | #2, #19 | Offline region, GeoJSON layers, routing | Curated stages, dharamshalas, medical points (editorial research needed; verify) | Personal notes | Live closures/darshan queues/weather | Crowd/real-time |
| Emergency hospital finding | Rural POI coverage is poor, capability unknown | #5 | Search Box category (metros decent, rural limited), routing | Curated trauma centres on major highways (labour intensive) | Add/correct hospitals, family contacts | Hospital capability/bed APIs | Verified directory, ambulance dispatch |
| EV charging gaps | Chargers sparse outside metro corridors, data fragmented | #4 | Isochrone, routes, EV category (unreliable) | Curated charger list for key corridors | User-added chargers | Charger aggregator APIs, live status (terms/costs uncertain) | Aggregation, availability |
| Odisha cyclone preparedness | Network/power outage, evacuation by shelter | #3 | Offline tiles, offline routing | District shelter list (if licence permits) | Household-saved shelters/safe points | OSDMA/IMD alerts | Community coordination, push alerts |
| Rural low connectivity | Weak 4G, address ambiguity, low-end phones | #1, #3, #8, #14 | Offline maps; light styles | Regional packs | Local places | Address data | Sync |

India-specific design implications:

1. **Low-end device and data cost**: Android Go-class phones and 1-1.5 GB/day plans. Offer region size estimates before download, Wi-Fi-only default, small style variants, and resumable downloads.
2. **Languages**: Hindi, Odia, Bengali, Tamil, Telugu, Marathi UI and map labels (verify Mapbox label language coverage per region).
3. **Addresses**: Indian addresses are landmark-based. Offer "drop pin" and "share location" as first-class alternatives to address search.
4. **Payments**: UPI via Google Play Billing local methods; price points in INR (low ARPU; INR 49-99/month or INR 399-599/year; per-pack INR 99-199).
5. **Trust and safety**: disclaimers on emergency features; never claim real-time or verified data we don't have.
6. **Boundaries**: apply worldview IN and test; avoid shipping boundary errors.
7. **Navigation SDK**: confirm India turn-by-turn quality, voice languages, and licensing for the Navigation SDK before relying on it (Navigation pricing is per trip/MAU, which is the largest cost).

---

## 3. Monetization

### 3.1 Free vs Premium (for the recommended app, Raahi)

| Capability | Free | Premium ("Raahi Plus") |
|---|---|---|
| Map, explore, nearby essentials | Yes (rate-limited searches) | Unlimited |
| Saved places (home, car, emergency) | Up to 10 | Unlimited |
| Trip planner | 2 saved trips, up to 3 stops | Unlimited trips, up to 25 stops |
| Route comparison | 2 alternatives | All alternatives + saved comparisons |
| Offline downloads | 1 region (small, e.g. ~1 district/city) | Multiple regions, corridor downloads, large states |
| Curated Yatra / corridor packs | Preview | Per-pack purchase or included in annual plan |
| Turn-by-turn navigation | Limited trips/month | Unlimited |
| Export (GPX/GeoJSON/PDF itinerary) | No | Yes |
| Emergency mode | **Always free** (trust + safety) | Free |

### 3.2 Pricing and revenue levers
- Subscription: INR 49-99/month, INR 399-599/year; family plan optional.
- One-time packs: INR 99-199 per Yatra/corridor pack (good fit for seasonal buyers, avoids subscription fatigue).
- Affiliate: fuel cards, EV network signups, hotel/dharamshala booking (EXT partnerships; no BE needed if deep links).
- Ads: **low tolerance**. Users in-journey or in emergencies must never see ads. Allow only a small non-interstitial banner/native card in the Explore/home screen (free tier), never on navigation, emergency, or offline-download screens. Expect low eCPM in India; do not plan the business on ads.
- B2B/CSR/government sponsorship for disaster-preparedness packs (Cyclone mode).

### 3.3 Mapbox usage cost drivers (what each feature consumes)

| Feature | Mapbox product | Billing unit (approx.) | Driver | Cost risk | Mitigation |
|---|---|---|---|---|---|
| Map display / browsing | Maps SDK (mobile) | Monthly Active Users (free tier ~25k MAU, then tiered) | MAU | Low-Medium (scales with users) | Cap free MAU growth by monetising early; reuse one style |
| Offline region download | TileStore / tiles | Tile requests at download (and regions count against offline limits) | Downloads per user x region size | Medium-High for large corridors | Zoom caps, size estimates, premium gating, reuse tiles across regions |
| Search (typing/suggest/retrieve) | Search Box API | Per search session (free tier ~500 sessions/month, then per 1,000) | Searches per user | **High** (sessions add up) | Debounce, min chars, local-first search of saved places, cache results in Room |
| Nearby essentials (category search) | Search Box category/forward | Search sessions or requests | Taps on chips | Medium | Cache by geohash/area, refresh on demand |
| Route preview / comparison | Directions API | Per request (free ~100k/month) | Trips planned x alternatives x edits | Low-Medium | One request per change, debounce on stop reorder |
| Multi-stop optimisation | Optimization/Matrix | Per element/request | Stop count | Medium | Premium only; limited free |
| Turn-by-turn navigation | Navigation SDK | Per navigation trip / MAU (free tier small, ~1,000 trips/month) | Trips started | **Highest** | Limit free trips; offer "preview and hand off", gate in premium |
| Isochrone | Isochrone API | Per request | Reach searches | Low | Rate-limit |
| Static Images | Static Images API | Per request | Shared trip cards/thumbnails | Low | Generate once, cache files |
| Reverse geocode (parked car, SOS) | Geocoding | Per request | Saves | Low | Cache |
| GeoJSON/heatmap/cluster layers | Maps SDK | No extra cost (client-side) | n/a | None | Prefer for custom data |

Rule of thumb: **Navigation trips and search sessions dominate cost**; map loads are MAU-based and cheap early; offline downloads are the hidden spike. Instrument all calls through a single gateway class with per-user daily counters from day one (local only).

---

## 4. Top 10 comparison (each /10; total /100)

Columns: UV user value, OR originality, MU Mapbox utilization, TF technical feasibility, OF offline potential, MO monetization, IN India relevance, UI UI potential, EX long-term expansion, BI backend independence.

| # | Idea | UV | OR | MU | TF | OF | MO | IN | UI | EX | BI | **Total** |
|---|------|----|----|----|----|----|----|----|----|----|----|-----------|
| 1 | Raahi Road Trip Companion | 9 | 7 | 9 | 8 | 9 | 8 | 9 | 8 | 8 | 9 | **84** |
| 2 | Yatra Offline (pilgrimage) | 8 | 8 | 8 | 8 | 9 | 6 | 9 | 8 | 7 | 9 | **80** |
| 6 | TrekTrail Offline | 7 | 6 | 8 | 7 | 10 | 7 | 6 | 8 | 6 | 9 | **74** |
| 3 | Cyclone Ready | 8 | 8 | 7 | 8 | 9 | 4 | 9 | 6 | 6 | 8 | **73** |
| 5 | SOS Nearest | 9 | 5 | 6 | 8 | 8 | 4 | 9 | 6 | 5 | 8 | **68** |
| 10 | Heritage Walks | 6 | 6 | 7 | 8 | 8 | 5 | 8 | 8 | 5 | 7 | **68** |
| 8 | FieldSurvey Lite | 6 | 5 | 7 | 8 | 9 | 5 | 6 | 5 | 6 | 10 | **67** |
| 4 | ChargeGap EV Planner | 7 | 6 | 8 | 5 | 6 | 6 | 8 | 7 | 7 | 6 | **66** |
| 7 | Gig Rider Pro | 7 | 5 | 7 | 7 | 5 | 6 | 8 | 6 | 6 | 8 | **65** |
| 9 | Reach (Isochrone) | 6 | 7 | 8 | 8 | 3 | 5 | 6 | 8 | 5 | 9 | **65** |

Notes: Dark Zone Map scored 63 (strong originality 9, but backend independence 3), excluded from the top 10 but absorbed into the winner as a personal-signal feature. Family Circle Safe (BE required), Truck planner, Flood Watch (EXT/BE) were excluded for dependency reasons. Scores are team judgment, not measured data; confidence about +/-1 per cell.

---

## 5. Final recommendation

### 5.1 Honest evaluation of the prior
The prior ("offline-ready road-trip and journey companion for India") wins on the matrix (84), mainly because it scores high on **every** dimension without a single weak spot, whereas alternatives have a clear hole:
- Yatra (80) is strong but seasonal, editorially expensive, and lower monetization breadth; it is better as **premium content inside** the road-trip app than as a separate app.
- Trek (74) is offline-perfect but India relevance and trail data are weaker.
- Cyclone Ready (73) has the highest social value but near-zero monetization and mostly regional demand; it fits as an **Emergency/Storm mode** in the winner.
- SOS (68) is a feature, not a product (people don't open an app daily for it).

**Where I beat the prior**: the prior's "personal/safety places" use case is the weakest standalone idea (#20 parked car, low value). I upgraded it into **"Safe Places and Emergency mode"** with Cyclone/shelter semantics, which gives an India-native reason to keep the app installed. I also added **curated Yatra and corridor packs (bundled)** as the monetization engine, replacing generic "premium offline packs". The prior's "explore/search/nearby essentials" is kept but explicitly positioned as secondary to the trip, to avoid being a Google Maps clone.

### 5.2 The app

- **Name**: **Raahi** (working name, Hindi/Urdu for "traveller"; do a trademark/Play Store name check, since similar names likely exist).
- **Tagline**: "Your journey, even when the signal isn't."
- **Target audience**: Indian self-drive and two-wheeler travellers (families, weekend road-trippers, pilgrims' families) aged 25-55 in metros and Tier-2 cities travelling on highways with patchy coverage; secondary: coastal households preparing for cyclones.
- **Value proposition**: Plan the whole journey, compare routes, download the dead-zone stretches in advance, and know where the nearest fuel, hospital and safe place are, even with zero network. Trip-first and offline-first, built for Indian highways and pilgrim routes.

### 5.3 Use cases (5, all one coherent "before / during / in trouble" journey)

1. **Explore and Nearby Essentials**
   - Search places with Search Box; area-cached results
   - Category chips: fuel, hospital, ATM, food, EV charging (labelled "nearest known")
   - Clustered markers, list/map toggle
   - "Add / correct a place" (UGC) to fill rural gaps
   - Hindi + regional language labels
2. **Multi-stop Trip Planner and Route Comparison**
   - Up to N stops with drag-reorder
   - 2-3 alternative routes compared by time, distance, highway share
   - Along-route essentials ("fuel in the next 80 km")
   - Departure-time estimate; save/duplicate trips
   - Share itinerary as image (Static Images) or text
3. **Offline Journey Packs**
   - Download corridor along a chosen route, or a region
   - Size/data estimate, Wi-Fi-only default, resume
   - Pre-cache essentials along the corridor into Room
   - Offline map plus offline routing where supported
   - Storage manager with expiry reminders
4. **My Places and Emergency mode**
   - Home, parked car (photo/note/walk-back), emergency contacts, saved hospitals/shelters
   - One-tap "nearest saved/known hospital" with 112/108 call and SMS location share
   - Cyclone/storm preparedness checklist, download home district, route to saved shelter
   - Always free
5. **Curated Yatra and Corridor Packs**
   - Bundled GeoJSON for Char Dham, Puri/Konark, plus 2-3 top highway corridors
   - Stage-wise itinerary with stops, facilities, season notes
   - Offline pre-bundled tiles download on demand
   - Sold as one-time packs; fully static, no backend

Deliberately **not** included: live family tracking, crowd reports, EV live availability, truck routing (all BE/EXT).

### 5.4 Risks to validate first (week-1 spikes)
1. Navigation SDK and offline routing tiles actually work for Indian highways (Delhi-Manali, Bhubaneswar-Puri, Rishikesh-Badrinath) with no network.
2. Search Box category coverage of fuel/hospital/ATM along 5 sample corridors; quantify the gap and set the UGC fallback.
3. Offline tile limits/size per corridor and cost per download.
4. Worldview IN boundary rendering.
5. Navigation trip costs vs free tier at 10k MAU (model).
6. Editorial effort and licensing for curated pack data.

## 6. Mapbox API mapping for the recommendation

| Use case | Maps | Search Box | Directions | Navigation | TileStore | Isochrone | Static Images | GeoJSON/Cluster/Heatmap |
|---|---|---|---|---|---|---|---|---|
| Explore and Essentials | x | x |  |  |  |  |  | x (cluster) |
| Trip Planner | x | x | x | x (start trip) |  |  | x (share card) | x |
| Offline Packs | x |  | x (corridor) | x (offline routes) | x |  |  | x |
| My Places / Emergency | x | x (reverse) | x | x | x |  |  | x |
| Yatra Packs | x |  | x |  | x |  | x | x (BND layers) |

Isochrone is optional (nice "how far can I get by evening" feature on the planner); heatmap can later render the personal dead-zone log.

## 7. Open assumptions

- Prices/free tiers and offline limits approximate; verify before pricing.
- Scores are subjective judgments by the ideation team.
- No external dataset assumed available; curated data requires editorial work and licence checks.
