package com.raahi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.CongestionLevel
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.OfflineRegion
import com.raahi.app.domain.model.OfflineRegionStatus
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.RouteLeg
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.RouteStep
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.domain.model.ThemeMode
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.model.TripStop
import com.raahi.app.domain.model.UserPreferences
import com.raahi.app.fakes.FakeAppContainer
import com.raahi.app.fakes.FakeLocationRepository
import com.raahi.app.fakes.FakePreferencesRepository
import com.raahi.app.fakes.samplePlace
import com.raahi.app.navigation.RaahiApp
import com.raahi.app.navigation.RoutePreview
import com.raahi.app.navigation.TripEditor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val UI_TIMEOUT_MILLIS = 10_000L

private fun onboarded() = FakeAppContainer(
    preferencesRepository = FakePreferencesRepository(UserPreferences(onboardingDone = true)),
    locationRepository = FakeLocationRepository(LocationPermissionState.GRANTED_PRECISE),
)

private fun place(id: String, name: String, lat: Double) =
    Place(id, name, "$name, Odisha", GeoPoint(lat, 85.8), null, null, com.raahi.app.domain.model.PlaceSource.SEARCH)

private fun route(id: String, duration: Double, congestion: CongestionLevel = CongestionLevel.UNKNOWN): Route {
    val start = GeoPoint(20.29, 85.82)
    val end = GeoPoint(19.81, 85.83)
    val steps = listOf(
        RouteStep("Head south", "depart", "straight", 50_000.0, 3_000.0, start, "NH16"),
        RouteStep("You have arrived", "arrive", null, 0.0, 0.0, end, null),
    )
    return Route(id, listOf(start, end), 50_000.0, duration, listOf(RouteLeg(steps)), "NH16", congestion)
}

private fun region(id: String, status: OfflineRegionStatus, progress: Float, outdated: Boolean = false) = OfflineRegion(
    id, "Region $id", GeoBounds(20.0, 85.0, 20.1, 85.1), 10, 14, status, progress, 12_000_000L, 1_700_000_000_000L, outdated,
)

class TripsOfflineUiTests {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var navController: NavHostController

    private fun launch(container: FakeAppContainer) {
        rule.setContent {
            navController = rememberNavController()
            RaahiApp(container, navController)
        }
        rule.onNodeWithTag("screen_explore").assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        try {
            rule.waitUntil(UI_TIMEOUT_MILLIS) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Missing tag '$tag'. Tree:\n" + rule.onRoot().printToString(), e)
        }
    }

