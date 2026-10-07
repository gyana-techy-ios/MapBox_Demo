package com.raahi.app

import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.CongestionLevel
import com.raahi.app.domain.model.DownloadProgress
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.OfflineRegion
import com.raahi.app.domain.model.OfflineRegionStatus
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.RouteLeg
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.RouteStep
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.domain.model.ThemeMode
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.model.TripStop
import com.raahi.app.domain.model.UserPreferences
import com.raahi.app.domain.repository.DirectionsRepository
import com.raahi.app.domain.usecase.ActiveRouteStore
import com.raahi.app.domain.usecase.PlanRoutesUseCase
import com.raahi.app.domain.usecase.TripIssue
import com.raahi.app.domain.usecase.TripValidator
import com.raahi.app.feature.guidance.AnnouncementKind
import com.raahi.app.feature.guidance.GuidancePhase
import com.raahi.app.feature.guidance.GuidancePhraser
import com.raahi.app.feature.guidance.GuidanceViewModel
import com.raahi.app.feature.guidance.ManeuverGlyph
import com.raahi.app.feature.guidance.ManeuverGlyphs
import com.raahi.app.feature.offline.MeteredDownloadGuard
import com.raahi.app.feature.offline.OfflineEvent
import com.raahi.app.feature.offline.OfflineRegionMapper
import com.raahi.app.feature.offline.OfflineViewModel
import com.raahi.app.feature.offline.RegionAction
import com.raahi.app.feature.offlineregionpicker.DetailPreset
import com.raahi.app.feature.offlineregionpicker.OfflineRegionPickerViewModel
import com.raahi.app.feature.offlineregionpicker.PickerIssue
import com.raahi.app.feature.offlineregionpicker.RegionPickerEvent
import com.raahi.app.feature.routepreview.PreviewPhase
import com.raahi.app.feature.routepreview.RoutePreviewViewModel
import com.raahi.app.feature.settings.SettingsEvent
import com.raahi.app.feature.settings.SettingsViewModel
import com.raahi.app.feature.tripeditor.StopPickerEvent
import com.raahi.app.feature.tripeditor.StopPickerViewModel
import com.raahi.app.feature.tripeditor.StopSearchPhase
import com.raahi.app.feature.tripeditor.TripEditorEvent
import com.raahi.app.feature.tripeditor.TripEditorViewModel
import com.raahi.app.feature.trips.TripsEvent
import com.raahi.app.feature.trips.TripsViewModel
import com.raahi.app.fakes.FakeClock
import com.raahi.app.fakes.FakeConnectivityMonitor
import com.raahi.app.fakes.FakeDirectionsRepository
import com.raahi.app.fakes.FakeLocationRepository
import com.raahi.app.fakes.FakeNearbyCacheRepository
import com.raahi.app.fakes.FakeOfflineMapRepository
import com.raahi.app.fakes.FakePlaceSearchRepository
import com.raahi.app.fakes.FakePreferencesRepository
import com.raahi.app.fakes.FakeRecentSearchRepository
import com.raahi.app.fakes.FakeSavedPlaceRepository
import com.raahi.app.fakes.FakeTripRepository
import com.raahi.app.fakes.RecordingVoiceGuide
import com.raahi.app.fakes.samplePlace
import com.raahi.app.fakes.sampleUserLocation
import com.raahi.app.map.MapCameraInfo
import com.raahi.app.offline.DownloadCoordinator
import com.raahi.app.offline.StorageProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.viewModelScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val tripStart = GeoPoint(20.0, 85.0)
private val tripMiddle = GeoPoint(20.010, 85.0)
private val tripEnd = GeoPoint(20.020, 85.0)

private fun step(type: String, modifier: String?, distance: Double, at: GeoPoint, text: String = "$type $modifier") =
    RouteStep(text, type, modifier, distance, distance / 10, at, "Road")

private fun testRoute(
    id: String = "r1",
    duration: Double = 600.0,
    congestion: CongestionLevel = CongestionLevel.UNKNOWN,
): Route {
    val steps = listOf(
        step("depart", "straight", 1_112.0, tripStart),
        step("turn", "left", 1_112.0, tripMiddle, "Turn left onto Main Road"),
        step("arrive", null, 0.0, tripEnd, "You have arrived"),
    )
    return Route(id, listOf(tripStart, tripMiddle, tripEnd), 2_224.0, duration, listOf(RouteLeg(steps)), "NH16", congestion)
}

private fun placeStop(order: Int, place: Place) = TripStop(id = 0, place = place, useCurrentLocation = false, order = order)

private fun myLocationStop(order: Int = 0) = TripStop(id = 0, place = null, useCurrentLocation = true, order = order)

private fun newTrip(
    name: String = "Trip",
    stops: List<TripStop>,
    isQuick: Boolean = false,
    updatedAt: Long = 0L,
    profile: RouteProfile = RouteProfile.DRIVING_TRAFFIC,
) = Trip(Trip.NEW_ID, name, stops, profile, 0L, updatedAt, isQuick)

private fun namedPlace(id: String, point: GeoPoint = GeoPoint(20.0 + id.hashCode() % 50 / 1000.0, 85.0)) =
    samplePlace(id = id, name = "Place $id", point = point)

