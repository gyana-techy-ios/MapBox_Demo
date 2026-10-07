package com.raahi.app.domain.model

import com.raahi.app.core.result.AppError

enum class OfflineRegionStatus { Downloading, Completed, Failed, Cancelled, Partial }

data class OfflineRegion(
    val id: String,
    val name: String,
    val bounds: GeoBounds,
    val minZoom: Int,
    val maxZoom: Int,
    val status: OfflineRegionStatus,
    val progress: Float,
    val sizeBytes: Long,
    val downloadedAt: Long?,
    val isOutdated: Boolean,
)

data class OfflineEstimate(
    val tileCount: Long,
    val approximateBytes: Long,
)

data class DownloadProgress(
    val regionId: String,
    val status: OfflineRegionStatus,
    val progress: Float,
    val loadedBytes: Long,
    val error: AppError? = null,
)

data class UserLocation(
    val point: GeoPoint,
    val accuracyMeters: Float?,
    val timestampMillis: Long,
    val bearingDegrees: Float?,
    val speedMetersPerSecond: Float?,
    val isStale: Boolean,
    val isLowAccuracy: Boolean,
)

enum class LocationPermissionState { GRANTED_PRECISE, GRANTED_APPROXIMATE, DENIED }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class MapStyleMode { STANDARD, SATELLITE }

data class UserPreferences(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val mapStyle: MapStyleMode = MapStyleMode.STANDARD,
    val defaultProfile: RouteProfile = RouteProfile.DRIVING_TRAFFIC,
    val defaultRouteOptions: RouteOptions = RouteOptions(),
    val wifiOnlyDownloads: Boolean = true,
    val voiceGuidance: Boolean = true,
)

data class CachedPlaces(
    val places: List<Place>,
    val cachedAtMillis: Long,
)
