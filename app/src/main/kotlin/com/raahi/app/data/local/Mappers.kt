package com.raahi.app.data.local

import com.raahi.app.data.remote.RaahiJson
import com.raahi.app.domain.model.CachedPlaces
import com.raahi.app.domain.model.EmergencyContact
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.OfflineRegion
import com.raahi.app.domain.model.OfflineRegionStatus
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.PlaceSource
import com.raahi.app.domain.model.RecentSearch
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.SavedPlaceType
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.model.TripStop
import java.util.concurrent.TimeUnit
import kotlinx.serialization.builtins.ListSerializer

private val placeListSerializer = ListSerializer(Place.serializer())

fun TripWithStops.toDomain(): Trip = Trip(
    id = trip.id,
    name = trip.name,
    stops = stops.sortedBy { it.position }.map { it.toDomain() },
    profile = enumOrDefault(trip.profile, RouteProfile.DRIVING_TRAFFIC),
    createdAt = trip.createdAt,
    updatedAt = trip.updatedAt,
    isQuick = trip.isQuick,
)

fun TripStopEntity.toDomain(): TripStop {
    val place = if (lat != null && lng != null && placeName != null) {
        Place(
            id = placeId ?: "$lat,$lng",
            name = placeName,
            address = placeAddress,
            point = GeoPoint(lat, lng),
            category = PlaceCategory.fromName(category),
            distanceMeters = null,
            source = PlaceSource.SAVED,
        )
    } else {
        null
    }
    return TripStop(id = id, place = place, useCurrentLocation = useCurrentLocation, order = position)
}

fun Trip.toEntity(): TripEntity = TripEntity(
    id = id,
    name = name,
    profile = profile.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isQuick = isQuick,
)

fun Trip.toStopEntities(): List<TripStopEntity> = stops.sortedBy { it.order }.mapIndexed { index, stop ->
    TripStopEntity(
        id = stop.id,
        tripId = id,
        position = index,
        useCurrentLocation = stop.useCurrentLocation,
        placeId = stop.place?.id,
        placeName = stop.place?.name,
        placeAddress = stop.place?.address,
        lat = stop.place?.point?.lat,
        lng = stop.place?.point?.lng,
        category = stop.place?.category?.name,
    )
}

fun SavedPlaceEntity.toDomain(): SavedPlace = SavedPlace(
    id = id,
    place = Place(
        id = placeId,
        name = name,
        address = address,
        point = GeoPoint(lat, lng),
        category = PlaceCategory.fromName(category),
        distanceMeters = null,
        source = PlaceSource.SAVED,
    ),
    type = enumOrDefault(type, SavedPlaceType.FAVORITE),
    note = note,
    savedAt = savedAt,
)

fun SavedPlace.toEntity(): SavedPlaceEntity = SavedPlaceEntity(
    id = id,
    placeId = place.id,
    name = place.name,
    address = place.address,
    lat = place.point.lat,
    lng = place.point.lng,
    category = place.category?.name,
    type = type.name,
    note = note,
    savedAt = savedAt,
)

fun EmergencyContactEntity.toDomain() = EmergencyContact(id, name, phone)

fun EmergencyContact.toEntity() = EmergencyContactEntity(id, name, phone)

fun RecentSearchEntity.toDomain(): RecentSearch = RecentSearch(
    id = id,
    query = query,
    place = placeJson?.let { runCatching { RaahiJson.decodeFromString(Place.serializer(), it) }.getOrNull() },
    searchedAt = searchedAt,
)

fun Place.toJson(): String = RaahiJson.encodeToString(Place.serializer(), this)

fun List<Place>.toJson(): String = RaahiJson.encodeToString(placeListSerializer, this)

fun NearbyCacheEntity.toDomain(): CachedPlaces? = runCatching {
    CachedPlaces(RaahiJson.decodeFromString(placeListSerializer, payloadJson), cachedAt)
}.getOrNull()

fun OfflineRegionEntity.toDomain(nowMillis: Long): OfflineRegion = OfflineRegion(
    id = id,
    name = name,
    bounds = GeoBounds(south, west, north, east),
    minZoom = minZoom,
    maxZoom = maxZoom,
    status = enumOrDefault(status, OfflineRegionStatus.Failed),
    progress = progress,
    sizeBytes = sizeBytes,
    downloadedAt = downloadedAt,
    isOutdated = downloadedAt?.let { nowMillis - it > OUTDATED_AFTER_MILLIS } ?: false,
)

fun OfflineRegion.toEntity(): OfflineRegionEntity = OfflineRegionEntity(
    id = id,
    name = name,
    south = bounds.south,
    west = bounds.west,
    north = bounds.north,
    east = bounds.east,
    minZoom = minZoom,
    maxZoom = maxZoom,
    status = status.name,
    progress = progress,
    sizeBytes = sizeBytes,
    downloadedAt = downloadedAt,
)

val OUTDATED_AFTER_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
