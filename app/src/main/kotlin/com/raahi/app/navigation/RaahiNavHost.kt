package com.raahi.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.feature.about.AboutRoute
import com.raahi.app.feature.emergency.EmergencyRoute
import com.raahi.app.feature.emergencycontacts.EmergencyContactsRoute
import com.raahi.app.feature.explore.ExploreRoute
import com.raahi.app.feature.guidance.GuidanceRoute
import com.raahi.app.feature.language.LanguageRoute
import com.raahi.app.feature.nearby.NearbyRoute
import com.raahi.app.feature.offline.OfflineRoute
import com.raahi.app.feature.offlineregionpicker.OfflineRegionPickerRoute
import com.raahi.app.feature.onboarding.OnboardingRoute
import com.raahi.app.feature.placedetail.PlaceDetailRoute
import com.raahi.app.feature.profile.ProfileRoute
import com.raahi.app.feature.routepreview.RoutePreviewRoute
import com.raahi.app.feature.saved.SavedRoute
import com.raahi.app.feature.search.SearchRoute
import com.raahi.app.feature.settings.SettingsRoute
import com.raahi.app.feature.tripdetail.TripDetailRoute
import com.raahi.app.feature.tripeditor.TripEditorRoute
import com.raahi.app.feature.trips.TripsRoute

private fun Place.toRoute(): PlaceDetail = PlaceDetail(
    id = id,
    name = name,
    lat = point.lat,
    lng = point.lng,
    address = address,
    category = category?.name,
)

@Composable
fun RaahiNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
) {
    val openPlace: (Place) -> Unit = { navController.navigate(it.toRoute()) }
    val openNearby: (PlaceCategory) -> Unit = { navController.navigate(Nearby(it.name)) }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<Onboarding> {
            OnboardingRoute(
                onFinished = {
                    navController.navigate(Explore) {
                        popUpTo<Onboarding> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<Explore> {
            ExploreRoute(
                onOpenSearch = { navController.navigate(Search) },
                onOpenPlace = openPlace,
                onOpenEmergency = { navController.navigate(Emergency) },
                onOpenNearby = openNearby,
            )
        }
        composable<Search> {
            SearchRoute(onBack = back, onOpenPlace = openPlace, onOpenNearby = openNearby)
        }
        composable<PlaceDetail>(
            deepLinks = listOf(navDeepLink<PlaceDetail>(basePath = DeepLinks.PLACE_BASE)),
        ) { entry ->
            val args = entry.toRoute<PlaceDetail>()
            PlaceDetailRoute(
                placeId = args.id,
                name = args.name,
                lat = args.lat,
                lng = args.lng,
                address = args.address,
                category = args.category,
                onBack = back,
                onDirections = { tripId -> navController.navigate(RoutePreview(tripId)) },
            )
        }
        composable<Nearby> { entry ->
            NearbyRoute(category = entry.toRoute<Nearby>().category, onBack = back, onOpenPlace = openPlace)
        }
        composable<Emergency> {
            EmergencyRoute(
                onBack = back,
                onOpenPlace = openPlace,
                onOpenContacts = { navController.navigate(EmergencyContacts) },
            )
        }
        composable<Trips> {
            TripsRoute(
                onOpenTrip = { navController.navigate(TripDetail(it)) },
                onNewTrip = { navController.navigate(TripEditor()) },
            )
        }
        composable<TripEditor> { entry ->
            TripEditorRoute(
                tripId = entry.toRoute<TripEditor>().tripId,
                onBack = back,
                onSaved = { id ->
                    navController.navigate(TripDetail(id)) {
                        popUpTo<TripEditor> { inclusive = true }
                    }
                },
            )
        }
        composable<TripDetail>(
            deepLinks = listOf(navDeepLink<TripDetail>(basePath = DeepLinks.TRIP_BASE)),
        ) { entry ->
            TripDetailRoute(
                tripId = entry.toRoute<TripDetail>().tripId,
                onBack = back,
                onEdit = { navController.navigate(TripEditor(it)) },
                onPreviewRoute = { navController.navigate(RoutePreview(it)) },
            )
        }
        composable<RoutePreview> { entry ->
            RoutePreviewRoute(
                tripId = entry.toRoute<RoutePreview>().tripId,
                onBack = back,
                onStartGuidance = { tripId, routeIndex -> navController.navigate(Guidance(tripId, routeIndex)) },
            )
        }
        composable<Guidance> { entry ->
            val args = entry.toRoute<Guidance>()
            GuidanceRoute(tripId = args.tripId, routeIndex = args.routeIndex, onExit = back)
        }
        composable<Offline> {
            OfflineRoute(onPickRegion = { navController.navigate(OfflineRegionPicker) })
        }
        composable<OfflineRegionPicker> {
            OfflineRegionPickerRoute(onBack = back, onDownloadStarted = back)
        }
        composable<Saved> {
            SavedRoute(
                onOpenPlace = openPlace,
                onOpenContacts = { navController.navigate(EmergencyContacts) },
                onOpenExplore = { navController.navigateToTab(TopLevelTab.EXPLORE) },
            )
        }
        composable<EmergencyContacts> {
            EmergencyContactsRoute(onBack = back)
        }
        composable<Profile> {
            ProfileRoute(
                onOpenSettings = { navController.navigate(Settings) },
                onOpenLanguage = { navController.navigate(Language) },
                onOpenContacts = { navController.navigate(EmergencyContacts) },
                onOpenAbout = { navController.navigate(About) },
            )
        }
        composable<Settings> { SettingsRoute(onBack = back) }
        composable<Language> { LanguageRoute(onBack = back) }
        composable<About> { AboutRoute(onBack = back) }
    }
}
