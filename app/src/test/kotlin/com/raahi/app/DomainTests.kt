package com.raahi.app

import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.CongestionLevel
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.RouteLeg
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.RouteStep
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.model.TripStop
import com.raahi.app.domain.usecase.FindNearestEmergencyUseCase
import com.raahi.app.domain.usecase.GuidanceEngine
import com.raahi.app.domain.usecase.TileEstimator
import com.raahi.app.domain.usecase.TripIssue
import com.raahi.app.domain.usecase.TripValidator
import com.raahi.app.fakes.FakeClock
import com.raahi.app.fakes.FakeNearbyCacheRepository
import com.raahi.app.fakes.FakePlaceSearchRepository
import com.raahi.app.fakes.samplePlace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceEngineTest {

    private val geometry = (0..10).map { GeoPoint(28.0 + it * 0.001, 77.0) }
    private val length = geometry.first().distanceTo(geometry.last())

    private val route = Route(
        id = "r",
        geometry = geometry,
        distanceMeters = length,
        durationSeconds = 200.0,
        legs = listOf(
            RouteLeg(
                listOf(
                    RouteStep("Head north", "depart", "straight", length / 2, 100.0, geometry.first(), "Main Road"),
                    RouteStep("Turn right", "turn", "right", length / 2, 100.0, geometry[5], "Side Road"),
                    RouteStep("Arrive", "arrive", null, 0.0, 0.0, geometry.last(), null),
                ),
            ),
        ),
        summary = "Main Road",
        congestionLevel = CongestionLevel.LOW,
    )

    @Test
    fun atStartOfRoute() {
        val progress = GuidanceEngine(route).update(geometry.first())
        assertEquals(0, progress.currentStepIndex)
        assertEquals(length / 2, progress.distanceToNextManeuverMeters, 2.0)
        assertEquals(length, progress.remainingDistanceMeters, 2.0)
        assertEquals(200.0, progress.remainingDurationSeconds, 1.0)
        assertFalse(progress.isOffRoute)
        assertFalse(progress.hasArrived)
        assertEquals("Turn right", progress.nextStep?.instruction)
    }

    @Test
    fun quarterOfTheWay() {
        val progress = GuidanceEngine(route).update(GeoPoint(28.0025, 77.0))
        assertEquals(0, progress.currentStepIndex)
        assertEquals(length / 4, progress.distanceToNextManeuverMeters, 3.0)
        assertEquals(length * 0.75, progress.remainingDistanceMeters, 3.0)
        assertEquals(150.0, progress.remainingDurationSeconds, 3.0)
        assertTrue(progress.distanceFromRouteMeters < 1.0)
    }

    @Test
    fun advancesToNextStepPastManeuver() {
        val progress = GuidanceEngine(route).update(GeoPoint(28.0075, 77.0))
        assertEquals(1, progress.currentStepIndex)
        assertEquals("Turn right", progress.currentStep?.instruction)
        assertEquals(length / 4, progress.distanceToNextManeuverMeters, 3.0)
        assertEquals(length / 4, progress.remainingDistanceMeters, 3.0)
        assertEquals(50.0, progress.remainingDurationSeconds, 3.0)
    }

    @Test
    fun offRouteRequiresConsecutiveFixes() {
        val engine = GuidanceEngine(route)
        val farEast = GeoPoint(28.005, 77.0015)
        assertFalse(engine.update(farEast).isOffRoute)
        assertFalse(engine.update(farEast).isOffRoute)
        val third = engine.update(farEast)
        assertTrue(third.distanceFromRouteMeters > 50.0)
        assertTrue(third.isOffRoute)
    }

    @Test
    fun onRouteFixResetsOffRouteCounter() {
        val engine = GuidanceEngine(route)
        val farEast = GeoPoint(28.005, 77.0015)
        engine.update(farEast)
        engine.update(farEast)
        assertFalse(engine.update(GeoPoint(28.005, 77.0)).isOffRoute)
        assertFalse(engine.update(farEast).isOffRoute)
        assertFalse(engine.update(farEast).isOffRoute)
        assertTrue(engine.update(farEast).isOffRoute)
    }

    @Test
    fun smallDeviationIsNotOffRoute() {
        val engine = GuidanceEngine(route)
        repeat(5) {
            val progress = engine.update(GeoPoint(28.005, 77.0002))
            assertFalse(progress.isOffRoute)
        }
    }

    @Test
    fun arrivesAtDestination() {
        val progress = GuidanceEngine(route).update(geometry.last())
        assertTrue(progress.hasArrived)
        assertEquals(0.0, progress.remainingDistanceMeters, 0.0)
        assertEquals(0.0, progress.remainingDurationSeconds, 0.0)
    }

    @Test
    fun arrivesWithinThreshold() {
        val progress = GuidanceEngine(route).update(GeoPoint(28.0098, 77.0))
        assertTrue(progress.hasArrived)
    }

    @Test
    fun doesNotArriveEarly() {
        assertFalse(GuidanceEngine(route).update(GeoPoint(28.0085, 77.0)).hasArrived)
    }

    @Test
    fun emptyGeometryDoesNotCrash() {
        val empty = route.copy(geometry = emptyList(), legs = emptyList())
        val progress = GuidanceEngine(empty).update(GeoPoint(28.0, 77.0))
        assertNull(progress.snappedPoint)
        assertFalse(progress.hasArrived)
    }
}

