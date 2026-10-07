package com.raahi.app.domain.usecase

import com.raahi.app.domain.model.Trip

enum class TripIssue { TOO_FEW_STOPS, TOO_MANY_STOPS, EMPTY_STOP, MULTIPLE_CURRENT_LOCATIONS, CONSECUTIVE_DUPLICATE_STOPS }

data class TripValidation(val issues: List<TripIssue>) {
    val isValid: Boolean get() = issues.isEmpty()
}

class TripValidator {
    fun validate(trip: Trip): TripValidation {
        val stops = trip.stops.sortedBy { it.order }
        val issues = buildList {
            if (stops.size < MIN_STOPS) add(TripIssue.TOO_FEW_STOPS)
            if (stops.size > Trip.MAX_STOPS) add(TripIssue.TOO_MANY_STOPS)
            if (stops.any { it.place == null && !it.useCurrentLocation }) add(TripIssue.EMPTY_STOP)
            if (stops.count { it.useCurrentLocation } > 1) add(TripIssue.MULTIPLE_CURRENT_LOCATIONS)
            if (stops.zipWithNext().any { (a, b) -> isSameStop(a.place?.id, b.place?.id) }) {
                add(TripIssue.CONSECUTIVE_DUPLICATE_STOPS)
            }
        }
        return TripValidation(issues)
    }

    private fun isSameStop(first: String?, second: String?): Boolean = first != null && first == second

    private companion object {
        const val MIN_STOPS = 2
    }
}