@OptIn(ExperimentalCoroutinesApi::class)
open class MainDispatcherTest {
    @Before
    fun installMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun removeMain() = Dispatchers.resetMain()
}

class TripsViewModelTest : MainDispatcherTest() {

    private val repo = FakeTripRepository()
    private val clock = FakeClock()

    @Test
    fun splitsSavedTripsFromRecentDirectionsAndCapsRecent() = runTest {
        repo.upsert(newTrip("Beach run", listOf(myLocationStop(0), placeStop(1, namedPlace("a"))), updatedAt = 5))
        repeat(7) { index ->
            repo.upsert(newTrip("Quick $index", listOf(myLocationStop(0), placeStop(1, namedPlace("q$index"))), isQuick = true, updatedAt = index.toLong()))
        }
        val vm = TripsViewModel(repo, clock)
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Beach run"), state.trips.map { it.name })
        assertEquals(TripsViewModel.MAX_RECENT, state.recent.size)
        assertEquals("Quick 6", state.recent.first().name)
    }

    @Test
    fun emptyWhenNothingStored() = runTest {
        assertTrue(TripsViewModel(repo, clock).uiState.value.isEmpty)
    }

    @Test
    fun deleteAndUndoRestoreTheSameTrip() = runTest {
        val id = repo.upsert(newTrip("Hills", listOf(myLocationStop(0), placeStop(1, namedPlace("a")))))
        val vm = TripsViewModel(repo, clock)
        val trip = vm.uiState.value.trips.single()
        vm.deleteTrip(trip)
        assertTrue(vm.uiState.value.trips.isEmpty())
        assertEquals(TripsEvent.TripDeleted(trip), vm.events.first())
        vm.undoDelete()
        val restored = vm.uiState.value.trips.single()
        assertEquals(id, restored.id)
        assertEquals("Hills", restored.name)
        assertEquals(2, restored.stops.size)
    }

    @Test
    fun clearRecentRemovesOnlyQuickTrips() = runTest {
        repo.upsert(newTrip("Keep", listOf(myLocationStop(0), placeStop(1, namedPlace("a")))))
        repo.upsert(newTrip("Quick", listOf(myLocationStop(0), placeStop(1, namedPlace("b"))), isQuick = true))
        val vm = TripsViewModel(repo, clock)
        vm.clearRecent()
        assertTrue(vm.uiState.value.recent.isEmpty())
        assertEquals(1, vm.uiState.value.trips.size)
        assertEquals(TripsEvent.RecentCleared, vm.events.first())
    }
}

class TripEditorViewModelTest : MainDispatcherTest() {

    private val repo = FakeTripRepository()
    private val prefs = FakePreferencesRepository(UserPreferences(defaultProfile = RouteProfile.WALKING, defaultRouteOptions = RouteOptions(avoidTolls = true)))
    private val clock = FakeClock()

    private fun editor(tripId: Long = TripEditorViewModel.NEW_TRIP) =
        TripEditorViewModel(tripId, repo, prefs, TripValidator(), clock, defaultName = { "Trip on 8 Oct" })

    @Test
    fun newTripStartsFromMyLocationWithPreferenceDefaults() = runTest {
        val state = editor().uiState.value
        val draft = state.draft!!
        assertEquals("Trip on 8 Oct", draft.name)
        assertTrue(draft.start.useCurrentLocation)
        assertEquals(RouteProfile.WALKING, draft.profile)
        assertTrue(draft.options.avoidTolls)
        assertEquals(listOf(TripIssue.TOO_FEW_STOPS), state.issues)
        assertFalse(state.canSave)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun addingDestinationsValidatesAndMarksUnsaved() = runTest {
        val vm = editor()
        vm.addStop(namedPlace("a"))
        vm.addStop(namedPlace("b"))
        val state = vm.uiState.value
        assertTrue(state.issues.isEmpty())
        assertTrue(state.canSave)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun consecutiveDuplicateAndTooManyStopsAreReported() = runTest {
        val vm = editor()
        vm.addStop(namedPlace("a"))
        vm.addStop(namedPlace("a"))
        assertTrue(TripIssue.CONSECUTIVE_DUPLICATE_STOPS in vm.uiState.value.issues)
        assertFalse(vm.uiState.value.canSave)

        val many = editor()
        repeat(Trip.MAX_STOPS) { many.addStop(namedPlace("p$it")) }
        assertTrue(TripIssue.TOO_MANY_STOPS in many.uiState.value.issues)
    }

    @Test
    fun movingStopsReordersAndRevertingClearsUnsavedFlag() = runTest {
        val vm = editor()
        listOf("a", "b", "c").forEach { vm.addStop(namedPlace(it)) }
        vm.moveStopUp(2)
        assertEquals(listOf("a", "c", "b"), vm.uiState.value.draft!!.destinations.map { it.place!!.id })
        assertEquals(TripEditorEvent.StopMoved("Place c", 3), vm.events.first())
        vm.moveStopDown(1)
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.draft!!.destinations.map { it.place!!.id })
        vm.moveStopDown(2)
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.draft!!.destinations.map { it.place!!.id })
        vm.moveStop(0, 2)
        assertEquals(listOf("b", "c", "a"), vm.uiState.value.draft!!.destinations.map { it.place!!.id })
    }