    private fun waitForTagGone(tag: String) {
        rule.waitUntil(UI_TIMEOUT_MILLIS) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }
    }

    private fun waitForText(text: String) {
        rule.waitUntil(UI_TIMEOUT_MILLIS) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun savedTrip(container: FakeAppContainer, name: String = "Coast run", vararg places: Place): Long = runBlocking {
        val stops = places.mapIndexed { index, place -> TripStop(0, place, false, index) }
        container.tripRepository.upsert(Trip(Trip.NEW_ID, name, stops, RouteProfile.DRIVING_TRAFFIC, 0, 0, false))
    }

    private fun addStopViaSearch(container: FakeAppContainer, place: Place) {
        container.placeSearchRepository.suggestResult =
            AppResult.Success(listOf(SearchSuggestion("s-${place.id}", place.name, place.address, null, null)))
        container.placeSearchRepository.retrieveResult = AppResult.Success(place)
        rule.onNodeWithTag("trip_add_stop").performClick()
        waitForTag("stop_picker_field")
        rule.onNodeWithTag("stop_picker_field").performTextInput(place.name)
        waitForTag("stop_suggestion")
        rule.onNodeWithTag("stop_suggestion").performClick()
        waitForTagGone("stop_picker")
    }

    @Test
    fun createTripWithTwoStopsSavesListsAndOpensDetail() {
        val container = onboarded()
        launch(container)
        rule.onNodeWithTag("tab_trips").performClick()
        rule.onNodeWithTag("trips_empty").assertIsDisplayed()
        waitForTag("trips_new")
        rule.onNodeWithTag("trips_new").performClick()
        rule.onNodeWithTag("screen_tripeditor").assertIsDisplayed()

        addStopViaSearch(container, place("puri", "Puri", 19.81))
        addStopViaSearch(container, place("konark", "Konark", 19.89))
        rule.onNodeWithTag("stop_item_0").assertTextContains("Puri", substring = true)
        rule.onNodeWithTag("stop_item_1").assertTextContains("Konark", substring = true)

        rule.onNodeWithTag("trip_name").performTextInput(" east")
        rule.onNodeWithTag("trip_save").performClick()
        waitForTag("screen_tripdetail")
        rule.onNodeWithTag("tripdetail_stop_2").assertIsDisplayed()
        assertEquals(1, container.tripRepository.trips.value.size)
        assertEquals(3, container.tripRepository.trips.value.single().stops.size)

        rule.onNodeWithTag("top_bar_back").performClick()
        waitForTag("trips_list")
        val tripName = container.tripRepository.trips.value.single().name
        rule.onNodeWithText(tripName, substring = true).assertIsDisplayed()
        rule.onNodeWithTag("trip_card_${container.tripRepository.trips.value.single().id}").performClick()
        waitForTag("screen_tripdetail")
        rule.onNodeWithTag("tripdetail_preview").assertIsDisplayed()
    }

    @Test
    fun reorderingWithMoveButtonsChangesStopOrder() {
        val container = onboarded()
        val id = savedTrip(container, "Three stops", place("a", "Alpha", 20.0), place("b", "Bravo", 20.1), place("c", "Charlie", 20.2))
        launch(container)
        rule.runOnUiThread { navController.navigate(TripEditor(id)) }
        waitForTag("stop_item_1")
        rule.onNodeWithTag("stop_item_0").assertTextContains("Bravo", substring = true)

        rule.onNodeWithTag("stop_down_0").performClick()
        rule.waitUntil(UI_TIMEOUT_MILLIS) {
            runCatching { rule.onNodeWithTag("stop_item_0").assertTextContains("Charlie", substring = true) }.isSuccess
        }
        rule.onNodeWithTag("stop_item_1").assertTextContains("Bravo", substring = true)
        rule.onNodeWithTag("stop_up_1").performClick()
        rule.waitUntil(UI_TIMEOUT_MILLIS) {
            runCatching { rule.onNodeWithTag("stop_item_0").assertTextContains("Bravo", substring = true) }.isSuccess
        }
    }

    @Test
    fun routePreviewShowsOptionsStartOpensGuidanceAndExitReturns() {
        val container = onboarded()
        container.directionsRepository.result = AppResult.Success(
            listOf(route("slow", 4_200.0), route("fast", 3_000.0, CongestionLevel.MODERATE)),
        )
        val id = savedTrip(container, "Puri run", place("s", "Bhubaneswar", 20.29), place("t", "Puri", 19.81))
        launch(container)
        rule.runOnUiThread { navController.navigate(RoutePreview(id)) }

        waitForTag("route_option_1")
        rule.onNodeWithTag("route_option_0").assertIsDisplayed()
        rule.onNodeWithText("Fastest").assertIsDisplayed()
        rule.onNodeWithText("Moderate traffic").assertIsDisplayed()
        rule.onNodeWithTag("route_option_1").performClick()
        rule.onNodeWithTag("routepreview_start").performClick()

        waitForTag("screen_guidance")
        waitForTag("guidance_exit")
        rule.onNodeWithTag("guidance_exit").performClick()
        rule.onNode(hasText("Exit") and hasAnyAncestor(isDialog())).performClick()
        waitForTag("screen_routepreview")
    }

    @Test
    fun offlineTabShowsEmptyStateThenRegionWithProgress() {
        val container = onboarded()
        launch(container)
        rule.onNodeWithTag("tab_offline").performClick()
        waitForTag("offline_empty")
        rule.onNodeWithTag("offline_intro").assertIsDisplayed()

        container.offlineMapRepository.regionsState.value = listOf(
            region("a", OfflineRegionStatus.Downloading, 0.45f),
            region("b", OfflineRegionStatus.Completed, 1f, outdated = true),
        )
        waitForTag("offline_progress_a")
        rule.onNodeWithTag("offline_status_a").assertTextContains("Downloading")
        rule.onNodeWithTag("offline_cancel_a").assertIsDisplayed()
        rule.onNodeWithTag("offline_list").performScrollToNode(hasTestTag("offline_outdated_b"))
        rule.onNodeWithTag("offline_outdated_b").assertIsDisplayed()
        rule.onNodeWithTag("offline_update_b").assertIsDisplayed()
        rule.onNodeWithTag("offline_storage").assertIsDisplayed()
    }

    @Test
    fun settingsThemeToggleUpdatesPreference() {
        val container = onboarded()
        launch(container)
        rule.onNodeWithTag("tab_profile").performClick()
        rule.onNodeWithText("Settings").performClick()
        waitForTag("settings_theme_DARK")
        rule.onNodeWithTag("settings_theme_DARK").performClick()
        rule.waitUntil(UI_TIMEOUT_MILLIS) { container.preferencesRepository.state.value.themeMode == ThemeMode.DARK }
        rule.onNodeWithTag("settings_theme_LIGHT").performClick()
        rule.waitUntil(UI_TIMEOUT_MILLIS) { container.preferencesRepository.state.value.themeMode == ThemeMode.LIGHT }
        rule.onNodeWithTag("settings_voice").performScrollTo().performClick()
        rule.waitUntil(UI_TIMEOUT_MILLIS) { !container.preferencesRepository.state.value.voiceGuidance }
    }

    @Test
    fun profileRowsNavigateAndShowStats() {
        val container = onboarded()
        launch(container)
        rule.onNodeWithTag("tab_profile").performClick()
        rule.onNodeWithTag("profile_stat_trips").assertIsDisplayed()
        rule.onNodeWithText("Offline maps").performClick()
        waitForTag("screen_offline")
        rule.onNodeWithTag("tab_profile").performClick()
        rule.onNodeWithText("About Raahi").performClick()
        waitForTag("screen_about")
        rule.onNodeWithTag("about_attribution").assertIsDisplayed()
        rule.onNodeWithTag("about_version").assertIsDisplayed()
    }

    @Test
    fun missingTripShowsBackToTripsAction() {
        val container = onboarded()
        launch(container)
        rule.runOnUiThread { navController.navigate(com.raahi.app.navigation.TripDetail(99)) }
        waitForTag("tripdetail_missing")
        rule.onNodeWithText("Back to trips").performClick()
        waitForTag("screen_trips")
    }
}
