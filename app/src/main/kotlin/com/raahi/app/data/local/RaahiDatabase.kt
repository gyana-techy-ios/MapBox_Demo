package com.raahi.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class,
        TripStopEntity::class,
        SavedPlaceEntity::class,
        EmergencyContactEntity::class,
        RecentSearchEntity::class,
        OfflineRegionEntity::class,
        NearbyCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RaahiDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun offlineRegionDao(): OfflineRegionDao
    abstract fun nearbyCacheDao(): NearbyCacheDao

    companion object {
        const val NAME = "raahi.db"

        fun create(context: Context): RaahiDatabase =
            Room.databaseBuilder(context.applicationContext, RaahiDatabase::class.java, NAME).build()
    }
}