    @Test
    fun removingStopWorks() = runTest {
        val vm = editor()
        vm.addStop(namedPlace("a"))
        vm.addStop(namedPlace("b"))
        vm.removeStop(0)
        assertEquals(listOf("b"), vm.uiState.value.draft!!.destinations.map { it.place!!.id })
    }

    @Test
    fun savePersistsOrderedStopsAndEmitsSavedId() = runTest {
        val vm = editor()
        vm.setName("  Coast  ")
        vm.addStop(namedPlace("a"))
        vm.addStop(namedPlace("b"))
        vm.save()
        val saved = repo.trips.value.single()
        assertEquals("Coast", saved.name)
        assertFalse(saved.isQuick)
        assertEquals(listOf(true, false, false), saved.stops.map { it.useCurrentLocation })
        assertEquals(listOf(0, 1, 2), saved.stops.map { it.order })
        assertEquals(RouteProfile.WALKING, saved.profile)
        assertEquals(TripEditorEvent.Saved(saved.id), vm.events.first())
        assertFalse(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun blankNameFallsBackToDefaultName() = runTest {
        val vm = editor()
        vm.setName("   ")
        vm.addStop(namedPlace("a"))
        vm.save()
        assertEquals("Trip on 8 Oct", repo.trips.value.single().name)
    }

    @Test
    fun invalidDraftCannotBeSaved() = runTest {
        val vm = editor()
        vm.save()
        assertTrue(repo.trips.value.isEmpty())
    }

    @Test
    fun editModeLoadsExistingTripAndTracksChanges() = runTest {
        val id = repo.upsert(newTrip("Existing", listOf(placeStop(0, namedPlace("s")), placeStop(1, namedPlace("t")), placeStop(2, namedPlace("u")))))
        val vm = editor(id)
        val state = vm.uiState.value
        assertTrue(state.isEditing)
        assertEquals("Existing", state.draft!!.name)
        assertFalse(state.draft!!.start.useCurrentLocation)
        assertEquals(2, state.draft!!.destinations.size)
        assertFalse(state.hasUnsavedChanges)
        vm.setName("Renamed")
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        vm.save()
        assertEquals("Renamed", repo.trips.value.single().name)
        assertEquals(id, repo.trips.value.single().id)
    }

    @Test
    fun unknownTripIdShowsNotFound() = runTest {
        assertTrue(editor(999).uiState.value.isNotFound)
    }
}

class StopPickerViewModelTest : MainDispatcherTest() {

    private val search = FakePlaceSearchRepository()
    private val saved = FakeSavedPlaceRepository()

    private fun picker() = StopPickerViewModel(search, saved, FakeLocationRepository(), FakeConnectivityMonitor())

    @Test
    fun suggestionsAreDebouncedAndRetrievedPlaceIsPicked() = runTest {
        search.suggestResult = AppResult.Success(listOf(SearchSuggestion("s1", "Puri", "Odisha", null, null)))
        val vm = picker()
        vm.onQueryChange("pur")
        advanceTimeBy(200)
        runCurrent()
        assertEquals(StopSearchPhase.Idle, vm.uiState.value.phase)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(StopSearchPhase.Results, vm.uiState.value.phase)
        vm.pickSuggestion(vm.uiState.value.suggestions.single())
        val event = vm.events.first() as StopPickerEvent.Picked
        assertEquals("poi.1", event.place.id)
    }

    @Test
    fun offlineSearchReportsErrorButKeepsSavedPlaces() = runTest {
        val connectivity = FakeConnectivityMonitor(ConnectivityState.Offline)
        saved.save(com.raahi.app.domain.model.SavedPlace(0, samplePlace(), com.raahi.app.domain.model.SavedPlaceType.FAVORITE, null, 0))
        val vm = StopPickerViewModel(search, saved, FakeLocationRepository(), connectivity)
        vm.onQueryChange("pur")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(StopSearchPhase.Error, vm.uiState.value.phase)
        assertEquals(AppError.NoInternet, vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.saved.size)
        vm.pickSaved(vm.uiState.value.saved.single())
        assertNotNull((vm.events.first() as StopPickerEvent.Picked).place)
    }
}

class TripDetailAndPreviewTest : MainDispatcherTest() {

    private val trips = FakeTripRepository()
    private val directions = FakeDirectionsRepository()
    private val location = FakeLocationRepository(LocationPermissionState.GRANTED_PRECISE)
    private val connectivity = FakeConnectivityMonitor()
    private val store = ActiveRouteStore()
    private val clock = FakeClock()

    private suspend fun savedTrip(withCurrentLocation: Boolean = false): Long {
        val first = if (withCurrentLocation) myLocationStop(0) else placeStop(0, namedPlace("s", tripStart))
        return trips.upsert(newTrip("Preview", listOf(first, placeStop(1, namedPlace("t", tripEnd)))))
    }

    private fun preview(tripId: Long) = RoutePreviewViewModel(
        tripId, trips, PlanRoutesUseCase(location, directions, TripValidator()), location, connectivity, store, clock,
    )

    @Test
    fun successExposesRoutesFastestAndStoresThemForGuidance() = runTest {
        val id = savedTrip()
        directions.result = AppResult.Success(listOf(testRoute("slow", 900.0), testRoute("fast", 600.0, CongestionLevel.MODERATE)))
        val vm = preview(id)
        val state = vm.uiState.value
        assertEquals(PreviewPhase.Ready, state.phase)
        assertEquals(2, state.routes.size)
        assertEquals(1, state.fastestIndex)
        assertEquals(0, state.selectedIndex)
        assertEquals(2, store.get(id)!!.size)
    }

    @Test
    fun selectingARouteChangesSelectionAndIgnoresBadIndex() = runTest {
        val id = savedTrip()
        directions.result = AppResult.Success(listOf(testRoute("a"), testRoute("b")))
        val vm = preview(id)
        vm.selectRoute(1)
        assertEquals("b", vm.uiState.value.selectedRoute!!.id)
        vm.selectRoute(5)
        assertEquals(1, vm.uiState.value.selectedIndex)
    }

    @Test
    fun noRouteShowsNoRoutePhase() = runTest {
        val id = savedTrip()
        directions.result = AppResult.Failure(AppError.NoRoute)
        assertEquals(PreviewPhase.NoRoute, preview(id).uiState.value.phase)
    }

    @Test
    fun offlineSkipsTheNetworkAndRecoversWhenOnline() = runTest {
        val id = savedTrip()
        connectivity.mutableState.value = ConnectivityState.Offline
        directions.result = AppResult.Success(listOf(testRoute()))
        val vm = preview(id)
        assertEquals(PreviewPhase.Offline, vm.uiState.value.phase)
        assertTrue(directions.lastWaypoints.isEmpty())
        connectivity.mutableState.value = ConnectivityState.Online
        assertEquals(PreviewPhase.Ready, vm.uiState.value.phase)
    }

    @Test
    fun deniedLocationWithMyLocationStopAsksForPermission() = runTest {
        val id = savedTrip(withCurrentLocation = true)
        location.mutablePermission.value = LocationPermissionState.DENIED
        location.permissionAfterRefresh = LocationPermissionState.GRANTED_PRECISE
        directions.result = AppResult.Success(listOf(testRoute()))
        val vm = preview(id)
        assertEquals(PreviewPhase.NeedsLocationPermission, vm.uiState.value.phase)
        vm.onPermissionResult()
        assertEquals(PreviewPhase.Ready, vm.uiState.value.phase)
    }

    @Test
    fun unauthorizedAndRateLimitedKeepTheActionableError() = runTest {
        val id = savedTrip()
        directions.result = AppResult.Failure(AppError.RateLimited)
        val vm = preview(id)
        assertEquals(PreviewPhase.Failed, vm.uiState.value.phase)
        assertEquals(AppError.RateLimited, vm.uiState.value.error)
    }

    @Test
    fun retryIsDebouncedThenSucceeds() = runTest {
        val id = savedTrip()
        directions.result = AppResult.Failure(AppError.Timeout)
        val vm = preview(id)
        directions.result = AppResult.Success(listOf(testRoute()))
        vm.retry()
        assertEquals(PreviewPhase.Failed, vm.uiState.value.phase)
        clock.now += RoutePreviewViewModel.RETRY_DEBOUNCE_MILLIS + 1
        vm.retry()
        assertEquals(PreviewPhase.Ready, vm.uiState.value.phase)
    }

    @Test
    fun missingTripIsReported() = runTest {
        assertEquals(PreviewPhase.TripMissing, preview(404).uiState.value.phase)
    }
}

private class CountingDirections(var result: AppResult<List<Route>>) : DirectionsRepository {
    val waypointCalls = mutableListOf<List<GeoPoint>>()
    override suspend fun getRoutes(waypoints: List<GeoPoint>, profile: RouteProfile, options: RouteOptions): AppResult<List<Route>> {
        waypointCalls += waypoints
        return result
    }
}

class GuidanceViewModelTest : MainDispatcherTest() {

    private val trips = FakeTripRepository()
    private val location = FakeLocationRepository(LocationPermissionState.GRANTED_PRECISE)
    private val connectivity = FakeConnectivityMonitor()
    private val directions = CountingDirections(AppResult.Failure(AppError.NoRoute))
    private val store = ActiveRouteStore()
    private val clock = FakeClock()
    private val voice = RecordingVoiceGuide()
    private val prefs = FakePreferencesRepository()
    private val phraser = GuidancePhraser { kind, step, distance, _ -> "$kind:${step?.instruction}:${distance.toInt()}" }
    private var tripId = 0L

    private suspend fun guidance(withStoredRoute: Boolean = true): GuidanceViewModel {
        tripId = trips.upsert(newTrip("Drive", listOf(myLocationStop(0), placeStop(1, namedPlace("end", tripEnd)))))
        if (withStoredRoute) store.put(tripId, listOf(testRoute()))
        return GuidanceViewModel(
            tripId, 0, trips, store, PlanRoutesUseCase(location, directions, TripValidator()),
            location, prefs, connectivity, voice, clock, phraser,
        )
    }

    private fun fix(point: GeoPoint) = location.updates.tryEmit(sampleUserLocation(point))

    @Test
    fun usesStoredRouteAndReportsProgressAlongIt() = runTest {
        val vm = guidance()
        assertEquals(GuidancePhase.Active, vm.uiState.value.phase)
        fix(GeoPoint(20.002, 85.0))
        val state = vm.uiState.value
        val progress = state.progress!!
        assertTrue(progress.remainingDistanceMeters in 1_900.0..2_100.0)
        assertEquals("Turn left onto Main Road", progress.nextStep!!.instruction)
        assertNotNull(state.etaMillis)
        assertFalse(state.waitingForGps)
        assertEquals(0, directions.waypointCalls.size)
        vm.viewModelScope.cancel()
    }

    @Test
    fun recomputesTheRouteWhenTheStoreIsEmpty() = runTest {
        directions.result = AppResult.Success(listOf(testRoute("fresh")))
        val vm = guidance(withStoredRoute = false)
        assertEquals(GuidancePhase.Active, vm.uiState.value.phase)
        assertEquals("fresh", vm.uiState.value.route!!.id)
        assertEquals(1, directions.waypointCalls.size)
        vm.viewModelScope.cancel()
    }

    @Test
    fun failedRecomputeShowsErrorState() = runTest {
        directions.result = AppResult.Failure(AppError.Unauthorized)
        val vm = guidance(withStoredRoute = false)
        assertEquals(GuidancePhase.Failed, vm.uiState.value.phase)
        assertEquals(AppError.Unauthorized, vm.uiState.value.error)
        vm.viewModelScope.cancel()
    }

    @Test
    fun deniedPermissionWaitsUntilGranted() = runTest {
        location.mutablePermission.value = LocationPermissionState.DENIED
        val vm = guidance()
        assertEquals(GuidancePhase.PermissionDenied, vm.uiState.value.phase)
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        assertEquals(GuidancePhase.Active, vm.uiState.value.phase)
        vm.viewModelScope.cancel()
    }

    @Test
    fun offRouteTriggersRerouteAndThrottlesRepeats() = runTest {
        val vm = guidance()
        repeat(3) { fix(GeoPoint(20.005, 85.02)) }
        assertEquals(1, directions.waypointCalls.size)
        assertEquals(sampleUserLocation().point, directions.waypointCalls.single().first())
        repeat(3) { fix(GeoPoint(20.005, 85.02)) }
        assertEquals("throttled inside 15 s", 1, directions.waypointCalls.size)
        clock.now += GuidanceViewModel.REROUTE_THROTTLE_MILLIS + 1
        fix(GeoPoint(20.005, 85.02))
        assertEquals(2, directions.waypointCalls.size)
        vm.viewModelScope.cancel()
    }

    @Test
    fun successfulRerouteReplacesTheRoute() = runTest {
        val vm = guidance()
        directions.result = AppResult.Success(listOf(testRoute("detour")))
        repeat(3) { fix(GeoPoint(20.005, 85.02)) }
        assertEquals("detour", vm.uiState.value.route!!.id)
        assertFalse(vm.uiState.value.isRerouting)
        assertTrue(voice.spoken.any { it.startsWith("Rerouting") })
        vm.viewModelScope.cancel()
    }

    @Test
    fun offlineOffRouteDoesNotCallTheNetworkAndKeepsOldRoute() = runTest {
        val vm = guidance()
        connectivity.mutableState.value = ConnectivityState.Offline
        repeat(3) { fix(GeoPoint(20.005, 85.02)) }
        assertTrue(vm.uiState.value.needsConnectionToReroute)
        assertTrue(directions.waypointCalls.isEmpty())
        assertEquals("r1", vm.uiState.value.route!!.id)
        vm.viewModelScope.cancel()
    }

    @Test
    fun arrivingAtTheEndShowsFinalArrival() = runTest {
        val vm = guidance()
        fix(tripEnd)
        val arrival = vm.uiState.value.arrival!!
        assertTrue(arrival.isFinal)
        assertTrue(voice.spoken.any { it.startsWith("Arrived") })
        vm.dismissArrival()
        assertNull(vm.uiState.value.arrival)
        vm.viewModelScope.cancel()
    }

    @Test
    fun lostGpsShowsWaitingBannerAndFixClearsIt() = runTest {
        val vm = guidance()
        fix(tripStart)
        clock.now += GuidanceViewModel.GPS_LOSS_MILLIS + 1_000
        advanceTimeBy(GuidanceViewModel.GPS_CHECK_MILLIS + 1)
        runCurrent()
        assertTrue(vm.uiState.value.waitingForGps)
        fix(tripStart)
        assertFalse(vm.uiState.value.waitingForGps)
        vm.viewModelScope.cancel()
    }

    @Test
    fun announcesNewStepsAndTheImminentManeuverOnceAndRespectsMute() = runTest {
        val vm = guidance()
        fix(GeoPoint(20.001, 85.0))
        assertEquals(1, voice.spoken.size)
        assertTrue(voice.spoken.first().startsWith("Upcoming:Turn left"))
        fix(GeoPoint(20.0095, 85.0))
        assertEquals(2, voice.spoken.size)
        assertTrue(voice.spoken.last().startsWith("Imminent:Turn left"))
        fix(GeoPoint(20.0096, 85.0))
        assertEquals(2, voice.spoken.size)
        vm.toggleVoice()
        assertFalse(vm.uiState.value.voiceEnabled)
        fix(GeoPoint(20.0099, 85.0))
        assertEquals(2, voice.spoken.size)
        vm.viewModelScope.cancel()
    }

    @Test
    fun maneuverGlyphsFollowTypeAndModifier() {
        assertEquals(ManeuverGlyph.Left, ManeuverGlyphs.glyphFor("turn", "left"))
        assertEquals(ManeuverGlyph.SharpRight, ManeuverGlyphs.glyphFor("turn", "sharp right"))
        assertEquals(ManeuverGlyph.RoundaboutRight, ManeuverGlyphs.glyphFor("roundabout", "right"))
        assertEquals(ManeuverGlyph.Arrive, ManeuverGlyphs.glyphFor("arrive", null))
        assertEquals(ManeuverGlyph.Straight, ManeuverGlyphs.glyphFor("continue", "straight"))
        assertEquals(ManeuverGlyph.UTurn, ManeuverGlyphs.glyphFor("turn", "uturn"))
    }

    @Test
    fun missingTripShowsMissingPhase() = runTest {
        val vm = GuidanceViewModel(
            404, 0, trips, store, PlanRoutesUseCase(location, directions, TripValidator()),
            location, prefs, connectivity, voice, clock, phraser,
        )
        assertEquals(GuidancePhase.TripMissing, vm.uiState.value.phase)
        vm.viewModelScope.cancel()
    }
}

private fun region(
    id: String = "r",
    status: OfflineRegionStatus = OfflineRegionStatus.Completed,
    progress: Float = 1f,
    outdated: Boolean = false,
    size: Long = 10_000_000L,
) = OfflineRegion(id, "Region $id", GeoBounds(20.0, 85.0, 20.1, 85.1), 10, 14, status, progress, size, 1_000L, outdated)

class OfflineFeatureTest : MainDispatcherTest() {

    private val repo = FakeOfflineMapRepository()
    private val connectivity = FakeConnectivityMonitor()
    private val prefs = FakePreferencesRepository()

    @Test
    fun mapperAssignsActionsPerStatus() {
        assertEquals(listOf(RegionAction.Cancel, RegionAction.Delete), OfflineRegionMapper.toItem(region(status = OfflineRegionStatus.Downloading, progress = 0.2f), null).actions)
        assertEquals(listOf(RegionAction.Resume, RegionAction.Delete), OfflineRegionMapper.toItem(region(status = OfflineRegionStatus.Failed), null).actions)
        assertEquals(listOf(RegionAction.Resume, RegionAction.Delete), OfflineRegionMapper.toItem(region(status = OfflineRegionStatus.Partial, progress = 0.5f), null).actions)
        assertEquals(listOf(RegionAction.Resume, RegionAction.Delete), OfflineRegionMapper.toItem(region(status = OfflineRegionStatus.Cancelled), null).actions)
        assertEquals(listOf(RegionAction.Delete), OfflineRegionMapper.toItem(region(), null).actions)
        assertEquals(listOf(RegionAction.Update, RegionAction.Delete), OfflineRegionMapper.toItem(region(outdated = true), null).actions)
    }

    @Test
    fun outdatedBadgeOnlyForCompletedRegions() {
        assertTrue(OfflineRegionMapper.toItem(region(outdated = true), null).isOutdated)
        assertFalse(OfflineRegionMapper.toItem(region(status = OfflineRegionStatus.Failed, outdated = true), null).isOutdated)
        assertFalse(OfflineRegionMapper.toItem(region(), null).isOutdated)
    }

    @Test
    fun liveProgressOverridesStoredStatusAndFailureCarriesError() {
        val live = DownloadProgress("r", OfflineRegionStatus.Downloading, 0.6f, 100)
        val item = OfflineRegionMapper.toItem(region(status = OfflineRegionStatus.Partial, progress = 0.1f), live)
        assertEquals(OfflineRegionStatus.Downloading, item.status)
        assertEquals(0.6f, item.progress)
        val failed = DownloadProgress("r", OfflineRegionStatus.Failed, 0.1f, 0, AppError.StorageFull)
        assertEquals(AppError.StorageFull, OfflineRegionMapper.toItem(region(status = OfflineRegionStatus.Failed), failed).error)
    }

    private fun viewModel(token: String? = "pk.test", scope: kotlinx.coroutines.CoroutineScope): OfflineViewModel {
        val coordinator = DownloadCoordinator(repo, scope)
        return OfflineViewModel(repo, coordinator, connectivity, com.raahi.app.core.config.MapboxConfig(token), MeteredDownloadGuard(prefs, connectivity))
    }

    @Test
    fun listsRegionsReconcilesAndSumsStorage() = runTest {
        repo.regionsState.value = listOf(region("a", size = 5_000_000), region("b", OfflineRegionStatus.Downloading, 0.3f, size = 1_000_000))
        val vm = viewModel(scope = backgroundScope)
        val state = vm.uiState.value
        assertEquals(1, repo.reconcileCalls)
        assertEquals(2, state.items.size)
        assertEquals(6_000_000L, state.totalBytes)
        assertTrue(state.isConfigured)
        assertFalse(state.isLoading)
    }

    @Test
    fun emptyAndUnconfiguredStatesAreExposed() = runTest {
        val vm = viewModel(token = null, scope = backgroundScope)
        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.isConfigured)
    }

    @Test
    fun resumeAsksForConfirmationOnMeteredWifiOnlyThenResumes() = runTest {
        connectivity.metered = true
        val vm = viewModel(scope = backgroundScope)
        vm.resume("r1")
        assertEquals(OfflineEvent.ConfirmMetered("r1"), vm.events.first())
        assertTrue(repo.resumeCalls.isEmpty())
        vm.resume("r1", allowMetered = true)
        runCurrent()
        assertEquals(listOf("r1" to true), repo.resumeCalls)
    }

    @Test
    fun deleteRemovesTheRegionThroughTheCoordinator() = runTest {
        repo.regionsState.value = listOf(region("a"))
        val vm = viewModel(scope = backgroundScope)
        vm.delete("a")
        assertEquals(listOf("a"), repo.deleteCalls)
        assertTrue(vm.uiState.value.items.isEmpty())
        assertEquals(OfflineEvent.Deleted, vm.events.first())
    }
}

class DownloadCoordinatorTest : MainDispatcherTest() {

