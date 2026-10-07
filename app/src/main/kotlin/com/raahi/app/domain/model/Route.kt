package com.raahi.app.domain.model

enum class RouteProfile(val apiName: String) {
    DRIVING_TRAFFIC("driving-traffic"),
    DRIVING("driving"),
    WALKING("walking"),
    CYCLING("cycling"),
}

data class RouteOptions(
    val avoidTolls: Boolean = false,
    val avoidMotorways: Boolean = false,
    val avoidFerries: Boolean = false,
)

enum class CongestionLevel { UNKNOWN, LOW, MODERATE, HEAVY, SEVERE }

data class Route(
    val id: String,
    val geometry: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val legs: List<RouteLeg>,
    val summary: String,
    val congestionLevel: CongestionLevel,
) {
    val steps: List<RouteStep> get() = legs.flatMap { it.steps }
}

data class RouteLeg(val steps: List<RouteStep>)

data class RouteStep(
    val instruction: String,
    val maneuverType: String,
    val modifier: String?,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val location: GeoPoint,
    val roadName: String?,
)
