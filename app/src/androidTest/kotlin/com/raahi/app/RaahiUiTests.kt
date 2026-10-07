package com.raahi.app

import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raahi.app.fakes.FakeAppContainer
import com.raahi.app.fakes.FakePreferencesRepository
import com.raahi.app.domain.model.UserPreferences
import com.raahi.app.map.MAP_UNAVAILABLE_TAG
import com.raahi.app.navigation.About
import com.raahi.app.navigation.EmergencyContacts
import com.raahi.app.navigation.Emergency
import com.raahi.app.navigation.Guidance
import com.raahi.app.navigation.Language
import com.raahi.app.navigation.Nearby
import com.raahi.app.navigation.Offline
import com.raahi.app.navigation.OfflineRegionPicker
import com.raahi.app.navigation.PlaceDetail
import com.raahi.app.navigation.Profile
import com.raahi.app.navigation.RaahiApp
import com.raahi.app.navigation.RoutePreview
import com.raahi.app.navigation.Saved
import com.raahi.app.navigation.Search
import com.raahi.app.navigation.Settings
import com.raahi.app.navigation.TripDetail
import com.raahi.app.navigation.TripEditor
import com.raahi.app.navigation.Trips
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun onboardedContainer(token: String? = null) =
    FakeAppContainer(token = token, preferencesRepository = FakePreferencesRepository(UserPreferences(onboardingDone = true)))

class OnboardingFlowTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun onboardingCompletesAndLandsOnExplore() {
        val container = FakeAppContainer()
        rule.setContent { RaahiApp(container) }

        rule.onNodeWithTag("screen_onboarding").assertIsDisplayed()
        rule.onNodeWithText("Explore and find essentials").assertIsDisplayed()

        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithText("Plan multi-stop trips").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithText("Offline packs and emergency help").assertIsDisplayed()
        rule.onNodeWithText("Get started").performClick()

        rule.onNodeWithTag("onboarding_location_step").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_location_skip").performClick()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rule.onNodeWithTag("onboarding_notifications_step").assertIsDisplayed()
            rule.onNodeWithTag("onboarding_notifications_skip").performClick()
        }

        rule.waitUntil(5_000) { container.preferencesRepository.state.value.onboardingDone }
        rule.onNodeWithTag("screen_explore").assertIsDisplayed()
        rule.onNodeWithTag("bottom_bar").assertIsDisplayed()
    }

    @Test
    fun skipJumpsStraightToLocationStep() {
        rule.setContent { RaahiApp(FakeAppContainer()) }
        rule.onNodeWithTag("onboarding_skip").performClick()
        rule.onNodeWithTag("onboarding_location_step").assertIsDisplayed()
    }

    @Test
    fun alreadyOnboardedStartsOnExplore() {
        rule.setContent { RaahiApp(onboardedContainer()) }
        rule.onNodeWithTag("screen_explore").assertIsDisplayed()
    }
}

class ExploreTokenMissingTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun exploreShowsMapUnavailablePanelWithoutCrashing() {
        rule.setContent { RaahiApp(onboardedContainer(token = null)) }
        rule.onNodeWithTag(MAP_UNAVAILABLE_TAG).assertIsDisplayed()
        rule.onNodeWithText("Map unavailable").assertIsDisplayed()
        rule.onNodeWithTag("explore_search_bar").assertIsDisplayed()
        rule.onNodeWithTag("chip_FUEL").assertIsDisplayed()
        rule.onNodeWithTag("explore_emergency").assertIsDisplayed()
        rule.onNodeWithTag("explore_my_location").assertIsDisplayed()
    }

    @Test
    fun secretTokenIsRejectedWithExplanation() {
        rule.setContent { RaahiApp(onboardedContainer(token = "sk.should.never.ship")) }
        rule.onNodeWithTag(MAP_UNAVAILABLE_TAG).assertIsDisplayed()
        rule.onNodeWithText("A secret Mapbox token", substring = true).assertIsDisplayed()
    }

    @Test
    fun searchBarOpensFullScreenSearchWithoutBottomBar() {
        rule.setContent { RaahiApp(onboardedContainer()) }
        rule.onNodeWithTag("explore_search_bar").performClick()
        rule.onNodeWithTag("screen_search").assertIsDisplayed()
        rule.onNodeWithTag("bottom_bar").assertDoesNotExist()
    }

    @Test
    fun emergencyPillOpensEmergency() {
        rule.setContent { RaahiApp(onboardedContainer()) }
        rule.onNodeWithTag("explore_emergency").performClick()
        rule.onNodeWithTag("screen_emergency").assertIsDisplayed()
    }
}

class BottomNavigationTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun allFiveTabsAreReachable() {
        rule.setContent { RaahiApp(onboardedContainer()) }
        rule.onNodeWithTag("screen_explore").assertIsDisplayed()

        rule.onNodeWithTag("tab_trips").performClick()
        rule.onNodeWithTag("screen_trips").assertIsDisplayed()