    private val repo = FakeOfflineMapRepository()

    @Test
    fun startPublishesProgressAndClearsOnCompletion() = runTest {
        val source = MutableSharedFlow<DownloadProgress>()
        repo.downloadFlow = { source }
        val coordinator = DownloadCoordinator(repo, backgroundScope)
        coordinator.start(region("x", OfflineRegionStatus.Downloading, 0f))
        runCurrent()
        assertTrue(coordinator.isDownloading("x"))
        source.emit(DownloadProgress("x", OfflineRegionStatus.Downloading, 0.4f, 10))
        assertEquals(0.4f, coordinator.progress.value["x"]!!.progress)
        source.emit(DownloadProgress("x", OfflineRegionStatus.Completed, 1f, 20))
        assertNull(coordinator.progress.value["x"])
        assertEquals(1, repo.downloadCalls.size)
    }

    @Test
    fun failureKeepsTheErrorVisibleUntilRetry() = runTest {
        repo.downloadFlow = { flow { emit(DownloadProgress("x", OfflineRegionStatus.Failed, 0f, 0, AppError.NoInternet)) } }
        val coordinator = DownloadCoordinator(repo, backgroundScope)
        coordinator.start(region("x", OfflineRegionStatus.Downloading, 0f))
        runCurrent()
        assertEquals(AppError.NoInternet, coordinator.progress.value["x"]!!.error)
        assertFalse(coordinator.isDownloading("x"))
        repo.resumeFlow = { emptyFlow() }
        coordinator.resume("x")
        runCurrent()
        assertNull(coordinator.progress.value["x"])
    }

