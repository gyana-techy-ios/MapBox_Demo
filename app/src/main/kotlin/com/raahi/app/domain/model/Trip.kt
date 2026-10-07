package com.raahi.app.domain.model

data class Trip(
    val id: Long,
    val name: String,
    val stops: List<TripStop>,
    val profile: RouteProfile,
    val createdAt: Long,
    val updatedAt: Long,
    val isQuick: Boolean,
    val options: RouteOptions = RouteOptions(),
) {
    companion object {
        const val NEW_ID = 0L
        const val MAX_STOPS = 25
    }
}

data class TripStop(
    val id: Long,
    val place: Place?,
    val useCurrentLocation: Boolean,
    val order: Int,
)

enum class SavedPlaceType { HOME, WORK, FAVORITE, PARKING, EMERGENCY }

data class SavedPlace(
    val id: Long,
    val place: Place,
    val type: SavedPlaceType,
    val note: String?,
    val savedAt: Long,
)

data class EmergencyContact(
    val id: Long,
    val name: String,
    val phone: String,
)
