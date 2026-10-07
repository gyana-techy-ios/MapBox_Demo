package com.raahi.app

import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.data.remote.DirectionsApi
import com.raahi.app.data.remote.HttpClientFactory
import com.raahi.app.data.remote.MapboxDirectionsRepository
import com.raahi.app.data.remote.MapboxPlaceSearchRepository
import com.raahi.app.data.remote.RetrofitFactory
import com.raahi.app.data.remote.SearchBoxApi
import com.raahi.app.domain.model.CongestionLevel
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.SearchSuggestion
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun fixture(name: String): String =
    checkNotNull(Thread.currentThread().contextClassLoader?.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

private fun client() = HttpClientFactory.create(tokenProvider = { "pk.test" }, enableLogging = false, readTimeoutSeconds = 1)

private val centroidCategoryBody = """
{"type":"FeatureCollection","features":[
{"type":"Feature","geometry":{"type":"Point","coordinates":[78.6569,22.9734]},"properties":{"mapbox_id":"fake","name":"Satna Station"}},
{"type":"Feature","geometry":{"type":"Point","coordinates":[85.82,20.29]},"properties":{"mapbox_id":"real","name":"KIMS"}}]}
"""

class SearchBoxRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: MapboxPlaceSearchRepository
    private var configured = true

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = RetrofitFactory.create(server.url("/search/searchbox/v1/").toString(), client(), SearchBoxApi::class.java)
        repository = MapboxPlaceSearchRepository(api, isConfigured = { configured }, language = { "en" })
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun suggestParsesAndSendsSessionAndProximity() = runTest {
        server.enqueue(MockResponse().setBody(fixture("suggest.json")))
        val result = repository.suggest("indian oil", GeoPoint(28.6139, 77.2090), "session-1")
        val suggestions = (result as AppResult.Success).value
        assertEquals(2, suggestions.size)
        assertEquals("Indian Oil Petrol Pump", suggestions[0].name)
        assertEquals(PlaceCategory.FUEL, suggestions[0].category)
        assertEquals(820.5, suggestions[0].distanceMeters!!, 0.01)
        assertEquals("New Delhi, Delhi, India", suggestions[1].address)
        assertNull(suggestions[1].category)

        val request = server.takeRequest()
        assertTrue(request.requestUrl!!.encodedPath.endsWith("/suggest"))
        assertEquals("indian oil", request.requestUrl!!.queryParameter("q"))
        assertEquals("session-1", request.requestUrl!!.queryParameter("session_token"))
        assertEquals("77.209,28.6139", request.requestUrl!!.queryParameter("proximity"))
        assertEquals("pk.test", request.requestUrl!!.queryParameter("access_token"))
        assertNull("country must not hard-restrict results", request.requestUrl!!.queryParameter("country"))
    }

    @Test
    fun suggestWithoutProximityBiasesToIndia() = runTest {
        server.enqueue(MockResponse().setBody(fixture("suggest.json")))
        repository.suggest("delhi", null, "s")
        val proximity = server.takeRequest().requestUrl!!.queryParameter("proximity")
        assertNotNull(proximity)
        assertTrue(proximity!!.startsWith("78.6569"))
    }

    @Test
    fun blankQueryDoesNotHitNetwork() = runTest {
        val result = repository.suggest("  ", null, "s")
        assertEquals(emptyList<SearchSuggestion>(), (result as AppResult.Success).value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun retrieveReturnsPlace() = runTest {
        server.enqueue(MockResponse().setBody(fixture("retrieve.json")))
        val suggestion = SearchSuggestion("dXJuOm1ieHBvaToxMjM", "Indian Oil", null, null, null)
        val place = (repository.retrieve(suggestion, "session-1") as AppResult.Success).value
        assertEquals(28.6150, place.point.lat, 1e-6)
        assertEquals(77.2100, place.point.lng, 1e-6)
        assertEquals(PlaceCategory.FUEL, place.category)
        val request = server.takeRequest()
        assertTrue(request.requestUrl!!.encodedPath.endsWith("/retrieve/dXJuOm1ieHBvaToxMjM"))
        assertEquals("session-1", request.requestUrl!!.queryParameter("session_token"))
    }

    @Test
    fun categorySearchUsesCanonicalIdAndComputesDistance() = runTest {
        server.enqueue(MockResponse().setBody(fixture("category.json")))
        val places = (repository.categorySearch(PlaceCategory.FUEL, GeoPoint(28.6139, 77.2090), 10) as AppResult.Success).value
        assertEquals(2, places.size)
        assertNotNull(places[0].distanceMeters)
        val request = server.takeRequest()
        assertTrue(request.requestUrl!!.encodedPath.endsWith("/category/gas_station"))
        assertEquals("10", request.requestUrl!!.queryParameter("limit"))
    }

    @Test
    fun categorySearchDropsPlaceholderCentroidFeatures() = runTest {
        server.enqueue(MockResponse().setBody(centroidCategoryBody))
        val places = (repository.categorySearch(PlaceCategory.HOSPITAL, GeoPoint(20.2961, 85.8245), 10) as AppResult.Success).value
        assertEquals(listOf("real"), places.map { it.id })
    }

    @Test
    fun reverseGeocodeReturnsAddress() = runTest {
        server.enqueue(MockResponse().setBody(fixture("reverse.json")))
        val place = (repository.reverseGeocode(GeoPoint(28.6139, 77.2090)) as AppResult.Success).value
        assertEquals("Connaught Place", place.name)
        val url = server.takeRequest().requestUrl!!
        assertEquals("77.209", url.queryParameter("longitude"))
        assertEquals("28.6139", url.queryParameter("latitude"))
    }

    @Test
    fun unauthorizedMapsToUnauthorized() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody(fixture("error_unauthorized.json")))
        val result = repository.suggest("x", null, "s")
        assertEquals(AppError.Unauthorized, (result as AppResult.Failure).error)
    }

    @Test
    fun rateLimitedMapsToRateLimited() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        val result = repository.categorySearch(PlaceCategory.HOSPITAL, GeoPoint(1.0, 1.0))
        assertEquals(AppError.RateLimited, (result as AppResult.Failure).error)
    }

    @Test
    fun serverErrorMapsToServer() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = repository.reverseGeocode(GeoPoint(1.0, 1.0))
        assertEquals(AppError.Server(503), (result as AppResult.Failure).error)
    }

    @Test
    fun emptyRetrieveIsNotFound() = runTest {
        server.enqueue(MockResponse().setBody("""{"type":"FeatureCollection","features":[]}"""))
        val result = repository.retrieve(SearchSuggestion("id", "n", null, null, null), "s")
        assertEquals(AppError.NotFound, (result as AppResult.Failure).error)
    }

    @Test
    fun timeoutMapsToTimeout() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = repository.suggest("slow", null, "s")
        assertEquals(AppError.Timeout, (result as AppResult.Failure).error)
    }

    @Test
    fun notConfiguredFailsWithoutNetwork() = runTest {
        configured = false
        val result = repository.suggest("x", null, "s")
        assertEquals(AppError.Configuration, (result as AppResult.Failure).error)
        assertEquals(0, server.requestCount)
    }
}

class DirectionsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: MapboxDirectionsRepository
    private var configured = true

    private val origin = GeoPoint(28.6139, 77.2090)
    private val destination = GeoPoint(28.64, 77.235)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = RetrofitFactory.create(server.url("/directions/v5/mapbox/").toString(), client(), DirectionsApi::class.java)
        repository = MapboxDirectionsRepository(api, isConfigured = { configured })
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun parsesRoutesStepsGeometryAndCongestion() = runTest {
        server.enqueue(MockResponse().setBody(fixture("directions_ok.json")))
        val routes = (repository.getRoutes(listOf(origin, destination), RouteProfile.DRIVING_TRAFFIC) as AppResult.Success).value
        assertEquals(2, routes.size)
        val primary = routes[0]
        assertEquals("route-0", primary.id)
        assertEquals(4, primary.geometry.size)
        assertEquals(3100.0, primary.distanceMeters, 0.0)
        assertEquals(600.0, primary.durationSeconds, 0.0)
        assertEquals("Ring Road, Mathura Road", primary.summary)
        assertEquals(3, primary.steps.size)
        assertEquals("turn", primary.steps[1].maneuverType)
        assertEquals("right", primary.steps[1].modifier)
        assertEquals("Mathura Road", primary.steps[1].roadName)
        assertEquals(28.6200, primary.steps[1].location.lat, 1e-6)
        assertNull(primary.steps[2].roadName)
        assertEquals(CongestionLevel.MODERATE, primary.congestionLevel)
        assertEquals(CongestionLevel.UNKNOWN, routes[1].congestionLevel)
    }

    @Test
    fun requestShapeForTwoWaypoints() = runTest {
        server.enqueue(MockResponse().setBody(fixture("directions_ok.json")))
        repository.getRoutes(listOf(origin, destination), RouteProfile.DRIVING_TRAFFIC, RouteOptions(avoidTolls = true, avoidFerries = true))
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/directions/v5/mapbox/driving-traffic/77.209,28.6139;77.235,28.64?"))
        val url = request.requestUrl!!
        assertEquals("true", url.queryParameter("alternatives"))
        assertEquals("full", url.queryParameter("overview"))
        assertEquals("polyline6", url.queryParameter("geometries"))
        assertEquals("true", url.queryParameter("steps"))
        assertEquals("congestion,duration,distance", url.queryParameter("annotations"))
        assertEquals("toll,ferry", url.queryParameter("exclude"))
        assertEquals("pk.test", url.queryParameter("access_token"))
    }

    @Test
    fun noAlternativesWithMoreThanTwoWaypoints() = runTest {
        server.enqueue(MockResponse().setBody(fixture("directions_ok.json")))
        repository.getRoutes(listOf(origin, GeoPoint(28.62, 77.22), destination), RouteProfile.DRIVING)
        val url = server.takeRequest().requestUrl!!
        assertEquals("false", url.queryParameter("alternatives"))
        assertNull(url.queryParameter("exclude"))
    }

    @Test
    fun walkingProfileSkipsDrivingOnlyParams() = runTest {
        server.enqueue(MockResponse().setBody(fixture("directions_ok.json")))
        repository.getRoutes(listOf(origin, destination), RouteProfile.WALKING, RouteOptions(avoidTolls = true))
        val url = server.takeRequest().requestUrl!!
        assertEquals("duration,distance", url.queryParameter("annotations"))
        assertNull(url.queryParameter("exclude"))
    }

    @Test
    fun noRouteBodyMapsToNoRoute() = runTest {
        server.enqueue(MockResponse().setBody(fixture("directions_noroute.json")))
        val result = repository.getRoutes(listOf(origin, destination), RouteProfile.DRIVING)
        assertEquals(AppError.NoRoute, (result as AppResult.Failure).error)
    }

    @Test
    fun noRouteWithHttp404MapsToNoRoute() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody(fixture("directions_noroute.json")))
        val result = repository.getRoutes(listOf(origin, destination), RouteProfile.DRIVING)
        assertEquals(AppError.NoRoute, (result as AppResult.Failure).error)
    }

    @Test
    fun unauthorizedMapsToUnauthorized() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody(fixture("error_unauthorized.json")))
        val result = repository.getRoutes(listOf(origin, destination), RouteProfile.DRIVING)
        assertEquals(AppError.Unauthorized, (result as AppResult.Failure).error)
    }

    @Test
    fun rateLimitedMapsToRateLimited() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val result = repository.getRoutes(listOf(origin, destination), RouteProfile.DRIVING)
        assertEquals(AppError.RateLimited, (result as AppResult.Failure).error)
    }

    @Test
    fun timeoutMapsToTimeout() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = repository.getRoutes(listOf(origin, destination), RouteProfile.DRIVING)
        assertEquals(AppError.Timeout, (result as AppResult.Failure).error)
    }

    @Test
    fun waypointLimitsAreEnforcedBeforeNetwork() = runTest {
        val tooMany = List(26) { GeoPoint(28.0 + it * 0.01, 77.0) }
        assertTrue(repository.getRoutes(tooMany, RouteProfile.DRIVING) is AppResult.Failure)
        assertTrue(repository.getRoutes(listOf(origin), RouteProfile.DRIVING) is AppResult.Failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun notConfiguredFailsWithoutNetwork() = runTest {
        configured = false
        val result = repository.getRoutes(listOf(origin, destination), RouteProfile.DRIVING)
        assertEquals(AppError.Configuration, (result as AppResult.Failure).error)
        assertFalse(server.requestCount > 0)
    }
}