    @Test
    fun cancelStopsTheJobAndTellsTheRepository() = runTest {
        repo.downloadFlow = { MutableSharedFlow() }
        val coordinator = DownloadCoordinator(repo, backgroundScope)
        coordinator.start(region("x", OfflineRegionStatus.Downloading, 0f))
        runCurrent()
        coordinator.cancel("x")
        runCurrent()
        assertFalse(coordinator.isDownloading("x"))
        assertEquals(listOf("x"), repo.cancelCalls)
    }

    @Test
    fun startingTwiceWhileActiveIsIgnoredAndResumePassesMeteredFlag() = runTest {
        repo.downloadFlow = { MutableSharedFlow() }
        repo.resumeFlow = { MutableSharedFlow() }
        val coordinator = DownloadCoordinator(repo, backgroundScope)
        coordinator.start(region("x", OfflineRegionStatus.Downloading, 0f))
        coordinator.start(region("x", OfflineRegionStatus.Downloading, 0f))
        runCurrent()
        assertEquals(1, repo.downloadCalls.size)
        coordinator.resume("y", allowMetered = true)
        runCurrent()
        assertEquals(listOf("y" to true), repo.resumeCalls)
    }
}

class OfflineRegionPickerViewModelTest : MainDispatcherTest() {

    private val repo = FakeOfflineMapRepository()
    private val connectivity = FakeConnectivityMonitor()
    private val prefs = FakePreferencesRepository()
    private val places = FakePlaceSearchRepository()
    private var availableBytes = Long.MAX_VALUE

