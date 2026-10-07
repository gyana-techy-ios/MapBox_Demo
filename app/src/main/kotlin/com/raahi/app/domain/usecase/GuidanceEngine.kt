package com.raahi.app.domain.usecase

import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.RouteStep
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class GuidanceProgress(
    val currentStepIndex: Int,
    val currentStep: RouteStep?,
    val nextStep: RouteStep?,
    val distanceToNextManeuverMeters: Double,
    val remainingDistanceMeters: Double,
    val remainingDurationSeconds: Double,
    val distanceFromRouteMeters: Double,
    val snappedPoint: GeoPoint?,
    val isOffRoute: Boolean,
    val hasArrived: Boolean,
)

class GuidanceEngine(
    private val route: Route,
    private val offRouteThresholdMeters: Double = DEFAULT_OFF_ROUTE_METERS,
    private val offRouteFixCount: Int = DEFAULT_OFF_ROUTE_FIXES,
    private val arrivalThresholdMeters: Double = DEFAULT_ARRIVAL_METERS,
) {
    private val geometry = route.geometry
    private val steps = route.steps
    private val cumulative: DoubleArray = buildCumulativeDistances()
    private val totalLength: Double = cumulative.lastOrNull() ?: 0.0
    private val stepStarts: DoubleArray = buildStepStarts()
    private var consecutiveOffRouteFixes = 0

    fun update(location: GeoPoint): GuidanceProgress {
        val snap = snapToRoute(location)
        val distanceFromRoute = snap?.distanceMeters ?: Double.MAX_VALUE
        val along = snap?.alongMeters ?: 0.0

        consecutiveOffRouteFixes = if (distanceFromRoute > offRouteThresholdMeters) consecutiveOffRouteFixes + 1 else 0

        val remainingDistance = max(0.0, totalLength - along)
        val endPoint = geometry.lastOrNull()
        val hasArrived = endPoint != null && distanceFromRoute <= offRouteThresholdMeters &&
            (remainingDistance <= arrivalThresholdMeters || location.distanceTo(endPoint) <= arrivalThresholdMeters)

        val stepIndex = currentStepIndexFor(along)
        val nextStart = stepStarts.getOrNull(stepIndex + 1) ?: totalLength

        return GuidanceProgress(
            currentStepIndex = stepIndex,
            currentStep = steps.getOrNull(stepIndex),
            nextStep = steps.getOrNull(stepIndex + 1),
            distanceToNextManeuverMeters = max(0.0, nextStart - along),
            remainingDistanceMeters = if (hasArrived) 0.0 else remainingDistance,
            remainingDurationSeconds = if (hasArrived) 0.0 else remainingDuration(along),
            distanceFromRouteMeters = distanceFromRoute,
            snappedPoint = snap?.point,
            isOffRoute = consecutiveOffRouteFixes >= offRouteFixCount,
            hasArrived = hasArrived,
        )
    }

    fun reset() {
        consecutiveOffRouteFixes = 0
    }

    private fun currentStepIndexFor(along: Double): Int {
        if (steps.isEmpty()) return 0
        var index = 0
        for (i in stepStarts.indices) {
            if (stepStarts[i] <= along) index = i else break
        }
        return index
    }

    private fun remainingDuration(along: Double): Double {
        if (steps.isEmpty()) return route.durationSeconds * (if (totalLength > 0) (totalLength - along) / totalLength else 0.0)
        val index = currentStepIndexFor(along)
        val stepStart = stepStarts[index]
        val stepEnd = stepStarts.getOrNull(index + 1) ?: totalLength
        val stepLength = max(stepEnd - stepStart, 1e-6)
        val fractionLeft = min(1.0, max(0.0, (stepEnd - along) / stepLength))
        val later = (index + 1 until steps.size).sumOf { steps[it].durationSeconds }
        return steps[index].durationSeconds * fractionLeft + later
    }

    private fun buildCumulativeDistances(): DoubleArray {
        if (geometry.isEmpty()) return DoubleArray(0)
        val result = DoubleArray(geometry.size)
        for (i in 1 until geometry.size) {
            result[i] = result[i - 1] + geometry[i - 1].distanceTo(geometry[i])
        }
        return result
    }

    private fun buildStepStarts(): DoubleArray {
        if (steps.isEmpty()) return DoubleArray(0)
        val stepTotal = steps.sumOf { it.distanceMeters }
        val scale = if (stepTotal > 0.0) totalLength / stepTotal else 0.0
        val starts = DoubleArray(steps.size)
        var running = 0.0
        for (i in steps.indices) {
            starts[i] = running
            running += steps[i].distanceMeters * scale
        }
        return starts
    }

    private data class Snap(val point: GeoPoint, val distanceMeters: Double, val alongMeters: Double)

    private fun snapToRoute(location: GeoPoint): Snap? {
        if (geometry.isEmpty()) return null
        if (geometry.size == 1) return Snap(geometry[0], location.distanceTo(geometry[0]), 0.0)
        var best: Snap? = null
        for (i in 0 until geometry.size - 1) {
            val candidate = projectOnSegment(location, geometry[i], geometry[i + 1], cumulative[i])
            if (best == null || candidate.distanceMeters < best.distanceMeters) best = candidate
        }
        return best
    }

    private fun projectOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint, alongAtA: Double): Snap {
        val latScale = METERS_PER_DEGREE
        val lngScale = METERS_PER_DEGREE * cos(Math.toRadians(a.lat))
        val bx = (b.lng - a.lng) * lngScale
        val by = (b.lat - a.lat) * latScale
        val px = (p.lng - a.lng) * lngScale
        val py = (p.lat - a.lat) * latScale
        val lengthSquared = bx * bx + by * by
        val t = if (lengthSquared == 0.0) 0.0 else ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
        val projected = GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
        val distance = hypot(px - bx * t, py - by * t)
        return Snap(projected, distance, alongAtA + a.distanceTo(b) * t)
    }

    companion object {
        const val DEFAULT_OFF_ROUTE_METERS = 50.0
        const val DEFAULT_OFF_ROUTE_FIXES = 3
        const val DEFAULT_ARRIVAL_METERS = 30.0
        private const val METERS_PER_DEGREE = 111_319.49
    }
}
