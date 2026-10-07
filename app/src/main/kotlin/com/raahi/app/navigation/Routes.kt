package com.raahi.app.navigation

import kotlinx.serialization.Serializable

@Serializable data object Onboarding

@Serializable data object Explore

@Serializable data object Search

@Serializable
data class PlaceDetail(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String? = null,
    val category: String? = null,
)

@Serializable data class Nearby(val category: String)

@Serializable data object Emergency

@Serializable data object Trips

@Serializable data class TripEditor(val tripId: Long = NEW_TRIP_ID) {
    companion object {
        const val NEW_TRIP_ID = -1L
    }
}

@Serializable data class TripDetail(val tripId: Long)

@Serializable data class RoutePreview(val tripId: Long)

@Serializable data class Guidance(val tripId: Long, val routeIndex: Int = 0)

@Serializable data object Offline

@Serializable data object OfflineRegionPicker

@Serializable data object Saved

@Serializable data object EmergencyContacts

@Serializable data object Profile

@Serializable data object Settings

@Serializable data object Language

@Serializable data object About

object DeepLinks {
    const val PLACE_BASE = "raahi://place"
    const val TRIP_BASE = "raahi://trip"
}
