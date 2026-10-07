package com.raahi.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val profile: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isQuick: Boolean,
    @ColumnInfo(defaultValue = "0") val avoidTolls: Boolean = false,
    @ColumnInfo(defaultValue = "0") val avoidMotorways: Boolean = false,
    @ColumnInfo(defaultValue = "0") val avoidFerries: Boolean = false,
)

@Entity(
    tableName = "trip_stops",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class TripStopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val position: Int,
    val useCurrentLocation: Boolean,
    val placeId: String?,
    val placeName: String?,
    val placeAddress: String?,
    val lat: Double?,
    val lng: Double?,
    val category: String?,
)

data class TripWithStops(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId") val stops: List<TripStopEntity>,
)

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: String,
    val name: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val category: String?,
    val type: String,
    val note: String?,
    val savedAt: Long,
)

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
)

@Entity(tableName = "recent_searches", indices = [Index(value = ["query"], unique = true)])
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val placeJson: String?,
    val searchedAt: Long,
)

@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val status: String,
    val progress: Float,
    val sizeBytes: Long,
    val downloadedAt: Long?,
)

@Entity(tableName = "nearby_cache")
data class NearbyCacheEntity(
    @PrimaryKey val key: String,
    val payloadJson: String,
    val cachedAt: Long,
)