    private fun picker(scope: kotlinx.coroutines.CoroutineScope) = OfflineRegionPickerViewModel(
        offlineMaps = repo,
        coordinator = DownloadCoordinator(repo, scope),
        placeSearch = places,
        locationRepository = FakeLocationRepository(LocationPermissionState.GRANTED_PRECISE),
        connectivity = connectivity,
        storage = StorageProbe { availableBytes },
        meteredGuard = MeteredDownloadGuard(prefs, connectivity),
        defaultName = { "Area $it" },
        newRegionId = { "region-1" },
    )

    private fun camera(span: Double) = MapCameraInfo(
        center = GeoPoint(20.2961, 85.8245),
        zoom = 13.0,
        bounds = GeoBounds(20.2961 - span, 85.8245 - span, 20.2961 + span, 85.8245 + span),
    )

    @Test
    fun smallAreaProducesEstimateAndNoIssue() = runTest {
        val vm = picker(backgroundScope)
        vm.onCameraIdle(camera(0.01))
        val state = vm.uiState.value
        assertTrue(state.estimate!!.tileCount > 0)
        assertNull(state.issue)
        assertTrue(state.canDownload)
        assertEquals("Area 1", state.name)
    }

    @Test
    fun hugeAreaIsRejectedAsTooLarge() = runTest {
        val vm = picker(backgroundScope)
        vm.onCameraIdle(camera(3.0))
        assertEquals(PickerIssue.TooLarge, vm.uiState.value.issue)
        assertFalse(vm.uiState.value.canDownload)
    }

