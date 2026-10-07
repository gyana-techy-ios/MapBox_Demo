package com.raahi.app.domain.usecase

import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.repository.DirectionsRepository
import com.raahi.app.domain.repository.LocationRepository

class PlanRoutesUseCase(
    private val locationRepository: LocationRepository,
    private val directionsRepository: DirectionsRepository,
    private val validator: TripValidator,
) {
    suspend operator fun invoke(trip: Trip, options: RouteOptions = trip.options): AppResult<List<Route>> {
        val validation = validator.validate(trip)
        if (!validation.isValid) return AppResult.Failure(AppError.Unknown("invalid trip: ${validation.issues}"))

        val waypoints = mutableListOf<GeoPoint>()
        for (stop in trip.stops.sortedBy { it.order }) {
            val point = if (stop.useCurrentLocation) {
                when (val location = locationRepository.currentLocation()) {
                    is AppResult.Success -> location.value.point
                    is AppResult.Failure -> return location
                }
            } else {
                stop.place?.point ?: return AppResult.Failure(AppError.Unknown("stop without place"))
            }
            waypoints += point
        }
        return directionsRepository.getRoutes(waypoints, trip.profile, options)
    }
}
