package com.raahi.app.domain.repository

import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.CachedPlaces
import com.raahi.app.domain.model.DownloadProgress
import com.raahi.app.domain.model.EmergencyContact
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.OfflineEstimate
import com.raahi.app.domain.model.OfflineRegion
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.RecentSearch
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.domain.model.ThemeMode
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.model.UserLocation
import com.raahi.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PlaceSearchRepository {
    suspend fun suggest(query: String, proximity: GeoPoint?, sessionToken: String): AppResult<List<SearchSuggestion>>
    suspend fun retrieve(suggestion: SearchSuggestion, sessionToken: String): AppResult<Place>
    suspend fun categorySearch(category: PlaceCategory, near: GeoPoint, limit: Int = 25): AppResult<List<Place>>
    suspend fun reverseGeocode(point: GeoPoint): AppResult<Place>
}

interface DirectionsRepository {
    suspend fun getRoutes(
        waypoints: List<GeoPoint>,
        profile: RouteProfile,
        options: RouteOptions = RouteOptions(),
    ): AppResult<List<Route>>
}

interface TripRepository {
    fun observeTrips(): Flow<List<Trip>>
    fun observeTrip(id: Long): Flow<Trip?>
    suspend fun getTrip(id: Long): Trip?
    suspend fun upsert(trip: Trip): Long
    suspend fun delete(id: Long)
    suspend fun reorder(tripId: Long, orderedStopIds: List<Long>)
}

interface SavedPlaceRepository {
    fun observeAll(): Flow<List<SavedPlace>>
    suspend fun save(saved: SavedPlace): Long
    suspend fun delete(id: Long)
}

interface EmergencyContactRepository {
    fun observeAll(): Flow<List<EmergencyContact>>
    suspend fun upsert(contact: EmergencyContact): Long
    suspend fun delete(id: Long)
}

interface RecentSearchRepository {
    fun observeRecent(limit: Int = 10): Flow<List<RecentSearch>>
    suspend fun record(query: String, place: Place?)
    suspend fun delete(id: Long)
    suspend fun clear()
}

interface LocationRepository {
    val permissionState: StateFlow<LocationPermissionState>
    fun refreshPermissionState()
    fun isLocationEnabled(): Boolean
    suspend fun lastKnown(): AppResult<UserLocation>
    suspend fun currentLocation(): AppResult<UserLocation>
    fun locationUpdates(intervalMillis: Long = 2000L): Flow<UserLocation>
}

interface OfflineMapRepository {
    val regions: Flow<List<OfflineRegion>>
    suspend fun reconcile()
    fun estimate(bounds: GeoBounds, minZoom: Int, maxZoom: Int): OfflineEstimate
    fun download(region: OfflineRegion, allowMetered: Boolean = false): Flow<DownloadProgress>
    fun resume(regionId: String, allowMetered: Boolean = false): Flow<DownloadProgress>
    suspend fun cancel(regionId: String)
    suspend fun delete(regionId: String): AppResult<Unit>
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setMapStyle(style: MapStyleMode)
    suspend fun setRouteDefaults(profile: RouteProfile, options: RouteOptions)
    suspend fun setWifiOnlyDownloads(enabled: Boolean)
    suspend fun setVoiceGuidance(enabled: Boolean)
}

interface NearbyCacheRepository {
    suspend fun get(key: String): CachedPlaces?
    suspend fun put(key: String, places: List<Place>)
    suspend fun clear()
}