        rule.onNodeWithTag("tab_offline").performClick()
        rule.onNodeWithTag("screen_offline").assertIsDisplayed()

        rule.onNodeWithTag("tab_saved").performClick()
        rule.onNodeWithTag("screen_saved").assertIsDisplayed()

        rule.onNodeWithTag("tab_profile").performClick()
        rule.onNodeWithTag("screen_profile").assertIsDisplayed()

        rule.onNodeWithTag("tab_explore").performClick()
        rule.onNodeWithTag("screen_explore").assertIsDisplayed()
    }

    @Test
    fun profileLinksReachSettingsLanguageContactsAndAbout() {
        rule.setContent { RaahiApp(onboardedContainer()) }
        rule.onNodeWithTag("tab_profile").performClick()

        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithTag("screen_settings").assertIsDisplayed()
        rule.onNodeWithTag("top_bar_back").performClick()

        rule.onNodeWithText("Language").performClick()
        rule.onNodeWithTag("screen_language").assertIsDisplayed()
        rule.onNodeWithTag("top_bar_back").performClick()

        rule.onNodeWithText("Emergency contacts").performClick()
        rule.onNodeWithTag("screen_emergencycontacts").assertIsDisplayed()
        rule.onNodeWithTag("top_bar_back").performClick()

        rule.onNodeWithText("About Raahi").performClick()
        rule.onNodeWithTag("screen_about").assertIsDisplayed()
    }

    @Test
    fun tripsFlowNewTripEditorBack() {
        rule.setContent { RaahiApp(onboardedContainer()) }
        rule.onNodeWithTag("tab_trips").performClick()
        rule.onNodeWithText("New trip").performClick()
        rule.onNodeWithTag("screen_tripeditor").assertIsDisplayed()
        rule.onNodeWithTag("top_bar_back").performClick()
        rule.onNodeWithTag("screen_trips").assertIsDisplayed()
    }
}

class NavigationGraphTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var navController: NavHostController

    private fun launch() {
        rule.setContent {
            navController = rememberNavController()
            RaahiApp(onboardedContainer(), navController)
        }
        rule.onNodeWithTag("screen_explore").assertIsDisplayed()
    }

    private fun go(route: Any, tag: String) {
        rule.runOnUiThread { navController.navigate(route) }
        rule.waitForIdle()
        rule.onNodeWithTag(tag).assertIsDisplayed()
    }

    @Test
    fun everyRouteInTheGraphIsNavigable() {
        launch()
        go(Search, "screen_search")
        go(PlaceDetail(id = "poi.1", name = "Test Place", lat = 28.6, lng = 77.2, address = "Somewhere", category = "FUEL"), "screen_placedetail")
        go(Nearby("FUEL"), "screen_nearby")
        go(Emergency, "screen_emergency")
        go(Trips, "screen_trips")
        go(TripEditor(), "screen_tripeditor")
        go(TripDetail(7), "screen_tripdetail")
        go(RoutePreview(7), "screen_routepreview")
        go(Guidance(7, 0), "screen_guidance")
        go(Offline, "screen_offline")
        go(OfflineRegionPicker, "screen_offlineregionpicker")
        go(Saved, "screen_saved")
        go(EmergencyContacts, "screen_emergencycontacts")
        go(Profile, "screen_profile")
        go(Settings, "screen_settings")
        go(Language, "screen_language")
        go(About, "screen_about")
    }

    @Test
    fun placeDeepLinkOpensPlaceDetail() {
        launch()
        rule.runOnUiThread { navController.navigate(Uri.parse("raahi://place?lat=19.07&lng=72.87&name=Mumbai")) }
        rule.waitForIdle()
        rule.onNodeWithTag("screen_placedetail").assertIsDisplayed()
    }

    @Test
    fun tripDeepLinkOpensTripDetail() {
        launch()
        rule.runOnUiThread { navController.navigate(Uri.parse("raahi://trip/5")) }
        rule.waitForIdle()
        rule.onNodeWithTag("screen_tripdetail").assertIsDisplayed()
    }

    @Test
    fun fullScreenFlowsHideBottomBar() {
        launch()
        rule.onNodeWithTag("bottom_bar").assertIsDisplayed()
        go(Guidance(1, 0), "screen_guidance")
        rule.onNodeWithTag("bottom_bar").assertDoesNotExist()
        go(OfflineRegionPicker, "screen_offlineregionpicker")
        rule.onNodeWithTag("bottom_bar").assertDoesNotExist()
        go(About, "screen_about")
        rule.onNodeWithTag("bottom_bar").assertIsDisplayed()
    }

    @Test
    fun repeatedTabTapDoesNotStackDestinations() {
        launch()
        repeat(3) { rule.onNodeWithTag("tab_trips").performClick() }
        rule.runOnUiThread {
            val trips = navController.currentBackStack.value.count { it.destination.route?.contains("Trips") == true }
            assertTrue("Trips should appear once, was $trips", trips == 1)
        }
    }
}

