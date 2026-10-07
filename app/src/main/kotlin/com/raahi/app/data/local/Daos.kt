package com.raahi.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TripDao {

    @Transaction
    @Query("SELECT * FROM trips ORDER BY updatedAt DESC")
    abstract fun observeTrips(): Flow<List<TripWithStops>>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    abstract fun observeTrip(id: Long): Flow<TripWithStops?>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    abstract suspend fun getTrip(id: Long): TripWithStops?

    @Upsert
    abstract suspend fun upsertTrip(trip: TripEntity): Long

    @Insert
    abstract suspend fun insertStops(stops: List<TripStopEntity>)

    @Query("DELETE FROM trip_stops WHERE tripId = :tripId")
    abstract suspend fun deleteStops(tripId: Long)

    @Query("DELETE FROM trips WHERE id = :id")
    abstract suspend fun deleteTrip(id: Long)

    @Query("UPDATE trip_stops SET position = :position WHERE id = :stopId AND tripId = :tripId")
    abstract suspend fun updateStopPosition(tripId: Long, stopId: Long, position: Int)

    @Transaction
    open suspend fun replaceTrip(trip: TripEntity, stops: List<TripStopEntity>): Long {
        val rowId = upsertTrip(trip)
        val tripId = if (trip.id == 0L) rowId else trip.id
        deleteStops(tripId)
        insertStops(stops.map { it.copy(tripId = tripId, id = 0) })
        return tripId
    }

    @Transaction
    open suspend fun reorderStops(tripId: Long, orderedStopIds: List<Long>) {
        orderedStopIds.forEachIndexed { index, stopId -> updateStopPosition(tripId, stopId, index) }
    }
}

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Upsert
    suspend fun upsert(entity: SavedPlaceEntity): Long

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<EmergencyContactEntity>>

    @Upsert
    suspend fun upsert(entity: EmergencyContactEntity): Long

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecentSearchEntity): Long

    @Query("DELETE FROM recent_searches WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recent_searches")
    suspend fun clear()
}

@Dao
interface OfflineRegionDao {
    @Query("SELECT * FROM offline_regions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<OfflineRegionEntity>>

    @Query("SELECT * FROM offline_regions")
    suspend fun getAll(): List<OfflineRegionEntity>

    @Query("SELECT * FROM offline_regions WHERE id = :id")
    suspend fun get(id: String): OfflineRegionEntity?

    @Upsert
    suspend fun upsert(entity: OfflineRegionEntity)

    @Query("UPDATE offline_regions SET status = :status, progress = :progress, sizeBytes = :sizeBytes, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, progress: Float, sizeBytes: Long, downloadedAt: Long?)

    @Query("DELETE FROM offline_regions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface NearbyCacheDao {
    @Query("SELECT * FROM nearby_cache WHERE `key` = :key")
    suspend fun get(key: String): NearbyCacheEntity?

    @Upsert
    suspend fun upsert(entity: NearbyCacheEntity)
}
