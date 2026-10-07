package com.raahi.app.data.repository

import com.raahi.app.core.time.Clock
import com.raahi.app.data.local.EmergencyContactDao
import com.raahi.app.data.local.NearbyCacheDao
import com.raahi.app.data.local.NearbyCacheEntity
import com.raahi.app.data.local.RecentSearchDao
import com.raahi.app.data.local.RecentSearchEntity
import com.raahi.app.data.local.SavedPlaceDao
import com.raahi.app.data.local.TripDao
import com.raahi.app.data.local.toDomain
import com.raahi.app.data.local.toEntity
import com.raahi.app.data.local.toJson
import com.raahi.app.data.local.toStopEntities
import com.raahi.app.domain.model.CachedPlaces
import com.raahi.app.domain.model.EmergencyContact
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.RecentSearch
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.repository.EmergencyContactRepository
import com.raahi.app.domain.repository.NearbyCacheRepository
import com.raahi.app.domain.repository.RecentSearchRepository
import com.raahi.app.domain.repository.SavedPlaceRepository
import com.raahi.app.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTripRepository(private val dao: TripDao, private val clock: Clock) : TripRepository {

    override fun observeTrips(): Flow<List<Trip>> = dao.observeTrips().map { list -> list.map { it.toDomain() } }

    override fun observeTrip(id: Long): Flow<Trip?> = dao.observeTrip(id).map { it?.toDomain() }

    override suspend fun getTrip(id: Long): Trip? = dao.getTrip(id)?.toDomain()

    override suspend fun upsert(trip: Trip): Long {
        val now = clock.nowMillis()
        val stamped = trip.copy(
            createdAt = if (trip.id == Trip.NEW_ID) now else trip.createdAt,
            updatedAt = now,
        )
        return dao.replaceTrip(stamped.toEntity(), stamped.toStopEntities())
    }

    override suspend fun delete(id: Long) = dao.deleteTrip(id)

    override suspend fun reorder(tripId: Long, orderedStopIds: List<Long>) = dao.reorderStops(tripId, orderedStopIds)
}

class RoomSavedPlaceRepository(private val dao: SavedPlaceDao) : SavedPlaceRepository {
    override fun observeAll(): Flow<List<SavedPlace>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun save(saved: SavedPlace): Long = dao.upsert(saved.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
}

class RoomEmergencyContactRepository(private val dao: EmergencyContactDao) : EmergencyContactRepository {
    override fun observeAll(): Flow<List<EmergencyContact>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun upsert(contact: EmergencyContact): Long = dao.upsert(contact.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
}

class RoomRecentSearchRepository(
    private val dao: RecentSearchDao,
    private val clock: Clock,
) : RecentSearchRepository {
    override fun observeRecent(limit: Int): Flow<List<RecentSearch>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun record(query: String, place: Place?) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dao.insert(RecentSearchEntity(query = trimmed, placeJson = place?.toJson(), searchedAt = clock.nowMillis()))
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun clear() = dao.clear()
}

class RoomNearbyCacheRepository(
    private val dao: NearbyCacheDao,
    private val clock: Clock,
) : NearbyCacheRepository {
    override suspend fun get(key: String): CachedPlaces? = dao.get(key)?.toDomain()

    override suspend fun put(key: String, places: List<Place>) {
        dao.upsert(NearbyCacheEntity(key = key, payloadJson = places.toJson(), cachedAt = clock.nowMillis()))
    }

    override suspend fun clear() = dao.clear()
}
