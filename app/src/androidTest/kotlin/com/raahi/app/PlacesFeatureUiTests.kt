package com.raahi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.EmergencyContact
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.SavedPlaceType
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.domain.model.UserPreferences
import com.raahi.app.fakes.FakeAppContainer
import com.raahi.app.fakes.FakePreferencesRepository
import com.raahi.app.fakes.samplePlace
import com.raahi.app.navigation.Emergency
import com.raahi.app.navigation.EmergencyContacts
import com.raahi.app.navigation.PlaceDetail
import com.raahi.app.navigation.RaahiApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val UI_TIMEOUT_MILLIS = 8_000L

private fun onboardedContainer() =
    FakeAppContainer(preferencesRepository = FakePreferencesRepository(UserPreferences(onboardingDone = true)))

class PlacesFeatureUiTests {

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

    private fun waitForText(text: String) {
        try {
            rule.waitUntil(UI_TIMEOUT_MILLIS) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Missing text '$text'. Tree:\n" + rule.onRoot().printToString(), e)
        }
    }

    private fun waitForTag(tag: String) {
        rule.waitUntil(UI_TIMEOUT_MILLIS) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openPlaceDetail() {
        rule.runOnUiThread {
            navController.navigate(
                PlaceDetail(id = "poi.1", name = "Test Fuel Station", lat = 28.61, lng = 77.21, address = "1 Test Road, Delhi", category = "FUEL"),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("screen_placedetail").assertIsDisplayed()
    }

    @Test
    fun searchShowsSuggestionsAndOpensPlaceDetail() {
        val container = onboardedContainer()
        container.placeSearchRepository.suggestResult = AppResult.Success(
            listOf(SearchSuggestion("s1", "Test Fuel Station", "1 Test Road, Delhi", null, 450.0)),
        )
        launch(container)
        rule.onNodeWithTag("explore_search_bar").performClick()
        rule.onNodeWithTag("screen_search").assertIsDisplayed()
        rule.onNodeWithTag("search_field").performTextInput("test fuel")

        waitForTag("search_suggestions")
        rule.onNodeWithText("Test Fuel Station").assertIsDisplayed()
        rule.onNodeWithText("1 Test Road, Delhi").assertIsDisplayed()
        rule.onNodeWithText("Test Fuel Station").performClick()

        waitForTag("screen_placedetail")
        rule.onNodeWithTag("placedetail_name").assertIsDisplayed()
        rule.waitUntil(UI_TIMEOUT_MILLIS) { container.recentSearchRepository.items.value.isNotEmpty() }
        assertEquals("poi.1", container.recentSearchRepository.items.value.first().place?.id)
    }

    @Test
    fun searchWithNoMatchesSuggestsCheckingSpelling() {
        val container = onboardedContainer()
        launch(container)
        rule.onNodeWithTag("explore_search_bar").performClick()
        rule.onNodeWithTag("search_field").performTextInput("zzzz")
        waitForTag("search_no_results")
        rule.onNodeWithText("Check the spelling", substring = true).assertIsDisplayed()
    }

    @Test
    fun emptySearchShowsRecentsAndCanClearThem() {
        val container = onboardedContainer()
        runBlocking { container.recentSearchRepository.record("taj", samplePlace(id = "taj", name = "Taj Mahal")) }
        launch(container)
        rule.onNodeWithTag("explore_search_bar").performClick()

        rule.onNodeWithTag("search_recents_list").assertIsDisplayed()
        rule.onNodeWithText("Taj Mahal").assertIsDisplayed()
        rule.onNodeWithText("Recent searches").assertIsDisplayed()

        rule.onNodeWithTag("search_clear_recents").performClick()
        rule.waitUntil(UI_TIMEOUT_MILLIS) { container.recentSearchRepository.items.value.isEmpty() }
        rule.onNodeWithTag("search_recents_empty").assertIsDisplayed()
    }

    @Test
    fun offlineSearchExplainsAndKeepsRecents() {
        val container = onboardedContainer()
        runBlocking { container.recentSearchRepository.record("taj", samplePlace(id = "taj", name = "Taj Mahal")) }
        container.connectivityMonitor.mutableState.value = ConnectivityState.Offline
        launch(container)
        rule.onNodeWithTag("explore_search_bar").performClick()
        rule.onNodeWithTag("search_field").performTextInput("delhi")
        waitForTag("search_offline_message")
        rule.onNodeWithText("Search needs an internet connection", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Taj Mahal").assertIsDisplayed()
    }

    @Test
    fun placeDetailSaveButtonTogglesFavorite() {
        val container = onboardedContainer()
        launch(container)
        openPlaceDetail()

        rule.onNodeWithText("Test Fuel Station").assertIsDisplayed()
        rule.onNodeWithTag("placedetail_save").assertIsDisplayed()
        rule.onNodeWithTag("placedetail_save").performClick()
        rule.waitUntil(UI_TIMEOUT_MILLIS) {
            rule.onAllNodes(hasTestTag("placedetail_save") and hasText("Saved")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(SavedPlaceType.FAVORITE, container.savedPlaceRepository.items.value.single().type)

        rule.waitUntil(UI_TIMEOUT_MILLIS) { rule.onAllNodes(hasText("Saved to favourites")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("placedetail_save").performClick()
        rule.waitUntil(UI_TIMEOUT_MILLIS) { container.savedPlaceRepository.items.value.isEmpty() }
    }

    @Test
    fun placeDetailDirectionsCreatesQuickTripAndOpensRoutePreview() {
        val container = onboardedContainer()
        launch(container)
        openPlaceDetail()
        rule.onNodeWithTag("placedetail_directions").performClick()
        waitForTag("screen_routepreview")
        val trip = container.tripRepository.trips.value.single()
        assertTrue(trip.isQuick)
        assertEquals(2, trip.stops.size)
    }

    @Test
    fun emergencyShowsDialButtonsAndContactsWhileOffline() {
        val container = onboardedContainer()
        runBlocking { container.emergencyContactRepository.upsert(EmergencyContact(0, "Amma", "9876543210")) }
        container.connectivityMonitor.mutableState.value = ConnectivityState.Offline
        launch(container)
        rule.runOnUiThread { navController.navigate(Emergency) }
        rule.waitForIdle()

        rule.onNodeWithTag("screen_emergency").assertIsDisplayed()
        rule.onNodeWithTag("emergency_dial_112").assertIsDisplayed()
        rule.onNodeWithTag("emergency_dial_108").assertIsDisplayed()
        rule.onNodeWithTag("emergency_share_location").assertIsDisplayed()
        waitForText("Amma")
        rule.onNodeWithTag("emergency_manage_contacts").assertIsDisplayed()
        rule.onNodeWithTag("connectivity_banner").assertIsDisplayed()
    }

    @Test
    fun addingContactValidatesThenSaves() {
        val container = onboardedContainer()
        launch(container)
        rule.runOnUiThread { navController.navigate(EmergencyContacts) }
        rule.waitForIdle()
        rule.onNodeWithTag("contacts_empty").assertIsDisplayed()

        rule.onNodeWithTag("contacts_add").performClick()
        rule.onNodeWithTag("contact_editor").assertIsDisplayed()
        rule.onNodeWithTag("contact_phone_field").performTextInput("12")
        rule.onNodeWithTag("contact_save").performClick()
        rule.onNodeWithTag("contact_name_error", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("contact_phone_error", useUnmergedTree = true).assertExists()
        assertTrue(container.emergencyContactRepository.items.value.isEmpty())

        rule.onNodeWithTag("contact_name_field").performTextInput("Amma")
        rule.onNodeWithTag("contact_phone_field").performTextClearance()
        rule.onNodeWithTag("contact_phone_field").performTextInput("9876543210")
        rule.onNodeWithTag("contact_save").performClick()
        rule.waitUntil(UI_TIMEOUT_MILLIS) { container.emergencyContactRepository.items.value.isNotEmpty() }
        rule.onNodeWithTag("contacts_list").assertIsDisplayed()
        rule.onNodeWithText("Amma").assertIsDisplayed()
    }

    @Test
    fun savedShowsEmptyStateThenItems() {
        val container = onboardedContainer()
        launch(container)
        rule.onNodeWithTag("tab_saved").performClick()
        rule.onNodeWithTag("screen_saved").assertIsDisplayed()
        rule.onNodeWithTag("saved_empty").assertIsDisplayed()
        rule.onNodeWithTag("saved_contacts_row").assertIsDisplayed()

        runBlocking {
            container.savedPlaceRepository.save(SavedPlace(0, samplePlace(id = "home", name = "Casa"), SavedPlaceType.HOME, null, 1L))
            container.savedPlaceRepository.save(SavedPlace(0, samplePlace(id = "fav", name = "Chai Point"), SavedPlaceType.FAVORITE, null, 2L))
            container.savedPlaceRepository.save(SavedPlace(0, samplePlace(id = "park", name = "Mall Parking"), SavedPlaceType.PARKING, null, 3L))
        }
        waitForTag("saved_list")
        rule.onNodeWithTag("saved_slot_home").assertIsDisplayed()
        rule.onNodeWithTag("saved_slot_work").assertIsDisplayed()
        rule.onNodeWithText("Chai Point").assertIsDisplayed()
        rule.onNodeWithText("Mall Parking").assertIsDisplayed()
    }
}
