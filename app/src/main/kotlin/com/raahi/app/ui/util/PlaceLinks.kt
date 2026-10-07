package com.raahi.app.ui.util

import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.Place
import com.raahi.app.navigation.DeepLinks
import java.net.URLEncoder
import java.util.Locale

object PlaceLinks {

    fun coordinatesText(point: GeoPoint): String =
        String.format(Locale.US, "%.5f, %.5f", point.lat, point.lng)

    fun mapsLink(point: GeoPoint): String =
        String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", point.lat, point.lng)

    fun deepLink(place: Place): String {
        val query = buildList {
            add(String.format(Locale.US, "lat=%.6f", place.point.lat))
            add(String.format(Locale.US, "lng=%.6f", place.point.lng))
            if (place.name.isNotBlank()) add("name=${encode(place.name)}")
            place.address?.takeIf { it.isNotBlank() }?.let { add("address=${encode(it)}") }
            place.category?.let { add("category=${it.name}") }
        }
        return "${DeepLinks.PLACE_BASE}?${query.joinToString("&")}"
    }

    fun placeShareText(place: Place, displayName: String): String = buildString {
        appendLine(displayName)
        place.address?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        appendLine(mapsLink(place.point))
        append(deepLink(place))
    }

    fun locationShareText(header: String, point: GeoPoint): String = buildString {
        appendLine(header)
        appendLine(coordinatesText(point))
        append(mapsLink(point))
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

object TripLinks {
    fun deepLink(tripId: Long): String = "${DeepLinks.TRIP_BASE}/$tripId"

    fun shareText(header: String, stopLines: List<String>, openLine: String): String = buildString {
        appendLine(header)
        stopLines.forEach { appendLine(it) }
        append(openLine)
    }
}