    @Test
    fun streetDetailMultipliesTheEstimate() = runTest {
        val vm = picker(backgroundScope)
        vm.onCameraIdle(camera(0.05))
        val roads = vm.uiState.value.estimate!!.tileCount
        vm.onPresetChange(DetailPreset.Street)
        assertTrue(vm.uiState.value.estimate!!.tileCount > roads)
    }

    @Test
    fun insufficientStorageAndOfflineAreFlagged() = runTest {
        availableBytes = 1_000L
        val vm = picker(backgroundScope)
        vm.onCameraIdle(camera(0.01))
        assertEquals(PickerIssue.StorageLow, vm.uiState.value.issue)
        availableBytes = Long.MAX_VALUE
        connectivity.mutableState.value = ConnectivityState.Offline
        assertEquals(PickerIssue.Offline, vm.uiState.value.issue)
    }

    @Test
    fun downloadStartsTheCoordinatorWithTheSelectedPreset() = runTest {
        val vm = picker(backgroundScope)
        vm.onCameraIdle(camera(0.01))
        vm.onNameChange("  Old town ")
        vm.onPresetChange(DetailPreset.Street)
        vm.download()
        runCurrent()
        assertEquals(RegionPickerEvent.DownloadStarted, vm.events.first())
        val (started, allowMetered) = repo.downloadCalls.single()
        assertEquals("region-1", started.id)
        assertEquals("Old town", started.name)
        assertEquals(10, started.minZoom)
        assertEquals(16, started.maxZoom)
        assertFalse(allowMetered)
    }