class TileEstimatorTest {

    private val delhi = GeoBounds(south = 28.58, west = 77.17, north = 28.64, east = 77.25)

    @Test
    fun worldAtZoomZeroIsOneTile() {
        assertEquals(1L, TileEstimator.tileCount(GeoBounds(-85.0, -180.0, 85.0, 180.0), 0))
    }

    @Test
    fun worldAtZoomOneIsFourTiles() {
        assertEquals(4L, TileEstimator.tileCount(GeoBounds(-85.0, -180.0, 85.0, 180.0), 1))
    }

    @Test
    fun tileCountGrowsRoughlyFourfoldPerZoom() {
        val region = GeoBounds(south = 28.0, west = 77.0, north = 29.0, east = 78.0)
        val z12 = TileEstimator.tileCount(region, 12)
        val z14 = TileEstimator.tileCount(region, 14)
        val ratio = z14.toDouble() / z12
        assertTrue("ratio=$ratio", ratio in 14.0..18.0)
    }

    @Test
    fun smallAreaHasPlausibleTileCount() {
        val count = TileEstimator.tileCount(delhi, 14)
        assertTrue("count=$count", count in 9..25)
    }

    @Test
    fun estimateSumsZoomLevelsAndAddsOverhead() {
        val estimate = TileEstimator.estimate(delhi, 10, 12)
        val expectedTiles = (10..12).sumOf { TileEstimator.tileCount(delhi, it) }
        assertEquals(expectedTiles, estimate.tileCount)
        assertEquals(
            expectedTiles * TileEstimator.AVERAGE_TILE_BYTES + TileEstimator.STYLE_PACK_OVERHEAD_BYTES,
            estimate.approximateBytes,
        )
    }

