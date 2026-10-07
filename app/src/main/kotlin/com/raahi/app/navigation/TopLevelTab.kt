package com.raahi.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.raahi.app.R
import kotlin.reflect.KClass

enum class TopLevelTab(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val testTag: String,
) {
    EXPLORE(Explore, Explore::class, Icons.Outlined.Explore, R.string.tab_explore, "tab_explore"),
    TRIPS(Trips, Trips::class, Icons.Outlined.Route, R.string.tab_trips, "tab_trips"),
    OFFLINE(Offline, Offline::class, Icons.Outlined.CloudDownload, R.string.tab_offline, "tab_offline"),
    SAVED(Saved, Saved::class, Icons.Outlined.BookmarkBorder, R.string.tab_saved, "tab_saved"),
    PROFILE(Profile, Profile::class, Icons.Outlined.Person, R.string.tab_profile, "tab_profile"),
}

private val fullScreenRoutes = listOf(Onboarding::class, Search::class, Guidance::class, OfflineRegionPicker::class)

fun NavDestination?.isSelected(tab: TopLevelTab): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true

fun NavDestination?.showsBottomBar(): Boolean {
    if (this == null) return false
    return fullScreenRoutes.none { hasRoute(it) }
}
