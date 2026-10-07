package com.raahi.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlaceCategory(val canonicalId: String) {
    FUEL("gas_station"),
    EV_CHARGING("charging_station"),
    HOSPITAL("hospital"),
    PHARMACY("pharmacy"),
    POLICE("police_station"),
    ATM("atm"),
    RESTAURANT("restaurant"),
    HOTEL("hotel"),
    PARKING("parking_lot"),
    TOILET("toilet");

    companion object {
        fun fromName(name: String?): PlaceCategory? = entries.firstOrNull { it.name == name }

        fun fromCanonicalIds(ids: List<String>): PlaceCategory? =
            entries.firstOrNull { it.canonicalId in ids }
    }
}

@Serializable
enum class PlaceSource { SEARCH, CATEGORY, REVERSE_GEOCODE, SAVED, CACHE, MAP }

@Serializable
data class Place(
    val id: String,
    val name: String,
    val address: String?,
    val point: GeoPoint,
    val category: PlaceCategory?,
    val distanceMeters: Double?,
    val source: PlaceSource,
)

data class SearchSuggestion(
    val id: String,
    val name: String,
    val address: String?,
    val category: PlaceCategory?,
    val distanceMeters: Double?,
)

data class RecentSearch(
    val id: Long,
    val query: String,
    val place: Place?,
    val searchedAt: Long,
)