    @Test
    fun polarLatitudesAreClamped() {
        assertTrue(TileEstimator.tileCount(GeoBounds(-90.0, -180.0, 90.0, 180.0), 3) >= 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invertedZoomRangeThrows() {
        TileEstimator.estimate(delhi, 14, 10)
    }
}

class TripValidatorTest {

    private val validator = TripValidator()

    private fun stop(id: Long, order: Int, placeId: String? = "p$id", current: Boolean = false) = TripStop(
        id = id,
        place = placeId?.let { samplePlace(id = it, name = "Stop $id") },
        useCurrentLocation = current,
        order = order,
    )

    private fun trip(vararg stops: TripStop) = Trip(1, "Trip", stops.toList(), RouteProfile.DRIVING_TRAFFIC, 0, 0, false)

    @Test
    fun validTripHasNoIssues() {
        assertTrue(validator.validate(trip(stop(1, 0), stop(2, 1))).isValid)
    }

    @Test
    fun currentLocationCountsAsStop() {
        assertTrue(validator.validate(trip(stop(1, 0, placeId = null, current = true), stop(2, 1))).isValid)
    }

    @Test
    fun tooFewStops() {
        assertEquals(listOf(TripIssue.TOO_FEW_STOPS), validator.validate(trip(stop(1, 0))).issues)
        assertEquals(listOf(TripIssue.TOO_FEW_STOPS), validator.validate(trip()).issues)
    }

    @Test
    fun tooManyStops() {
        val stops = (1..26).map { stop(it.toLong(), it) }.toTypedArray()
        assertTrue(TripIssue.TOO_MANY_STOPS in validator.validate(trip(*stops)).issues)
        val allowed = (1..25).map { stop(it.toLong(), it) }.toTypedArray()
        assertTrue(validator.validate(trip(*allowed)).isValid)
    }

    @Test
    fun emptyStopIsInvalid() {
        assertTrue(TripIssue.EMPTY_STOP in validator.validate(trip(stop(1, 0), stop(2, 1, placeId = null))).issues)
    }

    @Test
    fun multipleCurrentLocationsAreInvalid() {
        val result = validator.validate(trip(stop(1, 0, null, true), stop(2, 1, null, true)))
        assertTrue(TripIssue.MULTIPLE_CURRENT_LOCATIONS in result.issues)
    }

    @Test
    fun consecutiveDuplicatesAreInvalidButNonConsecutiveAreFine() {
        val dup = validator.validate(trip(stop(1, 0, "same"), stop(2, 1, "same")))
        assertTrue(TripIssue.CONSECUTIVE_DUPLICATE_STOPS in dup.issues)
        val loop = validator.validate(trip(stop(1, 0, "a"), stop(2, 1, "b"), stop(3, 2, "a")))
        assertTrue(loop.isValid)
    }

    @Test
    fun ordersByOrderFieldNotListPosition() {
        val reversed = trip(stop(2, 1, "same"), stop(1, 0, "x"), stop(3, 2, "y"))
        assertTrue(validator.validate(reversed).isValid)
    }
}

class FindNearestEmergencyUseCaseTest {

    private val near = GeoPoint(28.6139, 77.2090)
    private val search = FakePlaceSearchRepository()
    private val cache = FakeNearbyCacheRepository().also { it.clock = FakeClock(5_000L) }
    private val useCase = FindNearestEmergencyUseCase(search, cache)

    @Test
    fun onlineSuccessIsSortedAndCached() = runTest {
        val far = samplePlace(id = "far", point = GeoPoint(28.7, 77.3), distance = 9000.0)
        val close = samplePlace(id = "close", point = GeoPoint(28.615, 77.21), distance = 200.0)
        search.categoryResult = AppResult.Success(listOf(far, close))
        val result = (useCase(PlaceCategory.HOSPITAL, near) as AppResult.Success).value
        assertEquals(listOf("close", "far"), result.places.map { it.id })
        assertFalse(result.isFromCache)
        assertNotNull(cache.get(FindNearestEmergencyUseCase.cacheKey(PlaceCategory.HOSPITAL)))
    }

    @Test
    fun offlineFallsBackToCacheWithTimestamp() = runTest {
        cache.put(FindNearestEmergencyUseCase.cacheKey(PlaceCategory.POLICE), listOf(samplePlace(id = "cached")))
        search.categoryResult = AppResult.Failure(AppError.NoInternet)
        val result = (useCase(PlaceCategory.POLICE, near) as AppResult.Success).value
        assertTrue(result.isFromCache)
        assertEquals(5_000L, result.cachedAtMillis)
        assertEquals("cached", result.places.single().id)
    }

    @Test
    fun offlineWithoutCacheFails() = runTest {
        search.categoryResult = AppResult.Failure(AppError.NoInternet)
        val result = useCase(PlaceCategory.PHARMACY, near)
        assertEquals(AppError.NoInternet, (result as AppResult.Failure).error)
    }

    @Test
    fun emptyOnlineResultIsNotCached() = runTest {
        search.categoryResult = AppResult.Success(emptyList())
        useCase(PlaceCategory.ATM, near)
        assertNull(cache.get(FindNearestEmergencyUseCase.cacheKey(PlaceCategory.ATM)))
    }
}
