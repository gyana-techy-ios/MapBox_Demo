package com.raahi.app.data.remote

import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.result.HttpErrorMapper
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.repository.DirectionsRepository

class MapboxDirectionsRepository(
    private val api: DirectionsApi,
    private val isConfigured: () -> Boolean,
    private val mapper: DirectionsMapper = DirectionsMapper(),
) : DirectionsRepository {

    override suspend fun getRoutes(
        waypoints: List<GeoPoint>,
        profile: RouteProfile,
        options: RouteOptions,
    ): AppResult<List<Route>> {
        if (!isConfigured()) return AppResult.Failure(AppError.Configuration)
        if (waypoints.size < MIN_WAYPOINTS) return AppResult.Failure(AppError.Unknown("at least two waypoints required"))
        if (waypoints.size > MAX_WAYPOINTS) return AppResult.Failure(AppError.Unknown("at most $MAX_WAYPOINTS waypoints"))

        val coordinates = waypoints.joinToString(";") { "${it.lng},${it.lat}" }
        val isDriving = profile == RouteProfile.DRIVING || profile == RouteProfile.DRIVING_TRAFFIC
        val response = apiCall {
            api.getRoutes(
                profile = profile.apiName,
                coordinates = coordinates,
                alternatives = waypoints.size == MIN_WAYPOINTS,
                annotations = if (isDriving) "congestion,duration,distance" else "duration,distance",
                exclude = if (isDriving) excludeValue(options) else null,
                language = "en",
            )
        }
        return when (response) {
            is AppResult.Failure -> response
            is AppResult.Success -> {
                val dto = response.value
                val codeError = HttpErrorMapper.fromApiCode(dto.code)
                when {
                    codeError != null -> AppResult.Failure(codeError)
                    dto.routes.isEmpty() -> AppResult.Failure(AppError.NoRoute)
                    else -> AppResult.Success(dto.routes.mapIndexed { index, route -> mapper.toDomain(index, route) })
                }
            }
        }
    }

    private fun excludeValue(options: RouteOptions): String? = buildList {
        if (options.avoidTolls) add("toll")
        if (options.avoidMotorways) add("motorway")
        if (options.avoidFerries) add("ferry")
    }.takeIf { it.isNotEmpty() }?.joinToString(",")

    companion object {
        const val MIN_WAYPOINTS = 2
        const val MAX_WAYPOINTS = 25
    }
}
