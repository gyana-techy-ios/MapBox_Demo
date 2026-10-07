package com.raahi.app

import app.cash.turbine.test
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.SavedPlaceType
import com.raahi.app.feature.explore.ExploreEvent
import com.raahi.app.feature.explore.ExploreViewModel
import com.raahi.app.feature.onboarding.LocationOutcome
import com.raahi.app.feature.onboarding.OnboardingStep
import com.raahi.app.feature.onboarding.OnboardingViewModel
import com.raahi.app.feature.onboarding.classifyLocationResult
import com.raahi.app.fakes.FakeClock
import com.raahi.app.fakes.FakeConnectivityMonitor
import com.raahi.app.fakes.FakeLocationRepository
import com.raahi.app.fakes.FakeNearbyCacheRepository
import com.raahi.app.fakes.FakePlaceSearchRepository
import com.raahi.app.fakes.FakePreferencesRepository
import com.raahi.app.fakes.FakeSavedPlaceRepository
import com.raahi.app.fakes.samplePlace
import com.raahi.app.fakes.sampleUserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {

    private val search = FakePlaceSearchRepository()
    private val location = FakeLocationRepository()
    private val preferences = FakePreferencesRepository()
    private val saved = FakeSavedPlaceRepository()
    private val cache = FakeNearbyCacheRepository()
    private val connectivity = FakeConnectivityMonitor()
    private val clock = FakeClock(10_000L)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        cache.clock = clock
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ExploreViewModel(search, location, preferences, saved, cache, connectivity, clock)

    @Test
    fun selectingCategoryLoadsSortedResultsAndCachesThem() = runTest {
        val far = samplePlace(id = "far", point = GeoPoint(28.9, 77.4), distance = 9000.0)
        val close = samplePlace(id = "close", point = GeoPoint(28.6141, 77.2091), distance = 30.0)
        search.categoryResult = AppResult.Success(listOf(far, close))
        val vm = viewModel().apply { onCameraIdle(GeoPoint(28.6139, 77.2090), 12.0) }

        vm.onCategorySelected(PlaceCategory.FUEL)

        val state = vm.uiState.value
        assertEquals(PlaceCategory.FUEL, state.selectedCategory)
        assertEquals(listOf("close", "far"), state.results.map { it.id })
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, cache.entries["explore_FUEL"]?.places?.size)
    }

    @Test
    fun zoomedInCameraCentreIsSearchOrigin() = runTest {
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        val vm = viewModel()
        vm.onCameraIdle(GeoPoint(12.97, 77.59), 10.0)
        vm.onCategorySelected(PlaceCategory.ATM)
        assertEquals(GeoPoint(12.97, 77.59), search.categoryCalls.single().second)
    }

    @Test
    fun zoomedOutCameraUsesUserLocationAsOrigin() = runTest {
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        val user = GeoPoint(20.2961, 85.8245)
        location.locationResult = AppResult.Success(sampleUserLocation(user))
        val vm = viewModel()
        vm.onCameraIdle(GeoPoint.INDIA_CENTER, 4.2)
        vm.onCategorySelected(PlaceCategory.HOSPITAL)
        assertEquals(user, search.categoryCalls.single().second)
        assertFalse(vm.uiState.value.needsLocation)
    }

    @Test
    fun zoomedOutWithoutUserLocationNeedsLocationAndDoesNotSearch() = runTest {
        val vm = viewModel()
        vm.onCameraIdle(GeoPoint.INDIA_CENTER, 4.2)
        vm.onCategorySelected(PlaceCategory.HOSPITAL)
        assertTrue(vm.uiState.value.needsLocation)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(search.categoryCalls.isEmpty())
    }

    @Test
    fun resultsShowDistanceFromUserAndFitEventIncludesOrigin() = runTest {
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        val user = GeoPoint(20.2961, 85.8245)
        location.locationResult = AppResult.Success(sampleUserLocation(user))
        val place = samplePlace(id = "h", point = GeoPoint(20.2962, 85.8246), distance = 5000.0)
        search.categoryResult = AppResult.Success(listOf(place))
        val vm = viewModel()
        vm.onCameraIdle(GeoPoint(20.3, 85.8), 12.0)
        vm.events.test {
            vm.onCategorySelected(PlaceCategory.HOSPITAL)
            assertEquals(ExploreEvent.FitToResults(listOf(place.point, GeoPoint(20.3, 85.8))), awaitItem())
        }
        assertEquals(user.distanceTo(place.point), vm.uiState.value.results.single().distanceMeters!!, 0.001)
    }

    @Test
    fun centerOnUserOnceEmitsFlyToUserOnlyOnce() = runTest {
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        val user = GeoPoint(20.2961, 85.8245)
        location.locationResult = AppResult.Success(sampleUserLocation(user))
        val vm = viewModel()
        vm.events.test {
            vm.centerOnUserOnce()
            vm.centerOnUserOnce()
            assertEquals(ExploreEvent.FlyToUser(user, 13.0), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun centerOnUserOnceDoesNothingWithoutPermission() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.centerOnUserOnce()
            expectNoEvents()
        }
    }

    @Test
    fun tappingSelectedCategoryAgainClearsResults() = runTest {
        val vm = viewModel().apply { onCameraIdle(GeoPoint(28.6139, 77.2090), 12.0) }
        vm.onCategorySelected(PlaceCategory.HOSPITAL)
        vm.onCategorySelected(PlaceCategory.HOSPITAL)
        val state = vm.uiState.value
        assertNull(state.selectedCategory)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun failureFallsBackToCachedResultsWithTimestamp() = runTest {
        cache.put("explore_PHARMACY", listOf(samplePlace(id = "cached", category = PlaceCategory.PHARMACY)))
        search.categoryResult = AppResult.Failure(AppError.NoInternet)
        val vm = viewModel().apply { onCameraIdle(GeoPoint(28.6139, 77.2090), 12.0) }

        vm.onCategorySelected(PlaceCategory.PHARMACY)

        val state = vm.uiState.value
        assertEquals(listOf("cached"), state.results.map { it.id })
        assertEquals(10_000L, state.cachedAtMillis)
        assertNull(state.error)
    }

    @Test
    fun failureWithoutCacheSurfacesErrorAndRetryRecovers() = runTest {
        search.categoryResult = AppResult.Failure(AppError.Timeout)
        val vm = viewModel().apply { onCameraIdle(GeoPoint(28.6139, 77.2090), 12.0) }
        vm.onCategorySelected(PlaceCategory.HOTEL)
        assertEquals(AppError.Timeout, vm.uiState.value.error)
        assertTrue(vm.uiState.value.results.isEmpty())

        search.categoryResult = AppResult.Success(listOf(samplePlace(id = "h1", category = PlaceCategory.HOTEL)))
        vm.retry()
        assertNull(vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.results.size)
    }

    @Test
    fun myLocationWithoutPermissionRequestsRationale() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onMyLocationClicked()
            assertEquals(ExploreEvent.ShowPermissionRationale, awaitItem())
        }
    }

    @Test
    fun myLocationWithGpsOffShowsLocationDisabled() = runTest {
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        location.locationEnabled = false
        val vm = viewModel()
        vm.events.test {
            vm.onMyLocationClicked()
            assertEquals(ExploreEvent.ShowLocationDisabled, awaitItem())
        }
    }

    @Test
    fun myLocationSuccessFliesToUser() = runTest {
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        val target = GeoPoint(19.07, 72.87)
        location.locationResult = AppResult.Success(sampleUserLocation(target))
        val vm = viewModel()
        vm.events.test {
            vm.onMyLocationClicked()
            assertEquals(ExploreEvent.FlyToUser(target), awaitItem())
        }
        assertEquals(target, vm.uiState.value.userLocation?.point)
    }

    @Test
    fun myLocationFailureEmitsMessage() = runTest {
        location.mutablePermission.value = LocationPermissionState.GRANTED_APPROXIMATE
        location.locationResult = AppResult.Failure(AppError.LocationUnavailable)
        val vm = viewModel()
        vm.events.test {
            vm.onMyLocationClicked()
            assertEquals(ExploreEvent.ShowMessage(R.string.error_location_unavailable), awaitItem())
        }
    }

    @Test
    fun deniedPermissionResultEmitsRationale() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onPermissionResult(granted = false)
            assertEquals(ExploreEvent.ShowPermissionRationale, awaitItem())
        }
    }

    @Test
    fun saveParkingPersistsParkingSavedPlace() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.saveParking(GeoPoint(28.6, 77.2), "My parking")
            assertEquals(ExploreEvent.ShowMessage(R.string.explore_parking_saved), awaitItem())
        }
        val savedPlace = saved.items.value.single()
        assertEquals(SavedPlaceType.PARKING, savedPlace.type)
        assertEquals(GeoPoint(28.6, 77.2), savedPlace.place.point)
        assertEquals(1, vm.uiState.value.parkingSpots.size)
    }

    @Test
    fun toggleMapStylePersistsAndUpdatesState() = runTest {
        val vm = viewModel()
        assertEquals(MapStyleMode.STANDARD, vm.uiState.value.mapStyle)
        vm.toggleMapStyle()
        assertEquals(MapStyleMode.SATELLITE, vm.uiState.value.mapStyle)
        assertEquals(MapStyleMode.SATELLITE, preferences.state.value.mapStyle)
        vm.toggleMapStyle()
        assertEquals(MapStyleMode.STANDARD, vm.uiState.value.mapStyle)
    }

    @Test
    fun connectivityChangesAreReflected() = runTest {
        val vm = viewModel()
        connectivity.mutableState.value = ConnectivityState.Offline
        assertEquals(ConnectivityState.Offline, vm.uiState.value.connectivity)
    }

    @Test
    fun parkingTargetPrefersUserLocation() = runTest {
        location.mutablePermission.value = LocationPermissionState.GRANTED_PRECISE
        val user = GeoPoint(11.0, 76.0)
        location.locationResult = AppResult.Success(sampleUserLocation(user))
        val vm = viewModel()
        assertEquals(user, vm.parkingTarget(GeoPoint(1.0, 1.0)))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val preferences = FakePreferencesRepository()
    private val location = FakeLocationRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun fullFlowWithNotificationStepMarksOnboardingDone() = runTest {
        val vm = OnboardingViewModel(preferences, location, requiresNotificationStep = true)
        assertEquals(OnboardingStep.INTRO, vm.uiState.value.step)

        vm.onIntroFinished()
        assertEquals(OnboardingStep.LOCATION, vm.uiState.value.step)

        vm.onLocationOutcome(LocationOutcome.PRECISE)
        assertEquals(LocationOutcome.PRECISE, vm.uiState.value.locationOutcome)

        vm.onLocationStepContinue()
        assertEquals(OnboardingStep.NOTIFICATIONS, vm.uiState.value.step)
        assertFalse(preferences.state.value.onboardingDone)

        vm.finished.test {
            vm.onNotificationsHandled()
            awaitItem()
        }
        assertTrue(preferences.state.value.onboardingDone)
    }

    @Test
    fun skippingNotificationStepOnOlderDevicesCompletesFromLocation() = runTest {
        val vm = OnboardingViewModel(preferences, location, requiresNotificationStep = false)
        vm.onIntroFinished()
        vm.finished.test {
            vm.onLocationStepContinue()
            awaitItem()
        }
        assertTrue(preferences.state.value.onboardingDone)
    }

    @Test
    fun denyingLocationStillAllowsContinuing() = runTest {
        val vm = OnboardingViewModel(preferences, location, requiresNotificationStep = false)
        vm.onIntroFinished()
        vm.onLocationOutcome(LocationOutcome.PERMANENTLY_DENIED)
        assertEquals(LocationOutcome.PERMANENTLY_DENIED, vm.uiState.value.locationOutcome)
        vm.onLocationStepContinue()
        assertTrue(preferences.state.value.onboardingDone)
    }

    @Test
    fun completingTwiceEmitsOnlyOnce() = runTest {
        val vm = OnboardingViewModel(preferences, location, requiresNotificationStep = false)
        vm.finished.test {
            vm.onLocationStepContinue()
            vm.onLocationStepContinue()
            awaitItem()
            expectNoEvents()
        }
    }

    @Test
    fun locationResultClassification() {
        assertEquals(LocationOutcome.PRECISE, classifyLocationResult(true, true, false))
        assertEquals(LocationOutcome.PRECISE, classifyLocationResult(true, false, false))
        assertEquals(LocationOutcome.APPROXIMATE, classifyLocationResult(false, true, false))
        assertEquals(LocationOutcome.DENIED, classifyLocationResult(false, false, true))
        assertEquals(LocationOutcome.PERMANENTLY_DENIED, classifyLocationResult(false, false, false))
    }
}