    @Test
    fun meteredWifiOnlyAsksBeforeDownloading() = runTest {
        connectivity.metered = true
        val vm = picker(backgroundScope)
        vm.onCameraIdle(camera(0.01))
        vm.download()
        assertEquals(RegionPickerEvent.ConfirmMetered, vm.events.first())
        assertTrue(repo.downloadCalls.isEmpty())
        vm.download(allowMetered = true)
        runCurrent()
        assertTrue(repo.downloadCalls.single().second)
    }

    @Test
    fun reverseGeocodedNameIsUsedUntilTheUserEditsIt() = runTest {
        places.reverseResult = AppResult.Success(samplePlace(name = "Bhubaneswar"))
        val vm = picker(backgroundScope)
        vm.onCameraIdle(camera(0.01))
        advanceTimeBy(OfflineRegionPickerViewModel.GEOCODE_DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertEquals("Bhubaneswar", vm.uiState.value.name)
        vm.onNameChange("Mine")
        places.reverseResult = AppResult.Success(samplePlace(name = "Cuttack"))
        vm.onCameraIdle(camera(0.02))
        advanceTimeBy(OfflineRegionPickerViewModel.GEOCODE_DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertEquals("Mine", vm.uiState.value.name)
    }
}

class SettingsViewModelTest : MainDispatcherTest() {

    private val prefs = FakePreferencesRepository()
    private val recents = FakeRecentSearchRepository()
    private val cache = FakeNearbyCacheRepository()

    private fun settings() = SettingsViewModel(prefs, recents, cache)

    @Test
    fun togglesWriteThroughToPreferences() = runTest {
        val vm = settings()
        vm.setTheme(ThemeMode.DARK)
        vm.setMapStyle(MapStyleMode.SATELLITE)
        vm.setWifiOnly(false)
        vm.setVoiceGuidance(false)
        vm.setDefaultProfile(RouteProfile.CYCLING)
        vm.setDefaultOptions(RouteOptions(avoidTolls = true, avoidFerries = true))
        val stored = prefs.state.value
        assertEquals(ThemeMode.DARK, stored.themeMode)
        assertEquals(MapStyleMode.SATELLITE, stored.mapStyle)
        assertFalse(stored.wifiOnlyDownloads)
        assertFalse(stored.voiceGuidance)
        assertEquals(RouteProfile.CYCLING, stored.defaultProfile)
        assertEquals(RouteOptions(avoidTolls = true, avoidFerries = true), stored.defaultRouteOptions)
        assertEquals(ThemeMode.DARK, vm.uiState.value.preferences.themeMode)
    }

    @Test
    fun changingProfileKeepsAvoidOptions() = runTest {
        val vm = settings()
        vm.setDefaultOptions(RouteOptions(avoidMotorways = true))
        vm.setDefaultProfile(RouteProfile.WALKING)
        assertTrue(prefs.state.value.defaultRouteOptions.avoidMotorways)
    }

    @Test
    fun clearActionsEmptyTheirStoresAndReportBack() = runTest {
        recents.record("taj", samplePlace())
        cache.put("fuel", listOf(samplePlace()))
        val vm = settings()
        vm.clearSearchHistory()
        vm.clearNearbyCache()
        assertTrue(recents.items.value.isEmpty())
        assertTrue(cache.entries.isEmpty())
        assertEquals(SettingsEvent.ShowMessage(R.string.settings_history_cleared), vm.events.first())
    }
}
