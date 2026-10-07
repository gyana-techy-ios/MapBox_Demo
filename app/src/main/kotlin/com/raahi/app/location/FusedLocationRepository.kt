package com.raahi.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.time.Clock
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.UserLocation
import com.raahi.app.domain.repository.LocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FusedLocationRepository(
    context: Context,
    private val clock: Clock,
) : LocationRepository {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    private val mutablePermissionState = MutableStateFlow(readPermissionState())
    override val permissionState: StateFlow<LocationPermissionState> = mutablePermissionState.asStateFlow()

    override fun refreshPermissionState() {
        mutablePermissionState.value = readPermissionState()
    }

    override fun isLocationEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(locationManager)

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): AppResult<UserLocation> {
        if (readPermissionState() == LocationPermissionState.DENIED) return AppResult.Failure(AppError.PermissionDenied)
        return try {
            client.lastLocation.await()?.let { AppResult.Success(it.toUserLocation()) }
                ?: AppResult.Failure(AppError.LocationUnavailable)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Failure(AppError.LocationUnavailable)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): AppResult<UserLocation> {
        if (readPermissionState() == LocationPermissionState.DENIED) return AppResult.Failure(AppError.PermissionDenied)
        val cached = lastKnown()
        if (cached is AppResult.Success && !cached.value.isStale && !cached.value.isLowAccuracy) return cached
        if (!isLocationEnabled()) return if (cached is AppResult.Success) cached else AppResult.Failure(AppError.LocationUnavailable)
        return try {
            val tokenSource = CancellationTokenSource()
            val fresh = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await()
            when {
                fresh != null -> AppResult.Success(fresh.toUserLocation())
                cached is AppResult.Success -> cached
                else -> AppResult.Failure(AppError.LocationUnavailable)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (cached is AppResult.Success) cached else AppResult.Failure(AppError.LocationUnavailable)
        }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMillis: Long): Flow<UserLocation> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toUserLocation()) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, appContext.mainLooper)
        } catch (_: SecurityException) {
            close(SecurityException("Location permission missing"))
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun Location.toUserLocation(): UserLocation {
        val ageMillis = clock.nowMillis() - time
        return UserLocation(
            point = GeoPoint(latitude, longitude),
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            timestampMillis = time,
            bearingDegrees = if (hasBearing()) bearing else null,
            speedMetersPerSecond = if (hasSpeed()) speed else null,
            isStale = ageMillis > STALE_AFTER_MILLIS,
            isLowAccuracy = !hasAccuracy() || accuracy > LOW_ACCURACY_METERS,
        )
    }

    private fun readPermissionState(): LocationPermissionState = when {
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationPermissionState.GRANTED_PRECISE
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationPermissionState.GRANTED_APPROXIMATE
        else -> LocationPermissionState.DENIED
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val STALE_AFTER_MILLIS = 2 * 60 * 1000L
        const val LOW_ACCURACY_METERS = 100f
    }
}
