package com.raahi.app.data.remote

import com.raahi.app.domain.model.CongestionLevel
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.RouteLeg
import com.raahi.app.domain.model.RouteStep

class DirectionsMapper {

    fun toDomain(index: Int, dto: RouteDto): Route = Route(
        id = "route-$index",
        geometry = Polyline6.decode(dto.geometry),
        distanceMeters = dto.distance,
        durationSeconds = dto.duration,
        legs = dto.legs.map { leg -> RouteLeg(leg.steps.map(::toStep)) },
        summary = dto.legs.map { it.summary }.filter { it.isNotBlank() }.joinToString(", "),
        congestionLevel = congestionOf(dto.legs.flatMap { it.annotation?.congestion.orEmpty() }),
    )

    private fun toStep(dto: StepDto): RouteStep = RouteStep(
        instruction = dto.maneuver.instruction,
        maneuverType = dto.maneuver.type,
        modifier = dto.maneuver.modifier,
        distanceMeters = dto.distance,
        durationSeconds = dto.duration,
        location = GeoPoint(
            lat = dto.maneuver.location.getOrElse(1) { 0.0 },
            lng = dto.maneuver.location.getOrElse(0) { 0.0 },
        ),
        roadName = dto.name.takeIf { it.isNotBlank() },
    )

    fun congestionOf(segments: List<String>): CongestionLevel {
        val known = segments.filter { it != "unknown" }
        if (known.isEmpty()) return CongestionLevel.UNKNOWN
        val total = known.size.toDouble()
        val severe = known.count { it == "severe" } / total
        val heavy = known.count { it == "heavy" } / total
        val moderate = known.count { it == "moderate" } / total
        return when {
            severe >= SEVERE_SHARE -> CongestionLevel.SEVERE
            severe + heavy >= HEAVY_SHARE -> CongestionLevel.HEAVY
            severe + heavy + moderate >= MODERATE_SHARE -> CongestionLevel.MODERATE
            else -> CongestionLevel.LOW
        }
    }

    private companion object {
        const val SEVERE_SHARE = 0.1
        const val HEAVY_SHARE = 0.2
        const val MODERATE_SHARE = 0.3
    }
}
