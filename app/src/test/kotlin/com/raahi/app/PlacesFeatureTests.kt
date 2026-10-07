package com.raahi.app

import app.cash.turbine.test
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.CachedPlaces
import com.raahi.app.domain.model.EmergencyContact
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.PlaceSource
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.SavedPlaceType
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.model.TripStop
import com.raahi.app.domain.model.UserPreferences
import com.raahi.app.domain.repository.PlaceSearchRepository
import com.raahi.app.domain.usecase.ContactFieldError
import com.raahi.app.domain.usecase.ContactValidator
import com.raahi.app.domain.usecase.FindNearestEmergencyUseCase
import com.raahi.app.feature.emergency.EmergencyEvent
import com.raahi.app.feature.emergency.EmergencyViewModel
import com.raahi.app.feature.emergency.ServiceListState
import com.raahi.app.feature.emergencycontacts.EmergencyContactsEvent
import com.raahi.app.feature.emergencycontacts.EmergencyContactsViewModel
import com.raahi.app.feature.nearby.NearbyViewModel
import com.raahi.app.feature.placedetail.PlaceDetailEvent
import com.raahi.app.feature.placedetail.PlaceDetailViewModel
import com.raahi.app.feature.saved.SavedEvent
import com.raahi.app.feature.saved.SavedViewModel
import com.raahi.app.feature.search.SearchEvent
import com.raahi.app.feature.search.SearchPhase
import com.raahi.app.feature.search.SearchViewModel
import com.raahi.app.fakes.FakeClock
import com.raahi.app.fakes.FakeConnectivityMonitor
import com.raahi.app.fakes.FakeEmergencyContactRepository
import com.raahi.app.fakes.FakeLocationRepository
import com.raahi.app.fakes.FakeNearbyCacheRepository
import com.raahi.app.fakes.FakePlaceSearchRepository
import com.raahi.app.fakes.FakePreferencesRepository
import com.raahi.app.fakes.FakeRecentSearchRepository
import com.raahi.app.fakes.FakeSavedPlaceRepository
import com.raahi.app.fakes.FakeTripRepository
import com.raahi.app.fakes.samplePlace
import com.raahi.app.fakes.sampleUserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun suggestion(id: String, name: String = "Place $id") =
    SearchSuggestion(id = id, name = name, address = "Addr $id", category = null, distanceMeters = 100.0)

private class RecordingSearchRepository(private val latencyMillis: Long = 0L) : PlaceSearchRepository {
    val suggestCalls = mutableListOf<Triple<String, GeoPoint?, String>>()
    val retrieveTokens = mutableListOf<String>()
    var retrieveResult: AppResult<Place> = AppResult.Success(samplePlace())

    override suspend fun suggest(query: String, proximity: GeoPoint?, sessionToken: String): AppResult<List<SearchSuggestion>> {
        suggestCalls += Triple(query, proximity, sessionToken)
        delay(latencyMillis)
        return AppResult.Success(listOf(suggestion(id = query)))
    }

    override suspend fun retrieve(suggestion: SearchSuggestion, sessionToken: String): AppResult<Place> {
        retrieveTokens += sessionToken
        return retrieveResult
    }

    override suspend fun categorySearch(category: PlaceCategory, near: GeoPoint, limit: Int): AppResult<List<Place>> =
        AppResult.Success(emptyList())

    override suspend fun reverseGeocode(point: GeoPoint): AppResult<Place> = AppResult.Failure(AppError.NotFound)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val recents = FakeRecentSearchRepository()
    private val location = FakeLocationRepository()
    private val connectivity = FakeConnectivityMonitor()
    private var tokenCounter = 0

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(search: PlaceSearchRepository) =
        SearchViewModel(search, recents, location, connectivity, newSessionToken = { "token-${tokenCounter++}" })

    @Test
    fun queryIsDebouncedByThreeHundredMillis() = runTest {
        val search = RecordingSearchRepository()
        val vm = viewModel(search)
        vm.onQueryChange("de")
        advanceTimeBy(299)
        runCurrent()
        assertTrue(search.suggestCalls.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, search.suggestCalls.size)
        assertEquals(SearchPhase.Results, vm.uiState.value.phase)
        assertEquals("de", vm.uiState.value.suggestions.single().id)
    }

    @Test
    fun singleCharacterQueryNeverSearches() = runTest {
        val search = RecordingSearchRepository()
        val vm = viewModel(search)
        vm.onQueryChange("d")
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(search.suggestCalls.isEmpty())
        assertEquals(SearchPhase.Idle, vm.uiState.value.phase)
    }

    @Test
    fun rapidTypingOnlySearchesFinalQuery() = runTest {
        val search = RecordingSearchRepository()
        val vm = viewModel(search)
        vm.onQueryChange("de")
        advanceTimeBy(100)
        vm.onQueryChange("del")
        advanceTimeBy(100)
        vm.onQueryChange("delh")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf("delh"), search.suggestCalls.map { it.first })
    }

    @Test
    fun newQueryCancelsInFlightRequestAndDropsItsResult() = runTest {
        val search = RecordingSearchRepository(latencyMillis = 1_000)
        val vm = viewModel(search)
        vm.onQueryChange("ab")
        advanceTimeBy(350)
        assertEquals(SearchPhase.Loading, vm.uiState.value.phase)
        vm.onQueryChange("abc")
        advanceTimeBy(350)
        advanceUntilIdle()
        assertEquals(listOf("ab", "abc"), search.suggestCalls.map { it.first })
        assertEquals("abc", vm.uiState.value.suggestions.single().id)
        assertEquals(SearchPhase.Results, vm.uiState.value.phase)
    }

    @Test
    fun clearingQueryReturnsToIdleAndCancelsRequest() = runTest {
        val search = RecordingSearchRepository(latencyMillis = 1_000)
        val vm = viewModel(search)
        vm.onQueryChange("ab")
        advanceTimeBy(350)
        vm.onQueryChange("")
        advanceUntilIdle()
        assertEquals(SearchPhase.Idle, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun proximityIsLastKnownLocationWhenPermitted() = runTest {
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        val search = RecordingSearchRepository()
        val vm = viewModel(search)
        vm.onQueryChange("park")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(sampleUserLocation().point, search.suggestCalls.single().second)
    }

    @Test
    fun proximityIsNullWithoutPermission() = runTest {
        val search = RecordingSearchRepository()
        val vm = viewModel(search)
        vm.onQueryChange("park")
        advanceTimeBy(400)
        runCurrent()
        assertNull(search.suggestCalls.single().second)
    }

    @Test
    fun sessionTokenIsStableWithinSessionAndRegeneratedAfterSelection() = runTest {
        val search = RecordingSearchRepository()
        val vm = viewModel(search)
        vm.onQueryChange("ab")
        advanceTimeBy(400)
        vm.onQueryChange("abc")
        advanceTimeBy(400)
        runCurrent()
        val tokens = search.suggestCalls.map { it.third }.distinct()
        assertEquals(1, tokens.size)

        vm.select(vm.uiState.value.suggestions.single())
        runCurrent()
        vm.onQueryChange("abcd")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(tokens.single(), search.retrieveTokens.single())
        assertNotEquals(tokens.single(), search.suggestCalls.last().third)
    }

    @Test
    fun selectingSuggestionRetrievesRecordsRecentAndOpensPlace() = runTest {
        val search = RecordingSearchRepository()
        val vm = viewModel(search)
        vm.onQueryChange("fuel")
        advanceTimeBy(400)
        runCurrent()
        vm.events.test {
            vm.select(vm.uiState.value.suggestions.single())
            val event = awaitItem() as SearchEvent.OpenPlace
            assertEquals(samplePlace().id, event.place.id)
        }
        assertEquals(1, recents.items.value.size)
        assertEquals(samplePlace().id, recents.items.value.single().place?.id)
    }

    @Test
    fun failedRetrieveShowsErrorAndStaysOnScreen() = runTest {
        val search = RecordingSearchRepository().apply { retrieveResult = AppResult.Failure(AppError.NotFound) }
        val vm = viewModel(search)
        vm.onQueryChange("fuel")
        advanceTimeBy(400)
        runCurrent()
        vm.events.test {
            vm.select(vm.uiState.value.suggestions.single())
            assertTrue(awaitItem() is SearchEvent.ShowMessage)
            expectNoEvents()
        }
        assertTrue(recents.items.value.isEmpty())
        assertFalse(vm.uiState.value.isRetrieving)
    }

    @Test
    fun retrievedPlaceWithoutCoordinatesIsRejected() = runTest {
        val search = RecordingSearchRepository().apply {
            retrieveResult = AppResult.Success(samplePlace(point = GeoPoint(0.0, 0.0)))
        }
        val vm = viewModel(search)
        vm.onQueryChange("fuel")
        advanceTimeBy(400)
        runCurrent()
        vm.events.test {
            vm.select(vm.uiState.value.suggestions.single())
            assertTrue(awaitItem() is SearchEvent.ShowMessage)
        }
    }

    @Test
    fun imeSearchSelectsFirstSuggestion() = runTest {
        val fake = FakePlaceSearchRepository().apply {
            suggestResult = AppResult.Success(listOf(suggestion("first"), suggestion("second")))
        }
        val vm = viewModel(fake)
        vm.onQueryChange("fuel")
        advanceTimeBy(400)
        runCurrent()
        vm.events.test {
            vm.onImeSearch()
            assertTrue(awaitItem() is SearchEvent.OpenPlace)
        }
    }

    @Test
    fun emptyResultsProduceEmptyPhase() = runTest {
        val vm = viewModel(FakePlaceSearchRepository())
        vm.onQueryChange("zzzz")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(SearchPhase.Empty, vm.uiState.value.phase)
    }

    @Test
    fun offlineSkipsNetworkAndKeepsRecentsAvailable() = runTest {
        recents.record("fuel", samplePlace())
        connectivity.mutableState.value = ConnectivityState.Offline
        val search = RecordingSearchRepository()
        val vm = viewModel(search)
        vm.onQueryChange("delhi")
        advanceTimeBy(400)
        runCurrent()
        assertTrue(search.suggestCalls.isEmpty())
        assertEquals(SearchPhase.Error, vm.uiState.value.phase)
        assertEquals(AppError.NoInternet, vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.recents.size)
    }

    @Test
    fun recentsCanBeDeletedAndCleared() = runTest {
        recents.record("a", samplePlace(id = "a"))
        recents.record("b", samplePlace(id = "b"))
        val vm = viewModel(FakePlaceSearchRepository())
        assertEquals(2, vm.uiState.value.recents.size)
        vm.deleteRecent(vm.uiState.value.recents.first().id)
        assertEquals(1, vm.uiState.value.recents.size)
        vm.clearRecents()
        assertTrue(vm.uiState.value.recents.isEmpty())
    }

    @Test
    fun selectingRecentWithPlaceOpensItAndRecentWithoutPlaceFillsQuery() = runTest {
        recents.record("fuel", samplePlace(id = "r1"))
        recents.record("just text", null)
        val vm = viewModel(FakePlaceSearchRepository())
        val withPlace = vm.uiState.value.recents.first { it.place != null }
        val textOnly = vm.uiState.value.recents.first { it.place == null }
        vm.events.test {
            vm.selectRecent(withPlace)
            assertEquals("r1", (awaitItem() as SearchEvent.OpenPlace).place.id)
        }
        vm.selectRecent(textOnly)
        assertEquals("just text", vm.uiState.value.query)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceDetailViewModelTest {

    private val saved = FakeSavedPlaceRepository()
    private val trips = FakeTripRepository()
    private val preferences = FakePreferencesRepository(UserPreferences(defaultProfile = RouteProfile.WALKING))
    private val search = FakePlaceSearchRepository()
    private val location = FakeLocationRepository()
    private val connectivity = FakeConnectivityMonitor()
    private val clock = FakeClock(5_000L)
    private val place = Place("poi.9", "Dhaba", "NH 48", GeoPoint(28.0, 77.0), PlaceCategory.RESTAURANT, null, PlaceSource.SEARCH)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(initial: Place = place) =
        PlaceDetailViewModel(initial, saved, trips, preferences, search, location, connectivity, clock)

    @Test
    fun toggleFavoriteSavesThenRemoves() = runTest {
        val vm = viewModel()
        vm.toggleFavorite()
        assertTrue(vm.uiState.value.isFavorite)
        assertEquals(SavedPlaceType.FAVORITE, saved.items.value.single().type)
        vm.toggleFavorite()
        assertFalse(vm.uiState.value.isFavorite)
        assertTrue(saved.items.value.isEmpty())
    }

    @Test
    fun alreadySavedPlaceIsRecognisedOnOpen() = runTest {
        saved.save(SavedPlace(0, place, SavedPlaceType.FAVORITE, null, 1L))
        assertTrue(viewModel().uiState.value.isFavorite)
    }

    @Test
    fun settingHomeReplacesPreviousHome() = runTest {
        val other = place.copy(id = "poi.other", point = GeoPoint(10.0, 10.0))
        saved.save(SavedPlace(0, other, SavedPlaceType.HOME, null, 1L))
        val vm = viewModel()
        vm.toggleLabel(SavedPlaceType.HOME)
        val homes = saved.items.value.filter { it.type == SavedPlaceType.HOME }
        assertEquals(1, homes.size)
        assertEquals(place.id, homes.single().place.id)
        assertTrue(vm.uiState.value.isHome)
        vm.toggleLabel(SavedPlaceType.HOME)
        assertTrue(saved.items.value.none { it.type == SavedPlaceType.HOME })
    }

    @Test
    fun directionsCreatesQuickTripFromCurrentLocationWithDefaultProfile() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.startDirections()
            val event = awaitItem() as PlaceDetailEvent.OpenRoutePreview
            val trip = trips.getTrip(event.tripId)!!
            assertTrue(trip.isQuick)
            assertEquals(RouteProfile.WALKING, trip.profile)
            assertEquals(2, trip.stops.size)
            assertTrue(trip.stops[0].useCurrentLocation)
            assertNull(trip.stops[0].place)
            assertEquals(place.id, trip.stops[1].place?.id)
            assertEquals(1, trip.stops[1].order)
        }
    }

    @Test
    fun addToTripAppendsStopAndConfirms() = runTest {
        val id = trips.upsert(
            Trip(Trip.NEW_ID, "Goa", listOf(TripStop(0, null, true, 0), TripStop(0, samplePlace(), false, 1)), RouteProfile.DRIVING, 1L, 1L, false),
        )
        val vm = viewModel()
        assertEquals(1, vm.uiState.value.tripsForAdding.size)
        vm.events.test {
            vm.addToTrip(id)
            assertTrue(awaitItem() is PlaceDetailEvent.ShowMessage)
        }
        val updated = trips.getTrip(id)!!
        assertEquals(3, updated.stops.size)
        assertEquals(place.id, updated.stops.last().place?.id)
    }

    @Test
    fun addToTripRejectsWhenTripIsFull() = runTest {
        val stops = (0 until Trip.MAX_STOPS).map { TripStop(0, samplePlace(id = "p$it"), false, it) }
        val id = trips.upsert(Trip(Trip.NEW_ID, "Big", stops, RouteProfile.DRIVING, 1L, 1L, false))
        val vm = viewModel()
        vm.addToTrip(id)
        assertEquals(Trip.MAX_STOPS, trips.getTrip(id)!!.stops.size)
    }

    @Test
    fun quickTripsAreNotOfferedForAdding() = runTest {
        trips.upsert(Trip(Trip.NEW_ID, "Quick", emptyList(), RouteProfile.DRIVING, 1L, 1L, true))
        assertTrue(viewModel().uiState.value.tripsForAdding.isEmpty())
    }

    @Test
    fun missingAddressIsReverseGeocodedWhenOnline() = runTest {
        search.reverseResult = AppResult.Success(samplePlace(name = "Geocoded").copy(address = "Resolved Road"))
        val vm = viewModel(place.copy(address = null))
        assertEquals("Resolved Road", vm.uiState.value.place.address)
        assertEquals("Dhaba", vm.uiState.value.place.name)
    }

    @Test
    fun blankNameIsFilledFromReverseGeocode() = runTest {
        search.reverseResult = AppResult.Success(samplePlace(name = "Geocoded").copy(address = "Resolved Road"))
        val vm = viewModel(place.copy(name = "", address = null))
        assertEquals("Geocoded", vm.uiState.value.place.name)
    }

    @Test
    fun addressLookupWaitsUntilOnline() = runTest {
        connectivity.mutableState.value = ConnectivityState.Offline
        search.reverseResult = AppResult.Success(samplePlace().copy(address = "Back Online Road"))
        val vm = viewModel(place.copy(address = null))
        assertNull(vm.uiState.value.place.address)
        connectivity.mutableState.value = ConnectivityState.Online
        assertEquals("Back Online Road", vm.uiState.value.place.address)
    }

    @Test
    fun existingAddressIsNeverReplaced() = runTest {
        search.reverseResult = AppResult.Success(samplePlace().copy(address = "Other"))
        val vm = viewModel()
        assertEquals("NH 48", vm.uiState.value.place.address)
    }

    @Test
    fun distanceFromUserIsComputedWhenLocationKnown() = runTest {
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        val vm = viewModel()
        assertNotNull(vm.uiState.value.userDistanceMeters)
        assertTrue(vm.uiState.value.distanceMeters!! > 0.0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyViewModelTest {

    private val search = FakePlaceSearchRepository()
    private val location = FakeLocationRepository(LocationPermissionState.GRANTED_PRECISE)
    private val cache = FakeNearbyCacheRepository()
    private val connectivity = FakeConnectivityMonitor()
    private val clock = FakeClock(100_000L)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        cache.clock = clock
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(category: PlaceCategory = PlaceCategory.FUEL) =
        NearbyViewModel(category, search, location, cache, connectivity)

    @Test
    fun loadsResultsSortedByDistanceAndCachesThem() = runTest {
        val far = samplePlace(id = "far", point = GeoPoint(28.9, 77.4), distance = 9_000.0)
        val near = samplePlace(id = "near", point = GeoPoint(28.6141, 77.2091), distance = 30.0)
        search.categoryResult = AppResult.Success(listOf(far, near))
        val vm = viewModel()
        assertEquals(listOf("near", "far"), vm.uiState.value.results.map { it.id })
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(cache.entries[NearbyViewModel.cacheKey(PlaceCategory.FUEL)])
        assertEquals(PlaceCategory.FUEL to sampleUserLocation().point, search.categoryCalls.single())
    }

    @Test
    fun networkFailureFallsBackToCacheWithAge() = runTest {
        cache.entries[NearbyViewModel.cacheKey(PlaceCategory.FUEL)] = CachedPlaces(listOf(samplePlace(id = "cached")), 40_000L)
        search.categoryResult = AppResult.Failure(AppError.Timeout)
        val vm = viewModel()
        assertEquals("cached", vm.uiState.value.results.single().id)
        assertEquals(40_000L, vm.uiState.value.cachedAtMillis)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun offlineUsesCacheWithoutCallingNetwork() = runTest {
        connectivity.mutableState.value = ConnectivityState.Offline
        cache.entries[NearbyViewModel.cacheKey(PlaceCategory.FUEL)] = CachedPlaces(listOf(samplePlace(id = "cached")), 40_000L)
        val vm = viewModel()
        assertTrue(search.categoryCalls.isEmpty())
        assertEquals(40_000L, vm.uiState.value.cachedAtMillis)
        assertEquals("cached", vm.uiState.value.results.single().id)
    }

    @Test
    fun failureWithoutCacheSurfacesError() = runTest {
        search.categoryResult = AppResult.Failure(AppError.NoInternet)
        val vm = viewModel()
        assertEquals(AppError.NoInternet, vm.uiState.value.error)
        assertTrue(vm.uiState.value.results.isEmpty())
    }

    @Test
    fun deniedPermissionWithoutCacheNeedsLocation() = runTest {
        location.mutablePermission.value = LocationPermissionState.DENIED
        val vm = viewModel()
        assertTrue(vm.uiState.value.needsLocation)
        assertTrue(search.categoryCalls.isEmpty())
    }

    @Test
    fun grantingPermissionLoadsResults() = runTest {
        location.mutablePermission.value = LocationPermissionState.DENIED
        val vm = viewModel()
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        vm.onPermissionResult(granted = true)
        assertFalse(vm.uiState.value.needsLocation)
        assertEquals(1, vm.uiState.value.results.size)
    }

    @Test
    fun refreshReloadsFromNetwork() = runTest {
        val vm = viewModel()
        vm.refresh()
        assertEquals(2, search.categoryCalls.size)
        assertFalse(vm.uiState.value.isRefreshing)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyViewModelTest {

    private val search = FakePlaceSearchRepository()
    private val cache = FakeNearbyCacheRepository()
    private val contacts = FakeEmergencyContactRepository()
    private val location = FakeLocationRepository(LocationPermissionState.GRANTED_PRECISE)
    private val connectivity = FakeConnectivityMonitor()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        EmergencyViewModel(FindNearestEmergencyUseCase(search, cache), contacts, location, connectivity)

    @Test
    fun loadsAllThreeServiceCategories() = runTest {
        val vm = viewModel()
        val services = vm.uiState.value.services
        assertEquals(3, services.size)
        assertTrue(services.values.all { it is ServiceListState.Loaded })
    }

    @Test
    fun noLocationAndOfflineDegradesToContactsOnly() = runTest {
        location.mutablePermission.value = LocationPermissionState.DENIED
        connectivity.mutableState.value = ConnectivityState.Offline
        search.categoryResult = AppResult.Failure(AppError.NoInternet)
        contacts.upsert(EmergencyContact(0, "Amma", "9876543210"))
        val vm = viewModel()
        assertEquals(1, vm.uiState.value.contacts.size)
        assertTrue(vm.uiState.value.services.values.all { it == ServiceListState.NeedsLocation })
        assertTrue(search.categoryCalls.isEmpty())
    }

    @Test
    fun offlineWithLocationShowsCachedServices() = runTest {
        search.categoryResult = AppResult.Failure(AppError.NoInternet)
        cache.entries[FindNearestEmergencyUseCase.cacheKey(PlaceCategory.HOSPITAL)] =
            CachedPlaces(listOf(samplePlace(id = "h", category = PlaceCategory.HOSPITAL)), 1_234L)
        val vm = viewModel()
        val hospitals = vm.uiState.value.services.getValue(PlaceCategory.HOSPITAL) as ServiceListState.Loaded
        assertEquals(1_234L, hospitals.cachedAtMillis)
        assertTrue(vm.uiState.value.services.getValue(PlaceCategory.POLICE) is ServiceListState.Failed)
    }

    @Test
    fun shareLocationEmitsPointWhenKnown() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.shareLocation()
            assertEquals(sampleUserLocation().point, (awaitItem() as EmergencyEvent.ShareLocation).point)
        }
    }

    @Test
    fun shareLocationWithoutLocationAsksForHelp() = runTest {
        location.mutablePermission.value = LocationPermissionState.DENIED
        val vm = viewModel()
        vm.events.test {
            vm.shareLocation()
            assertEquals(EmergencyEvent.ShowLocationHelp, awaitItem())
        }
    }

    @Test
    fun deniedPermissionResultAsksForHelp() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onPermissionResult(granted = false)
            assertEquals(EmergencyEvent.ShowLocationHelp, awaitItem())
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyContactsViewModelTest {

    private val repository = FakeEmergencyContactRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun phoneValidationAcceptsIndianMobileAndServiceNumbers() {
        listOf("9876543210", "+919876543210", "+91 98765 43210", "98765-43210", "09876543210", "919876543210", "108", "100", "1091")
            .forEach { assertNull("$it should be valid", ContactValidator.validatePhone(it)) }
    }

    @Test
    fun phoneValidationRejectsMalformedNumbers() {
        listOf("12", "5876543210", "98765", "98765432101", "abcdefghij", "+1 9876543210", "12345")
            .forEach { assertEquals("$it should be invalid", ContactFieldError.Invalid, ContactValidator.validatePhone(it)) }
        assertEquals(ContactFieldError.Empty, ContactValidator.validatePhone("  "))
    }

    @Test
    fun nameValidationRequiresNonBlankAndBoundedLength() {
        assertEquals(ContactFieldError.Empty, ContactValidator.validateName("   "))
        assertEquals(ContactFieldError.TooLong, ContactValidator.validateName("x".repeat(ContactValidator.MAX_NAME_LENGTH + 1)))
        assertNull(ContactValidator.validateName("Amma"))
    }

    @Test
    fun invalidEditorShowsErrorsAndSavesNothing() = runTest {
        val vm = EmergencyContactsViewModel(repository)
        vm.startAdd()
        vm.onPhoneChange("12")
        vm.saveEditor()
        val editor = vm.uiState.value.editor!!
        assertEquals(ContactFieldError.Empty, editor.nameError)
        assertEquals(ContactFieldError.Invalid, editor.phoneError)
        assertTrue(repository.items.value.isEmpty())
    }

    @Test
    fun editingFieldClearsItsError() = runTest {
        val vm = EmergencyContactsViewModel(repository)
        vm.startAdd()
        vm.saveEditor()
        vm.onNameChange("A")
        assertNull(vm.uiState.value.editor!!.nameError)
        assertNotNull(vm.uiState.value.editor!!.phoneError)
    }

    @Test
    fun validEditorSavesNormalizedPhoneAndCloses() = runTest {
        val vm = EmergencyContactsViewModel(repository)
        vm.startAdd()
        vm.onNameChange("  Amma ")
        vm.onPhoneChange("+91 98765 43210")
        vm.saveEditor()
        assertNull(vm.uiState.value.editor)
        val saved = repository.items.value.single()
        assertEquals("Amma", saved.name)
        assertEquals("+919876543210", saved.phone)
    }

    @Test
    fun editingExistingContactKeepsItsIdentity() = runTest {
        val id = repository.upsert(EmergencyContact(0, "Old", "9876543210"))
        val vm = EmergencyContactsViewModel(repository)
        vm.startEdit(vm.uiState.value.contacts.single())
        vm.onNameChange("New")
        vm.saveEditor()
        val saved = repository.items.value.single()
        assertEquals(id, saved.id)
        assertEquals("New", saved.name)
    }

    @Test
    fun pickedContactFillsEditor() = runTest {
        val vm = EmergencyContactsViewModel(repository)
        vm.startAdd()
        vm.onContactPicked("Bhai", "98765 43210")
        assertEquals("Bhai", vm.uiState.value.editor!!.name)
        assertEquals("98765 43210", vm.uiState.value.editor!!.phone)
    }

    @Test
    fun deleteRequiresConfirmationAndOffersUndo() = runTest {
        repository.upsert(EmergencyContact(0, "Amma", "9876543210"))
        val vm = EmergencyContactsViewModel(repository)
        val contact = vm.uiState.value.contacts.single()
        vm.requestDelete(contact)
        assertEquals(1, repository.items.value.size)
        vm.events.test {
            vm.confirmDelete()
            assertTrue(awaitItem() is EmergencyContactsEvent.ShowUndo)
        }
        assertTrue(repository.items.value.isEmpty())
        vm.undoDelete()
        assertEquals("Amma", repository.items.value.single().name)
    }

    @Test
    fun cancelledDeleteKeepsContact() = runTest {
        repository.upsert(EmergencyContact(0, "Amma", "9876543210"))
        val vm = EmergencyContactsViewModel(repository)
        vm.requestDelete(vm.uiState.value.contacts.single())
        vm.cancelDelete()
        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(1, repository.items.value.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SavedViewModelTest {

    private val repository = FakeSavedPlaceRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun seed() {
        repository.save(SavedPlace(0, samplePlace(id = "home"), SavedPlaceType.HOME, null, 10L))
        repository.save(SavedPlace(0, samplePlace(id = "work"), SavedPlaceType.WORK, null, 20L))
        repository.save(SavedPlace(0, samplePlace(id = "p-old"), SavedPlaceType.PARKING, null, 30L))
        repository.save(SavedPlace(0, samplePlace(id = "p-new"), SavedPlaceType.PARKING, null, 40L))
        repository.save(SavedPlace(0, samplePlace(id = "fav"), SavedPlaceType.FAVORITE, null, 50L))
        repository.save(SavedPlace(0, samplePlace(id = "sos"), SavedPlaceType.EMERGENCY, null, 60L))
    }

    @Test
    fun startsEmptyAndLoaded() = runTest {
        val state = SavedViewModel(repository).uiState.value
        assertTrue(state.isLoaded)
        assertTrue(state.isEmpty)
    }

    @Test
    fun groupsIntoSlotsParkingAndFavorites() = runTest {
        seed()
        val state = SavedViewModel(repository).uiState.value
        assertEquals("home", state.home?.place?.id)
        assertEquals("work", state.work?.place?.id)
        assertEquals(listOf("p-new", "p-old"), state.parking.map { it.place.id })
        assertEquals(listOf("fav"), state.favorites.map { it.place.id })
        assertFalse(state.isEmpty)
    }

    @Test
    fun deleteOffersUndoAndRestoresSameItem() = runTest {
        seed()
        val vm = SavedViewModel(repository)
        val target = vm.uiState.value.favorites.single()
        vm.events.test {
            vm.delete(target)
            assertTrue(awaitItem() is SavedEvent.ShowUndo)
        }
        assertTrue(vm.uiState.value.favorites.isEmpty())
        vm.undoDelete()
        assertEquals(target.id, vm.uiState.value.favorites.single().id)
    }

    @Test
    fun deletingHomeEmptiesTheSlot() = runTest {
        seed()
        val vm = SavedViewModel(repository)
        vm.delete(vm.uiState.value.home!!)
        assertNull(vm.uiState.value.home)
    }
}
